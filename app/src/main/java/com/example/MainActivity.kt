package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.services.ZoyaAssistantService
import com.example.ui.ZoyaViewModel
import com.example.ui.components.ZoyaConfirmationDialog
import com.example.ui.screens.AboutZoyaScreen
import com.example.ui.screens.AccessibilityNotificationScreen
import com.example.ui.screens.ApiConfigScreen
import com.example.ui.screens.AppControlScreen
import com.example.ui.screens.BackgroundAssistantScreen
import com.example.ui.screens.ConversationHistoryScreen
import com.example.ui.screens.FeaturesScreen
import com.example.ui.screens.ModelInfoScreen
import com.example.ui.screens.PermissionsPrivacyScreen
import com.example.ui.screens.SettingsHubScreen
import com.example.ui.screens.VoiceLanguageScreen
import com.example.ui.screens.ZoyaHomeScreen
import com.example.ui.theme.ZoyaAiTheme
import com.example.ui.theme.ZoyaDeepSurface
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaTextMuted
import com.example.ui.theme.ZoyaTextPrimary

object ZoyaRoutes {
    const val HOME = "zoya_home"
    const val APP_CONTROL = "zoya_app_control"
    const val FEATURES = "zoya_features"
    const val SETTINGS = "zoya_settings"
    const val API_SETUP = "zoya_api_setup"
    const val API_CONFIG = "zoya_api_config"
    const val MODEL_INFO = "zoya_model_info"
    const val VOICE_LANGUAGE = "zoya_voice_language"
    const val BACKGROUND_WAKE = "zoya_background_wake"
    const val ACCESSIBILITY_NOTIFICATIONS = "zoya_accessibility_notifications"
    const val PERMISSIONS_PRIVACY = "zoya_permissions_privacy"
    const val HISTORY = "zoya_history"
    const val ABOUT = "zoya_about"
}

private data class BottomNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private var launchVoiceRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchVoiceRequested = intent?.getBooleanExtra(ZoyaAssistantService.EXTRA_START_VOICE, false) == true

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            ZoyaAiTheme {
                ZoyaAppRoot(
                    startVoiceImmediately = launchVoiceRequested,
                    onConsumeStartVoice = { launchVoiceRequested = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(ZoyaAssistantService.EXTRA_START_VOICE, false)) {
            launchVoiceRequested = true
        }
    }
}

