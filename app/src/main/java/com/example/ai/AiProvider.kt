package com.example.ai

import kotlinx.coroutines.flow.Flow

/**
 * Modular AI provider interface with Gemini 3.8 Flash as Zoya AI's primary brain.
 * Supports explicit user-selected alternative models if primary is unavailable, never silent switching.
 */
interface AiProvider {
    val id: String
    val displayName: String
    val primaryModelId: String
    val supportedModels: List<AiModelOption>

    fun getModelOption(modelId: String): AiModelOption =
        supportedModels.firstOrNull { it.id == modelId } ?: supportedModels.first()

    suspend fun validateApiKey(
        apiKey: String,
        modelId: String = primaryModelId
    ): AiValidationResult

    fun streamChat(
        apiKey: String,
        modelId: String = primaryModelId,
        systemPrompt: String,
        history: List<ChatTurn>,
        userMessage: String,
        imageAttachment: ImageAttachment? = null
    ): Flow<AiStreamEvent>
}

class AiProviderRegistry(
    private val providers: List<AiProvider> = listOf(GeminiAiProvider())
) {
    fun getProvider(id: String): AiProvider =
        providers.firstOrNull { it.id == id } ?: providers.first()

    fun allProviders(): List<AiProvider> = providers
}
