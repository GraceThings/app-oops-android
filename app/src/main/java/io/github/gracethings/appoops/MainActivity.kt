package io.github.gracethings.appoops

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import io.github.gracethings.appoops.data.preference.LanguagePreferences
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.service.AppOpsMonitorService
import io.github.gracethings.appoops.ui.screen.AboutScreen
import io.github.gracethings.appoops.ui.screen.AppDetailScreen
import io.github.gracethings.appoops.ui.screen.AppListScreen
import io.github.gracethings.appoops.ui.screen.AuditLogScreen
import io.github.gracethings.appoops.ui.screen.BatchScreen
import io.github.gracethings.appoops.ui.screen.CommunityRulesScreen
import io.github.gracethings.appoops.ui.screen.DashboardScreen
import io.github.gracethings.appoops.ui.screen.SettingsScreen
import io.github.gracethings.appoops.ui.theme.AppOopsTheme
import io.github.gracethings.appoops.ui.theme.ThemePreferences
import io.github.gracethings.appoops.util.PackageUtils

sealed class Screen {
    data object Dashboard : Screen()
    data object Audit : Screen()
    data object Apps : Screen()
    data class Detail(
        val packageName: String,
        val uid: Int,
        val backTarget: String = "Apps"
    ) : Screen()
    data object Batch : Screen()
    data class BatchSelect(val suite: io.github.gracethings.appoops.data.model.BatchSuiteType) : Screen()
    data class CommunityRules(
        val initialPackage: String? = null,
        val returnToDetail: Triple<String, Int, String>? = null
    ) : Screen()
    data object Settings : Screen()
    data object About : Screen()

    fun getOrder(): Int = when (this) {
        Dashboard -> 0
        Audit -> 1
        Apps -> 2
        is Detail -> 3
        Batch -> 4
        is BatchSelect -> 5
        is CommunityRules -> 5
        Settings -> 6
        About -> 7
    }
}

class MainActivity : ComponentActivity() {