@Composable
fun ZoyaAppRoot(
    startVoiceImmediately: Boolean = false,
    onConsumeStartVoice: () -> Unit = {},
    viewModel: ZoyaViewModel = viewModel()
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val voices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val micAmplitude by viewModel.micAmplitude.collectAsStateWithLifecycle()
    val speechLipSync by viewModel.speechLipSync.collectAsStateWithLifecycle()
    val activeNotifications by viewModel.activeNotifications.collectAsStateWithLifecycle()

    // Runtime permission launchers (Android 10 - Android 16 compliant)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshSystemStatus()
        if (granted) {
            viewModel.startVoiceListening()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshSystemStatus()
    }

    // Zero-permission Android Photo Picker for Gemini 3.8 Flash Image Understanding
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachImageFromUri(uri)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(uiState.isKeyLoaded, uiState.hasApiKey, settings.setupSkippedOnce) {
        if (uiState.isKeyLoaded && !uiState.hasApiKey && !settings.setupSkippedOnce) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == ZoyaRoutes.HOME) {
                navController.navigate(ZoyaRoutes.API_SETUP) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(startVoiceImmediately) {
        if (startVoiceImmediately) {
            onConsumeStartVoice()
            if (viewModel.deviceControl.readPermissions().microphoneGranted) {
                viewModel.startVoiceListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val topTabs = remember {
        listOf(
            BottomNavTab(ZoyaRoutes.HOME, "Zoya", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_home"),
            BottomNavTab(ZoyaRoutes.APP_CONTROL, "Control", Icons.Filled.Apps, Icons.Outlined.Apps, "nav_control"),
            BottomNavTab(ZoyaRoutes.FEATURES, "Features", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_features"),
            BottomNavTab(ZoyaRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in topTabs.map { it.route }

    val navigateToTopTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val requestTalk: () -> Unit = {
        if (viewModel.deviceControl.readPermissions().microphoneGranted) {
            viewModel.startVoiceListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val requestNotificationsPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshSystemStatus()
        }
    }

    Scaffold(
        containerColor = ZoyaObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = ZoyaDeepSurface,
                    contentColor = ZoyaTextPrimary,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    topTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTopTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ZoyaNeonCyan,
                                selectedTextColor = ZoyaNeonCyan,
                                unselectedIconColor = ZoyaTextMuted,
                                unselectedTextColor = ZoyaTextMuted,
                                indicatorColor = ZoyaNeonPurple.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = ZoyaRoutes.HOME
            ) {
                composable(ZoyaRoutes.HOME) {
                    ZoyaHomeScreen(
                        uiState = uiState,
                        settings = settings,
                        availableModels = viewModel.availableModels,
                        micAmplitude = micAmplitude,
                        speechLipSync = speechLipSync,
                        onTapToTalk = requestTalk,
                        onStop = viewModel::stopAllVoiceAndAi,
                        onCancelRequest = viewModel::cancelActiveRequest,
                        onRetryRequest = viewModel::retryLastFailedRequest,
                        onSelectAlternativeAndRetry = viewModel::selectAlternativeModelAndRetry,
                        onSendText = viewModel::submitTextCommand,
                        onPickImage = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onClearAttachedImage = viewModel::clearAttachedImage,
                        onOpenApiConfig = { navController.navigate(ZoyaRoutes.API_CONFIG) },
                        onOpenModelInfo = { navController.navigate(ZoyaRoutes.MODEL_INFO) },
                        onOpenAppControl = { navigateToTopTab(ZoyaRoutes.APP_CONTROL) },
                        onOpenFeatures = { navigateToTopTab(ZoyaRoutes.FEATURES) },
                        onOpenSettings = { navigateToTopTab(ZoyaRoutes.SETTINGS) }
                    )
                }

                composable(ZoyaRoutes.APP_CONTROL) {
                    AppControlScreen(
                        installedApps = uiState.installedApps,
                        onLaunchQuickApp = viewModel::launchApp,
                        onLaunchPackage = viewModel::launchPackage,
                        onOpenSetting = viewModel::openSetting,
                        onPrepareCall = viewModel::requestCallConfirmation,
                        onPrepareSms = viewModel::requestSmsConfirmation
                    )
                }

                composable(ZoyaRoutes.FEATURES) {
                    FeaturesScreen(
                        telemetry = uiState.telemetry,
                        permissions = uiState.permissions,
                        reminders = reminders,
                        notifications = activeNotifications,
                        onRefreshTelemetry = viewModel::refreshSystemStatus,
                        onCreateReminder = { title, mins ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                uiState.permissions?.notificationsGranted != true
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.createReminderFromUi(title, mins)
                        },
                        onToggleReminder = viewModel::toggleReminder,
                        onDeleteReminder = viewModel::deleteReminder,
                        onQuickAlarm = viewModel::quickSetAlarm,
                        onQuickTimer = viewModel::quickSetTimer,
                        onRunMorningBrief = viewModel::runMorningBriefRoutine,
                        onRunVoiceCommand = { cmd ->
                            navigateToTopTab(ZoyaRoutes.HOME)
                            viewModel.submitTextCommand(cmd)
                        },
                        onOpenAccessibilitySettings = { viewModel.openSetting("accessibility") },
                        onOpenNotificationAccessSettings = { viewModel.openSetting("notification_access") }
                    )
                }

                composable(ZoyaRoutes.SETTINGS) {
                    SettingsHubScreen(
                        uiState = uiState,
                        settings = settings,
                        historyCount = chatHistory.size,
                        onOpenApiConfig = { navController.navigate(ZoyaRoutes.API_CONFIG) },
                        onOpenModelInfo = { navController.navigate(ZoyaRoutes.MODEL_INFO) },
                        onOpenVoiceLanguage = { navController.navigate(ZoyaRoutes.VOICE_LANGUAGE) },
                        onOpenBackgroundWake = { navController.navigate(ZoyaRoutes.BACKGROUND_WAKE) },
                        onOpenAccessibilityNotifications = {
                            navController.navigate(ZoyaRoutes.ACCESSIBILITY_NOTIFICATIONS)
                        },
                        onOpenPermissionsPrivacy = {
                            navController.navigate(ZoyaRoutes.PERMISSIONS_PRIVACY)
                        },
                        onOpenBatteryOptimization = { viewModel.openSetting("battery_optimization") },
                        onOpenHistory = { navController.navigate(ZoyaRoutes.HISTORY) },
                        onClearData = viewModel::requestClearAllDataConfirmation,
                        onOpenAbout = { navController.navigate(ZoyaRoutes.ABOUT) }
                    )
                }

                composable(ZoyaRoutes.API_SETUP) {
                    ApiConfigScreen(
                        uiState = uiState,
                        settings = settings,
                        availableModels = viewModel.availableModels,
                        isOnboardingMode = true,
                        onBack = null,
                        onTestApi = viewModel::testApiKey,
                        onCancelApiTest = viewModel::cancelApiTest,
                        onSelectModel = viewModel::selectModel,
                        onSaveApiKey = { key ->
                            viewModel.saveApiKey(key) {
                                navController.popBackStack(ZoyaRoutes.HOME, inclusive = false)
                            }
                        },
                        onRemoveApiKey = viewModel::removeApiKey,
                        onOpenModelInfo = { navController.navigate(ZoyaRoutes.MODEL_INFO) },
                        onSkipForNow = {
                            viewModel.skipInitialApiSetup()
                            navController.popBackStack(ZoyaRoutes.HOME, inclusive = false)
                        }
                    )
                }

                composable(ZoyaRoutes.API_CONFIG) {
                    ApiConfigScreen(
                        uiState = uiState,
                        settings = settings,
                        availableModels = viewModel.availableModels,
                        isOnboardingMode = false,
                        onBack = { navController.popBackStack() },
                        onTestApi = viewModel::testApiKey,
                        onCancelApiTest = viewModel::cancelApiTest,
                        onSelectModel = viewModel::selectModel,
                        onSaveApiKey = { key -> viewModel.saveApiKey(key) },
                        onRemoveApiKey = viewModel::removeApiKey,
                        onOpenModelInfo = { navController.navigate(ZoyaRoutes.MODEL_INFO) }
                    )
                }

                composable(ZoyaRoutes.MODEL_INFO) {
                    ModelInfoScreen(
                        uiState = uiState,
                        modelOption = viewModel.activeModelOption,
                        availableModels = viewModel.availableModels,
                        onBack = { navController.popBackStack() },
                        onTestConnection = { viewModel.testApiKey("", uiState.activeModelId) },
                        onSelectModel = viewModel::selectModel,
                        onOpenApiConfig = { navController.navigate(ZoyaRoutes.API_CONFIG) }
                    )
                }

                composable(ZoyaRoutes.VOICE_LANGUAGE) {
                    VoiceLanguageScreen(
                        settings = settings,
                        voices = voices,
                        onBack = { navController.popBackStack() },
                        onSelectLanguage = viewModel::selectLanguage,
                        onSelectVoice = viewModel::selectVoice,
                        onUpdateRate = viewModel::updateSpeechRate,
                        onUpdatePitch = viewModel::updateSpeechPitch,
                        onTestVoice = viewModel::previewZoyaVoice
                    )
                }

                composable(ZoyaRoutes.BACKGROUND_WAKE) {
                    BackgroundAssistantScreen(
                        settings = settings,
                        permissions = uiState.permissions,
                        onBack = { navController.popBackStack() },
                        onToggleWakeWord = viewModel::toggleWakeWord,
                        onToggleContinuous = viewModel::toggleContinuousConversation,
                        onToggleBackgroundService = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                uiState.permissions?.notificationsGranted != true
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.toggleBackgroundAssistant(enable)
                        },
                        onRequestMicPermission = {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onOpenBatteryOptimization = { viewModel.openSetting("battery_optimization") }
                    )
                }

                composable(ZoyaRoutes.ACCESSIBILITY_NOTIFICATIONS) {
                    AccessibilityNotificationScreen(
                        permissions = uiState.permissions,
                        onBack = { navController.popBackStack() },
                        onOpenAccessibilitySettings = { viewModel.openSetting("accessibility") },
                        onOpenNotificationAccess = { viewModel.openSetting("notification_access") },
                        onTestReadScreen = {
                            navigateToTopTab(ZoyaRoutes.HOME)
                            viewModel.submitTextCommand("read screen")
                        }
                    )
                }

                composable(ZoyaRoutes.PERMISSIONS_PRIVACY) {
                    PermissionsPrivacyScreen(
                        settings = settings,
                        permissions = uiState.permissions,
                        onBack = { navController.popBackStack() },
                        onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onRequestNotifications = requestNotificationsPermission,
                        onOpenAppSettings = { viewModel.openSetting("app_permissions") },
                        onToggleSaveHistory = viewModel::toggleSaveHistory,
                        onClearHistory = viewModel::requestClearHistoryConfirmation,
                        onClearAllData = viewModel::requestClearAllDataConfirmation
                    )
                }

                composable(ZoyaRoutes.HISTORY) {
                    ConversationHistoryScreen(
                        messages = chatHistory,
                        onBack = { navController.popBackStack() },
                        onClearHistory = viewModel::requestClearHistoryConfirmation
                    )
                }

                composable(ZoyaRoutes.ABOUT) {
                    AboutZoyaScreen(
                        onBack = { navController.popBackStack() },
                        onOpenApiConfig = { navController.navigate(ZoyaRoutes.API_CONFIG) }
                    )
                }
            }
        }
    }

    uiState.pendingConfirmation?.let { pending ->
        ZoyaConfirmationDialog(
            pending = pending,
            onConfirm = viewModel::confirmPendingAction,
            onDismiss = viewModel::dismissConfirmation
        )
    }
}
