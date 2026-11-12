package io.github.gracethings.appoops.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.AppItem
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.model.RuleLevel
import io.github.gracethings.appoops.data.repository.CommunityRuleRepository
import io.github.gracethings.appoops.data.shizuku.AppOpsController
import io.github.gracethings.appoops.util.AppOpsConstants

@Composable
fun ContributeRuleDialog(
    targetApp: AppItem,
    preloadedOps: Map<String, OpMode>? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoadingOps by remember { mutableStateOf(preloadedOps == null) }
    val discoveredOps = remember { mutableStateMapOf<String, OpMode>() }
    val selectedOpKeys = remember { mutableStateSetOf<String>() }

    LaunchedEffect(targetApp.packageName, preloadedOps) {
        discoveredOps.clear()
        selectedOpKeys.clear()
        if (preloadedOps != null) {
            preloadedOps.forEach { (opName, mode) ->
                if (mode != OpMode.DEFAULT && AppOpsConstants.getOpCode(opName) != null) {
                    discoveredOps[opName] = mode
                    selectedOpKeys.add(opName)
                }
            }
            isLoadingOps = false
        } else {
            isLoadingOps = true
            AppOpsConstants.communityAllowedOps.forEach { (opName, opCode) ->
                val mode = AppOpsController.getMode(targetApp.packageName, targetApp.uid, opCode)
                if (mode != OpMode.DEFAULT) {
                    discoveredOps[opName] = mode
                    selectedOpKeys.add(opName)
                }
            }
            isLoadingOps = false
        }
    }

    var ruleTitle by remember { mutableStateOf("") }
    var ruleSlug by remember { mutableStateOf("custom-privacy-rule") }
    var ruleDescription by remember { mutableStateOf("") }
    var authorName by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(RuleLevel.BALANCED) }

    fun copyToClipboard(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun buildSelectedJson(): String? {
        if (ruleTitle.isBlank()) {
            Toast.makeText(context, context.getString(R.string.community_contribute_missing_title), Toast.LENGTH_SHORT).show()
            return null
        }
        val includedOps = discoveredOps.filterKeys { it in selectedOpKeys }
        if (includedOps.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.select_at_least_one), Toast.LENGTH_SHORT).show()
            return null
        }
        return CommunityRuleRepository.buildRuleJson(
            ruleId = ruleSlug,
            packageName = targetApp.packageName,
            title = ruleTitle,
            description = ruleDescription,
            author = authorName,
            level = selectedLevel,
            ops = includedOps
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.community_contribute_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${targetApp.appName} (${targetApp.packageName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoadingOps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                } else if (discoveredOps.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.community_contribute_no_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.common_close))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ruleTitle,
                            onValueChange = { ruleTitle = it },
                            label = { Text(stringResource(R.string.community_contribute_rule_title_label)) },
                            placeholder = { Text(stringResource(R.string.community_contribute_rule_title_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ruleSlug,
                            onValueChange = { ruleSlug = it },
                            label = { Text(stringResource(R.string.community_contribute_rule_id_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = ruleDescription,
                            onValueChange = { ruleDescription = it },
                            label = { Text(stringResource(R.string.community_contribute_desc_label)) },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = authorName,
                            onValueChange = { authorName = it },
                            label = { Text(stringResource(R.string.community_contribute_author_label)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(R.string.community_contribute_level_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                RuleLevel.GENTLE to R.string.community_level_gentle,
                                RuleLevel.BALANCED to R.string.community_level_balanced,
                                RuleLevel.STRICT to R.string.community_level_strict
                            ).forEach { (lvl, labelRes) ->
                                FilterChip(
                                    selected = selectedLevel == lvl,
                                    onClick = { selectedLevel = lvl },
                                    label = { Text(stringResource(labelRes)) }
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.community_contribute_ops_header, selectedOpKeys.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        discoveredOps.entries.forEach { (opName, mode) ->
                            val checked = opName in selectedOpKeys
                            val opCode = AppOpsConstants.getOpCode(opName)
                            val readableName = opCode?.let { AppOpsConstants.getOpReadableName(context, it) } ?: opName
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                onClick = {
                                    if (checked) selectedOpKeys.remove(opName) else selectedOpKeys.add(opName)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            if (isChecked) selectedOpKeys.add(opName) else selectedOpKeys.remove(opName)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = readableName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "$opName → ${mode.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.community_contribute_hint_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val json = buildSelectedJson() ?: return@Button
                            copyToClipboard("app-oops-rule.json", json)
                            Toast.makeText(context, context.getString(R.string.community_contribute_copied), Toast.LENGTH_SHORT).show()
                            val url = CommunityRuleRepository.buildGitHubIssueUrl(
                                packageName = targetApp.packageName,
                                appName = targetApp.appName,
                                ruleTitle = ruleTitle.trim(),
                                ruleJson = json
                            )
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.community_contribute_submit_github))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                val json = buildSelectedJson() ?: return@TextButton
                                copyToClipboard("app-oops-rule.json", json)
                                Toast.makeText(context, context.getString(R.string.community_contribute_copied), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.community_contribute_copy_json))
                        }

                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}
