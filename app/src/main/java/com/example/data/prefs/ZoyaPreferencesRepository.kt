package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.PRIMARY_GEMINI_MODEL_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.zoyaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "zoya_preferences"
)

enum class ZoyaLanguageMode(val displayName: String, val subtitle: String, val localeTag: String) {
    AUTO("Auto Detect", "English, Hindi & Hinglish automatically", "en-IN"),
    HINGLISH("Hinglish", "Natural Indian English + Hindi mix", "en-IN"),
    HINDI("Hindi (हिन्दी)", "Devanagari Hindi voice & replies", "hi-IN"),
    ENGLISH("English", "Crisp English voice & replies", "en-US")
}

data class ZoyaSettings(
    val providerId: String = "gemini",
    val selectedModelId: String = PRIMARY_GEMINI_MODEL_ID,
    val languageMode: ZoyaLanguageMode = ZoyaLanguageMode.AUTO,
    val selectedVoiceName: String = "",
    val speechRate: Float = 1.02f,
    val speechPitch: Float = 1.08f,
    val wakeWordEnabled: Boolean = true,
    val continuousConversation: Boolean = true,
    val backgroundAssistantEnabled: Boolean = false,
    val setupSkippedOnce: Boolean = false,
    val saveHistoryEnabled: Boolean = true
)

class ZoyaPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.zoyaDataStore

    val settingsFlow: Flow<ZoyaSettings> = dataStore.data.map { prefs ->
        val langName = prefs[KEY_LANGUAGE_MODE] ?: ZoyaLanguageMode.AUTO.name
        val lang = runCatching { ZoyaLanguageMode.valueOf(langName) }.getOrDefault(ZoyaLanguageMode.AUTO)
        val rawModel = prefs[KEY_MODEL_ID]?.trim().orEmpty()
        // Keep primary gemini-3.8-flash by default; allow explicit user-selected alternatives (never 2.0-flash)
        val safeModelId = when {
            rawModel.isEmpty() -> PRIMARY_GEMINI_MODEL_ID
            rawModel.contains("2.0") || rawModel.contains("1.5") -> PRIMARY_GEMINI_MODEL_ID
            else -> rawModel
        }
        ZoyaSettings(
            providerId = prefs[KEY_PROVIDER_ID] ?: "gemini",
            selectedModelId = safeModelId,
            languageMode = lang,
            selectedVoiceName = prefs[KEY_VOICE_NAME] ?: "",
            speechRate = prefs[KEY_SPEECH_RATE] ?: 1.02f,
            speechPitch = prefs[KEY_SPEECH_PITCH] ?: 1.08f,
            wakeWordEnabled = prefs[KEY_WAKE_WORD] ?: true,
            continuousConversation = prefs[KEY_CONTINUOUS_CONVERSATION] ?: true,
            backgroundAssistantEnabled = prefs[KEY_BACKGROUND_ASSISTANT] ?: false,
            setupSkippedOnce = prefs[KEY_SETUP_SKIPPED] ?: false,
            saveHistoryEnabled = prefs[KEY_SAVE_HISTORY] ?: true
        )
    }

    suspend fun setModel(modelId: String) {
        val cleaned = modelId.trim()
        val safe = if (cleaned.isEmpty() || cleaned.contains("2.0") || cleaned.contains("1.5")) {
            PRIMARY_GEMINI_MODEL_ID
        } else {
            cleaned
        }
        dataStore.edit { it[KEY_MODEL_ID] = safe }
    }

    suspend fun setLanguageMode(mode: ZoyaLanguageMode) {
        dataStore.edit { it[KEY_LANGUAGE_MODE] = mode.name }
    }

    suspend fun setVoiceName(voiceName: String) {
        dataStore.edit { it[KEY_VOICE_NAME] = voiceName }
    }

    suspend fun setSpeechRate(rate: Float) {
        dataStore.edit { it[KEY_SPEECH_RATE] = rate.coerceIn(0.6f, 1.8f) }
    }

    suspend fun setSpeechPitch(pitch: Float) {
        dataStore.edit { it[KEY_SPEECH_PITCH] = pitch.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_WAKE_WORD] = enabled }
    }

    suspend fun setContinuousConversation(enabled: Boolean) {
        dataStore.edit { it[KEY_CONTINUOUS_CONVERSATION] = enabled }
    }

    suspend fun setBackgroundAssistantEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_ASSISTANT] = enabled }
    }

    suspend fun setSetupSkippedOnce(skipped: Boolean) {
        dataStore.edit { it[KEY_SETUP_SKIPPED] = skipped }
    }

    suspend fun setSaveHistoryEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SAVE_HISTORY] = enabled }
    }

    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        private val KEY_MODEL_ID = stringPreferencesKey("model_id")
        private val KEY_LANGUAGE_MODE = stringPreferencesKey("language_mode")
        private val KEY_VOICE_NAME = stringPreferencesKey("voice_name")
        private val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
        private val KEY_SPEECH_PITCH = floatPreferencesKey("speech_pitch")
        private val KEY_WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        private val KEY_CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        private val KEY_BACKGROUND_ASSISTANT = booleanPreferencesKey("background_assistant_enabled")
        private val KEY_SETUP_SKIPPED = booleanPreferencesKey("setup_skipped_once")
        private val KEY_SAVE_HISTORY = booleanPreferencesKey("save_history_enabled")
    }
}
