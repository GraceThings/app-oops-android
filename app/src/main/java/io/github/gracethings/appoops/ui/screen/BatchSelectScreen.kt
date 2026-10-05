package io.github.gracethings.appoops.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.appoops.R
import io.github.gracethings.appoops.data.model.AppItem
import io.github.gracethings.appoops.data.model.BatchSuiteType
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.shizuku.AppOpsController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSelectScreen(
    suite: BatchSuiteType,
    onBack: () -> Unit,
    onApplied: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apps by AppRepository.appsFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    // By default, pre-select user (third-party) apps for convenience
    val selectedPackages = remember {
        mutableStateMapOf<String, AppItem>().apply {
            apps.filter { !it.isSystemApp }.forEach { put(it.packageName, it) }
        }
    }

    var isProcessing by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var progressMessage by remember { mutableStateOf("") }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    fun applyRulesToSelected() {
        if (selectedPackages.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.select_at_least_one), Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch {
            isProcessing = true
            val targetList = selectedPackages.values.toList()
            val total = targetList.size

            targetList.forEachIndexed { index, app ->
                progressMessage = context.getString(R.string.batch_processing_msg, app.appName, index + 1, total)
                currentProgress = (index + 1).toFloat() / total

                runCatching {
                    when (suite) {
                        BatchSuiteType.SENSOR -> {
                            AppOpsController.immunizeShakeAds(app.packageName, app.uid)
                        }
                        BatchSuiteType.CLIPBOARD -> {
                            AppOpsController.shieldClipboard(app.packageName, app.uid)
                        }
                        BatchSuiteType.QUERY_AND_USAGE -> {
                            AppOpsController.blockQueryPackages(app.packageName, app.uid)
                        }
                        BatchSuiteType.BACKGROUND -> {
                            AppOpsController.restrictBackground(app.packageName, app.uid)
                        }
                    }
                }
            }

            isProcessing = false
            Toast.makeText(context, context.getString(R.string.batch_success_toast, context.getString(suite.titleRes), total), Toast.LENGTH_LONG).show()
            AppRepository.loadApps(context, forceRefresh = true)
            onApplied()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.select_apps_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(suite.titleRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isProcessing) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { applyRulesToSelected() },
                expanded = true,
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text(stringResource(R.string.apply_to_selected_fab, selectedPackages.size)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Processing Progress Box
            if (isProcessing) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.batch_processing_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )

            // Fast selection row
            val chipScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScroll)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedPackages.size == filteredApps.size && filteredApps.isNotEmpty(),
                    onClick = {
                        filteredApps.forEach { selectedPackages[it.packageName] = it }
                    },
                    label = { Text(stringResource(R.string.select_all_current, filteredApps.size)) }
                )
                FilterChip(
                    selected = selectedPackages.isEmpty(),
                    onClick = {
                        selectedPackages.clear()
                    },
                    label = { Text(stringResource(R.string.deselect_all)) }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        selectedPackages.clear()
                        apps.filter { !it.isSystemApp }.forEach { selectedPackages[it.packageName] = it }
                    },
                    label = { Text(stringResource(R.string.select_only_user)) }
                )
            }

            Text(
                text = stringResource(R.string.selected_count_hint, selectedPackages.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // App selection list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = selectedPackages.containsKey(app.packageName)
                    Card(
                        onClick = {
                            if (isChecked) {
                                selectedPackages.remove(app.packageName)
                            } else {
                                selectedPackages[app.packageName] = app
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedPackages[app.packageName] = app
                                    } else {
                                        selectedPackages.remove(app.packageName)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            val iconBitmap = app.iconBitmap ?: app.icon?.let {
                                runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull()
                            }
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (app.isSystemApp) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_system),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

