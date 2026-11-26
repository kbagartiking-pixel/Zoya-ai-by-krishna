package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.automation.DeviceControlManager
import com.example.data.security.SecureApiKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Zoya AI branding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zoya AI", appName)
    }

    @Test
    fun `secure api key store encrypts saves masks and removes key`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureApiKeyStore(context)
        val sampleSecret = "test_sample_key_9876543210"

        store.saveApiKey("gemini", sampleSecret)
        assertTrue(store.hasApiKey("gemini"))
        assertEquals(sampleSecret, store.getApiKey("gemini"))

        // Ensure persisted preference does not store plaintext key
        val rawPrefs = context.getSharedPreferences("zoya_keystore_vault", Context.MODE_PRIVATE)
        val rawStored = rawPrefs.getString("enc_key_gemini", null)
        assertNotEquals(sampleSecret, rawStored)

        val masked = store.maskKey(sampleSecret)
        assertTrue(masked.startsWith("test"))
        assertTrue(masked.endsWith("3210"))
        assertFalse(masked.contains("sample_key"))

        store.removeApiKey("gemini")
        assertFalse(store.hasApiKey("gemini"))
        assertNull(store.getApiKey("gemini"))
    }

    @Test
    fun `device control manager reads telemetry and permissions safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceControlManager(context)
        val telemetry = manager.readDeviceTelemetry()
        val permissions = manager.readPermissions()

        assertTrue(telemetry.model.isNotBlank())
        assertTrue(telemetry.battery.percentage in 0..100)
        assertFalse(permissions.backgroundServiceRunning)
    }
}
