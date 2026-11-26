package com.example.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Official Google Gemini REST API provider with Gemini 3.8 Flash (`gemini-3.8-flash`) as primary model.
 *
 * Reliability guarantees:
 * - Parses and exposes real HTTP status codes and exact Google Gemini API `error.message` / `error.status`.
 * - Separates Quota Exceeded, Temporary Server Overload (503/500/502/504/rate-limit 429),
 *   Invalid API Key (401/403/400 API_KEY_INVALID), Unsupported Model (404), and Timeout errors.
 * - Automatic bounded retry with exponential backoff (1s -> 2s -> 4s, max 3 retries) ONLY for transient errors.
 * - Full coroutine cancellation support: cancelling immediately aborts the underlying OkHttp Call.
 * - NEVER silently downgrades or switches models.
 */
class GeminiAiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    internal val retryBaseDelayMs: Long = DEFAULT_RETRY_BASE_DELAY_MS
) : AiProvider {

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    override val id: String = "gemini"
    override val displayName: String = "Google Gemini API"
    override val primaryModelId: String = PRIMARY_GEMINI_MODEL_ID

    override val supportedModels: List<AiModelOption> = listOf(
        AiModelOption(
            id = PRIMARY_GEMINI_MODEL_ID,
            displayName = PRIMARY_GEMINI_MODEL_NAME,
            badge = "Primary Brain",
            isPrimary = true,
            description = "Primary fast multimodal model for text conversations, reasoning, image understanding, and Android function calling.",
            capabilities = listOf(
                "Text Conversations",
                "Multi-step Reasoning",
                "Image Understanding",
                "Function Calling",
                "Streaming Responses"
            ),
            supportsDirectLiveAudio = false,
            voiceArchitectureNote = "Uses modular STT → Gemini 3.8 Flash → TTS pipeline with synchronized anime lip-sync. Ready to plug in an optional Live Audio model separately."
        ),
        AiModelOption(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            badge = "Optional Alternative",
            isPrimary = false,
            description = "Optional user-selected alternative if Gemini 3.8 Flash is unavailable or experiencing temporary spikes.",
            capabilities = listOf(
                "Text Conversations",
                "Reasoning",
                "Image Understanding",
                "Function Calling",
                "Streaming Responses"
            ),
            supportsDirectLiveAudio = false,
            voiceArchitectureNote = "Uses modular STT → Gemini → TTS pipeline. Never selected automatically."
        ),
        AiModelOption(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            badge = "Optional Alternative",
            isPrimary = false,
            description = "Optional user-selected deep reasoning alternative when explicitly chosen by the user.",
            capabilities = listOf(
                "Deep Reasoning",
                "Text Conversations",
                "Image Understanding",
                "Function Calling",
                "Streaming Responses"
            ),
            supportsDirectLiveAudio = false,
            voiceArchitectureNote = "Uses modular STT → Gemini → TTS pipeline. Never selected automatically."
        )
    )

    fun buildGenerateEndpoint(modelId: String): String =
        "$BASE_URL/${modelId.trim().ifEmpty { primaryModelId }}:generateContent"

    fun buildStreamEndpoint(modelId: String): String =
        "$BASE_URL/${modelId.trim().ifEmpty { primaryModelId }}:streamGenerateContent?alt=sse"

    override suspend fun validateApiKey(apiKey: String, modelId: String): AiValidationResult =
        withContext(Dispatchers.IO) {
            val trimmedKey = apiKey.trim()
            val targetModel = modelId.trim().ifEmpty { primaryModelId }
            if (trimmedKey.isEmpty()) {
                return@withContext AiValidationResult.InvalidKey(
                    userMessage = "Please enter your Google Gemini API key before testing.",
                    rawApiMessage = null,
                    httpCode = null
                )
            }
            if (trimmedKey.length < 20) {
                return@withContext AiValidationResult.InvalidKey(
                    userMessage = "That API key looks too short. Paste the full Gemini API key from Google AI Studio.",
                    rawApiMessage = null,
                    httpCode = null
                )
            }

            val start = System.currentTimeMillis()
            val payloadMap = mapOf(
                "contents" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(mapOf("text" to "Reply OK"))
                    )
                ),
                "generationConfig" to mapOf(
                    "maxOutputTokens" to 12,
                    "temperature" to 0.0
                )
            )
            val request = Request.Builder()
                .url(buildGenerateEndpoint(targetModel))
                .addHeader("x-goog-api-key", trimmedKey)
                .addHeader("Content-Type", "application/json")
                .post(mapAdapter.toJson(payloadMap).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            var retryIndex = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                try {
                    val response = client.newCall(request).awaitCancellable()
                    response.use { res ->
                        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                        val responseBody = res.body?.string().orEmpty()
                        if (res.isSuccessful) {
                            val reportedVersion = extractModelVersion(responseBody).ifBlank { targetModel }
                            return@withContext AiValidationResult.Valid(
                                latencyMs = elapsed,
                                modelId = targetModel,
                                reportedModelVersion = reportedVersion
                            )
                        }
                        val classified = classifyHttpError(res.code, responseBody, targetModel, retryIndex)
                        if (shouldRetryError(classified, res.code) && retryIndex < MAX_VALIDATE_RETRIES) {
                            retryIndex++
                            delay(computeBackoffDelayMs(retryIndex))
                        } else {
                            return@withContext classified
                        }
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (e: SocketTimeoutException) {
                    if (retryIndex < MAX_VALIDATE_RETRIES) {
                        retryIndex++
                        delay(computeBackoffDelayMs(retryIndex))
                    } else {
                        return@withContext AiValidationResult.TimeoutError(
                            userMessage = "Connection to $targetModel timed out after ${retryIndex + 1} attempts.",
                            rawApiMessage = e.message,
                            retriesAttempted = retryIndex
                        )
                    }
                } catch (e: UnknownHostException) {
                    return@withContext AiValidationResult.NetworkError(
                        userMessage = "No internet connection detected. Connect to Wi-Fi or mobile data.",
                        rawApiMessage = e.message
                    )
                } catch (e: IOException) {
                    return@withContext AiValidationResult.NetworkError(
                        userMessage = "Network error while contacting Gemini API.",
                        rawApiMessage = e.message
                    )
                } catch (e: Exception) {
                    return@withContext AiValidationResult.ProviderError(
                        code = -1,
                        userMessage = "Unexpected error while validating $targetModel.",
                        rawApiMessage = e.message
                    )
                }
            }
            @Suppress("UNREACHABLE_CODE")
            AiValidationResult.ProviderError(-1, "Validation stopped.")
        }

    override fun streamChat(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        history: List<ChatTurn>,
        userMessage: String,
        imageAttachment: ImageAttachment?
    ): Flow<AiStreamEvent> = flow {
        val trimmedKey = apiKey.trim()
        val targetModel = modelId.trim().ifEmpty { primaryModelId }
        if (trimmedKey.isEmpty()) {
            emit(
                AiStreamEvent.Failed(
                    AiValidationResult.InvalidKey(
                        userMessage = "No Gemini API key configured. Enter your key in API Configuration.",
                        httpCode = null
                    )
                )
            )
            return@flow
        }

        val payloadJson = buildChatPayloadJson(
            systemPrompt = systemPrompt,
            history = history,
            userMessage = userMessage,
            imageAttachment = imageAttachment
        )
        val request = Request.Builder()
            .url(buildStreamEndpoint(targetModel))
            .addHeader("x-goog-api-key", trimmedKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var retryIndex = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                val response = client.newCall(request).awaitCancellable()
                response.use { res ->
                    if (!res.isSuccessful) {
                        val bodyStr = res.body?.string().orEmpty()
                        val classified = classifyHttpError(res.code, bodyStr, targetModel, retryIndex)

                        if (shouldRetryError(classified, res.code) && retryIndex < MAX_RETRY_ATTEMPTS) {
                            retryIndex++
                            val backoffMs = computeBackoffDelayMs(retryIndex)
                            emit(
                                AiStreamEvent.Retrying(
                                    attempt = retryIndex,
                                    maxRetries = MAX_RETRY_ATTEMPTS,
                                    delayMs = backoffMs,
                                    httpCode = res.code,
                                    reason = classified.rawApiMessage?.take(120)
                                        ?: "HTTP ${res.code} temporary error"
                                )
                            )
                            delay(backoffMs)
                            return@use
                        }

                        emit(AiStreamEvent.Failed(classified))
                        return@flow
                    }

                    val source = res.body?.source()
                    if (source == null) {
                        emit(
                            AiStreamEvent.Failed(
                                AiValidationResult.ProviderError(
                                    code = 204,
                                    userMessage = "Empty response stream received from $targetModel."
                                )
                            )
                        )
                        return@flow
                    }

                    val fullBuilder = StringBuilder()
                    var emittedFunctionCall = false

                    while (!source.exhausted()) {
                        currentCoroutineContext().ensureActive()
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val jsonStr = line.removePrefix("data:").trim()
                            if (jsonStr.isEmpty() || jsonStr == "[DONE]") continue
                            val parsed = parseSseChunk(jsonStr)
                            if (parsed.inlineError != null) {
                                emit(AiStreamEvent.Failed(parsed.inlineError))
                                return@flow
                            }
                            if (parsed.functionCall != null) {
                                emittedFunctionCall = true
                                emit(AiStreamEvent.FunctionCallRequested(parsed.functionCall))
                            }
                            if (parsed.text.isNotEmpty()) {
                                fullBuilder.append(parsed.text)
                                emit(AiStreamEvent.Delta(parsed.text))
                            }
                        }
                    }

                    val finalText = fullBuilder.toString().trim()
                    if (finalText.isNotEmpty()) {
                        emit(AiStreamEvent.Completed(finalText))
                    } else if (!emittedFunctionCall) {
                        emit(
                            AiStreamEvent.Failed(
                                AiValidationResult.ProviderError(
                                    code = 200,
                                    userMessage = "$targetModel returned an empty response. Tap Retry to try again."
                                )
                            )
                        )
                    }
                    return@flow
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: SocketTimeoutException) {
                if (retryIndex < MAX_RETRY_ATTEMPTS) {
                    retryIndex++
                    val backoffMs = computeBackoffDelayMs(retryIndex)
                    emit(
                        AiStreamEvent.Retrying(
                            attempt = retryIndex,
                            maxRetries = MAX_RETRY_ATTEMPTS,
                            delayMs = backoffMs,
                            httpCode = 504,
                            reason = "Request timed out"
                        )
                    )
                    delay(backoffMs)
                } else {
                    emit(
                        AiStreamEvent.Failed(
                            AiValidationResult.TimeoutError(
                                userMessage = "Request to $targetModel timed out after $MAX_RETRY_ATTEMPTS retries. Tap Retry to try again.",
                                rawApiMessage = e.message,
                                retriesAttempted = retryIndex
                            )
                        )
                    )
                    return@flow
                }
            } catch (e: UnknownHostException) {
                emit(
                    AiStreamEvent.Failed(
                        AiValidationResult.NetworkError(
                            userMessage = "No internet connection. Connect to Wi-Fi or mobile data, or use local phone commands offline.",
                            rawApiMessage = e.message
                        )
                    )
                )
                return@flow
            } catch (e: IOException) {
                if (retryIndex < MAX_RETRY_ATTEMPTS) {
                    retryIndex++
                    val backoffMs = computeBackoffDelayMs(retryIndex)
                    emit(
                        AiStreamEvent.Retrying(
                            attempt = retryIndex,
                            maxRetries = MAX_RETRY_ATTEMPTS,
                            delayMs = backoffMs,
                            httpCode = null,
                            reason = e.message ?: "Connection interrupted"
                        )
                    )
                    delay(backoffMs)
                } else {
                    emit(
                        AiStreamEvent.Failed(
                            AiValidationResult.NetworkError(
                                userMessage = "Network connection dropped while reaching $targetModel.",
                                rawApiMessage = e.message
                            )
                        )
                    )
                    return@flow
                }
            } catch (e: Exception) {
                emit(
                    AiStreamEvent.Failed(
                        AiValidationResult.ProviderError(
                            code = -1,
                            userMessage = "Unexpected error calling $targetModel.",
                            rawApiMessage = e.message
                        )
                    )
                )
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    internal fun computeBackoffDelayMs(retryAttempt: Int): Long {
        // Exponential backoff: 1s, 2s, 4s (capped at 8s)
        val shift = (retryAttempt - 1).coerceIn(0, 3)
        return (retryBaseDelayMs * (1L shl shift)).coerceAtMost(8000L)
    }

    internal fun shouldRetryError(result: AiValidationResult, httpCode: Int): Boolean {
        return when (result) {
            is AiValidationResult.ServerOverloaded -> true
            is AiValidationResult.TimeoutError -> true
            is AiValidationResult.ProviderError -> httpCode in RETRYABLE_HTTP_CODES
            is AiValidationResult.InvalidKey,
            is AiValidationResult.QuotaExceeded,
            is AiValidationResult.ModelUnavailable,
            is AiValidationResult.NetworkError,
            is AiValidationResult.Valid -> false
        }
    }

    internal fun buildChatPayloadJson(
        systemPrompt: String,
        history: List<ChatTurn>,
        userMessage: String,
        imageAttachment: ImageAttachment? = null
    ): String {
        val contents = mutableListOf<Map<String, Any>>()
        history.takeLast(12).forEach { turn ->
            contents.add(
                mapOf(
                    "role" to if (turn.role == "user") "user" else "model",
                    "parts" to listOf(mapOf("text" to turn.text))
                )
            )
        }

        val currentUserParts = mutableListOf<Map<String, Any>>()
        if (imageAttachment != null) {
            currentUserParts.add(
                mapOf(
                    "inlineData" to mapOf(
                        "mimeType" to imageAttachment.mimeType,
                        "data" to imageAttachment.base64Data
                    )
                )
            )
        }
        val promptText = userMessage.ifBlank { "Describe this image clearly and helpfully." }
        currentUserParts.add(mapOf("text" to promptText))

        contents.add(
            mapOf(
                "role" to "user",
                "parts" to currentUserParts
            )
        )

        val payload = mapOf(
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemPrompt))
            ),
            "contents" to contents,
            "tools" to buildZoyaFunctionTools(),
            "generationConfig" to mapOf(
                "temperature" to 0.7,
                "maxOutputTokens" to 700
            )
        )
        return mapAdapter.toJson(payload)
    }

    private fun buildZoyaFunctionTools(): List<Map<String, Any>> {
        val declarations = listOf(
            declareFunction(
                name = "open_app",
                description = "Open an installed Android application such as YouTube, Instagram, WhatsApp, Camera, Gallery, Chrome, or any app by name.",
                properties = mapOf("app_name" to "Name of the app to launch"),
                required = listOf("app_name")
            ),
            declareFunction(
                name = "open_setting",
                description = "Open an Android system settings page.",
                properties = mapOf(
                    "setting_key" to "One of: wifi, bluetooth, location, display, sound, battery, accessibility, notification_access"
                ),
                required = listOf("setting_key")
            ),
            declareFunction(
                name = "get_battery_status",
                description = "Read the phone's current battery percentage and charging status.",
                properties = emptyMap(),
                required = emptyList()
            ),
            declareFunction(
                name = "create_reminder",
                description = "Schedule a local Android reminder notification after a given delay in minutes.",
                properties = mapOf(
                    "title" to "Short reminder title",
                    "delay_minutes" to "Positive integer delay in minutes"
                ),
                required = listOf("title", "delay_minutes")
            ),
            declareFunction(
                name = "set_timer",
                description = "Set a countdown timer in the Android Clock app.",
                properties = mapOf(
                    "duration_seconds" to "Timer duration in seconds",
                    "label" to "Optional timer label"
                ),
                required = listOf("duration_seconds")
            ),
            declareFunction(
                name = "prepare_call",
                description = "Request user confirmation before opening the Android phone dialer for a number.",
                properties = mapOf("phone_number" to "Phone number or recipient"),
                required = listOf("phone_number")
            ),
            declareFunction(
                name = "prepare_sms",
                description = "Request user confirmation before preparing an SMS message.",
                properties = mapOf(
                    "phone_number" to "Recipient phone number",
                    "message" to "SMS body text"
                ),
                required = listOf("phone_number")
            )
        )
        return listOf(mapOf("functionDeclarations" to declarations))
    }

    private fun declareFunction(
        name: String,
        description: String,
        properties: Map<String, String>,
        required: List<String>
    ): Map<String, Any> {
        val propsObj = properties.mapValues { (_, desc) ->
            mapOf("type" to "STRING", "description" to desc)
        }
        val paramsObj = mutableMapOf<String, Any>(
            "type" to "OBJECT",
            "properties" to propsObj
        )
        if (required.isNotEmpty()) {
            paramsObj["required"] = required
        }
        return mapOf(
            "name" to name,
            "description" to description,
            "parameters" to paramsObj
        )
    }

    internal data class ParsedSseChunk(
        val text: String = "",
        val functionCall: AiFunctionCall? = null,
        val inlineError: AiValidationResult? = null
    )

    @Suppress("UNCHECKED_CAST")
    internal fun parseSseChunk(jsonStr: String, modelId: String = primaryModelId): ParsedSseChunk {
        return try {
            val root = mapAdapter.fromJson(jsonStr) ?: return ParsedSseChunk()
            if (root["error"] is Map<*, *>) {
                val errMap = root["error"] as Map<String, Any>
                val code = (errMap["code"] as? Number)?.toInt() ?: 500
                return ParsedSseChunk(inlineError = classifyHttpError(code, jsonStr, modelId, 0))
            }
            val candidates = root["candidates"] as? List<Map<String, Any>> ?: return ParsedSseChunk()
            if (candidates.isEmpty()) return ParsedSseChunk()
            val content = candidates[0]["content"] as? Map<String, Any> ?: return ParsedSseChunk()
            val parts = content["parts"] as? List<Map<String, Any>> ?: return ParsedSseChunk()

            val textBuilder = StringBuilder()
            var detectedCall: AiFunctionCall? = null

            for (part in parts) {
                val text = part["text"] as? String
                if (!text.isNullOrEmpty()) {
                    textBuilder.append(text)
                }
                val fnCall = part["functionCall"] as? Map<String, Any>
                if (fnCall != null && detectedCall == null) {
                    val fnName = (fnCall["name"] as? String)?.trim().orEmpty()
                    if (fnName.isNotEmpty()) {
                        val rawArgs = fnCall["args"] as? Map<String, Any> ?: emptyMap()
                        val stringArgs = rawArgs.mapValues { (_, v) -> v.toString() }
                        detectedCall = AiFunctionCall(name = fnName, args = stringArgs)
                    }
                }
            }
            ParsedSseChunk(text = textBuilder.toString(), functionCall = detectedCall)
        } catch (_: Exception) {
            ParsedSseChunk()
        }
    }

    private fun extractModelVersion(rawBody: String): String {
        return try {
            val root = mapAdapter.fromJson(rawBody) ?: return ""
            (root["modelVersion"] as? String)?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Parses the actual Google Gemini API error payload (`error.code`, `error.status`, `error.message`)
     * and distinguishes invalid keys, quota exhaustion, temporary server overload / high demand,
     * unsupported models, and timeouts cleanly.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun classifyHttpError(
        code: Int,
        rawBody: String,
        modelId: String,
        retriesAttempted: Int = 0
    ): AiValidationResult {
        var apiMessage = ""
        var apiStatus = ""
        try {
            val root = mapAdapter.fromJson(rawBody)
            val errObj = root?.get("error") as? Map<String, Any>
            apiMessage = (errObj?.get("message") as? String).orEmpty().trim()
            apiStatus = (errObj?.get("status") as? String).orEmpty().trim()
        } catch (_: Exception) {
            // Keep apiMessage empty if body was non-JSON (e.g. HTML gateway 502/504)
        }

        val lowerMsg = apiMessage.lowercase()
        val lowerStatus = apiStatus.lowercase()
        val exactDetail = apiMessage.ifBlank { null }

        // 1. Invalid API key / authentication errors (never retry)
        val isInvalidKey = (code == 400 && (lowerMsg.contains("api key") || lowerMsg.contains("api_key"))) ||
            code == 401 ||
            (code == 403 && !lowerMsg.contains("quota")) ||
            lowerStatus == "unauthenticated" ||
            lowerStatus == "permission_denied"
        if (isInvalidKey) {
            return AiValidationResult.InvalidKey(
                userMessage = exactDetail ?: "Your Gemini API key was rejected (HTTP $code). Please verify your key in API Configuration.",
                rawApiMessage = exactDetail,
                httpCode = code
            )
        }

        // 2. Unsupported or unavailable model (HTTP 404 / NOT_FOUND — never retry or auto-downgrade)
        val isUnsupportedModel = code == 404 ||
            lowerStatus == "not_found" ||
            (lowerMsg.contains("model") && (lowerMsg.contains("not found") ||
                lowerMsg.contains("not supported") || lowerMsg.contains("does not exist") ||
                lowerMsg.contains("deprecated") || lowerMsg.contains("no longer available")))
        if (isUnsupportedModel) {
            return AiValidationResult.ModelUnavailable(
                modelId = modelId,
                userMessage = "Model '$modelId' is not supported or unavailable on this API key/endpoint (HTTP $code). Zoya will not switch models silently — you can choose an optional alternative below.",
                rawApiMessage = exactDetail,
                httpCode = code
            )
        }

        // 3. HTTP 429: Separate HARD Quota Exhausted from TEMPORARY Rate Limit / Server Demand Spike
        if (code == 429 || lowerStatus == "resource_exhausted") {
            val isTemporarySpike = lowerMsg.contains("high demand") ||
                lowerMsg.contains("overloaded") ||
                lowerMsg.contains("temporarily") ||
                lowerMsg.contains("try again in") ||
                lowerMsg.contains("requests per minute") ||
                lowerMsg.contains("too many requests")
            val isHardQuota = lowerMsg.contains("quota") ||
                lowerMsg.contains("billing") ||
                lowerMsg.contains("daily") ||
                lowerMsg.contains("exceeded your current") ||
                lowerMsg.contains("plan and billing") ||
                lowerMsg.contains("free_tier")

            if (isHardQuota && !isTemporarySpike) {
                return AiValidationResult.QuotaExceeded(
                    userMessage = exactDetail ?: "Your Gemini API quota has been exhausted (HTTP 429). Check your Google AI Studio quota or billing plan.",
                    rawApiMessage = exactDetail,
                    httpCode = code
                )
            }
            if (isTemporarySpike) {
                return AiValidationResult.ServerOverloaded(
                    userMessage = exactDetail ?: "Temporary rate limit or high demand on $modelId (HTTP 429). Tap Retry in a moment.",
                    rawApiMessage = exactDetail,
                    httpCode = code,
                    retriesAttempted = retriesAttempted
                )
            }
            // Default 429 without spike keywords -> treat as Quota Exceeded
            return AiValidationResult.QuotaExceeded(
                userMessage = exactDetail ?: "Gemini API quota or rate limit reached (HTTP 429). Check your API quota in Google AI Studio.",
                rawApiMessage = exactDetail,
                httpCode = code
            )
        }

        // 4. Timeout / Gateway Timeout (504 / DEADLINE_EXCEEDED)
        if (code == 504 || lowerStatus == "deadline_exceeded") {
            return AiValidationResult.TimeoutError(
                userMessage = exactDetail ?: "Gemini server timed out responding for $modelId (HTTP $code).",
                rawApiMessage = exactDetail,
                httpCode = code,
                retriesAttempted = retriesAttempted
            )
        }

        // 5. Temporary Server Overload / High Demand (500, 502, 503, UNAVAILABLE)
        if (code in setOf(500, 502, 503) || lowerStatus == "unavailable" ||
            lowerMsg.contains("high demand") || lowerMsg.contains("overloaded")
        ) {
            return AiValidationResult.ServerOverloaded(
                userMessage = exactDetail ?: "$modelId is temporarily unavailable due to server load (HTTP $code). Tap Retry or try an optional alternative model.",
                rawApiMessage = exactDetail,
                httpCode = code,
                retriesAttempted = retriesAttempted
            )
        }

        // 6. Any other specific API error (e.g. 400 invalid payload, safety block, etc.)
        return AiValidationResult.ProviderError(
            code = code,
            userMessage = exactDetail ?: "Google Gemini API returned HTTP $code for $modelId.",
            rawApiMessage = exactDetail
        )
    }

    private suspend fun Call.awaitCancellable(): Response =
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                runCatching { cancel() }
            }
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_RETRY_ATTEMPTS = 3
        private const val MAX_VALIDATE_RETRIES = 2
        private const val DEFAULT_RETRY_BASE_DELAY_MS = 1000L
        private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)
    }
}
