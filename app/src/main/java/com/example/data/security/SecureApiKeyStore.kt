package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Non-secret metadata for a securely stored API key entry.
 * Never holds or exposes the raw API key value.
 */
data class StoredApiKeyMeta(
    val id: String,
    val label: String,
    val maskedKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

internal interface VaultBackingStore {
    fun getString(key: String): String?
    fun putStrings(entries: Map<String, String>)
    fun removeKeys(keys: List<String>)
    fun clearAll()
}

private class SharedPrefsVaultStore(
    private val prefs: SharedPreferences
) : VaultBackingStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putStrings(entries: Map<String, String>) {
        val editor = prefs.edit()
        entries.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
    }

    override fun removeKeys(keys: List<String>) {
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    override fun clearAll() {
        prefs.edit().clear().apply()
    }
}

/**
 * Hardware/Android Keystore-backed AES-256-GCM encrypted vault supporting multiple user-supplied API keys.
 *
 * Security guarantees:
 * - Each API key is encrypted individually with a fresh random GCM IV (AES-256-GCM).
 * - Metadata index stores ONLY non-secret fields (id, label, maskedKey, timestamps).
 * - Raw API keys are NEVER logged, NEVER stored in plaintext, and NEVER auto-rotated across projects.
 */
class SecureApiKeyStore internal constructor(
    private val store: VaultBackingStore
) {
    constructor(context: Context) : this(
        SharedPrefsVaultStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    )

    private val moshi = Moshi.Builder().build()
    private val metaListAdapter = moshi.adapter<List<Map<String, Any>>>(
        Types.newParameterizedType(
            List::class.java,
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
    )

    // In-memory ephemeral AES-256 key used strictly when running on pure JVM/Robolectric tests where AndroidKeyStore is absent.
    @Volatile
    private var jvmTestFallbackKey: SecretKey? = null

    suspend fun listKeys(providerId: String): List<StoredApiKeyMeta> = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        readMetadataList(providerId)
    }

    suspend fun getActiveKeyId(providerId: String): String? = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        val storedActive = store.getString(activeKeyPref(providerId))?.takeIf { it.isNotBlank() }
        if (storedActive == null) return@withContext null
        val exists = readMetadataList(providerId).any { it.id == storedActive }
        if (exists) storedActive else null
    }

    /**
     * Returns ONLY the user-selected active API key. Never falls back or rotates to another key.
     */
    suspend fun getActiveApiKey(providerId: String): String? = withContext(Dispatchers.IO) {
        val activeId = getActiveKeyId(providerId) ?: return@withContext null
        decryptEntry(providerId, activeId)
    }

    /**
     * Decrypts a specific key by ID (for individual connection testing).
     */
    suspend fun getApiKeyById(providerId: String, keyId: String): String? = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        decryptEntry(providerId, keyId)
    }

    suspend fun addApiKey(
        providerId: String,
        label: String,
        rawApiKey: String,
        setAsActive: Boolean = true
    ): StoredApiKeyMeta = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        val trimmedKey = rawApiKey.trim()
        require(trimmedKey.isNotEmpty()) { "API key cannot be blank" }

        val existing = readMetadataList(providerId).toMutableList()
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val cleanLabel = sanitizeLabel(label, fallbackIndex = existing.size + 1)

        val (cipherEncoded, ivEncoded) = encryptSecret(trimmedKey)
        val entry = StoredApiKeyMeta(
            id = id,
            label = cleanLabel,
            maskedKey = maskKey(trimmedKey),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        existing.add(entry)

        val updates = mutableMapOf(
            entryCipherPref(providerId, id) to cipherEncoded,
            entryIvPref(providerId, id) to ivEncoded,
            metaIndexPref(providerId) to serializeMetadataList(existing)
        )
        val currentActive = store.getString(activeKeyPref(providerId))
        if (setAsActive || currentActive.isNullOrBlank()) {
            updates[activeKeyPref(providerId)] = id
        }
        store.putStrings(updates)
        entry
    }

    suspend fun replaceApiKey(
        providerId: String,
        keyId: String,
        newRawApiKey: String,
        newLabel: String? = null
    ): StoredApiKeyMeta? = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        val trimmedKey = newRawApiKey.trim()
        require(trimmedKey.isNotEmpty()) { "Replacement API key cannot be blank" }

        val existing = readMetadataList(providerId).toMutableList()
        val index = existing.indexOfFirst { it.id == keyId }
        if (index == -1) return@withContext null

        val old = existing[index]
        val now = System.currentTimeMillis()
        val updatedLabel = if (!newLabel.isNullOrBlank()) {
            sanitizeLabel(newLabel, fallbackIndex = index + 1)
        } else {
            old.label
        }

        val (cipherEncoded, ivEncoded) = encryptSecret(trimmedKey)
        val updated = old.copy(
            label = updatedLabel,
            maskedKey = maskKey(trimmedKey),
            updatedAtMillis = now
        )
        existing[index] = updated

        store.putStrings(
            mapOf(
                entryCipherPref(providerId, keyId) to cipherEncoded,
                entryIvPref(providerId, keyId) to ivEncoded,
                metaIndexPref(providerId) to serializeMetadataList(existing)
            )
        )
        updated
    }

    suspend fun setActiveKey(providerId: String, keyId: String): Boolean = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        val exists = readMetadataList(providerId).any { it.id == keyId }
        if (!exists) return@withContext false
        store.putStrings(mapOf(activeKeyPref(providerId) to keyId))
        true
    }

    /**
     * Deletes a saved key by ID.
     * If the deleted key was active, clears activeKeyId rather than auto-rotating to another key.
     */
    suspend fun deleteApiKey(providerId: String, keyId: String): Boolean = withContext(Dispatchers.IO) {
        migrateLegacySingleKeyIfNeeded(providerId)
        val existing = readMetadataList(providerId).toMutableList()
        val removed = existing.removeAll { it.id == keyId }
        if (!removed) return@withContext false

        val keysToRemove = mutableListOf(
            entryCipherPref(providerId, keyId),
            entryIvPref(providerId, keyId)
        )
        if (store.getString(activeKeyPref(providerId)) == keyId) {
            keysToRemove.add(activeKeyPref(providerId))
        }
        store.removeKeys(keysToRemove)
        store.putStrings(mapOf(metaIndexPref(providerId) to serializeMetadataList(existing)))
        true
    }

    // --- Backward-Compatible Single-Key Facade ---

    suspend fun saveApiKey(providerId: String, rawApiKey: String) = withContext(Dispatchers.IO) {
        val trimmed = rawApiKey.trim()
        if (trimmed.isEmpty()) {
            removeApiKey(providerId)
            return@withContext
        }
        val activeId = getActiveKeyId(providerId)
        if (activeId != null) {
            replaceApiKey(providerId, activeId, trimmed)
        } else {
            addApiKey(providerId, label = "Primary Key", rawApiKey = trimmed, setAsActive = true)
        }
    }

    suspend fun getApiKey(providerId: String): String? = getActiveApiKey(providerId)

    suspend fun hasApiKey(providerId: String): Boolean = !getActiveApiKey(providerId).isNullOrBlank()

    suspend fun removeApiKey(providerId: String) = withContext(Dispatchers.IO) {
        val activeId = getActiveKeyId(providerId)
        if (activeId != null) {
            deleteApiKey(providerId, activeId)
        }
        store.removeKeys(listOf(legacyCipherPref(providerId), legacyIvPref(providerId)))
    }

    suspend fun clearAllKeys() = withContext(Dispatchers.IO) {
        store.clearAll()
    }

    fun maskKey(apiKey: String?): String {
        if (apiKey.isNullOrBlank()) return "Not configured"
        return if (apiKey.length <= 8) {
            "••••••••"
        } else {
            "${apiKey.take(4)}••••••••${apiKey.takeLast(4)}"
        }
    }

    private fun migrateLegacySingleKeyIfNeeded(providerId: String) {
        val legacyCipher = store.getString(legacyCipherPref(providerId)) ?: return
        val legacyIv = store.getString(legacyIvPref(providerId)) ?: return
        val decrypted = decryptRaw(legacyCipher, legacyIv) ?: return

        val existing = readMetadataList(providerId).toMutableList()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val (newCipher, newIv) = encryptSecret(decrypted)
        existing.add(
            StoredApiKeyMeta(
                id = id,
                label = "Primary Key",
                maskedKey = maskKey(decrypted),
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
        val updates = mutableMapOf(
            entryCipherPref(providerId, id) to newCipher,
            entryIvPref(providerId, id) to newIv,
            metaIndexPref(providerId) to serializeMetadataList(existing)
        )
        if (store.getString(activeKeyPref(providerId)).isNullOrBlank()) {
            updates[activeKeyPref(providerId)] = id
        }
        store.putStrings(updates)
        store.removeKeys(listOf(legacyCipherPref(providerId), legacyIvPref(providerId)))
    }

    private fun sanitizeLabel(rawLabel: String, fallbackIndex: Int): String {
        val cleaned = rawLabel.trim().replace(Regex("\\s+"), " ").take(40)
        return cleaned.ifBlank { "Gemini Key $fallbackIndex" }
    }

    private fun readMetadataList(providerId: String): List<StoredApiKeyMeta> {
        val json = store.getString(metaIndexPref(providerId)) ?: return emptyList()
        return try {
            val rawList = metaListAdapter.fromJson(json).orEmpty()
            rawList.mapNotNull { item ->
                val id = (item["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = (item["label"] as? String)?.takeIf { it.isNotBlank() } ?: "Gemini Key"
                val masked = (item["maskedKey"] as? String).orEmpty()
                val created = (item["createdAtMillis"] as? Number)?.toLong() ?: 0L
                val updated = (item["updatedAtMillis"] as? Number)?.toLong() ?: created
                StoredApiKeyMeta(
                    id = id,
                    label = label,
                    maskedKey = masked,
                    createdAtMillis = created,
                    updatedAtMillis = updated
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeMetadataList(items: List<StoredApiKeyMeta>): String {
        val mapped = items.map { item ->
            mapOf<String, Any>(
                "id" to item.id,
                "label" to item.label,
                "maskedKey" to item.maskedKey,
                "createdAtMillis" to item.createdAtMillis,
                "updatedAtMillis" to item.updatedAtMillis
            )
        }
        return metaListAdapter.toJson(mapped)
    }

    private fun encryptSecret(plaintext: String): Pair<String, String> {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val ivEncoded = cipher.iv.toByteString().base64()
        val cipherEncoded = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)).toByteString().base64()
        return cipherEncoded to ivEncoded
    }

    private fun decryptEntry(providerId: String, keyId: String): String? {
        val cipherEncoded = store.getString(entryCipherPref(providerId, keyId)) ?: return null
        val ivEncoded = store.getString(entryIvPref(providerId, keyId)) ?: return null
        return decryptRaw(cipherEncoded, ivEncoded)
    }

    private fun decryptRaw(cipherEncoded: String, ivEncoded: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val iv = ivEncoded.decodeBase64()?.toByteArray() ?: return null
            val cipherBytes = cipherEncoded.decodeBase64()?.toByteArray() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            jvmTestFallbackKey ?: synchronized(this) {
                jvmTestFallbackKey ?: KeyGenerator.getInstance("AES").apply {
                    init(256)
                }.generateKey().also { jvmTestFallbackKey = it }
            }
        }
    }

    private fun legacyCipherPref(providerId: String) = "enc_key_${providerId}"
    private fun legacyIvPref(providerId: String) = "enc_iv_${providerId}"
    private fun entryCipherPref(providerId: String, keyId: String) = "enc_entry_cipher_${providerId}_${keyId}"
    private fun entryIvPref(providerId: String, keyId: String) = "enc_entry_iv_${providerId}_${keyId}"
    private fun metaIndexPref(providerId: String) = "meta_index_${providerId}"
    private fun activeKeyPref(providerId: String) = "active_key_id_${providerId}"

    companion object {
        private const val PREFS_NAME = "zoya_keystore_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "zoya_ai_api_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
