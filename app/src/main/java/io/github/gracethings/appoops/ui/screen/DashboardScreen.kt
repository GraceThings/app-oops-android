package io.github.gracethings.appoops.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.repository.AuditRepository
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.data.shizuku.ShizukuState
import io.github.gracethings.appoops.service.AppOpsMonitorService
import io.github.gracethings.appoops.ui.components.ShizukuStatusCard
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToBatch: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shizukuStatus by ShizukuManager.status.collectAsState()
    val isGuardEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isGuardEnabled.collectAsState()
    val isMonitorRunning by AppOpsMonitorService.isRunning.collectAsState()
    val auditLogs by AuditRepository.logsFlow.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Title
        Text(
            text = stringResource(R.string.dashboard),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )

        // Shizuku Status Banner
        ShizukuStatusCard(
            status = shizukuStatus,
            onRequestPermission = { ShizukuManager.requestPermission() },
            onRefresh = {
                ShizukuManager.updateState()
                coroutineScope.launch { AppRepository.loadApps(context, forceRefresh = true) }
            }
        )

        // Real-time Guard Switch Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = if (isMonitorRunning || isGuardEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.monitor_service_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = when {
                        isMonitorRunning -> stringResource(R.string.monitor_service_running)
                        isGuardEnabled && !ShizukuManager.isAuthorized -> stringResource(R.string.shizuku_needs_permission_desc)
                        else -> stringResource(R.string.monitor_service_stopped)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isGuardEnabled,
                    onCheckedChange = { enable ->
                        io.github.gracethings.appoops.data.preference.AlertPreferences.setGuardEnabled(context, enable)
                        if (enable) {
                            if (ShizukuManager.isAuthorized) {
                                AppOpsMonitorService.startService(context)
                            }
                        } else {
                            AppOpsMonitorService.stopService(context)
                        }
                    }
                )
            }
        }



        // Recent Interception Banner
        if (auditLogs.isNotEmpty()) {
            val latest = auditLogs.first()
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onNavigateToAuditLog() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.latest_intercept_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = stringResource(R.string.latest_intercept_format, latest.appName, latest.opName, latest.count),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text(
                        text = stringResource(R.string.view_log),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

