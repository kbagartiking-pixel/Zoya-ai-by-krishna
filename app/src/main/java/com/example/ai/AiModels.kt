package com.example.ai

const val PRIMARY_GEMINI_MODEL_ID = "gemini-3.8-flash"
const val PRIMARY_GEMINI_MODEL_NAME = "Gemini 3.8 Flash"

data class AiModelOption(
    val id: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val isPrimary: Boolean = false,
    val capabilities: List<String> = emptyList(),
    val supportsDirectLiveAudio: Boolean = false,
    val voiceArchitectureNote: String = ""
)

data class ImageAttachment(
    val mimeType: String,
    val base64Data: String,
    val label: String = "Attached photo"
)

data class AiFunctionCall(
    val name: String,
    val args: Map<String, String>
)

data class ChatTurn(
    val role: String, // "user" or "model"
    val text: String
)

enum class AiConnectionState {
    NOT_CONFIGURED,
    OFFLINE,
    CHECKING,
    CONNECTED,
    INVALID_KEY,
    QUOTA_EXCEEDED,
    SERVER_OVERLOADED,
    MODEL_UNAVAILABLE,
    TIMEOUT,
    ERROR
}

sealed class AiValidationResult {
    abstract val userTitle: String
    abstract val userMessage: String
    abstract val rawApiMessage: String?
    abstract val httpCode: Int?

    data class Valid(
        val latencyMs: Long,
        val modelId: String,
        val reportedModelVersion: String = modelId
    ) : AiValidationResult() {
        override val userTitle: String = "Connected"
        override val userMessage: String = "Verified active model: $modelId (${latencyMs}ms)"
        override val rawApiMessage: String? = null
        override val httpCode: Int? = 200
    }

    data class InvalidKey(
        override val userMessage: String,
        override val rawApiMessage: String? = null,
        override val httpCode: Int? = 401
    ) : AiValidationResult() {
        override val userTitle: String = "Invalid API Key"
    }

    data class QuotaExceeded(
        override val userMessage: String,
        override val rawApiMessage: String? = null,
        override val httpCode: Int? = 429
    ) : AiValidationResult() {
        override val userTitle: String = "API Quota Exceeded"
    }

    data class ServerOverloaded(
        override val userMessage: String,
        override val rawApiMessage: String? = null,
        override val httpCode: Int? = 503,
        val retriesAttempted: Int = 0
    ) : AiValidationResult() {
        override val userTitle: String = "Temporary Server Overload"
    }

    data class ModelUnavailable(
        val modelId: String,
        override val userMessage: String,
        override val rawApiMessage: String? = null,
        override val httpCode: Int? = 404
    ) : AiValidationResult() {
        override val userTitle: String = "Unsupported or Unavailable Model"
    }

    data class TimeoutError(
        override val userMessage: String,
        override val rawApiMessage: String? = null,
        override val httpCode: Int? = 504,
        val retriesAttempted: Int = 0
    ) : AiValidationResult() {
        override val userTitle: String = "Request Timed Out"
    }

    data class NetworkError(
        override val userMessage: String,
        override val rawApiMessage: String? = null
    ) : AiValidationResult() {
        override val userTitle: String = "Network Connection Error"
        override val httpCode: Int? = null
    }

    data class ProviderError(
        val code: Int,
        override val userMessage: String,
        override val rawApiMessage: String? = null
    ) : AiValidationResult() {
        override val userTitle: String = if (code > 0) "Gemini API Error (HTTP $code)" else "Gemini API Error"
        override val httpCode: Int? = code.takeIf { it > 0 }
    }
}

sealed class AiStreamEvent {
    data class Retrying(
        val attempt: Int,
        val maxRetries: Int,
        val delayMs: Long,
        val httpCode: Int?,
        val reason: String
    ) : AiStreamEvent()

    data class Delta(val textChunk: String) : AiStreamEvent()
    data class FunctionCallRequested(val call: AiFunctionCall) : AiStreamEvent()
    data class Completed(val fullText: String) : AiStreamEvent()
    data class Failed(val error: AiValidationResult) : AiStreamEvent()
}