    private var targetPackageFromIntent = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrapContext(newBase))
    }

    override fun onResume() {
        super.onResume()
        LanguagePreferences.syncFromSystem(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            val themeMode by ThemePreferences.themeMode.collectAsState()
            val themeAccent by ThemePreferences.themeAccent.collectAsState()

            AppOopsTheme(themeMode = themeMode, themeAccent = themeAccent) {
                AppOopsMainApp(
                    initialTargetPackage = targetPackageFromIntent.value,
                    onClearTargetPackage = { targetPackageFromIntent.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val pkg = intent?.getStringExtra(AppOpsMonitorService.EXTRA_PACKAGE)
        io.github.gracethings.appoops.util.AppLogger.i("handleIntent: action=${intent?.action}, pkg=$pkg")
        if (!pkg.isNullOrEmpty()) {
            targetPackageFromIntent.value = pkg
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.destroy()
    }
}

private val ScreenSaver: Saver<Screen, Any> = listSaver(
    save = { screen ->
        when (screen) {
            is Screen.Dashboard -> listOf("Dashboard")
            is Screen.Audit -> listOf("Audit")
            is Screen.Apps -> listOf("Apps")
            is Screen.Detail -> listOf("Detail", screen.packageName, screen.uid, screen.backTarget)
            is Screen.Batch -> listOf("Batch")
            is Screen.BatchSelect -> listOf("BatchSelect", screen.suite.name)
            is Screen.CommunityRules -> listOf(
                "CommunityRules",
                screen.initialPackage,
                screen.returnToDetail?.first,
                screen.returnToDetail?.second,
                screen.returnToDetail?.third
            )
            is Screen.Settings -> listOf("Settings")
            is Screen.About -> listOf("About")
        }
    },
    restore = { list ->
        when (list.firstOrNull() as? String) {
            "Audit" -> Screen.Audit
            "Apps" -> Screen.Apps
            "Detail" -> Screen.Detail(
                packageName = list[1] as String,
                uid = list[2] as Int,
                backTarget = list.getOrNull(3) as? String ?: "Apps"
            )
            "Batch" -> Screen.Batch
            "BatchSelect" -> Screen.BatchSelect(
                io.github.gracethings.appoops.data.model.BatchSuiteType.valueOf(list[1] as String)
            )
            "CommunityRules" -> {
                val initPkg = list[1] as? String
                val retPkg = list[2] as? String
                val retUid = list[3] as? Int
                val retBack = list.getOrNull(4) as? String ?: "Apps"
                Screen.CommunityRules(
                    initialPackage = initPkg,
                    returnToDetail = if (retPkg != null && retUid != null) Triple(retPkg, retUid, retBack) else null
                )
            }
            "Settings" -> Screen.Settings
            "About" -> Screen.About
            else -> Screen.Dashboard
        }
    }
)

@Composable
fun AppOopsMainApp(
    initialTargetPackage: String?,
    onClearTargetPackage: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentScreen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Dashboard) }

    val shizukuStatus by ShizukuManager.status.collectAsState()
    val isGuardEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isGuardEnabled.collectAsState()


    // Auto-start or stop monitor service based on user preference and Shizuku status
    LaunchedEffect(shizukuStatus, isGuardEnabled) {
        if (isGuardEnabled && shizukuStatus == io.github.gracethings.appoops.data.shizuku.ShizukuState.AUTHORIZED) {
            if (!AppOpsMonitorService.isRunning.value) {
                AppOpsMonitorService.startService(context)
            }
        } else if (!isGuardEnabled && AppOpsMonitorService.isRunning.value) {
            AppOpsMonitorService.stopService(context)
        }
    }

    // Direct jump if opened from notification
    LaunchedEffect(initialTargetPackage) {
        if (!initialTargetPackage.isNullOrEmpty()) {
            val apps = AppRepository.appsFlow.value
            val targetApp = apps.firstOrNull { it.packageName == initialTargetPackage }
            val uid = targetApp?.uid ?: runCatching {
                context.packageManager.getApplicationInfo(initialTargetPackage, 0).uid
            }.getOrDefault(0)
            io.github.gracethings.appoops.util.AppLogger.i("Navigating to detail for $initialTargetPackage (uid=$uid)")
            currentScreen = Screen.Detail(initialTargetPackage, uid)
            onClearTargetPackage()
        }
    }

    val isFullScreen = currentScreen is Screen.Audit ||
        currentScreen is Screen.Detail ||
        currentScreen is Screen.BatchSelect ||
        currentScreen is Screen.CommunityRules ||
        currentScreen is Screen.About

    BackHandler(enabled = currentScreen is Screen.Audit) {
        currentScreen = Screen.Dashboard
    }

    BackHandler(enabled = currentScreen is Screen.Detail) {
        val detail = currentScreen as? Screen.Detail
        currentScreen = when (detail?.backTarget) {
            "Dashboard" -> Screen.Dashboard
            "Audit" -> Screen.Audit
            else -> Screen.Apps
        }
    }

    BackHandler(enabled = currentScreen is Screen.BatchSelect) {
        currentScreen = Screen.Batch
    }

    BackHandler(enabled = currentScreen is Screen.CommunityRules) {
        val cs = currentScreen as? Screen.CommunityRules
        currentScreen = if (cs?.returnToDetail != null) {
            Screen.Detail(cs.returnToDetail.first, cs.returnToDetail.second, cs.returnToDetail.third)
        } else {
            Screen.Batch
        }
    }

    BackHandler(enabled = currentScreen is Screen.About) {
        currentScreen = Screen.Settings
    }

    Scaffold(
        bottomBar = {
            if (!isFullScreen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Dashboard,
                        onClick = { currentScreen = Screen.Dashboard },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = stringResource(R.string.dashboard)) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Apps,
                        onClick = { currentScreen = Screen.Apps },
                        icon = { Icon(Icons.Default.Apps, contentDescription = stringResource(R.string.apps)) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Batch,
                        onClick = { currentScreen = Screen.Batch },
                        icon = { Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.batch_rules)) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Settings,
                        onClick = { currentScreen = Screen.Settings },
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val fromOrder = initialState.getOrder()
                    val toOrder = targetState.getOrder()
                    if (toOrder >= fromOrder) {
                        (slideInHorizontally(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) + fadeIn(animationSpec = tween(durationMillis = 300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> -fullWidth }
                                ) + fadeOut(animationSpec = tween(durationMillis = 180))
                            )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -fullWidth }
                        ) + fadeIn(animationSpec = tween(durationMillis = 300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    targetOffsetX = { fullWidth -> fullWidth }
                                ) + fadeOut(animationSpec = tween(durationMillis = 180))
                            )
                    }
                },
                label = "screen_horizontal_slide"
            ) { screen ->
                when (screen) {
                    is Screen.Dashboard -> DashboardScreen(
                        onNavigateToAuditLog = { currentScreen = Screen.Audit },
                        onNavigateToAppDetail = { pkg ->
                            val uid = runCatching {
                                context.packageManager.getApplicationInfo(pkg, 0).uid
                            }.getOrDefault(0)
                            currentScreen = Screen.Detail(pkg, uid, backTarget = "Dashboard")
                        }
                    )
                    is Screen.Apps -> AppListScreen(
                        onNavigateToDetail = { pkg, uid ->
                            currentScreen = Screen.Detail(pkg, uid, backTarget = "Apps")
                        }
                    )
                    is Screen.Batch -> BatchScreen(
                        onNavigateToSelect = { suite ->
                            currentScreen = Screen.BatchSelect(suite)
                        },
                        onNavigateToCommunityRules = {
                            currentScreen = Screen.CommunityRules()
                        }
                    )
                    is Screen.BatchSelect -> io.github.gracethings.appoops.ui.screen.BatchSelectScreen(
                        suite = screen.suite,
                        onBack = { currentScreen = Screen.Batch },
                        onApplied = { currentScreen = Screen.Batch }
                    )
                    is Screen.CommunityRules -> CommunityRulesScreen(
                        initialPackage = screen.initialPackage,
                        onBack = {
                            currentScreen = if (screen.returnToDetail != null) {
                                Screen.Detail(
                                    screen.returnToDetail.first,
                                    screen.returnToDetail.second,
                                    screen.returnToDetail.third
                                )
                            } else {
                                Screen.Batch
                            }
                        }
                    )
                    is Screen.Audit -> AuditLogScreen(
                        onBack = { currentScreen = Screen.Dashboard },
                        onNavigateToDetail = { pkg ->
                            val uid = runCatching {
                                context.packageManager.getApplicationInfo(pkg, 0).uid
                            }.getOrDefault(0)
                            currentScreen = Screen.Detail(pkg, uid, backTarget = "Audit")
                        }
                    )
                    is Screen.Settings -> SettingsScreen(
                        onNavigateToAbout = { currentScreen = Screen.About }
                    )
                    is Screen.About -> AboutScreen(
                        onBack = { currentScreen = Screen.Settings }
                    )
                    is Screen.Detail -> AppDetailScreen(
                        packageName = screen.packageName,
                        uid = screen.uid,
                        onBack = {
                            currentScreen = when (screen.backTarget) {
                                "Dashboard" -> Screen.Dashboard
                                "Audit" -> Screen.Audit
                                else -> Screen.Apps
                            }
                        },
                        onNavigateToCommunityRules = { pkg ->
                            currentScreen = Screen.CommunityRules(
                                initialPackage = pkg,
                                returnToDetail = Triple(pkg, screen.uid, screen.backTarget)
                            )
                        }
                    )
                }
            }
        }
    }
}

