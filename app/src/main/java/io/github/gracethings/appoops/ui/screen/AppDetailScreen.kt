package io.github.gracethings.appoops.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.AppItem
import io.github.gracethings.appoops.data.model.AppOpDef
import io.github.gracethings.appoops.data.model.OpCategory
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.shizuku.AppOpsController
import io.github.gracethings.appoops.ui.components.AppIcon
import io.github.gracethings.appoops.ui.components.ContributeRuleDialog
import io.github.gracethings.appoops.ui.components.OpModeSelector
import io.github.gracethings.appoops.util.AppOpsConstants
import io.github.gracethings.appoops.util.PackageUtils
import kotlinx.coroutines.launch

@Composable
fun AppDetailScreen(
    packageName: String,
    uid: Int,
    onBack: () -> Unit,
    onNavigateToCommunityRules: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val apps by AppRepository.appsFlow.collectAsState()
    val app = remember(apps, packageName) {
        apps.firstOrNull { it.packageName == packageName } ?: AppItem(
            packageName = packageName,
            appName = PackageUtils.getAppName(context, packageName),
            icon = PackageUtils.getAppIcon(context, packageName),
            uid = uid,
            isSystemApp = false
        )
    }

    // Definitions of all target AppOps
    val opDefs = remember {
        listOf(
            AppOpDef(
                opCode = AppOpsConstants.OP_BODY_SENSORS,
                opName = "BODY_SENSORS",
                title = "Body & Motion Sensors",
                description = "Set to IGNORE to silently intercept accelerometer and motion sensors",
                category = OpCategory.SENSOR,
                titleRes = R.string.op_body_sensors_title,
                descRes = R.string.op_body_sensors_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_HIGH_SAMPLING_RATE_SENSORS,
                opName = "HIGH_SAMPLING_RATE_SENSORS",
                title = "High Sampling Rate Sensors",
                description = "Limits gyroscope & accelerometer refresh rate to prevent high-frequency motion tracking",
                category = OpCategory.SENSOR,
                titleRes = R.string.op_high_sampling_sensors_title,
                descRes = R.string.op_high_sampling_sensors_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_BODY_SENSORS_BACKGROUND,
                opName = "BODY_SENSORS_BACKGROUND",
                title = "Background Body Sensors",
                description = "Prevents reading motion state when app is in background",
                category = OpCategory.SENSOR,
                titleRes = R.string.op_body_sensors_bg_title,
                descRes = R.string.op_body_sensors_bg_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_ACTIVITY_RECOGNITION,
                opName = "ACTIVITY_RECOGNITION",
                title = "Activity Recognition",
                description = "Prevents apps from detecting walking, running, or device shaking state",
                category = OpCategory.SENSOR,
                titleRes = R.string.op_activity_rec_title,
                descRes = R.string.op_activity_rec_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_READ_CLIPBOARD,
                opName = "READ_CLIPBOARD",
                title = "Read Clipboard",
                description = "Returns empty content when set to IGNORE, eliminating clipboard snooping",
                category = OpCategory.PRIVACY,
                titleRes = R.string.op_read_clipboard_title,
                descRes = R.string.op_read_clipboard_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_WRITE_CLIPBOARD,
                opName = "WRITE_CLIPBOARD",
                title = "Write Clipboard",
                description = "Prevents rogue SDKs from overwriting current clipboard content silently",
                category = OpCategory.PRIVACY,
                titleRes = R.string.op_write_clipboard_title,
                descRes = R.string.op_write_clipboard_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_QUERY_ALL_PACKAGES,
                opName = "QUERY_ALL_PACKAGES",
                title = "Read Installed Packages",
                description = "Cuts off device profiling; prevents scanning banking, gaming, or competitor apps",
                category = OpCategory.PRIVACY,
                titleRes = R.string.op_query_packages_title,
                descRes = R.string.op_query_packages_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_GET_USAGE_STATS,
                opName = "GET_USAGE_STATS",
                title = "Usage Statistics",
                description = "Revokes ability to monitor what other apps you are currently using",
                category = OpCategory.PRIVACY,
                titleRes = R.string.op_usage_stats_title,
                descRes = R.string.op_usage_stats_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_RUN_IN_BACKGROUND,
                opName = "RUN_IN_BACKGROUND",
                title = "Run in Background",
                description = "Suspends app immediately upon backgrounding to prevent battery and network drain",
                category = OpCategory.BACKGROUND,
                titleRes = R.string.op_run_in_bg_title,
                descRes = R.string.op_run_in_bg_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_WAKE_LOCK,
                opName = "WAKE_LOCK",
                title = "Hold Wake Lock (WakeLock)",
                description = "Revokes ability to hold CPU wake lock to stop background overheating and battery drain",
                category = OpCategory.BACKGROUND,
                titleRes = R.string.op_wake_lock_title,
                descRes = R.string.op_wake_lock_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_SYSTEM_ALERT_WINDOW,
                opName = "SYSTEM_ALERT_WINDOW",
                title = "Display Over Other Apps (Overlay)",
                description = "Completely closes rogue popups and protects against clickjacking",
                category = OpCategory.SYSTEM,
                titleRes = R.string.op_system_alert_title,
                descRes = R.string.op_system_alert_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_WRITE_SETTINGS,
                opName = "WRITE_SETTINGS",
                title = "Modify System Settings",
                description = "Prevents unauthorized changes to system volume, screen brightness, or network",
                category = OpCategory.SYSTEM,
                titleRes = R.string.op_write_settings_title,
                descRes = R.string.op_write_settings_desc
            ),
            AppOpDef(
                opCode = AppOpsConstants.OP_ACCESS_NOTIFICATIONS,
                opName = "ACCESS_NOTIFICATIONS",
                title = "Notification Listener Access",
                description = "Inspects and blocks snooping on notification messages and SMS OTP codes",
                category = OpCategory.SYSTEM,
                titleRes = R.string.op_access_notif_title,
                descRes = R.string.op_access_notif_desc
            )
        )
    }

    val modeStates = remember { mutableStateMapOf<Int, OpMode>() }
    var isLoadingModes by remember { mutableStateOf(true) }

    fun syncRepositoryState() {
        val currentApp = AppRepository.appsFlow.value.firstOrNull { it.packageName == packageName } ?: return
        val shakeMode = modeStates[AppOpsConstants.OP_BODY_SENSORS] ?: OpMode.DEFAULT
        val clipMode = modeStates[AppOpsConstants.OP_READ_CLIPBOARD] ?: OpMode.DEFAULT
        val bgMode = modeStates[AppOpsConstants.OP_RUN_IN_BACKGROUND] ?: OpMode.DEFAULT
        val qMode = modeStates[AppOpsConstants.OP_QUERY_ALL_PACKAGES] ?: OpMode.DEFAULT

        AppRepository.updateAppItem(
            currentApp.copy(
                isShakeImmunized = shakeMode == OpMode.IGNORE || shakeMode == OpMode.DENY,
                isClipboardProtected = clipMode == OpMode.IGNORE || clipMode == OpMode.DENY,
                isBgRestricted = bgMode == OpMode.IGNORE || bgMode == OpMode.DENY,
                isQueryPackagesBlocked = qMode == OpMode.IGNORE || qMode == OpMode.DENY
            )
        )
    }

    fun refreshModes() {
        coroutineScope.launch {
            isLoadingModes = true
            opDefs.forEach { def ->
                val mode = AppOpsController.getMode(packageName, uid, def.opCode)
                modeStates[def.opCode] = mode
            }
            isLoadingModes = false
            syncRepositoryState()
        }
    }

    LaunchedEffect(packageName) {
        refreshModes()
    }

    // Multi-select state for recommended protections
    val selectedActions = remember { mutableStateSetOf<String>() }
    val chipScrollState = rememberScrollState()
    var showContributeDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Manual top bar (same level as other screens — no extra inset)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = app.appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onNavigateToCommunityRules(packageName) }) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = stringResource(R.string.community_detail_view_rules)
                )
            }
            IconButton(onClick = { showContributeDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = stringResource(R.string.community_detail_share_rule)
                )
            }
            IconButton(onClick = { refreshModes() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // App Header Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            packageName = app.packageName,
                            contentDescription = app.appName,
                            initialBitmap = app.iconBitmap,
                            initialDrawable = app.icon,
                            size = 60.dp,
                            cornerRadius = 16.dp,
                            fallbackIconSize = 32.dp,
                            iconPx = 128
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(
                                    R.string.app_uid_format,
                                    app.uid,
                                    if (app.isSystemApp) stringResource(R.string.app_type_system) else stringResource(R.string.app_type_user)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Recommended Protections — multi-select FilterChips
            item {
                Text(
                    text = stringResource(R.string.quick_recommendations),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = "shake" in selectedActions,
                        onClick = {
                            if ("shake" in selectedActions) selectedActions.remove("shake")
                            else selectedActions.add("shake")
                        },
                        label = { Text(stringResource(R.string.immunize_shake_btn)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = "clipboard" in selectedActions,
                        onClick = {
                            if ("clipboard" in selectedActions) selectedActions.remove("clipboard")
                            else selectedActions.add("clipboard")
                        },
                        label = { Text(stringResource(R.string.shield_clipboard_btn)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = "reset" in selectedActions,
                        onClick = {
                            if ("reset" in selectedActions) selectedActions.remove("reset")
                            else selectedActions.add("reset")
                        },
                        label = { Text(stringResource(R.string.reset_default_btn)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                if (selectedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if ("shake" in selectedActions) {
                                    AppOpsController.immunizeShakeAds(packageName, uid)
                                }
                                if ("clipboard" in selectedActions) {
                                    AppOpsController.shieldClipboard(packageName, uid)
                                }
                                if ("reset" in selectedActions) {
                                    AppOpsController.resetToDefault(packageName, uid)
                                }
                                selectedActions.clear()
                                Toast.makeText(context, context.getString(R.string.toast_applied_sensors), Toast.LENGTH_SHORT).show()
                                refreshModes()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.apply_btn))
                    }
                }
            }

            // Categorized Op Controls
            OpCategory.entries.forEach { category ->
                val categoryOps = opDefs.filter { it.category == category }
                if (categoryOps.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(category.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(categoryOps.size) { index ->
                        val def = categoryOps[index]
                        val currentMode = modeStates[def.opCode] ?: OpMode.DEFAULT

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = def.titleRes?.let { stringResource(it) } ?: def.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = def.opName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = def.descRes?.let { stringResource(it) } ?: def.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OpModeSelector(
                                    currentMode = currentMode,
                                    onModeSelected = { newMode ->
                                        modeStates[def.opCode] = newMode
                                        coroutineScope.launch {
                                            val res = AppOpsController.setMode(
                                                packageName = packageName,
                                                uid = uid,
                                                opCode = def.opCode,
                                                opName = def.opName,
                                                mode = newMode
                                            )
                                            if (res.isFailure) {
                                                Toast.makeText(context, context.getString(R.string.toast_set_failed, res.exceptionOrNull()?.message), Toast.LENGTH_SHORT).show()
                                                modeStates[def.opCode] = currentMode
                                            } else {
                                                syncRepositoryState()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContributeDialog) {
        val currentPreloaded = remember(modeStates.toMap()) {
            opDefs.associate { def ->
                def.opName to (modeStates[def.opCode] ?: OpMode.DEFAULT)
            }
        }
        ContributeRuleDialog(
            targetApp = app,
            preloadedOps = currentPreloaded,
            onDismiss = { showContributeDialog = false }
        )
    }
}

