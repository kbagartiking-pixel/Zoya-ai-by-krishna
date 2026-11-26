package com.example

import com.example.ai.AiStreamEvent
import com.example.ai.AiValidationResult
import com.example.ai.ChatTurn
import com.example.ai.GeminiAiProvider
import com.example.ai.ImageAttachment
import com.example.ai.PRIMARY_GEMINI_MODEL_ID
import com.example.automation.CommandRouter
import com.example.automation.ZoyaIntent
import com.example.voice.DetectedLanguage
import com.example.voice.LanguageDetector
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ExampleUnitTest {

    @Test
    fun `primary Gemini model and endpoints match official gemini-3_8-flash with zero 2_0 references`() {
        val provider = GeminiAiProvider()
        assertEquals("gemini-3.8-flash", PRIMARY_GEMINI_MODEL_ID)
        assertEquals("gemini-3.8-flash", provider.primaryModelId)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
            provider.buildGenerateEndpoint(PRIMARY_GEMINI_MODEL_ID)
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:streamGenerateContent?alt=sse",
            provider.buildStreamEndpoint(PRIMARY_GEMINI_MODEL_ID)
        )
        assertTrue(provider.supportedModels.first().isPrimary)
        assertEquals("gemini-3.8-flash", provider.supportedModels.first().id)
        assertFalse(provider.supportedModels.any { it.id.contains("2.0") || it.id.contains("1.5") })
    }

    @Test
    fun `payload includes multimodal inlineData and function calling tools`() {
        val provider = GeminiAiProvider()
        val json = provider.buildChatPayloadJson(
            systemPrompt = "You are Zoya",
            history = listOf(ChatTurn("user", "Hi"), ChatTurn("model", "Hello!")),
            userMessage = "What is in this photo?",
            imageAttachment = ImageAttachment("image/jpeg", "YWJjMTIz", "Sample")
        )
        assertTrue(json.contains("inlineData"))
        assertTrue(json.contains("image/jpeg"))
        assertTrue(json.contains("functionDeclarations"))
        assertTrue(json.contains("open_app"))
        assertTrue(json.contains("create_reminder"))
    }

    @Test
    fun `SSE parser extracts text chunks, function calls, and inline API errors`() {
        val provider = GeminiAiProvider()
        val fnChunk = """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"open_app","args":{"app_name":"YouTube"}}}]}}]}"""
        val parsedFn = provider.parseSseChunk(fnChunk)
        assertNotNull(parsedFn.functionCall)
        assertEquals("open_app", parsedFn.functionCall?.name)
        assertEquals("YouTube", parsedFn.functionCall?.args?.get("app_name"))

        val textChunk = """{"candidates":[{"content":{"parts":[{"text":"Opening YouTube now."}]}}]}"""
        val parsedText = provider.parseSseChunk(textChunk)
        assertEquals("Opening YouTube now.", parsedText.text)

        val inlineErr = """{"error":{"code":503,"status":"UNAVAILABLE","message":"This model is currently experiencing high demand."}}"""
        val parsedErr = provider.parseSseChunk(inlineErr)
        assertTrue(parsedErr.inlineError is AiValidationResult.ServerOverloaded)
        assertEquals(
            "This model is currently experiencing high demand.",
            parsedErr.inlineError?.rawApiMessage
        )
    }

    @Test
    fun `classifies real Gemini API HTTP errors separately and preserves exact raw API message`() {
        val provider = GeminiAiProvider()

        // 1. Temporary Server Overload (503 high demand) -> retryable
        val highDemandRaw = "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later."
        val overload = provider.classifyHttpError(
            code = 503,
            rawBody = """{"error":{"code":503,"status":"UNAVAILABLE","message":"$highDemandRaw"}}""",
            modelId = PRIMARY_GEMINI_MODEL_ID
        )
        assertTrue(overload is AiValidationResult.ServerOverloaded)
        assertEquals(503, overload.httpCode)
        assertEquals(highDemandRaw, overload.rawApiMessage)
        assertTrue(provider.shouldRetryError(overload, 503))

        // 2. Hard Quota Exceeded (429 quota/billing) -> NOT retryable, distinct from overload
        val quotaRaw = "You exceeded your current quota, please check your plan and billing details."
        val quota = provider.classifyHttpError(
            code = 429,
            rawBody = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"$quotaRaw"}}""",
            modelId = PRIMARY_GEMINI_MODEL_ID
        )
        assertTrue(quota is AiValidationResult.QuotaExceeded)
        assertEquals(429, quota.httpCode)
        assertEquals(quotaRaw, quota.rawApiMessage)
        assertFalse(provider.shouldRetryError(quota, 429))

        // 3. Temporary Rate-Limit Spike (429 per-minute spike) -> ServerOverloaded & retryable
        val spike = provider.classifyHttpError(
            code = 429,
            rawBody = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Too many requests per minute, please try again in a few seconds."}}""",
            modelId = PRIMARY_GEMINI_MODEL_ID
        )
        assertTrue(spike is AiValidationResult.ServerOverloaded)
        assertTrue(provider.shouldRetryError(spike, 429))

        // 4. Invalid API Key (400 / 401) -> NOT retryable
        val invalidKeyRaw = "API key not valid. Please pass a valid API key."
        val invalidKey = provider.classifyHttpError(
            code = 400,
            rawBody = """{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"$invalidKeyRaw"}}""",
            modelId = PRIMARY_GEMINI_MODEL_ID
        )
        assertTrue(invalidKey is AiValidationResult.InvalidKey)
        assertEquals(400, invalidKey.httpCode)
        assertEquals(invalidKeyRaw, invalidKey.rawApiMessage)
        assertFalse(provider.shouldRetryError(invalidKey, 400))

        // 5. Unsupported / Unavailable Model (404) -> NOT retryable, never silent downgrade
        val unavailableRaw = "models/gemini-3.8-flash is not found for API version v1beta"
        val unavailable = provider.classifyHttpError(
            code = 404,
            rawBody = """{"error":{"code":404,"status":"NOT_FOUND","message":"$unavailableRaw"}}""",
            modelId = PRIMARY_GEMINI_MODEL_ID
        )
        assertTrue(unavailable is AiValidationResult.ModelUnavailable)
        assertEquals("gemini-3.8-flash", (unavailable as AiValidationResult.ModelUnavailable).modelId)
        assertEquals(unavailableRaw, unavailable.rawApiMessage)
        assertFalse(provider.shouldRetryError(unavailable, 404))

        // 6. Timeout (504 / DEADLINE_EXCEEDED) -> retryable
        val timeout = provider.classifyHttpError(
            code = 504,
            rawBody = """{"error":{"code":504,"status":"DEADLINE_EXCEEDED","message":"Deadline exceeded waiting for model response."}}""",
            modelId = PRIMARY_GEMINI_MODEL_ID
        )
        assertTrue(timeout is AiValidationResult.TimeoutError)
        assertTrue(provider.shouldRetryError(timeout, 504))
    }

    @Test
    fun `exponential backoff scales 1s to 2s to 4s`() {
        val provider = GeminiAiProvider()
        assertEquals(1000L, provider.computeBackoffDelayMs(1))
        assertEquals(2000L, provider.computeBackoffDelayMs(2))
        assertEquals(4000L, provider.computeBackoffDelayMs(3))
    }

    @Test
    fun `retries transient 503 with backoff and succeeds when server recovers`() = runBlocking {
        val attempts = AtomicInteger(0)
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val count = attempts.incrementAndGet()
                val req = chain.request()
                if (count <= 2) {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body(
                            """{"error":{"code":503,"status":"UNAVAILABLE","message":"This model is currently experiencing high demand."}}"""
                                .toResponseBody("application/json".toMediaType())
                        )
                        .build()
                } else {
                    val sseBody = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello from Gemini 3.8 Flash!\"}]}}]}\n\n"
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(sseBody.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val provider = GeminiAiProvider(client = mockClient, retryBaseDelayMs = 1L)
        val events = provider.streamChat(
            apiKey = "AIzaSyTestKey12345678901234567890",
            modelId = PRIMARY_GEMINI_MODEL_ID,
            systemPrompt = "You are Zoya",
            history = emptyList(),
            userMessage = "Hello!"
        ).toList()

        assertEquals(3, attempts.get())
        val retries = events.filterIsInstance<AiStreamEvent.Retrying>()
        assertEquals(2, retries.size)
        assertEquals(1, retries[0].attempt)
        assertEquals(2, retries[1].attempt)
        val completed = events.filterIsInstance<AiStreamEvent.Completed>().firstOrNull()
        assertNotNull(completed)
        assertEquals("Hello from Gemini 3.8 Flash!", completed?.fullText)
    }

    @Test
    fun `enforces strict retry cap on persistent 503 and never retries 401 or hard quota 429`() = runBlocking {
        // Case A: Persistent 503 stops after exactly 1 initial + 3 retries = 4 total attempts
        val overloadAttempts = AtomicInteger(0)
        val overloadClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                overloadAttempts.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body(
                        """{"error":{"code":503,"status":"UNAVAILABLE","message":"Model overloaded"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            })
            .build()

        val overloadProvider = GeminiAiProvider(client = overloadClient, retryBaseDelayMs = 1L)
        val overloadEvents = overloadProvider.streamChat(
            apiKey = "AIzaSyTestKey12345678901234567890",
            modelId = PRIMARY_GEMINI_MODEL_ID,
            systemPrompt = "You are Zoya",
            history = emptyList(),
            userMessage = "Test"
        ).toList()

        assertEquals(1 + GeminiAiProvider.MAX_RETRY_ATTEMPTS, overloadAttempts.get())
        assertEquals(GeminiAiProvider.MAX_RETRY_ATTEMPTS, overloadEvents.filterIsInstance<AiStreamEvent.Retrying>().size)
        val finalErr = overloadEvents.filterIsInstance<AiStreamEvent.Failed>().firstOrNull()?.error
        assertTrue(finalErr is AiValidationResult.ServerOverloaded)

        // Case B: Invalid key (401) stops immediately with 0 retries
        val authAttempts = AtomicInteger(0)
        val authClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                authAttempts.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(
                        """{"error":{"code":401,"status":"UNAUTHENTICATED","message":"API key invalid"}}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            })
            .build()

        val authProvider = GeminiAiProvider(client = authClient, retryBaseDelayMs = 1L)
        val authEvents = authProvider.streamChat(
            apiKey = "AIzaSyInvalidKey12345678901234567",
            modelId = PRIMARY_GEMINI_MODEL_ID,
            systemPrompt = "You are Zoya",
            history = emptyList(),
            userMessage = "Test"
        ).toList()

        assertEquals(1, authAttempts.get())
        assertTrue(authEvents.filterIsInstance<AiStreamEvent.Retrying>().isEmpty())
        assertTrue(authEvents.filterIsInstance<AiStreamEvent.Failed>().first().error is AiValidationResult.InvalidKey)
    }

    @Test
    fun `routes Hinglish commands and detects languages properly`() {
        val yt = CommandRouter.route("Hey Zoya, YouTube kholo")
        assertTrue(yt is ZoyaIntent.OpenApp)
        assertEquals("YouTube", (yt as ZoyaIntent.OpenApp).appQuery)

        assertEquals(ZoyaIntent.ReadBattery, CommandRouter.route("Zoya battery kitni hai?"))
        assertEquals(DetectedLanguage.HINGLISH, LanguageDetector.detect("Zoya battery kitni hai?"))

        val reminder = CommandRouter.route("Zoya, 20 minute baad reminder dena")
        assertTrue(reminder is ZoyaIntent.CreateReminder)
        assertEquals(20, (reminder as ZoyaIntent.CreateReminder).delayMinutes)

        assertEquals(ZoyaIntent.OpenSetting("wifi"), CommandRouter.route("Open Wi-Fi settings"))
        assertEquals(ZoyaIntent.PrepareCall("9876543210"), CommandRouter.route("Call 9876543210"))
        assertEquals(DetectedLanguage.HINDI, LanguageDetector.detect("हे जोया बैटरी कितनी है"))
        assertEquals(DetectedLanguage.ENGLISH, LanguageDetector.detect("What is quantum computing?"))
    }
}
