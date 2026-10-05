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
            AppOpsMonitorService.updateNotification(
                context,
                "io.github.gracethings.appoops.test",
                testApp,
                testOp,
                1,
                io.github.gracethings.appoops.util.AppOpsConstants.OP_READ_CLIPBOARD,
                0
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
        val noticeType by io.github.gracethings.appoops.data.preference.AlertPreferences.noticeType.collectAsState()

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
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

                androidx.compose.animation.AnimatedVisibility(visible = isAlertEnabled) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = stringResource(R.string.setting_alert_type_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf(
                                io.github.gracethings.appoops.data.preference.AlertNoticeType.TOAST to stringResource(R.string.setting_alert_toast),
                                io.github.gracethings.appoops.data.preference.AlertNoticeType.LIVE_NOTIFICATION to stringResource(R.string.setting_alert_live_notification)
                            )
                            options.forEachIndexed { index, (type, label) ->
                                SegmentedButton(
                                    selected = noticeType == type,
                                    onClick = { io.github.gracethings.appoops.data.preference.AlertPreferences.setNoticeType(context, type) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
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

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                val testApp = context.getString(R.string.test_notice_app_name)
                                val testOp = context.getString(R.string.op_read_clipboard_title)
                                val nm = androidx.core.app.NotificationManagerCompat.from(context)

                                val needsNotification = noticeType == io.github.gracethings.appoops.data.preference.AlertNoticeType.LIVE_NOTIFICATION ||
                                        io.github.gracethings.appoops.data.preference.AlertPreferences.isTempAllowEnabled.value

                                if (needsNotification) {
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

                                when (noticeType) {
                                    io.github.gracethings.appoops.data.preference.AlertNoticeType.TOAST -> {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_blocked_single, testApp, testOp),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        if (io.github.gracethings.appoops.data.preference.AlertPreferences.isTempAllowEnabled.value) {
                                            AppOpsMonitorService.updateNotification(
                                                context,
                                                "io.github.gracethings.appoops.test",
                                                testApp,
                                                testOp,
                                                1,
                                                io.github.gracethings.appoops.util.AppOpsConstants.OP_READ_CLIPBOARD,
                                                0
                                            )
                                        }
                                    }
                                    io.github.gracethings.appoops.data.preference.AlertNoticeType.LIVE_NOTIFICATION -> {
                                        AppOpsMonitorService.updateNotification(
                                            context,
                                            "io.github.gracethings.appoops.test",
                                            testApp,
                                            testOp,
                                            1,
                                            io.github.gracethings.appoops.util.AppOpsConstants.OP_READ_CLIPBOARD,
                                            0
                                        )
                                    }
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

