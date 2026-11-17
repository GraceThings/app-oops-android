package io.github.gracethings.appoops.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.InterceptTrendRange
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.repository.AuditRepository
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.service.AppOpsMonitorService
import io.github.gracethings.appoops.ui.components.InterceptionTrendBoard
import io.github.gracethings.appoops.ui.components.ShizukuStatusCard
import io.github.gracethings.appoops.ui.components.TopInterceptedAppsBoard
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onNavigateToAuditLog: () -> Unit,
    onNavigateToAppDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val shizukuStatus by ShizukuManager.status.collectAsState()
    val isGuardEnabled by io.github.gracethings.appoops.data.preference.AlertPreferences.isGuardEnabled.collectAsState()
    val isMonitorRunning by AppOpsMonitorService.isRunning.collectAsState()
    val auditLogs by AuditRepository.logsFlow.collectAsState()
    val totalInterceptions by AuditRepository.totalInterceptions.collectAsState()
    val statsRevision by AuditRepository.statsRevision.collectAsState()

    var selectedRange by remember { mutableStateOf(InterceptTrendRange.DAY) }

    val topApps = remember(statsRevision) {
        AuditRepository.getTopInterceptedApps(limit = 3)
    }
    val trendBuckets = remember(statsRevision, selectedRange) {
        AuditRepository.getTrendSeries(selectedRange)
    }

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

        // Section Header: Interception Statistics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.stats_section_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.stats_total_badge, totalInterceptions),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Board 1: Top 3 Intercepted Apps
        TopInterceptedAppsBoard(
            topApps = topApps,
            onAppClick = onNavigateToAppDetail
        )

        // Board 2: Interception Trend Curve (1D / 1W / 1M)
        InterceptionTrendBoard(
            buckets = trendBuckets,
            selectedRange = selectedRange,
            onSelectRange = { selectedRange = it }
        )

        // Entry to Audit Log Page
        val latest = auditLogs.firstOrNull()
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable { onNavigateToAuditLog() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audit_log),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = if (latest != null) {
                            stringResource(R.string.latest_intercept_format, latest.appName, latest.opName, latest.count)
                        } else {
                            stringResource(R.string.stats_audit_entry_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.view_log),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
