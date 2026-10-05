package io.github.gracethings.appoops.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import io.github.gracethings.appoops.data.model.OpMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpModeSelector(
    currentMode: OpMode,
    onModeSelected: (OpMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth()
    ) {
        OpMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = currentMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = OpMode.entries.size),
                colors = SegmentedButtonDefaults.colors()
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}

