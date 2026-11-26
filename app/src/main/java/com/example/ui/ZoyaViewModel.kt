package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiConnectionState
import com.example.ai.AiFunctionCall
import com.example.ai.AiModelOption
import com.example.ai.AiProviderRegistry
import com.example.ai.AiStreamEvent
import com.example.ai.AiValidationResult
import com.example.ai.ChatTurn
import com.example.ai.ImageAttachment
import com.example.ai.PRIMARY_GEMINI_MODEL_ID
import com.example.ai.PRIMARY_GEMINI_MODEL_NAME
import com.example.automation.CommandRouter
import com.example.automation.DeviceControlManager
import com.example.automation.DeviceTelemetry
import com.example.automation.InstalledAppInfo
import com.example.automation.PermissionSummary
import com.example.automation.ZoyaIntent
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ZoyaDatabase
import com.example.data.local.ZoyaRepository
import com.example.data.prefs.ZoyaLanguageMode
import com.example.data.prefs.ZoyaPreferencesRepository
import com.example.data.prefs.ZoyaSettings
import com.example.data.security.SecureApiKeyStore
import com.example.services.CapturedNotification
import com.example.services.ZoyaAccessibilityService
import com.example.services.ZoyaAssistantService
import com.example.services.ZoyaNotificationListenerService
import com.example.voice.DetectedLanguage
import com.example.voice.LanguageDetector
import com.example.voice.VoiceOption
import com.example.voice.VoicePipelineMode
import com.example.voice.ZoyaVoiceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

enum class ZoyaAvatarState(val label: String, val tagline: String) {
    IDLE("STANDBY", "Ready whenever you are"),
    LISTENING("LISTENING", "Hearing your voice..."),
    THINKING("THINKING", "Thinking..."),
    SPEAKING("SPEAKING", "Zoya is speaking"),
    HAPPY("DONE", "Task completed!"),
    ERROR("ATTENTION", "Needs your quick check")
}

sealed class PendingConfirmation {
    data class Call(val phoneNumber: String, val displayLabel: String) : PendingConfirmation()
    data class Sms(val phoneNumber: String, val body: String) : PendingConfirmation()
    data object ClearHistory : PendingConfirmation()
    data object ClearAllData : PendingConfirmation()
}

data class ApiKeyItemUi(
    val id: String,
    val label: String,
    val maskedKey: String,
    val isActive: Boolean,
    val connectionState: AiConnectionState = AiConnectionState.NOT_CONFIGURED,
    val statusTitle: String = "Untested",
    val statusDetail: String? = null,
    val rawApiError: String? = null,
    val httpCode: Int? = null,
    val latencyMs: Long? = null,
    val isTesting: Boolean = false
)

