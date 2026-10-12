package io.github.gracethings.appoops.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.service.AppOpsMonitorService
import io.github.gracethings.appoops.ui.theme.ThemeAccent
import io.github.gracethings.appoops.ui.theme.ThemeMode
import io.github.gracethings.appoops.ui.theme.ThemePreferences

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentMode by ThemePreferences.themeMode.collectAsState()
    val currentAccent by ThemePreferences.themeAccent.collectAsState()

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val testApp = context.getString(R.string.test_notice_app_name)
            val testOp = context.getString(R.string.op_read_clipboard_title)
            val isLive = io.github.gracethings.appoops.data.preference.AlertPreferences.isLiveUpdatesEnabled.value &&
                    io.github.gracethings.appoops.data.preference.AlertPreferences.isAndroid16Supported()
            AppOpsMonitorService.updateNotification(
                context,
                "io.github.gracethings.appoops.test",
                testApp,
                testOp,
                1,
                io.github.gracethings.appoops.util.AppOpsConstants.OP_READ_CLIPBOARD,
                0,
                forceLive = isLive
            )
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )

        // Interception Alert Notification Card
        val isAlertEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isAlertEnabled.collectAsState()
        val isToastEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isToastEnabled.collectAsState()
        val isNotificationEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isNotificationEnabled.collectAsState()
        val isLiveUpdatesEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isLiveUpdatesEnabled.collectAsState()
        val isAndroid16 = remember { io.github.gracethings.appoops.data.preference.AlertPreferences.isAndroid16Supported() }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // 主开关：拦截成功提示 (默认关闭)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isAlertEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setting_alert_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAlertEnabled) stringResource(R.string.setting_alert_on_desc) else stringResource(R.string.setting_alert_off_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAlertEnabled,
                        onCheckedChange = { io.github.gracethings.appoops.data.preference.AlertPreferences.setAlertEnabled(context, it) }
                    )
                }

                // 关闭时只显示上述主开关与说明；开启时展开下方选项
                androidx.compose.animation.AnimatedVisibility(visible = isAlertEnabled) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // 子选项 1：toast notice (默认开启)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Feedback,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_alert_toast),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.setting_alert_toast_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isToastEnabled,
                                onCheckedChange = { io.github.gracethings.appoops.data.preference.AlertPreferences.setToastEnabled(context, it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 子选项 2：notification (默认开启)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_alert_notification),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.setting_alert_notification_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isNotificationEnabled,
                                onCheckedChange = { enable ->
                                    io.github.gracethings.appoops.data.preference.AlertPreferences.setNotificationEnabled(context, enable)
                                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 子选项 3：live updates (android16+) (直接显示在 notification 设置下方，不再作为子卡片)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isAndroid16) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_alert_live_notification),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isAndroid16) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = if (isAndroid16) {
                                        stringResource(R.string.setting_alert_live_desc_supported)
                                    } else {
                                        stringResource(R.string.setting_alert_live_desc_unsupported)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAndroid16) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isAndroid16 && isLiveUpdatesEnabled,
                                enabled = isAndroid16,
                                onCheckedChange = { enable ->
                                    if (isAndroid16) {
                                        io.github.gracethings.appoops.data.preference.AlertPreferences.setLiveUpdatesEnabled(context, enable)
                                        if (enable && !isNotificationEnabled) {
                                            io.github.gracethings.appoops.data.preference.AlertPreferences.setNotificationEnabled(context, true)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                val testApp = context.getString(R.string.test_notice_app_name)
                                val testOp = context.getString(R.string.op_read_clipboard_title)
                                val nm = androidx.core.app.NotificationManagerCompat.from(context)

                                val isToast = io.github.gracethings.appoops.data.preference.AlertPreferences.isToastEnabled.value
                                val isNotif = io.github.gracethings.appoops.data.preference.AlertPreferences.isNotificationEnabled.value
                                val isLive = isNotif && io.github.gracethings.appoops.data.preference.AlertPreferences.isLiveUpdatesEnabled.value && isAndroid16

                                if (isNotif) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@OutlinedButton
                                    }

                                    if (!nm.areNotificationsEnabled()) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_need_notification_permission),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        runCatching {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(intent)
                                        }
                                        return@OutlinedButton
                                    }
                                }

                                if (!isToast && !isNotif) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.test_alert_no_method_enabled),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    return@OutlinedButton
                                }

                                if (isToast) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_blocked_single, testApp, testOp),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }

                                if (isNotif) {
                                    AppOpsMonitorService.updateNotification(
                                        context,
                                        "io.github.gracethings.appoops.test",
                                        testApp,
                                        testOp,
                                        1,
                                        io.github.gracethings.appoops.util.AppOpsConstants.OP_READ_CLIPBOARD,
                                        0,
                                        forceLive = isLive
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_test_notification))
                        }

                        // Temporary Allow Section
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))

                        val isTempAllowEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isTempAllowEnabled.collectAsState()
                        val tempAllowDuration by io.github.gracethings.appoops.data.preference.AlertPreferences.tempAllowDuration.collectAsState()

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.setting_temp_allow_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.setting_temp_allow_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isTempAllowEnabled,
                                onCheckedChange = { io.github.gracethings.appoops.data.preference.AlertPreferences.setTempAllowEnabled(context, it) }
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(visible = isTempAllowEnabled) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.setting_temp_duration_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    val durationOptions = listOf(5, 10, 15, 30)
                                    durationOptions.forEachIndexed { index, duration ->
                                        SegmentedButton(
                                            selected = tempAllowDuration == duration,
                                            onClick = { io.github.gracethings.appoops.data.preference.AlertPreferences.setTempAllowDuration(context, duration) },
                                            shape = SegmentedButtonDefaults.itemShape(index = index, count = durationOptions.size)
                                        ) {
                                            Text(
                                                text = "${duration}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.basicMarquee()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Appearance Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.setting_theme_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.setting_theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.setting_theme_light),
                        ThemeMode.DARK to stringResource(R.string.setting_theme_dark)
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = currentMode == mode,
                            onClick = { ThemePreferences.setThemeMode(context, mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.setting_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ThemeAccent.entries.forEach { accent ->
                        val isSelected = currentAccent == accent
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { ThemePreferences.setThemeAccent(context, accent) }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(accent.color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(12.dp)
                                    ) {}
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(accent.labelRes).take(4),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Shizuku Guide Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.setting_shizuku_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.setting_shizuku_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // About Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.setting_about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.setting_about_version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

