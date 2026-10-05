package io.github.gracethings.appoops.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.BatchSuiteType

@Composable
fun BatchScreen(
    onNavigateToSelect: (BatchSuiteType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.batch_rules),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = stringResource(R.string.batch_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                BatchPresetCard(
                    suite = BatchSuiteType.SENSOR,
                    icon = Icons.Default.Vibration,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onSelect = { onNavigateToSelect(BatchSuiteType.SENSOR) }
                )
            }

            item {
                BatchPresetCard(
                    suite = BatchSuiteType.CLIPBOARD,
                    icon = Icons.Default.ContentPaste,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onSelect = { onNavigateToSelect(BatchSuiteType.CLIPBOARD) }
                )
            }

            item {
                BatchPresetCard(
                    suite = BatchSuiteType.QUERY_AND_USAGE,
                    icon = Icons.Default.Security,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onSelect = { onNavigateToSelect(BatchSuiteType.QUERY_AND_USAGE) }
                )
            }

            item {
                BatchPresetCard(
                    suite = BatchSuiteType.BACKGROUND,
                    icon = Icons.Default.BatteryChargingFull,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    onSelect = { onNavigateToSelect(BatchSuiteType.BACKGROUND) }
                )
            }
        }
    }
}

@Composable
fun BatchPresetCard(
    suite: BatchSuiteType,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
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
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(suite.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(suite.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_apply_to_selected), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

