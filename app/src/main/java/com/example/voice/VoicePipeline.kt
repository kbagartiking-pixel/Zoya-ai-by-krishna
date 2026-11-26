package com.example.voice

import kotlinx.coroutines.flow.StateFlow

/**
 * Modular voice pipeline architecture for ZOYA AI.
 *
 * Gemini 3.8 Flash (`gemini-3.8-flash`) powers reasoning, conversation, vision, and function calling
 * over the standard Gemini API rather than the direct Gemini Live bidirectional audio protocol.
 * Therefore Zoya runs the native `STT → Gemini 3.8 Flash → TTS` pipeline while keeping this
 * clean adapter contract so an optional compatible Gemini Live audio model can be plugged in separately.
 */
enum class VoicePipelineMode(val title: String, val description: String) {
    STT_LLM_TTS(
        title = "Modular STT → Gemini 3.8 Flash → TTS",
        description = "Native Android Speech-to-Text feeds Gemini 3.8 Flash streaming responses directly into natural Text-to-Speech with synchronized anime lip-sync."
    ),
    OPTIONAL_LIVE_AUDIO(
        title = "Optional Live Audio Model Bridge",
        description = "Extensible plugin interface ready to attach a separate Gemini Live bidirectional audio model without modifying Zoya's UI or mobile controls."
    )
}

interface OptionalLiveAudioBridge {
    val bridgeId: String
    val isConnected: StateFlow<Boolean>
    suspend fun connect(apiKey: String, liveModelId: String)
    suspend fun disconnect()
}
