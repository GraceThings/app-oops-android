package io.github.gracethings.appoops.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.shizuku.ShizukuState

@Composable
fun ShizukuStatusCard(
    status: ShizukuState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAuthorized = status == ShizukuState.AUTHORIZED
    val needsPermission = status == ShizukuState.NEEDS_PERMISSION || status == ShizukuState.RATIONALE

    val containerColor by animateColorAsState(
        targetValue = when {
            isAuthorized -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            needsPermission -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        },
        label = "status_color"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pulsing dot indicator
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isAuthorized -> Color(0xFF10B981)
                                needsPermission -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }
                        )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = when (status) {
                        ShizukuState.AUTHORIZED -> stringResource(R.string.shizuku_authorized)
                        ShizukuState.NEEDS_PERMISSION, ShizukuState.RATIONALE -> stringResource(R.string.shizuku_needs_permission)
                        ShizukuState.NOT_RUNNING, ShizukuState.DEAD -> stringResource(R.string.shizuku_not_running)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.shizuku_btn_refresh)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (status) {
                    ShizukuState.AUTHORIZED -> stringResource(R.string.shizuku_authorized_desc)
                    ShizukuState.NEEDS_PERMISSION, ShizukuState.RATIONALE -> stringResource(R.string.shizuku_needs_permission_desc)
                    ShizukuState.NOT_RUNNING, ShizukuState.DEAD -> stringResource(R.string.shizuku_not_running_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isAuthorized) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (needsPermission) onRequestPermission() else onRefresh()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (needsPermission) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (needsPermission) Icons.Default.Security else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (needsPermission) stringResource(R.string.shizuku_btn_grant) else stringResource(R.string.shizuku_btn_refresh),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

