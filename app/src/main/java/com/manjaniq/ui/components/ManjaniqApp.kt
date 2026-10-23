package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manjaniq.core.AetherConfigStore
import com.manjaniq.core.AetherProfileStore
import com.manjaniq.core.AetherRepository
import com.manjaniq.core.AppLanguage
import com.manjaniq.core.AppPreferences
import com.manjaniq.core.ConfigStore
import com.manjaniq.core.EngineRepository
import com.manjaniq.core.SelectedCore
import com.manjaniq.core.models.AetherConfig
import com.manjaniq.core.models.AetherProfile
import com.manjaniq.core.models.AetherProfileConfig
import com.manjaniq.ui.navigation.AppDestination
import com.manjaniq.ui.screens.AboutScreen
import com.manjaniq.ui.screens.GeneralSettingsScreen
import com.manjaniq.ui.screens.HomeScreen
import com.manjaniq.ui.screens.LogsScreenNew
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManjaniqApp() {
    val appContext = LocalContext.current.applicationContext
    val configStore = remember { ConfigStore(appContext) }
    val appPrefs = remember { AppPreferences(appContext) }

    var config by remember { mutableStateOf(configStore.load()) }
    var selectedCore by remember { mutableStateOf(appPrefs.currentCore()) }
    var currentLang by remember { mutableStateOf(appPrefs.currentLanguage()) }
    var localError by remember { mutableStateOf<String?>(null) }

    val ui by EngineRepository.uiState.collectAsState()
    val logs by EngineRepository.logs.collectAsState()

    val aetherConfigStore = remember { AetherConfigStore(appContext) }
    var aetherConfig by remember { mutableStateOf(aetherConfigStore.load()) }
    val aetherUi by AetherRepository.uiState.collectAsState()
    val aetherLogs by AetherRepository.logs.collectAsState()
    var aetherLocalError by remember { mutableStateOf<String?>(null) }

    // Profile store - new maintainable architecture
    val aetherProfileStore = remember { AetherProfileStore(appContext) }
    var selectedAetherProfile by remember { mutableStateOf(aetherProfileStore.getSelectedProfile()) }
    var currentProfileConfig by remember { mutableStateOf(aetherProfileStore.loadFor(selectedAetherProfile)) }

    LaunchedEffect(Unit) {
        appPrefs.coreFlow.collect { selectedCore = it }
    }
    LaunchedEffect(Unit) {
        appPrefs.languageFlow.collect { currentLang = it }
    }

    LaunchedEffect(Unit) {
        while (true) {
            EngineRepository.refresh()
            delay(1000)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: AppDestination.Home.route

    val activity = LocalContext.current as? android.app.Activity
    val layoutDirection = if (currentLang == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route != currentRoute) {
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                            }
                        }
                    },
                    onClose = {
                        scope.launch { drawerState.close() }
                    },
                    versionName = getVersionName()
                )
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    ManjaniqTopBar(
                        onMenuClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        }
                    )

                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Home.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(AppDestination.Home.route) {
                            HomeScreen(
                                selectedCore = selectedCore,
                                onSelectCore = { core ->
                                    selectedCore = core
                                    appPrefs.setCore(core)
                                },
                                config = config,
                                ui = ui.copy(lastError = localError ?: ui.lastError),
                                recentLogs = logs,
                                onStart = {
                                    val problem = config.validate()
                                    if (problem != null) {
                                        localError = problem
                                    } else {
                                        localError = null
                                        EngineRepository.requestStart(appContext)
                                    }
                                },
                                onStop = { EngineRepository.requestStop(appContext) },
                                onDismissError = {
                                    localError = null
                                    EngineRepository.clearError()
                                },
                                onOpenLogs = {
                                    navController.navigate(AppDestination.Logs.route) {
                                        launchSingleTop = true
                                    }
                                },
                                onSaveConfig = { saved, restart ->
                                    configStore.save(saved)
                                    config = saved
                                    if (restart) EngineRepository.requestRestart(appContext)
                                },
                                onResetDefaults = {
                                    val defaults = configStore.resetToDefaults()
                                    config = defaults
                                    defaults
                                },
                                // Aether bindings - independent from RSTA (legacy)
                                aetherConfig = currentProfileConfig.base,
                                aetherUi = aetherUi.copy(lastError = aetherLocalError ?: aetherUi.lastError),
                                aetherLogs = aetherLogs,
                                onAetherStart = {
                                    val base = currentProfileConfig.base
                                    val conflict = com.manjaniq.core.models.detectAetherPortConflict(base, config)
                                    if (conflict != null) {
                                        aetherLocalError = conflict
                                    } else if (!aetherUi.isNativeLoaded) {
                                        aetherLocalError = "Aether native library not built for this APK. See build logs. Diagnostics: ${com.manjaniq.AetherBridge.getLoadError()?.message}"
                                    } else {
                                        val v = base.validate()
                                        if (v != null) aetherLocalError = v else {
                                            val profErr = currentProfileConfig.validateForProfile(selectedAetherProfile)
                                            if (profErr != null) aetherLocalError = profErr else {
                                                aetherLocalError = null
                                                // Persist current profile config to legacy store for service to read
                                                aetherProfileStore.saveFor(selectedAetherProfile, currentProfileConfig)
                                                aetherConfigStore.save(base)
                                                // Also inform repository's active profile
                                                com.manjaniq.core.models.AetherProfileManager.selectedProfile = selectedAetherProfile
                                                AetherRepository.requestStart(appContext)
                                            }
                                        }
                                    }
                                },
                                onAetherStop = { AetherRepository.requestStop(appContext) },
                                onAetherDismissError = { aetherLocalError = null; AetherRepository.clearError() },
                                onAetherSaveConfig = { saved, restart ->
                                    // Legacy path: save to current profile
                                    currentProfileConfig = currentProfileConfig.copy(base = saved)
                                    aetherProfileStore.saveFor(selectedAetherProfile, currentProfileConfig)
                                    aetherConfigStore.save(saved)
                                    aetherConfig = saved
                                    if (restart) AetherRepository.requestRestart(appContext)
                                },
                                onAetherResetDefaults = {
                                    val d = AetherConfig.DEFAULT
                                    currentProfileConfig = currentProfileConfig.copy(base = d)
                                    aetherProfileStore.saveFor(selectedAetherProfile, currentProfileConfig)
                                    aetherConfigStore.save(d)
                                    aetherConfig = d
                                    d
                                },
                                // New profile-aware bindings
                                selectedAetherProfile = selectedAetherProfile,
                                onSelectAetherProfile = { prof ->
                                    // Save current before switching
                                    aetherProfileStore.saveFor(selectedAetherProfile, currentProfileConfig)
                                    aetherProfileStore.setSelectedProfile(prof)
                                    selectedAetherProfile = prof
                                    currentProfileConfig = aetherProfileStore.loadFor(prof)
                                },
                                aetherProfileConfig = currentProfileConfig,
                                onSaveAetherProfileConfig = { savedProfileCfg, restart ->
                                    currentProfileConfig = savedProfileCfg
                                    aetherProfileStore.saveFor(selectedAetherProfile, savedProfileCfg)
                                    // Sync base to legacy for service compat
                                    aetherConfigStore.save(savedProfileCfg.base)
                                    aetherConfig = savedProfileCfg.base
                                    if (restart) AetherRepository.requestRestart(appContext)
                                },
                                onResetAetherProfileDefaults = { prof ->
                                    val def = aetherProfileStore.resetFor(prof)
                                    if (prof == selectedAetherProfile) {
                                        currentProfileConfig = def
                                        aetherConfig = def.base
                                    }
                                    def
                                }
                            )
                        }

                        composable(AppDestination.Settings.route) {
                            GeneralSettingsScreen(
                                currentLanguage = currentLang,
                                onLanguageChange = { lang ->
                                    appPrefs.setLanguage(lang)
                                    currentLang = lang
                                    activity?.recreate()
                                },
                                versionName = getVersionName()
                            )
                        }

                        composable(AppDestination.Logs.route) {
                            LogsScreenNew(
                                logs = logs,
                                onClearLogs = { EngineRepository.clearLogs() }
                            )
                        }

                        composable(AppDestination.About.route) {
                            AboutScreen(versionName = getVersionName())
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun getVersionName(): String {
    val ctx = LocalContext.current
    return try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.1.0"
    } catch (e: Exception) {
        "0.1.0"
    }
}
