package com.manjaniq.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.manjaniq.core.AetherSettingsStorage
import com.manjaniq.core.AppLanguage
import com.manjaniq.core.AppPreferences
import com.manjaniq.core.ConfigStore
import com.manjaniq.core.EngineRepository
import com.manjaniq.service.AetherService
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
    remember { AetherSettingsStorage.init(appContext) }
    val configStore = remember { ConfigStore(appContext) }
    val appPrefs = remember { AppPreferences(appContext) }

    var config by remember { mutableStateOf(configStore.load()) }
    var selectedCore by remember { mutableStateOf(appPrefs.currentCore()) }
    var currentLang by remember { mutableStateOf(appPrefs.currentLanguage()) }
    var localError by remember { mutableStateOf<String?>(null) }

    val ui by EngineRepository.uiState.collectAsState()
    val logs by EngineRepository.logs.collectAsState()

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
                    versionName = getVersionName(),
                )
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    ManjaniqTopBar(
                        onMenuClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                    )

                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Home.route,
                        modifier = Modifier.weight(1f),
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
                                        AetherService.requestStop(appContext)
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
                                versionName = getVersionName(),
                            )
                        }

                        composable(AppDestination.Logs.route) {
                            LogsScreenNew(
                                logs = logs,
                                onClearLogs = { EngineRepository.clearLogs() },
                            )
                        }

                        composable(AppDestination.About.route) {
                            AboutScreen(versionName = getVersionName())
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun getVersionName(): String {
    val context = LocalContext.current
    return remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
    }
}
