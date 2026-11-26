package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.data.prefs.ZoyaLanguageMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

data class VoiceOption(
    val name: String,
    val displayLabel: String,
    val localeTag: String,
    val isFemaleHint: Boolean
)

/**
 * Native Android Voice Engine combining real SpeechRecognizer (STT) and TextToSpeech (TTS)
 * with live audio amplitude telemetry, streaming sentence speech queue, natural female voice selection,
 * and real-time speaking lip-sync modulation for the ZOYA avatar.
 */
class ZoyaVoiceEngine(private val context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var lipSyncJob: Job? = null

    private var activeSpeechRate = 1.02f
    private var activeSpeechPitch = 1.08f
    private var preferredVoiceName = ""

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _speechLipSync = MutableStateFlow(0f)
    val speechLipSync: StateFlow<Float> = _speechLipSync.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<VoiceOption>> = _availableVoices.asStateFlow()

    private var onPartialTranscript: ((String) -> Unit)? = null
    private var onFinalTranscript: ((String) -> Unit)? = null
    private var onListeningError: ((String) -> Unit)? = null
    private var onSpeechFinished: (() -> Unit)? = null

    private var pendingUtteranceCount = 0

    init {
        initTts()
    }

    private fun initTts() {
        try {
            textToSpeech = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    refreshAvailableVoices()
                    applyVoiceSettings(preferredVoiceName, activeSpeechRate, activeSpeechPitch)
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post {
                                _isSpeaking.value = true
                                startLipSyncPulse()
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                pendingUtteranceCount = (pendingUtteranceCount - 1).coerceAtLeast(0)
                                if (pendingUtteranceCount == 0) {
                                    _isSpeaking.value = false
                                    stopLipSyncPulse()
                                    onSpeechFinished?.invoke()
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                pendingUtteranceCount = 0
                                _isSpeaking.value = false
                                stopLipSyncPulse()
                            }
                        }
                    })
                }
            }
        } catch (_: Exception) {
            ttsReady = false
        }
    }

    fun setCallbacks(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit,
        onFinishedSpeaking: () -> Unit
    ) {
        onPartialTranscript = onPartial
        onFinalTranscript = onFinal
        onListeningError = onError
        onSpeechFinished = onFinishedSpeaking
    }

    fun refreshAvailableVoices() {
        val tts = textToSpeech ?: return
        if (!ttsReady) return
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        val filtered = voices
            .filter { voice ->
                val lang = voice.locale.language.lowercase()
                lang == "en" || lang == "hi"
            }
            .sortedWith(
                compareByDescending<android.speech.tts.Voice> { isFemaleVoiceName(it.name) }
                    .thenByDescending { it.locale.country.equals("IN", ignoreCase = true) }
                    .thenBy { it.locale.displayName }
            )
            .take(24)
            .map { voice ->
                val region = voice.locale.displayCountry.ifBlank { voice.locale.language.uppercase() }
                val genderTag = if (isFemaleVoiceName(voice.name)) "Female" else "Natural"
                VoiceOption(
                    name = voice.name,
                    displayLabel = "${voice.locale.displayLanguage} ($region) • $genderTag",
                    localeTag = voice.locale.toLanguageTag(),
                    isFemaleHint = isFemaleVoiceName(voice.name)
                )
            }
        _availableVoices.value = filtered
    }

    fun applyVoiceSettings(voiceName: String, rate: Float, pitch: Float) {
        preferredVoiceName = voiceName
        activeSpeechRate = rate.coerceIn(0.6f, 1.8f)
        activeSpeechPitch = pitch.coerceIn(0.6f, 1.6f)
        val tts = textToSpeech ?: return
        if (!ttsReady) return
        tts.setSpeechRate(activeSpeechRate)
        tts.setPitch(activeSpeechPitch)
        selectBestVoice(tts, preferredVoiceName, Locale("en", "IN"))
    }

    fun isSpeechRecognitionAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    fun startListening(languageMode: ZoyaLanguageMode) {
        mainHandler.post {
            stopSpeaking()
            if (!isSpeechRecognitionAvailable()) {
                onListeningError?.invoke("Speech recognition service is unavailable on this device. You can type commands below.")
                return@post
            }
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                            _micAmplitude.value = 0.15f
                        }

                        override fun onBeginningOfSpeech() {
                            _isListening.value = true
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            _micAmplitude.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() {
                            _isListening.value = false
                            _micAmplitude.value = 0f
                        }

                        override fun onError(error: Int) {
                            _isListening.value = false
                            _micAmplitude.value = 0f
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio capture error. Please try speaking again."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required to listen."
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition network issue. Please check connection."
                                SpeechRecognizer.ERROR_NO_MATCH -> "I didn't quite catch that. Tap to speak again."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap Zoya when you're ready."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy. Retrying shortly."
                                else -> "Voice input paused (code $error)."
                            }
                            onListeningError?.invoke(message)
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            _micAmplitude.value = 0f
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val top = matches?.firstOrNull()?.trim().orEmpty()
                            if (top.isNotEmpty()) {
                                onFinalTranscript?.invoke(top)
                            } else {
                                onListeningError?.invoke("I couldn't hear a command clearly.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim().orEmpty()
                            if (partial.isNotEmpty()) {
                                onPartialTranscript?.invoke(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }

                val localeTag = LanguageDetector.recognitionLocaleTag(languageMode)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                _isListening.value = false
                onListeningError?.invoke("Unable to start voice listener: ${e.message ?: "Unknown error"}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            _isListening.value = false
            _micAmplitude.value = 0f
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
        }
    }

    /**
     * Speaks a full response or appends a streamed sentence chunk cleanly to TTS queue.
     */
    fun speak(
        text: String,
        detectedLanguage: DetectedLanguage,
        flushExisting: Boolean = true
    ) {
        val cleaned = sanitizeForSpeech(text)
        if (cleaned.isEmpty()) return

        mainHandler.post {
            val tts = textToSpeech
            if (tts == null || !ttsReady) {
                // Gracefully animate brief visual lip-sync even on headless/emulated TTS devices
                simulateFallbackLipSync(cleaned)
                return@post
            }

            selectBestVoice(tts, preferredVoiceName, detectedLanguage.locale)
            tts.setSpeechRate(activeSpeechRate)
            tts.setPitch(activeSpeechPitch)

            val queueMode = if (flushExisting) {
                pendingUtteranceCount = 1
                TextToSpeech.QUEUE_FLUSH
            } else {
                pendingUtteranceCount += 1
                TextToSpeech.QUEUE_ADD
            }
            val utteranceId = "zoya_${UUID.randomUUID()}"
            val result = tts.speak(cleaned, queueMode, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                pendingUtteranceCount = (pendingUtteranceCount - 1).coerceAtLeast(0)
                simulateFallbackLipSync(cleaned)
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            pendingUtteranceCount = 0
            _isSpeaking.value = false
            stopLipSyncPulse()
            runCatching { textToSpeech?.stop() }
        }
    }

    fun release() {
        stopListening()
        stopSpeaking()
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching { textToSpeech?.shutdown() }
            textToSpeech = null
        }
        scope.cancel()
    }

    private fun startLipSyncPulse() {
        if (lipSyncJob?.isActive == true) return
        lipSyncJob = scope.launch {
            var tick = 0f
            while (isActive && _isSpeaking.value) {
                // Organic multi-harmonic cadence mimicking natural syllable opening/closing
                val primary = (sin(tick * 1.0) + 1.0) * 0.5
                val harmonic = (sin(tick * 2.35 + 0.7) + 1.0) * 0.5
                val level = (primary * 0.65 + harmonic * 0.35).toFloat().coerceIn(0f, 1f)
                _speechLipSync.value = level
                tick += 0.42f
                delay(55L)
            }
            _speechLipSync.value = 0f
        }
    }

    private fun stopLipSyncPulse() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        _speechLipSync.value = 0f
    }

    private fun simulateFallbackLipSync(text: String) {
        scope.launch {
            _isSpeaking.value = true
            startLipSyncPulse()
            val durationMs = (text.length * 42L).coerceIn(1100L, 4200L)
            delay(durationMs)
            _isSpeaking.value = false
            stopLipSyncPulse()
            onSpeechFinished?.invoke()
        }
    }

    private fun selectBestVoice(tts: TextToSpeech, customVoiceName: String, targetLocale: Locale) {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        if (customVoiceName.isNotBlank()) {
            val match = voices.firstOrNull { it.name == customVoiceName }
            if (match != null) {
                tts.voice = match
                return
            }
        }
        val femaleLocaleMatch = voices.firstOrNull {
            it.locale.language == targetLocale.language &&
                it.locale.country.equals(targetLocale.country, ignoreCase = true) &&
                isFemaleVoiceName(it.name)
        } ?: voices.firstOrNull {
            it.locale.language == targetLocale.language && isFemaleVoiceName(it.name)
        }
        if (femaleLocaleMatch != null) {
            tts.voice = femaleLocaleMatch
        } else {
            tts.language = targetLocale
        }
    }

    private fun isFemaleVoiceName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("female") ||
            lower.contains("-f-") ||
            lower.contains("#female") ||
            lower.contains("sfg") ||
            lower.contains("iob") ||
            lower.contains("hid") ||
            lower.contains("en-in-x-ena") ||
            lower.contains("en-in-x-enc") ||
            lower.contains("hi-in-x-hia") ||
            lower.contains("hi-in-x-hic") ||
            lower.contains("en-us-x-sfg")
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("[*_`#~]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