data class ZoyaUiState(
    val avatarState: ZoyaAvatarState = ZoyaAvatarState.IDLE,
    val connectionState: AiConnectionState = AiConnectionState.NOT_CONFIGURED,
    val connectionMessage: String = "Enter Gemini API key to connect Gemini 3.8 Flash",
    val activeModelId: String = PRIMARY_GEMINI_MODEL_ID,
    val activeModelName: String = PRIMARY_GEMINI_MODEL_NAME,
    val verifiedModelVersion: String? = null,
    val lastLatencyMs: Long? = null,
    val voicePipelineMode: VoicePipelineMode = VoicePipelineMode.STT_LLM_TTS,
    val hasApiKey: Boolean = false,
    val activeKeyId: String? = null,
    val activeKeyLabel: String? = null,
    val maskedApiKey: String = "Not configured",
    val savedApiKeys: List<ApiKeyItemUi> = emptyList(),
    val isTestingApi: Boolean = false,
    val apiTestFeedback: String? = null,
    val apiTestTitle: String? = null,
    val apiTestRawError: String? = null,
    val apiTestHttpCode: Int? = null,
    val apiTestSuccess: Boolean? = null,
    val isOnline: Boolean = true,
    val isRequestInFlight: Boolean = false,
    val retryStatusText: String? = null,
    val liveTranscript: String = "",
    val zoyaReplyText: String = "Hi! I'm Zoya, powered by Gemini 3.8 Flash. Tap me, speak, or attach a photo to begin.",
    val lastCommandBadge: String? = null,
    val errorBanner: String? = null,
    val lastErrorTitle: String? = null,
    val lastErrorHttpCode: Int? = null,
    val lastRawApiError: String? = null,
    val failedPrompt: String? = null,
    val failedImage: ImageAttachment? = null,
    val showAlternativeModelOffer: Boolean = false,
    val detectedLanguage: DetectedLanguage = DetectedLanguage.ENGLISH,
    val attachedImage: ImageAttachment? = null,
    val isKeyLoaded: Boolean = false,
    val pendingConfirmation: PendingConfirmation? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val telemetry: DeviceTelemetry? = null,
    val permissions: PermissionSummary? = null,
    val toastMessage: String? = null
)

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ZoyaDatabase.getInstance(application)
    private val preferences = ZoyaPreferencesRepository(application)
    private val secureKeyStore = SecureApiKeyStore(application)
    private val repository = ZoyaRepository(
        chatMessageDao = database.chatMessageDao(),
        reminderDao = database.reminderDao(),
        preferencesRepository = preferences,
        secureApiKeyStore = secureKeyStore
    )
    private val providerRegistry = AiProviderRegistry()
    val deviceControl = DeviceControlManager(application)
    val voiceEngine = ZoyaVoiceEngine(application)

    private val _uiState = MutableStateFlow(ZoyaUiState())
    val uiState: StateFlow<ZoyaUiState> = _uiState.asStateFlow()

    val settings: StateFlow<ZoyaSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ZoyaSettings()
    )

    val chatHistory: StateFlow<List<ChatMessageEntity>> = repository.messagesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val reminders: StateFlow<List<ReminderEntity>> = repository.remindersFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val availableVoices: StateFlow<List<VoiceOption>> = voiceEngine.availableVoices
    val micAmplitude: StateFlow<Float> = voiceEngine.micAmplitude
    val speechLipSync: StateFlow<Float> = voiceEngine.speechLipSync
    val activeNotifications: StateFlow<List<CapturedNotification>> =
        ZoyaNotificationListenerService.notifications

    private var activeAiJob: Job? = null
    private var activeTestJob: Job? = null
    private val keyTestJobs = mutableMapOf<String, Job>()
    private var stateResetJob: Job? = null
    private var shouldResumeContinuousAfterSpeech = false
    private val inFlightGuard = AtomicBoolean(false)
    private var lastFailedLang: DetectedLanguage = DetectedLanguage.ENGLISH
    private var lastFailedFromVoice: Boolean = false

    val availableModels: List<AiModelOption>
        get() = providerRegistry.getProvider(settings.value.providerId).supportedModels

    val activeModelOption: AiModelOption
        get() = providerRegistry.getProvider(settings.value.providerId)
            .getModelOption(settings.value.selectedModelId)

    init {
        setupVoiceCallbacks()
        observeSettings()
        observeVoiceStates()
        refreshSystemStatus()
        loadInitialKeyState()
        loadInstalledApps()
    }

    private fun setupVoiceCallbacks() {
        voiceEngine.setCallbacks(
            onPartial = { partial ->
                _uiState.update {
                    it.copy(
                        avatarState = ZoyaAvatarState.LISTENING,
                        liveTranscript = partial,
                        errorBanner = null
                    )
                }
            },
            onFinal = { finalText ->
                handleUserUtterance(finalText, fromVoice = true)
            },
            onError = { errorMsg ->
                _uiState.update {
                    it.copy(
                        avatarState = ZoyaAvatarState.ERROR,
                        errorBanner = errorMsg
                    )
                }
                scheduleReturnToIdle(2800L)
            },
            onFinishedSpeaking = {
                val shouldContinue = shouldResumeContinuousAfterSpeech &&
                    settings.value.continuousConversation &&
                    deviceControl.readPermissions().microphoneGranted
                shouldResumeContinuousAfterSpeech = false
                if (shouldContinue) {
                    viewModelScope.launch {
                        delay(450L)
                        if (_uiState.value.avatarState != ZoyaAvatarState.ERROR) {
                            startVoiceListening()
                        }
                    }
                } else if (_uiState.value.avatarState == ZoyaAvatarState.SPEAKING) {
                    _uiState.update { it.copy(avatarState = ZoyaAvatarState.IDLE) }
                }
            }
        )
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.collect { current ->
                voiceEngine.applyVoiceSettings(
                    voiceName = current.selectedVoiceName,
                    rate = current.speechRate,
                    pitch = current.speechPitch
                )
                val option = providerRegistry.getProvider(current.providerId)
                    .getModelOption(current.selectedModelId)
                _uiState.update { state ->
                    state.copy(
                        activeModelId = option.id,
                        activeModelName = option.displayName
                    )
                }
            }
        }
    }

    private fun observeVoiceStates() {
        viewModelScope.launch {
            voiceEngine.isSpeaking.collect { speaking ->
                if (speaking) {
                    _uiState.update { it.copy(avatarState = ZoyaAvatarState.SPEAKING) }
                }
            }
        }
    }

    private fun loadInitialKeyState() {
        viewModelScope.launch {
            refreshSavedKeysState(markKeyLoaded = true)
        }
    }

    private suspend fun refreshSavedKeysState(markKeyLoaded: Boolean = false) {
        val currentSettings = settings.value
        val providerId = currentSettings.providerId
        val modelOption = providerRegistry.getProvider(providerId)
            .getModelOption(currentSettings.selectedModelId)
        val storedList = secureKeyStore.listKeys(providerId)
        val activeId = secureKeyStore.getActiveKeyId(providerId)
        val activeMeta = storedList.firstOrNull { it.id == activeId }
        val online = deviceControl.isInternetAvailable()
        val existingUiById = _uiState.value.savedApiKeys.associateBy { it.id }

        val uiKeys = storedList.map { meta ->
            val prev = existingUiById[meta.id]
            if (prev != null) {
                prev.copy(
                    label = meta.label,
                    maskedKey = meta.maskedKey,
                    isActive = meta.id == activeId
                )
            } else {
                ApiKeyItemUi(
                    id = meta.id,
                    label = meta.label,
                    maskedKey = meta.maskedKey,
                    isActive = meta.id == activeId,
                    connectionState = AiConnectionState.NOT_CONFIGURED,
                    statusTitle = "Untested"
                )
            }
        }

        val hasActiveKey = activeMeta != null
        val activeUiItem = uiKeys.firstOrNull { it.isActive }
        val connState = when {
            !hasActiveKey -> AiConnectionState.NOT_CONFIGURED
            !online -> AiConnectionState.OFFLINE
            activeUiItem != null && activeUiItem.connectionState != AiConnectionState.NOT_CONFIGURED ->
                activeUiItem.connectionState
            else -> AiConnectionState.CONNECTED
        }
        val connMsg = when {
            !hasActiveKey && storedList.isNotEmpty() -> "Select an active API key"
            !hasActiveKey -> "API key required for ${modelOption.displayName}"
            !online -> "Offline • Local commands ready"
            activeUiItem != null && activeUiItem.connectionState != AiConnectionState.NOT_CONFIGURED ->
                "${activeUiItem.statusTitle} • ${activeMeta?.label.orEmpty()}"
            else -> "Ready • ${modelOption.id}"
        }

        _uiState.update { state ->
            state.copy(
                hasApiKey = hasActiveKey,
                activeKeyId = activeId,
                activeKeyLabel = activeMeta?.label,
                maskedApiKey = activeMeta?.maskedKey ?: "Not configured",
                savedApiKeys = uiKeys,
                isOnline = online,
                activeModelId = modelOption.id,
                activeModelName = modelOption.displayName,
                connectionState = connState,
                connectionMessage = connMsg,
                isKeyLoaded = if (markKeyLoaded) true else state.isKeyLoaded
            )
        }
    }

    fun refreshSystemStatus() {
        val online = deviceControl.isInternetAvailable()
        val telemetry = deviceControl.readDeviceTelemetry()
        val perms = deviceControl.readPermissions()
        _uiState.update { state ->
            val activeId = state.activeModelId
            val connState = when {
                !state.hasApiKey -> AiConnectionState.NOT_CONFIGURED
                !online -> AiConnectionState.OFFLINE
                state.connectionState == AiConnectionState.OFFLINE -> AiConnectionState.CONNECTED
                else -> state.connectionState
            }
            val connMsg = when (connState) {
                AiConnectionState.NOT_CONFIGURED -> "API key not set"
                AiConnectionState.OFFLINE -> "Offline • Local commands available"
                AiConnectionState.CONNECTED -> "Connected • $activeId"
                AiConnectionState.INVALID_KEY -> "Invalid API key"
                AiConnectionState.QUOTA_EXCEEDED -> "API quota exceeded"
                AiConnectionState.SERVER_OVERLOADED -> "Temporary high load on $activeId"
                AiConnectionState.MODEL_UNAVAILABLE -> "$activeId unavailable"
                AiConnectionState.TIMEOUT -> "Request timed out ($activeId)"
                else -> state.connectionMessage
            }
            state.copy(
                isOnline = online,
                telemetry = telemetry,
                permissions = perms,
                connectionState = connState,
                connectionMessage = connMsg
            )
        }
        voiceEngine.refreshAvailableVoices()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = deviceControl.getLaunchableApps()
            _uiState.update { it.copy(installedApps = apps) }
        }
    }

    // --- Multimodal Image Understanding (Zero-Permission Android Photo Picker) ---

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                encodeImageUriSafely(uri)
            }
            if (encoded != null) {
                _uiState.update {
                    it.copy(
                        attachedImage = encoded,
                        toastMessage = "Photo attached — ask Zoya anything about it!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(toastMessage = "Unable to read selected image.")
                }
            }
        }
    }

    fun clearAttachedImage() {
        _uiState.update { it.copy(attachedImage = null) }
    }

    private fun encodeImageUriSafely(uri: Uri): ImageAttachment? {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            val maxDim = 1024
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val outStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, outStream)
            bitmap.recycle()
            val bytes = outStream.toByteArray()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            ImageAttachment(
                mimeType = "image/jpeg",
                base64Data = b64,
                label = "Photo (${bytes.size / 1024} KB)"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun startVoiceListening() {
        refreshSystemStatus()
        if (!deviceControl.readPermissions().microphoneGranted) {
            setErrorState("Microphone permission is needed to listen. Please allow it to talk to Zoya.")
            return
        }
        cancelActiveRequestInternal(showCancelledMessage = false)
        stateResetJob?.cancel()
        voiceEngine.stopSpeaking()
        _uiState.update {
            it.copy(
                avatarState = ZoyaAvatarState.LISTENING,
                liveTranscript = "Listening...",
                errorBanner = null,
                lastErrorTitle = null,
                lastErrorHttpCode = null,
                lastRawApiError = null,
                showAlternativeModelOffer = false
            )
        }
        voiceEngine.startListening(settings.value.languageMode)
    }

    fun stopAllVoiceAndAi() {
        shouldResumeContinuousAfterSpeech = false
        cancelActiveRequestInternal(showCancelledMessage = false)
        stateResetJob?.cancel()
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        _uiState.update {
            it.copy(
                avatarState = ZoyaAvatarState.IDLE,
                liveTranscript = "",
                retryStatusText = null,
                errorBanner = null
            )
        }
    }

    fun cancelActiveRequest() {
        cancelActiveRequestInternal(showCancelledMessage = true)
    }

    private fun cancelActiveRequestInternal(showCancelledMessage: Boolean) {
        val wasRunning = activeAiJob?.isActive == true || inFlightGuard.get()
        activeAiJob?.cancel()
        activeAiJob = null
        inFlightGuard.set(false)
        if (wasRunning && showCancelledMessage) {
            shouldResumeContinuousAfterSpeech = false
            voiceEngine.stopSpeaking()
            _uiState.update { state ->
                state.copy(
                    isRequestInFlight = false,
                    retryStatusText = null,
                    avatarState = ZoyaAvatarState.IDLE,
                    lastErrorTitle = "Request Cancelled",
                    lastErrorHttpCode = null,
                    lastRawApiError = null,
                    errorBanner = "API request was cancelled. Your message is preserved below if you want to retry.",
                    zoyaReplyText = "Request cancelled. Tap Retry anytime to send again.",
                    toastMessage = "Request cancelled"
                )
            }
        } else if (!showCancelledMessage) {
            _uiState.update {
                it.copy(
                    isRequestInFlight = false,
                    retryStatusText = null
                )
            }
        }
    }

    fun cancelApiTest() {
        activeTestJob?.cancel()
        activeTestJob = null
        _uiState.update {
            it.copy(
                isTestingApi = false,
                apiTestSuccess = null,
                apiTestTitle = "Test Cancelled",
                apiTestFeedback = "API connection test cancelled.",
                apiTestRawError = null,
                apiTestHttpCode = null
            )
        }
    }

    fun retryLastFailedRequest() {
        if (inFlightGuard.get() || _uiState.value.isRequestInFlight) return
        val prompt = _uiState.value.failedPrompt ?: return
        val image = _uiState.value.failedImage
        runAiConversation(
            prompt = prompt,
            lang = lastFailedLang,
            fromVoice = lastFailedFromVoice,
            imageAttachment = image,
            isRetrySubmission = true
        )
    }

    fun selectAlternativeModelAndRetry(modelId: String) {
        if (inFlightGuard.get() || _uiState.value.isRequestInFlight) return
        val prompt = _uiState.value.failedPrompt
        val image = _uiState.value.failedImage
        viewModelScope.launch {
            val provider = providerRegistry.getProvider(settings.value.providerId)
            val chosen = provider.getModelOption(modelId)
            repository.setModel(chosen.id)
            _uiState.update {
                it.copy(
                    activeModelId = chosen.id,
                    activeModelName = chosen.displayName,
                    showAlternativeModelOffer = false,
                    toastMessage = "Switched to ${chosen.displayName} by your choice"
                )
            }
            if (!prompt.isNullOrBlank()) {
                runAiConversation(
                    prompt = prompt,
                    lang = lastFailedLang,
                    fromVoice = lastFailedFromVoice,
                    imageAttachment = image,
                    isRetrySubmission = true,
                    overrideModelId = chosen.id
                )
            }
        }
    }

    fun submitTextCommand(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && _uiState.value.attachedImage == null) return false
        if (inFlightGuard.get() || _uiState.value.isRequestInFlight) {
            _uiState.update {
                it.copy(toastMessage = "A request is already running. Wait or tap Cancel Request.")
            }
            return false
        }
        val prompt = trimmed.ifEmpty { "Describe this photo clearly." }
        handleUserUtterance(prompt, fromVoice = false)
        return true
    }

    private fun handleUserUtterance(rawText: String, fromVoice: Boolean) {
        stateResetJob?.cancel()
        val detectedLang = LanguageDetector.detect(rawText, settings.value.languageMode)
        val cleanedDisplay = rawText.trim()

        _uiState.update {
            it.copy(
                liveTranscript = cleanedDisplay,
                detectedLanguage = detectedLang,
                errorBanner = null,
                lastErrorTitle = null,
                lastErrorHttpCode = null,
                lastRawApiError = null,
                showAlternativeModelOffer = false
            )
        }

        // If an image is attached, route directly to active Gemini model multimodal vision
        val pendingImage = _uiState.value.attachedImage
        if (pendingImage != null) {
            _uiState.update { it.copy(attachedImage = null) }
            runAiConversation(
                prompt = LanguageDetector.stripWakeWord(rawText).ifBlank { cleanedDisplay },
                lang = detectedLang,
                fromVoice = fromVoice,
                imageAttachment = pendingImage,
                isRetrySubmission = false
            )
            return
        }

        // If wake word alone was spoken ("Hey Zoya"), greet and continue listening
        val stripped = LanguageDetector.stripWakeWord(rawText)
        if (LanguageDetector.containsWakeWord(rawText) && stripped.isEmpty()) {
            val greeting = CommandRouter.localizeReply(
                detectedLang,
                english = "Yes, I'm here! How can I help you?",
                hinglish = "Haan, main sun rahi hoon! Boliye kya karna hai?",
                hindi = "जी, मैं सुन रही हूँ! बताइए मैं आपकी क्या मदद करूँ?"
            )
            shouldResumeContinuousAfterSpeech = fromVoice
            speakAndDisplay(greeting, detectedLang, ZoyaAvatarState.HAPPY, "Wake Word")
            return
        }

        val intent = CommandRouter.route(rawText)
        if (intent !is ZoyaIntent.AiConversation) {
            executeDeviceIntent(intent, cleanedDisplay, detectedLang, fromVoice)
        } else {
            runAiConversation(
                prompt = intent.cleanedPrompt,
                lang = detectedLang,
                fromVoice = fromVoice,
                imageAttachment = null,
                isRetrySubmission = false
            )
        }
    }

    private fun executeDeviceIntent(
        intent: ZoyaIntent,
        originalText: String,
        lang: DetectedLanguage,
        fromVoice: Boolean
    ) {
        viewModelScope.launch {
            if (settings.value.saveHistoryEnabled && originalText.isNotBlank()) {
                repository.saveMessage("user", originalText, lang.tag)
            }

            when (intent) {
                is ZoyaIntent.OpenApp -> {
                    val (opened, appName) = deviceControl.openAppByQuery(intent.appQuery)
                    if (opened) {
                        val reply = CommandRouter.localizeReply(
                            lang,
                            english = "Sure, opening $appName.",
                            hinglish = "Sure, $appName open kar rahi hoon.",
                            hindi = "जी, $appName खोल रही हूँ।"
                        )
                        speakAndDisplay(reply, lang, ZoyaAvatarState.HAPPY, "Opened $appName")
                        saveAssistantReply(reply, lang, "App: $appName")
                    } else {
                        val reply = CommandRouter.localizeReply(
                            lang,
                            english = "I couldn't find $appName installed on your phone.",
                            hinglish = "Mujhe $appName aapke phone mein nahi mila.",
                            hindi = "मुझे आपके फोन में $appName नहीं मिला।"
                        )
                        speakAndDisplay(reply, lang, ZoyaAvatarState.ERROR, "App Not Found")
                        saveAssistantReply(reply, lang, "App Not Found")
                    }
                }

                is ZoyaIntent.OpenSetting -> {
                    val (opened, label) = deviceControl.openSystemSetting(intent.settingKey)
                    val reply = if (opened) {
                        CommandRouter.localizeReply(
                            lang,
                            english = "Opening $label.",
                            hinglish = "$label khol rahi hoon.",
                            hindi = "$label खोल रही हूँ।"
                        )
                    } else {
                        "I couldn't open $label on this device."
                    }
                    speakAndDisplay(
                        reply,
                        lang,
                        if (opened) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR,
                        label
                    )
                    saveAssistantReply(reply, lang, label)
                }

                ZoyaIntent.ReadBattery -> {
                    val battery = deviceControl.readBatteryTelemetry()
                    refreshSystemStatus()
                    val chargeEn = if (battery.isCharging) "and charging" else "and not charging"
                    val chargeHi = if (battery.isCharging) "और चार्ज हो रही है" else ""
                    val chargeHinglish = if (battery.isCharging) "aur charging par hai" else "hai"
                    val reply = CommandRouter.localizeReply(
                        lang,
                        english = "Your battery is currently at ${battery.percentage} percent $chargeEn.",
                        hinglish = "Aapki battery abhi ${battery.percentage} percent $chargeHinglish.",
                        hindi = "आपकी बैटरी अभी ${battery.percentage} प्रतिशत है $chargeHi।"
                    )
                    shouldResumeContinuousAfterSpeech = fromVoice
                    speakAndDisplay(reply, lang, ZoyaAvatarState.HAPPY, "Battery ${battery.percentage}%")
                    saveAssistantReply(reply, lang, "Battery ${battery.percentage}%")
                }

                ZoyaIntent.ShowDeviceInfo -> {
                    val info = deviceControl.readDeviceTelemetry()
                    refreshSystemStatus()
                    val reply = "This is ${info.manufacturer} ${info.model} running Android ${info.androidVersion}. Battery is ${info.battery.percentage}%, network is ${info.networkStatus}, and ${info.availableRamMb} MB RAM is free."
                    shouldResumeContinuousAfterSpeech = fromVoice
                    speakAndDisplay(reply, lang, ZoyaAvatarState.HAPPY, "Device Info")
                    saveAssistantReply(reply, lang, "Device Info")
                }

                is ZoyaIntent.CreateReminder -> {
                    val triggerAt = System.currentTimeMillis() + intent.delayMinutes * 60_000L
                    val id = repository.addReminder(intent.title, triggerAt)
                    deviceControl.scheduleLocalReminder(id, intent.title, triggerAt)
                    val reply = CommandRouter.localizeReply(
                        lang,
                        english = "Sure, your reminder '${intent.title}' is scheduled in ${intent.delayMinutes} minutes.",
                        hinglish = "Done! ${intent.delayMinutes} minute baad '${intent.title}' ka reminder set kar diya hai.",
                        hindi = "जी, ${intent.delayMinutes} मिनट बाद '${intent.title}' का रिमाइंडर सेट कर दिया गया है।"
                    )
                    shouldResumeContinuousAfterSpeech = fromVoice
                    speakAndDisplay(reply, lang, ZoyaAvatarState.HAPPY, "Reminder Set")
                    saveAssistantReply(reply, lang, "Reminder (${intent.delayMinutes}m)")
                }

                is ZoyaIntent.SetTimer -> {
                    val ok = deviceControl.setTimer(intent.durationSeconds, intent.label)
                    val mins = intent.durationSeconds / 60
                    val secs = intent.durationSeconds % 60
                    val readable = if (mins > 0) "$mins minute${if (mins > 1) "s" else ""}" else "$secs seconds"
                    val reply = if (ok) {
                        CommandRouter.localizeReply(
                            lang,
                            english = "Setting a timer for $readable.",
                            hinglish = "$readable ka timer set kar rahi hoon.",
                            hindi = "$readable का टाइमर सेट कर रही हूँ।"
                        )
                    } else {
                        "Your device doesn't have a compatible Clock app to set a timer."
                    }
                    speakAndDisplay(reply, lang, if (ok) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR, "Timer")
                    saveAssistantReply(reply, lang, "Timer")
                }

                is ZoyaIntent.SetAlarm -> {
                    val ok = deviceControl.setAlarm(intent.hour, intent.minute, intent.label)
                    val timeLabel = String.format("%02d:%02d", intent.hour, intent.minute)
                    val reply = if (ok) {
                        CommandRouter.localizeReply(
                            lang,
                            english = "Setting your alarm for $timeLabel.",
                            hinglish = "$timeLabel baje ka alarm set kar rahi hoon.",
                            hindi = "$timeLabel बजे का अलार्म सेट कर रही हूँ।"
                        )
                    } else {
                        "I couldn't open the Clock app to set that alarm."
                    }
                    speakAndDisplay(reply, lang, if (ok) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR, "Alarm $timeLabel")
                    saveAssistantReply(reply, lang, "Alarm $timeLabel")
                }

                is ZoyaIntent.PrepareCall -> {
                    _uiState.update {
                        it.copy(
                            pendingConfirmation = PendingConfirmation.Call(
                                phoneNumber = intent.target,
                                displayLabel = intent.target.ifBlank { "Phone Dialer" }
                            )
                        )
                    }
                    val reply = CommandRouter.localizeReply(
                        lang,
                        english = "Please confirm before I open the phone dialer.",
                        hinglish = "Dialer kholne se pehle please confirm kar dijiye.",
                        hindi = "डायलर खोलने से पहले कृपया पुष्टि करें।"
                    )
                    speakAndDisplay(reply, lang, ZoyaAvatarState.IDLE, "Confirm Call")
                }

                is ZoyaIntent.PrepareSms -> {
                    _uiState.update {
                        it.copy(
                            pendingConfirmation = PendingConfirmation.Sms(
                                phoneNumber = intent.target,
                                body = intent.message
                            )
                        )
                    }
                    val reply = CommandRouter.localizeReply(
                        lang,
                        english = "Please confirm before I prepare that SMS message.",
                        hinglish = "SMS taiyar karne se pehle please confirm kar dijiye.",
                        hindi = "एसएमएस तैयार करने से पहले कृपया पुष्टि करें।"
                    )
                    speakAndDisplay(reply, lang, ZoyaAvatarState.IDLE, "Confirm SMS")
                }

                ZoyaIntent.ReadNotifications -> {
                    val perms = deviceControl.readPermissions()
                    if (!perms.notificationListenerEnabled) {
                        val reply = "Notification Access is currently off. Enable it in Settings → Notification Access so I can read your notifications."
                        speakAndDisplay(reply, lang, ZoyaAvatarState.ERROR, "Permission Needed")
                    } else {
                        val notifs = activeNotifications.value
                        val reply = if (notifs.isEmpty()) {
                            CommandRouter.localizeReply(
                                lang,
                                english = "You have no active unread notifications right now.",
                                hinglish = "Abhi koi nayi notification nahi hai.",
                                hindi = "अभी कोई नई नोटिफिकेशन नहीं है।"
                            )
                        } else {
                            val summary = notifs.take(3).joinToString(". ") { n ->
                                "${n.appName}: ${n.title} ${n.text}".trim()
                            }
                            "You have ${notifs.size} notifications. Latest: $summary"
                        }
                        shouldResumeContinuousAfterSpeech = fromVoice
                        speakAndDisplay(reply, lang, ZoyaAvatarState.HAPPY, "Notifications")
                        saveAssistantReply(reply, lang, "Notifications")
                    }
                }

                is ZoyaIntent.AccessibilityAction -> {
                    val service = ZoyaAccessibilityService.instance
                    if (service == null) {
                        val reply = "Accessibility Assistant is disabled. Enable it in Settings → Accessibility if you want me to read or navigate screens."
                        speakAndDisplay(reply, lang, ZoyaAvatarState.ERROR, "Accessibility Off")
                    } else {
                        val reply = when (intent.action) {
                            "read_screen" -> {
                                val text = service.readVisibleScreenText()
                                if (text.isBlank()) "I couldn't detect readable text on the current screen."
                                else "Visible on screen: ${text.take(260)}"
                            }
                            "scroll_down" -> {
                                if (service.scrollScreen(forward = true)) "Scrolled down." else "Nothing scrollable found on screen."
                            }
                            "scroll_up" -> {
                                if (service.scrollScreen(forward = false)) "Scrolled up." else "Nothing scrollable found on screen."
                            }
                            "click" -> {
                                if (service.clickNodeByText(intent.targetText)) "Tapped ${intent.targetText}."
                                else "I couldn't find '${intent.targetText}' on the active screen."
                            }
                            else -> "Accessibility action completed."
                        }
                        speakAndDisplay(reply, lang, ZoyaAvatarState.HAPPY, "Accessibility")
                        saveAssistantReply(reply, lang, "Accessibility")
                    }
                }

                is ZoyaIntent.AiConversation -> Unit
            }
        }
    }

    private fun handleGeminiFunctionCall(
        call: AiFunctionCall,
        lang: DetectedLanguage,
        fromVoice: Boolean
    ) {
        val mappedIntent: ZoyaIntent? = when (call.name) {
            "open_app" -> call.args["app_name"]?.takeIf { it.isNotBlank() }?.let { ZoyaIntent.OpenApp(it) }
            "open_setting" -> call.args["setting_key"]?.takeIf { it.isNotBlank() }?.let { ZoyaIntent.OpenSetting(it) }
            "get_battery_status" -> ZoyaIntent.ReadBattery
            "create_reminder" -> {
                val title = call.args["title"].orEmpty().ifBlank { "Zoya Reminder" }
                val mins = call.args["delay_minutes"]?.toIntOrNull()?.coerceAtLeast(1) ?: 10
                ZoyaIntent.CreateReminder(title = title, delayMinutes = mins)
            }
            "set_timer" -> {
                val secs = call.args["duration_seconds"]?.toIntOrNull()?.coerceAtLeast(1) ?: 60
                val label = call.args["label"].orEmpty().ifBlank { "Zoya Timer" }
                ZoyaIntent.SetTimer(durationSeconds = secs, label = label)
            }
            "prepare_call" -> ZoyaIntent.PrepareCall(call.args["phone_number"].orEmpty())
            "prepare_sms" -> ZoyaIntent.PrepareSms(
                target = call.args["phone_number"].orEmpty(),
                message = call.args["message"].orEmpty()
            )
            else -> null
        }

        if (mappedIntent != null) {
            executeDeviceIntent(mappedIntent, originalText = "", lang = lang, fromVoice = fromVoice)
        }
    }

    private fun runAiConversation(
        prompt: String,
        lang: DetectedLanguage,
        fromVoice: Boolean,
        imageAttachment: ImageAttachment? = null,
        isRetrySubmission: Boolean = false,
        overrideModelId: String? = null
    ) {
        // Atomic guard prevents duplicate concurrent requests when Send is tapped rapidly
        if (!inFlightGuard.compareAndSet(false, true)) {
            return
        }

        lastFailedLang = lang
        lastFailedFromVoice = fromVoice

        activeAiJob = viewModelScope.launch {
            try {
                val online = deviceControl.isInternetAvailable()
                val providerId = settings.value.providerId
                val provider = providerRegistry.getProvider(providerId)
                val targetModelOption = provider.getModelOption(overrideModelId ?: settings.value.selectedModelId)
                val modelId = targetModelOption.id
                val modelName = targetModelOption.displayName
                val activeKeyId = secureKeyStore.getActiveKeyId(providerId)
                // Strictly use ONLY the user-selected active API key; never auto-rotate across keys.
                val apiKey = secureKeyStore.getActiveApiKey(providerId)

                // Preserve prompt & image immediately so failure or cancellation never loses user input
                _uiState.update {
                    it.copy(
                        isRequestInFlight = true,
                        retryStatusText = null,
                        failedPrompt = prompt,
                        failedImage = imageAttachment,
                        lastErrorTitle = null,
                        lastErrorHttpCode = null,
                        lastRawApiError = null,
                        errorBanner = null,
                        showAlternativeModelOffer = false,
                        activeModelId = modelId,
                        activeModelName = modelName
                    )
                }

                if (apiKey.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            isRequestInFlight = false,
                            hasApiKey = false,
                            connectionState = AiConnectionState.NOT_CONFIGURED,
                            connectionMessage = "API key required for $modelName",
                            avatarState = ZoyaAvatarState.ERROR,
                            lastErrorTitle = "API Key Not Configured",
                            lastErrorHttpCode = null,
                            lastRawApiError = null,
                            zoyaReplyText = "Please enter your Gemini API key in Settings → API Configuration to use $modelName. Local phone commands already work offline!",
                            errorBanner = "Enter your Gemini API key in API Configuration, then tap Retry."
                        )
                    }
                    voiceEngine.speak(
                        "Please enter your Gemini API key in API Configuration so I can answer.",
                        lang
                    )
                    return@launch
                }

                if (!online) {
                    _uiState.update {
                        it.copy(
                            isRequestInFlight = false,
                            isOnline = false,
                            connectionState = AiConnectionState.OFFLINE,
                            connectionMessage = "No internet connection",
                            avatarState = ZoyaAvatarState.ERROR,
                            lastErrorTitle = "No Internet Connection",
                            lastErrorHttpCode = null,
                            lastRawApiError = null,
                            zoyaReplyText = "You appear to be offline. Connect to Wi-Fi or mobile data for $modelName, then tap Retry.",
                            errorBanner = "No internet connection detected. Your message is preserved."
                        )
                    }
                    voiceEngine.speak("You are offline right now. Please connect to the internet.", lang)
                    return@launch
                }

                if (!isRetrySubmission && settings.value.saveHistoryEnabled) {
                    val savedPrompt = if (imageAttachment != null) "[Photo] $prompt" else prompt
                    repository.saveMessage("user", savedPrompt, lang.tag)
                }

                _uiState.update {
                    it.copy(
                        avatarState = ZoyaAvatarState.THINKING,
                        zoyaReplyText = "",
                        lastCommandBadge = if (imageAttachment != null) "$modelName Vision" else modelName
                    )
                }

                val recentHistory = repository.getRecentConversation(limit = 10)
                    .dropLast(1)
                    .map { ChatTurn(role = if (it.role == "user") "user" else "model", text = it.content) }

                val systemPrompt = buildZoyaSystemPrompt(lang, modelName)

                val streamedFull = StringBuilder()
                val pendingSentence = StringBuilder()
                var firstChunkSpoken = false
                shouldResumeContinuousAfterSpeech = fromVoice

                provider.streamChat(
                    apiKey = apiKey,
                    modelId = modelId,
                    systemPrompt = systemPrompt,
                    history = recentHistory,
                    userMessage = prompt,
                    imageAttachment = imageAttachment
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Retrying -> {
                            val sec = (event.delayMs / 1000.0)
                            val delayLabel = if (sec >= 1.0) "${sec.toInt()}s" else "${event.delayMs}ms"
                            val httpPart = event.httpCode?.let { "HTTP $it • " }.orEmpty()
                            _uiState.update {
                                it.copy(
                                    avatarState = ZoyaAvatarState.THINKING,
                                    retryStatusText = "Temporary server error (${httpPart}retrying ${event.attempt}/${event.maxRetries} in $delayLabel)..."
                                )
                            }
                        }

                        is AiStreamEvent.FunctionCallRequested -> {
                            handleGeminiFunctionCall(event.call, lang, fromVoice)
                        }

                        is AiStreamEvent.Delta -> {
                            streamedFull.append(event.textChunk)
                            pendingSentence.append(event.textChunk)
                            _uiState.update {
                                it.copy(
                                    retryStatusText = null,
                                    zoyaReplyText = streamedFull.toString(),
                                    connectionState = AiConnectionState.CONNECTED,
                                    connectionMessage = "Connected • $modelId"
                                )
                            }
                            val buffered = pendingSentence.toString()
                            val sentenceEndIdx = findSentenceBreak(buffered)
                            if (sentenceEndIdx > 18) {
                                val toSpeak = buffered.substring(0, sentenceEndIdx + 1).trim()
                                pendingSentence.delete(0, sentenceEndIdx + 1)
                                if (toSpeak.isNotEmpty()) {
                                    voiceEngine.speak(toSpeak, lang, flushExisting = !firstChunkSpoken)
                                    firstChunkSpoken = true
                                }
                            }
                        }

                        is AiStreamEvent.Completed -> {
                            val remaining = pendingSentence.toString().trim()
                            if (remaining.isNotEmpty()) {
                                voiceEngine.speak(remaining, lang, flushExisting = !firstChunkSpoken)
                                firstChunkSpoken = true
                            }
                            _uiState.update { state ->
                                state.copy(
                                    isRequestInFlight = false,
                                    retryStatusText = null,
                                    failedPrompt = null,
                                    failedImage = null,
                                    lastErrorTitle = null,
                                    lastErrorHttpCode = null,
                                    lastRawApiError = null,
                                    errorBanner = null,
                                    showAlternativeModelOffer = false,
                                    zoyaReplyText = event.fullText,
                                    connectionState = AiConnectionState.CONNECTED,
                                    connectionMessage = "Connected • $modelId",
                                    savedApiKeys = updateKeyStatusInList(
                                        list = state.savedApiKeys,
                                        keyId = activeKeyId,
                                        connectionState = AiConnectionState.CONNECTED,
                                        statusTitle = "Connected",
                                        statusDetail = "Active response from $modelId",
                                        rawApiError = null,
                                        httpCode = 200,
                                        latencyMs = null,
                                        isTesting = false
                                    )
                                )
                            }
                            saveAssistantReply(event.fullText, lang, modelId)
                        }

                        is AiStreamEvent.Failed -> {
                            shouldResumeContinuousAfterSpeech = false
                            val err = event.error
                            val connState = mapValidationState(err)
                            val offerAlt = modelId == PRIMARY_GEMINI_MODEL_ID && (
                                err is AiValidationResult.ServerOverloaded ||
                                    err is AiValidationResult.ModelUnavailable ||
                                    err is AiValidationResult.TimeoutError
                                )
                            _uiState.update { state ->
                                state.copy(
                                    isRequestInFlight = false,
                                    retryStatusText = null,
                                    avatarState = ZoyaAvatarState.ERROR,
                                    connectionState = connState,
                                    connectionMessage = "${err.userTitle}${err.httpCode?.let { c -> " (HTTP $c)" }.orEmpty()}",
                                    lastErrorTitle = err.userTitle,
                                    lastErrorHttpCode = err.httpCode,
                                    lastRawApiError = err.rawApiMessage,
                                    errorBanner = err.userMessage,
                                    zoyaReplyText = err.userMessage,
                                    failedPrompt = prompt,
                                    failedImage = imageAttachment,
                                    showAlternativeModelOffer = offerAlt,
                                    savedApiKeys = updateKeyStatusInList(
                                        list = state.savedApiKeys,
                                        keyId = activeKeyId,
                                        connectionState = connState,
                                        statusTitle = err.userTitle,
                                        statusDetail = err.userMessage,
                                        rawApiError = err.rawApiMessage,
                                        httpCode = err.httpCode,
                                        latencyMs = null,
                                        isTesting = false
                                    )
                                )
                            }
                            voiceEngine.speak(err.userTitle + ". " + err.userMessage.take(180), lang)
                            scheduleReturnToIdle(5500L)
                        }
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } finally {
                inFlightGuard.set(false)
                _uiState.update { it.copy(isRequestInFlight = false, retryStatusText = null) }
            }
        }
    }

    private fun buildZoyaSystemPrompt(lang: DetectedLanguage, modelName: String): String {
        val langInstruction = when (lang) {
            DetectedLanguage.HINDI -> "Respond naturally in warm conversational Hindi (Devanagari script)."
            DetectedLanguage.HINGLISH -> "Respond naturally in friendly Indian Hinglish (Roman Hindi mixed with English)."
            DetectedLanguage.ENGLISH -> "Respond naturally in warm, crisp English."
        }
        return """
            You are ZOYA AI, a friendly, intelligent anime personal Android assistant powered by $modelName.
            You support reasoning, image understanding, and Android device function calling.
            When the user clearly asks to open an app, adjust a system setting, check battery, create a reminder, set a timer, or prepare a call/SMS, call the appropriate function tool.
            For spoken responses, keep answers clear, warm, and concise (1 to 4 sentences) without markdown symbols like asterisks or bullet points.
            $langInstruction
        """.trimIndent()
    }

    private fun findSentenceBreak(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '.' || ch == '!' || ch == '?' || ch == '।' || ch == '\n') {
                return i
            }
        }
        return -1
    }

    private fun speakAndDisplay(
        reply: String,
        lang: DetectedLanguage,
        state: ZoyaAvatarState,
        badge: String?
    ) {
        _uiState.update {
            it.copy(
                avatarState = state,
                zoyaReplyText = reply,
                lastCommandBadge = badge,
                errorBanner = if (state == ZoyaAvatarState.ERROR) reply else null
            )
        }
        voiceEngine.speak(reply, lang, flushExisting = true)
        if (state == ZoyaAvatarState.HAPPY || state == ZoyaAvatarState.ERROR) {
            scheduleReturnToIdle(3600L)
        }
    }

    private suspend fun saveAssistantReply(reply: String, lang: DetectedLanguage, badge: String?) {
        if (settings.value.saveHistoryEnabled) {
            repository.saveMessage("assistant", reply, lang.tag, badge)
        }
    }

    private fun setErrorState(message: String) {
        _uiState.update {
            it.copy(
                avatarState = ZoyaAvatarState.ERROR,
                errorBanner = message
            )
        }
        scheduleReturnToIdle(3200L)
    }

    private fun scheduleReturnToIdle(delayMs: Long) {
        stateResetJob?.cancel()
        stateResetJob = viewModelScope.launch {
            delay(delayMs)
            if (!voiceEngine.isSpeaking.value && !voiceEngine.isListening.value) {
                _uiState.update { it.copy(avatarState = ZoyaAvatarState.IDLE) }
            }
        }
    }

    // --- API Key Configuration & Active Model Verification ---

    fun testApiKey(candidateKey: String, modelId: String = settings.value.selectedModelId) {
        activeTestJob?.cancel()
        val targetModelId = modelId.trim().ifEmpty { settings.value.selectedModelId }
        val trimmedCandidate = candidateKey.trim()
        if (trimmedCandidate.isEmpty()) {
            val activeId = _uiState.value.activeKeyId
            if (activeId != null) {
                testSavedApiKey(activeId, targetModelId)
                return
            }
        }
        activeTestJob = viewModelScope.launch {
            val online = deviceControl.isInternetAvailable()
            if (!online) {
                _uiState.update {
                    it.copy(
                        isOnline = false,
                        isTestingApi = false,
                        apiTestSuccess = false,
                        apiTestTitle = "No Internet Connection",
                        apiTestFeedback = "No internet connection detected. Connect to Wi-Fi or mobile data and try again.",
                        apiTestRawError = null,
                        apiTestHttpCode = null,
                        connectionState = AiConnectionState.OFFLINE
                    )
                }
                return@launch
            }

            val keyToUse = trimmedCandidate.ifEmpty {
                secureKeyStore.getActiveApiKey(settings.value.providerId).orEmpty()
            }

            _uiState.update {
                it.copy(
                    isTestingApi = true,
                    apiTestTitle = "Testing Key",
                    apiTestFeedback = "Verifying key against $targetModelId via Google Gemini API...",
                    apiTestRawError = null,
                    apiTestHttpCode = null,
                    apiTestSuccess = null,
                    connectionState = AiConnectionState.CHECKING
                )
            }

            val provider = providerRegistry.getProvider(settings.value.providerId)
            when (val result = provider.validateApiKey(keyToUse, targetModelId)) {
                is AiValidationResult.Valid -> {
                    _uiState.update {
                        it.copy(
                            isTestingApi = false,
                            apiTestSuccess = true,
                            apiTestTitle = result.userTitle,
                            apiTestRawError = null,
                            apiTestHttpCode = 200,
                            verifiedModelVersion = result.reportedModelVersion,
                            lastLatencyMs = result.latencyMs,
                            apiTestFeedback = "Verified active model: ${result.modelId} (${result.latencyMs}ms)",
                            connectionState = AiConnectionState.CONNECTED,
                            connectionMessage = "Connected • ${result.modelId}",
                            avatarState = ZoyaAvatarState.HAPPY
                        )
                    }
                    scheduleReturnToIdle(2400L)
                }
                else -> {
                    val connState = mapValidationState(result)
                    _uiState.update {
                        it.copy(
                            isTestingApi = false,
                            apiTestSuccess = false,
                            apiTestTitle = result.userTitle,
                            apiTestRawError = result.rawApiMessage,
                            apiTestHttpCode = result.httpCode,
                            verifiedModelVersion = null,
                            apiTestFeedback = result.userMessage,
                            connectionState = connState,
                            connectionMessage = "${result.userTitle}${result.httpCode?.let { c -> " (HTTP $c)" }.orEmpty()}"
                        )
                    }
                }
            }
        }
    }

    /**
     * Tests an individual saved key independently without changing which key is active.
     */
    fun testSavedApiKey(keyId: String, modelId: String = settings.value.selectedModelId) {
        keyTestJobs[keyId]?.cancel()
        val targetModelId = modelId.trim().ifEmpty { settings.value.selectedModelId }
        val job = viewModelScope.launch {
            val online = deviceControl.isInternetAvailable()
            val isTargetActive = _uiState.value.activeKeyId == keyId
            if (!online) {
                _uiState.update { state ->
                    state.copy(
                        isOnline = false,
                        savedApiKeys = updateKeyStatusInList(
                            list = state.savedApiKeys,
                            keyId = keyId,
                            connectionState = AiConnectionState.OFFLINE,
                            statusTitle = "Offline",
                            statusDetail = "No internet connection detected.",
                            rawApiError = null,
                            httpCode = null,
                            latencyMs = null,
                            isTesting = false
                        ),
                        connectionState = if (isTargetActive) AiConnectionState.OFFLINE else state.connectionState
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    isTestingApi = if (isTargetActive) true else state.isTestingApi,
                    savedApiKeys = updateKeyStatusInList(
                        list = state.savedApiKeys,
                        keyId = keyId,
                        connectionState = AiConnectionState.CHECKING,
                        statusTitle = "Testing...",
                        statusDetail = "Verifying $targetModelId...",
                        rawApiError = null,
                        httpCode = null,
                        latencyMs = null,
                        isTesting = true
                    ),
                    connectionState = if (isTargetActive) AiConnectionState.CHECKING else state.connectionState
                )
            }

            val secret = secureKeyStore.getApiKeyById(settings.value.providerId, keyId).orEmpty()
            val provider = providerRegistry.getProvider(settings.value.providerId)
            val result = provider.validateApiKey(secret, targetModelId)
            val mappedState = mapValidationState(result)

            when (result) {
                is AiValidationResult.Valid -> {
                    _uiState.update { state ->
                        val activeNow = state.activeKeyId == keyId
                        state.copy(
                            isTestingApi = if (activeNow) false else state.isTestingApi,
                            savedApiKeys = updateKeyStatusInList(
                                list = state.savedApiKeys,
                                keyId = keyId,
                                connectionState = AiConnectionState.CONNECTED,
                                statusTitle = "Connected",
                                statusDetail = "Verified ${result.modelId} (${result.latencyMs}ms)",
                                rawApiError = null,
                                httpCode = 200,
                                latencyMs = result.latencyMs,
                                isTesting = false
                            ),
                            verifiedModelVersion = if (activeNow) result.reportedModelVersion else state.verifiedModelVersion,
                            lastLatencyMs = if (activeNow) result.latencyMs else state.lastLatencyMs,
                            apiTestSuccess = if (activeNow) true else state.apiTestSuccess,
                            apiTestTitle = if (activeNow) result.userTitle else state.apiTestTitle,
                            apiTestFeedback = if (activeNow) "Verified active model: ${result.modelId} (${result.latencyMs}ms)" else state.apiTestFeedback,
                            apiTestRawError = if (activeNow) null else state.apiTestRawError,
                            apiTestHttpCode = if (activeNow) 200 else state.apiTestHttpCode,
                            connectionState = if (activeNow) AiConnectionState.CONNECTED else state.connectionState,
                            connectionMessage = if (activeNow) "Connected • ${result.modelId}" else state.connectionMessage,
                            avatarState = if (activeNow) ZoyaAvatarState.HAPPY else state.avatarState
                        )
                    }
                    if (_uiState.value.activeKeyId == keyId) {
                        scheduleReturnToIdle(2400L)
                    }
                }
                else -> {
                    _uiState.update { state ->
                        val activeNow = state.activeKeyId == keyId
                        state.copy(
                            isTestingApi = if (activeNow) false else state.isTestingApi,
                            savedApiKeys = updateKeyStatusInList(
                                list = state.savedApiKeys,
                                keyId = keyId,
                                connectionState = mappedState,
                                statusTitle = result.userTitle,
                                statusDetail = result.userMessage,
                                rawApiError = result.rawApiMessage,
                                httpCode = result.httpCode,
                                latencyMs = null,
                                isTesting = false
                            ),
                            apiTestSuccess = if (activeNow) false else state.apiTestSuccess,
                            apiTestTitle = if (activeNow) result.userTitle else state.apiTestTitle,
                            apiTestFeedback = if (activeNow) result.userMessage else state.apiTestFeedback,
                            apiTestRawError = if (activeNow) result.rawApiMessage else state.apiTestRawError,
                            apiTestHttpCode = if (activeNow) result.httpCode else state.apiTestHttpCode,
                            connectionState = if (activeNow) mappedState else state.connectionState,
                            connectionMessage = if (activeNow) {
                                "${result.userTitle}${result.httpCode?.let { c -> " (HTTP $c)" }.orEmpty()}"
                            } else {
                                state.connectionMessage
                            }
                        )
                    }
                }
            }
        }
        keyTestJobs[keyId] = job
    }

    fun cancelSavedKeyTest(keyId: String) {
        keyTestJobs.remove(keyId)?.cancel()
        val isTargetActive = _uiState.value.activeKeyId == keyId
        _uiState.update { state ->
            state.copy(
                isTestingApi = if (isTargetActive) false else state.isTestingApi,
                savedApiKeys = updateKeyStatusInList(
                    list = state.savedApiKeys,
                    keyId = keyId,
                    connectionState = AiConnectionState.NOT_CONFIGURED,
                    statusTitle = "Cancelled",
                    statusDetail = "Connection test cancelled.",
                    rawApiError = null,
                    httpCode = null,
                    latencyMs = null,
                    isTesting = false
                )
            )
        }
    }

    fun addNewApiKey(
        label: String,
        rawKey: String,
        setAsActive: Boolean = true,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val trimmed = rawKey.trim()
            if (trimmed.isEmpty()) {
                _uiState.update {
                    it.copy(
                        apiTestSuccess = false,
                        apiTestTitle = "API Key Required",
                        apiTestFeedback = "Please enter a valid Gemini API key before saving.",
                        apiTestRawError = null,
                        apiTestHttpCode = null
                    )
                }
                return@launch
            }
            val providerId = settings.value.providerId
            val added = secureKeyStore.addApiKey(
                providerId = providerId,
                label = label,
                rawApiKey = trimmed,
                setAsActive = setAsActive
            )
            repository.setSetupSkippedOnce(false)
            refreshSavedKeysState()
            _uiState.update {
                it.copy(
                    apiTestSuccess = true,
                    apiTestTitle = "Key Saved Securely",
                    apiTestFeedback = "'${added.label}' (${added.maskedKey}) encrypted in Android Keystore.",
                    apiTestRawError = null,
                    apiTestHttpCode = null,
                    toastMessage = "Saved '${added.label}' securely"
                )
            }
            onSaved()
        }
    }

    fun replaceExistingApiKey(keyId: String, newLabel: String, newRawKey: String) {
        viewModelScope.launch {
            val trimmed = newRawKey.trim()
            if (trimmed.isEmpty()) {
                _uiState.update {
                    it.copy(
                        apiTestSuccess = false,
                        apiTestTitle = "Replacement Key Required",
                        apiTestFeedback = "Please paste the new Gemini API key to replace this entry.",
                        apiTestRawError = null,
                        apiTestHttpCode = null
                    )
                }
                return@launch
            }
            keyTestJobs.remove(keyId)?.cancel()
            val updated = secureKeyStore.replaceApiKey(
                providerId = settings.value.providerId,
                keyId = keyId,
                newRawApiKey = trimmed,
                newLabel = newLabel
            )
            if (updated != null) {
                // Reset individual test status since underlying credential changed
                _uiState.update { state ->
                    state.copy(
                        savedApiKeys = updateKeyStatusInList(
                            list = state.savedApiKeys,
                            keyId = keyId,
                            connectionState = AiConnectionState.NOT_CONFIGURED,
                            statusTitle = "Untested",
                            statusDetail = "Key replaced — tap Test to verify.",
                            rawApiError = null,
                            httpCode = null,
                            latencyMs = null,
                            isTesting = false
                        )
                    )
                }
                refreshSavedKeysState()
                _uiState.update {
                    it.copy(
                        apiTestSuccess = true,
                        apiTestTitle = "Key Replaced",
                        apiTestFeedback = "Replaced '${updated.label}' (${updated.maskedKey}) in Android Keystore. Tap Test to verify.",
                        apiTestRawError = null,
                        apiTestHttpCode = null,
                        toastMessage = "Updated '${updated.label}'"
                    )
                }
            }
        }
    }

    fun selectActiveApiKey(keyId: String) {
        viewModelScope.launch {
            val ok = secureKeyStore.setActiveKey(settings.value.providerId, keyId)
            if (ok) {
                refreshSavedKeysState()
                val chosen = _uiState.value.savedApiKeys.firstOrNull { it.id == keyId }
                _uiState.update {
                    it.copy(
                        apiTestFeedback = null,
                        apiTestTitle = null,
                        apiTestRawError = null,
                        apiTestHttpCode = null,
                        apiTestSuccess = null,
                        toastMessage = "Active API key: ${chosen?.label ?: "Updated"}"
                    )
                }
            }
        }
    }

    fun deleteSavedApiKey(keyId: String) {
        viewModelScope.launch {
            keyTestJobs.remove(keyId)?.cancel()
            val target = _uiState.value.savedApiKeys.firstOrNull { it.id == keyId }
            val deleted = secureKeyStore.deleteApiKey(settings.value.providerId, keyId)
            if (deleted) {
                refreshSavedKeysState()
                _uiState.update {
                    it.copy(
                        verifiedModelVersion = if (target?.isActive == true) null else it.verifiedModelVersion,
                        lastLatencyMs = if (target?.isActive == true) null else it.lastLatencyMs,
                        apiTestSuccess = null,
                        apiTestTitle = null,
                        apiTestFeedback = "Deleted '${target?.label ?: "API key"}' from encrypted storage.",
                        apiTestRawError = null,
                        apiTestHttpCode = null,
                        toastMessage = "Deleted '${target?.label ?: "API key"}'"
                    )
                }
            }
        }
    }

    fun saveApiKey(rawKey: String, onSaved: () -> Unit = {}) {
        addNewApiKey(label = "Primary Key", rawKey = rawKey, setAsActive = true, onSaved = onSaved)
    }

    fun removeApiKey() {
        val activeId = _uiState.value.activeKeyId ?: return
        deleteSavedApiKey(activeId)
    }

    private fun updateKeyStatusInList(
        list: List<ApiKeyItemUi>,
        keyId: String?,
        connectionState: AiConnectionState,
        statusTitle: String,
        statusDetail: String?,
        rawApiError: String?,
        httpCode: Int?,
        latencyMs: Long?,
        isTesting: Boolean
    ): List<ApiKeyItemUi> {
        if (keyId == null) return list
        return list.map { item ->
            if (item.id == keyId) {
                item.copy(
                    connectionState = connectionState,
                    statusTitle = statusTitle,
                    statusDetail = statusDetail,
                    rawApiError = rawApiError,
                    httpCode = httpCode,
                    latencyMs = latencyMs,
                    isTesting = isTesting
                )
            } else {
                item
            }
        }
    }

    fun skipInitialApiSetup() {
        viewModelScope.launch {
            repository.setSetupSkippedOnce(true)
        }
    }

    // --- Direct UI Actions for App Control, Reminders, Calls/SMS, Settings ---

    fun launchApp(appNameOrQuery: String) {
        val (ok, label) = deviceControl.openAppByQuery(appNameOrQuery)
        _uiState.update {
            it.copy(
                avatarState = if (ok) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR,
                toastMessage = if (ok) "Opening $label" else "$label is not installed on this device"
            )
        }
        scheduleReturnToIdle(2200L)
    }

    fun launchPackage(app: InstalledAppInfo) {
        val ok = deviceControl.openPackage(app.packageName)
        _uiState.update {
            it.copy(
                avatarState = if (ok) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR,
                toastMessage = if (ok) "Opening ${app.label}" else "Unable to launch ${app.label}"
            )
        }
        scheduleReturnToIdle(2200L)
    }

    fun openSetting(settingKey: String) {
        val (ok, label) = deviceControl.openSystemSetting(settingKey)
        _uiState.update {
            it.copy(toastMessage = if (ok) "Opening $label" else "Unable to open $label")
        }
    }

    fun requestCallConfirmation(phoneNumber: String) {
        _uiState.update {
            it.copy(
                pendingConfirmation = PendingConfirmation.Call(
                    phoneNumber = phoneNumber.trim(),
                    displayLabel = phoneNumber.trim().ifBlank { "Phone Dialer" }
                )
            )
        }
    }

    fun requestSmsConfirmation(phoneNumber: String, body: String) {
        _uiState.update {
            it.copy(
                pendingConfirmation = PendingConfirmation.Sms(
                    phoneNumber = phoneNumber.trim(),
                    body = body.trim()
                )
            )
        }
    }

    fun requestClearHistoryConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = PendingConfirmation.ClearHistory) }
    }

    fun requestClearAllDataConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = PendingConfirmation.ClearAllData) }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    fun confirmPendingAction() {
        val action = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        when (action) {
            is PendingConfirmation.Call -> {
                val ok = deviceControl.prepareDialerCall(action.phoneNumber)
                _uiState.update {
                    it.copy(
                        avatarState = if (ok) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR,
                        toastMessage = if (ok) "Dialer opened for ${action.displayLabel}" else "Unable to open dialer"
                    )
                }
            }
            is PendingConfirmation.Sms -> {
                val ok = deviceControl.prepareSmsMessage(action.phoneNumber, action.body)
                _uiState.update {
                    it.copy(
                        avatarState = if (ok) ZoyaAvatarState.HAPPY else ZoyaAvatarState.ERROR,
                        toastMessage = if (ok) "SMS composer opened" else "Unable to open SMS app"
                    )
                }
            }
            PendingConfirmation.ClearHistory -> {
                viewModelScope.launch {
                    repository.clearConversationHistory()
                    _uiState.update { it.copy(toastMessage = "Conversation history cleared") }
                }
            }
            PendingConfirmation.ClearAllData -> {
                viewModelScope.launch {
                    keyTestJobs.values.forEach { it.cancel() }
                    keyTestJobs.clear()
                    ZoyaAssistantService.stop(getApplication())
                    repository.wipeAllLocalData()
                    _uiState.update {
                        it.copy(
                            hasApiKey = false,
                            activeKeyId = null,
                            activeKeyLabel = null,
                            maskedApiKey = "Not configured",
                            savedApiKeys = emptyList(),
                            verifiedModelVersion = null,
                            lastLatencyMs = null,
                            connectionState = AiConnectionState.NOT_CONFIGURED,
                            connectionMessage = "All local data cleared",
                            apiTestFeedback = null,
                            apiTestTitle = null,
                            apiTestRawError = null,
                            apiTestHttpCode = null,
                            toastMessage = "All local data & keys wiped"
                        )
                    }
                }
            }
        }
    }

    fun createReminderFromUi(title: String, delayMinutes: Int) {
        viewModelScope.launch {
            val safeTitle = title.trim().ifBlank { "Zoya Reminder" }
            val safeDelay = delayMinutes.coerceAtLeast(1)
            val triggerAt = System.currentTimeMillis() + safeDelay * 60_000L
            val id = repository.addReminder(safeTitle, triggerAt)
            deviceControl.scheduleLocalReminder(id, safeTitle, triggerAt)
            _uiState.update {
                it.copy(
                    avatarState = ZoyaAvatarState.HAPPY,
                    toastMessage = "Reminder set for $safeDelay min from now"
                )
            }
            scheduleReturnToIdle(2000L)
        }
    }

    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.toggleReminderCompleted(reminder.id, !reminder.isCompleted)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            repository.deleteReminder(reminder.id)
        }
    }

    fun quickSetAlarm(hour: Int, minute: Int) {
        val ok = deviceControl.setAlarm(hour, minute)
        _uiState.update {
            it.copy(toastMessage = if (ok) "Alarm intent opened" else "No Clock app available")
        }
    }

    fun quickSetTimer(minutes: Int) {
        val ok = deviceControl.setTimer(minutes * 60)
        _uiState.update {
            it.copy(toastMessage = if (ok) "${minutes}m timer opened" else "No Clock app available")
        }
    }

    fun runMorningBriefRoutine() {
        val info = deviceControl.readDeviceTelemetry()
        val notifCount = activeNotifications.value.size
        val activeReminders = reminders.value.count { !it.isCompleted }
        val brief = "Good to see you! Your battery is at ${info.battery.percentage} percent, network is ${info.networkStatus}, you have $activeReminders active reminders, and $notifCount notifications."
        speakAndDisplay(brief, DetectedLanguage.ENGLISH, ZoyaAvatarState.HAPPY, "Morning Brief")
    }

    // --- Settings Updates ---

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            val provider = providerRegistry.getProvider(settings.value.providerId)
            val option = provider.getModelOption(modelId)
            repository.setModel(option.id)
            _uiState.update {
                it.copy(
                    activeModelId = option.id,
                    activeModelName = option.displayName,
                    showAlternativeModelOffer = false,
                    toastMessage = "Active model set to ${option.displayName} (${option.id})"
                )
            }
        }
    }

    fun selectLanguage(mode: ZoyaLanguageMode) {
        viewModelScope.launch { repository.setLanguageMode(mode) }
    }

    fun selectVoice(voiceName: String) {
        viewModelScope.launch { repository.setVoiceName(voiceName) }
    }

    fun updateSpeechRate(rate: Float) {
        viewModelScope.launch { repository.setSpeechRate(rate) }
    }

    fun updateSpeechPitch(pitch: Float) {
        viewModelScope.launch { repository.setSpeechPitch(pitch) }
    }

    fun previewZoyaVoice() {
        val lang = when (settings.value.languageMode) {
            ZoyaLanguageMode.HINDI -> DetectedLanguage.HINDI
            ZoyaLanguageMode.HINGLISH -> DetectedLanguage.HINGLISH
            else -> DetectedLanguage.ENGLISH
        }
        val sample = when (lang) {
            DetectedLanguage.HINDI -> "नमस्ते! मैं ज़ोया हूँ, जेमिनी 3.8 फ्लैश द्वारा संचालित आपकी पर्सनल एआई असिस्टेंट।"
            DetectedLanguage.HINGLISH -> "Hey! Main Zoya hoon, Gemini 3.8 Flash par chalne wali aapki personal Android AI assistant."
            DetectedLanguage.ENGLISH -> "Hello! I'm Zoya, powered by Gemini 3.8 Flash. I'm ready whenever you need me."
        }
        voiceEngine.speak(sample, lang, flushExisting = true)
    }

    fun toggleWakeWord(enabled: Boolean) {
        viewModelScope.launch { repository.setWakeWordEnabled(enabled) }
    }

    fun toggleContinuousConversation(enabled: Boolean) {
        viewModelScope.launch { repository.setContinuousConversation(enabled) }
    }

    fun toggleSaveHistory(enabled: Boolean) {
        viewModelScope.launch { repository.setSaveHistoryEnabled(enabled) }
    }

    fun toggleBackgroundAssistant(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val perms = deviceControl.readPermissions()
                if (!perms.microphoneGranted) {
                    _uiState.update {
                        it.copy(toastMessage = "Grant Microphone permission before enabling Background Assistant")
                    }
                    return@launch
                }
                val started = ZoyaAssistantService.start(getApplication())
                repository.setBackgroundAssistantEnabled(started)
                _uiState.update {
                    it.copy(
                        toastMessage = if (started) "Background assistant active in notification bar"
                        else "Unable to start background service"
                    )
                }
            } else {
                ZoyaAssistantService.stop(getApplication())
                repository.setBackgroundAssistantEnabled(false)
                _uiState.update { it.copy(toastMessage = "Background assistant stopped") }
            }
            delay(250L)
            refreshSystemStatus()
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun mapValidationState(result: AiValidationResult): AiConnectionState =
        when (result) {
            is AiValidationResult.InvalidKey -> AiConnectionState.INVALID_KEY
            is AiValidationResult.QuotaExceeded -> AiConnectionState.QUOTA_EXCEEDED
            is AiValidationResult.ServerOverloaded -> AiConnectionState.SERVER_OVERLOADED
            is AiValidationResult.ModelUnavailable -> AiConnectionState.MODEL_UNAVAILABLE
            is AiValidationResult.TimeoutError -> AiConnectionState.TIMEOUT
            is AiValidationResult.NetworkError -> AiConnectionState.OFFLINE
            is AiValidationResult.ProviderError -> AiConnectionState.ERROR
            is AiValidationResult.Valid -> AiConnectionState.CONNECTED
        }

    override fun onCleared() {
        activeAiJob?.cancel()
        activeTestJob?.cancel()
        keyTestJobs.values.forEach { it.cancel() }
        keyTestJobs.clear()
        stateResetJob?.cancel()
        voiceEngine.release()
        super.onCleared()
    }
}
