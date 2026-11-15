package io.github.gracethings.appoops.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
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
import io.github.gracethings.appoops.data.model.AppRuleGroup
import io.github.gracethings.appoops.data.model.CommunityRule
import io.github.gracethings.appoops.data.model.OpMode
import io.github.gracethings.appoops.data.model.RuleLevel
import io.github.gracethings.appoops.data.model.RuleSubscription
import io.github.gracethings.appoops.data.preference.SubscriptionPreferences
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.repository.CommunityRuleRepository
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.ui.components.AppIcon
import io.github.gracethings.appoops.ui.components.ContributeRuleDialog
import io.github.gracethings.appoops.util.AppOpsConstants
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityRulesScreen(
    onBack: () -> Unit,
    initialPackage: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val subscriptions by SubscriptionPreferences.subscriptions.collectAsState()
    val allRules by CommunityRuleRepository.rulesFlow.collectAsState()
    val isRefreshing by CommunityRuleRepository.isRefreshing.collectAsState()
    val installedApps by AppRepository.appsFlow.collectAsState()
    val isAppsLoading by AppRepository.isLoading.collectAsState()

    var searchQuery by remember(initialPackage) { mutableStateOf(initialPackage ?: "") }

    var showAddSubDialog by remember { mutableStateOf(false) }
    var showManageSubsSheet by remember { mutableStateOf(false) }
    var selectedGroupForSheet by remember { mutableStateOf<AppRuleGroup?>(null) }

    var showAppPickerForContribute by remember { mutableStateOf(false) }
    var contributeTargetApp by remember { mutableStateOf<AppItem?>(null) }

    // Ensure installed app list is loaded for matching badges & icons
    LaunchedEffect(Unit) {
        if (installedApps.isEmpty()) {
            AppRepository.loadApps(context)
        }
    }

    val installedByPkg = remember(installedApps) {
        installedApps.associateBy { it.packageName }
    }

    val allGroups = remember(allRules, installedByPkg) {
        allRules
            .groupBy { it.packageName }
            .mapNotNull { (pkg, pkgRules) ->
                val installedItem = installedByPkg[pkg] ?: return@mapNotNull null
                val sortedPkgRules = pkgRules.sortedWith(
                    compareByDescending<CommunityRule> { it.recommended }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.title }
                )
                AppRuleGroup(
                    packageName = pkg,
                    displayAppName = installedItem.appName,
                    installedAppItem = installedItem,
                    rules = sortedPkgRules
                )
            }
            .sortedWith(
                compareByDescending<AppRuleGroup> { group -> group.rules.any { it.recommended } }
                    .thenBy { it.displayAppName.lowercase() }
            )
    }

    val filteredGroups = remember(allGroups, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            allGroups
        } else {
            allGroups.filter { group ->
                group.displayAppName.contains(q, ignoreCase = true) ||
                    group.packageName.contains(q, ignoreCase = true) ||
                    group.rules.any { rule ->
                        rule.title.contains(q, ignoreCase = true) ||
                            rule.author.contains(q, ignoreCase = true) ||
                            rule.description.contains(q, ignoreCase = true)
                    }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar matching Detail screen inset-free style
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
                    text = stringResource(R.string.community_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showAppPickerForContribute = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = stringResource(R.string.community_contribute_btn)
                    )
                }
                IconButton(
                    onClick = { showManageSubsSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.community_sub_manager_title)
                    )
                }
                IconButton(
                    enabled = !isRefreshing && subscriptions.isNotEmpty(),
                    onClick = {
                        coroutineScope.launch {
                            val res = CommunityRuleRepository.refreshSubscriptions(context)
                            res.onSuccess { count ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.community_sub_refresh_success, count),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.community_sub_refresh_failed, err.message ?: "Error"),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.shizuku_btn_refresh)
                    )
                }
            }

            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.community_search_hint)) },
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

                // Filter Chips & Subscription Summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                stringResource(
                                    R.string.community_matched_installed_apps,
                                    allGroups.size,
                                    allRules.size
                                )
                            )
                        }
                    )
                    AssistChip(
                        onClick = { showManageSubsSheet = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                if (subscriptions.isEmpty()) stringResource(R.string.community_sub_empty_short)
                                else "${subscriptions.count { it.isEnabled }}/${subscriptions.size}"
                            )
                        }
                    )
                    AssistChip(
                        onClick = { showAppPickerForContribute = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.community_contribute_btn)) }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (subscriptions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.community_empty_sub_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.community_empty_sub_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { showAddSubDialog = true },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.community_add_sub_fab))
                                }
                            }
                        }
                    }
                } else if (isAppsLoading && installedApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredGroups.isEmpty()) {
                    val noInstalledMatch = searchQuery.isBlank() && allRules.isNotEmpty() && allGroups.isEmpty()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = if (noInstalledMatch) {
                                    stringResource(R.string.community_empty_no_installed_match_title)
                                } else {
                                    stringResource(R.string.community_empty_rules_title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (noInstalledMatch) {
                                    stringResource(R.string.community_empty_no_installed_match_desc, allRules.size)
                                } else {
                                    stringResource(R.string.community_empty_rules_desc)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredGroups, key = { it.packageName }) { group ->
                            CommunityAppGroupCard(
                                group = group,
                                onClick = { selectedGroupForSheet = group }
                            )
                        }
                    }
                }
            }
        }

        // FAB to add subscriptions
        ExtendedFloatingActionButton(
            onClick = { showAddSubDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.community_add_sub_fab)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    if (showAddSubDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddSubDialog = false },
            onAdded = { sub ->
                showAddSubDialog = false
                Toast.makeText(
                    context,
                    context.getString(R.string.community_sub_added_toast, sub.name, sub.ruleCount),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showManageSubsSheet) {
        ManageSubscriptionsSheet(
            subscriptions = subscriptions,
            isRefreshing = isRefreshing,
            onDismiss = { showManageSubsSheet = false },
            onAddNew = {
                showManageSubsSheet = false
                showAddSubDialog = true
            },
            onToggle = { subId, enabled ->
                CommunityRuleRepository.toggleSubscription(context, subId, enabled)
            },
            onRefreshOne = { subId ->
                coroutineScope.launch {
                    val res = CommunityRuleRepository.refreshSubscriptions(context, targetSubId = subId)
                    res.onSuccess {
                        Toast.makeText(
                            context,
                            context.getString(R.string.community_sub_refresh_success, 1),
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { err ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.community_sub_refresh_failed, err.message ?: "Error"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDelete = { subId ->
                CommunityRuleRepository.removeSubscription(context, subId)
            }
        )
    }

    selectedGroupForSheet?.let { group ->
        AppRulesBottomSheet(
            group = group,
            onDismiss = { selectedGroupForSheet = null },
            onContribute = { appItem ->
                selectedGroupForSheet = null
                contributeTargetApp = appItem
            }
        )
    }

    if (showAppPickerForContribute) {
        PickAppForContributeDialog(
            apps = installedApps,
            onDismiss = { showAppPickerForContribute = false },
            onPicked = { pickedApp ->
                showAppPickerForContribute = false
                contributeTargetApp = pickedApp
            }
        )
    }

    contributeTargetApp?.let { targetApp ->
        ContributeRuleDialog(
            targetApp = targetApp,
            preloadedOps = null,
            onDismiss = { contributeTargetApp = null }
        )
    }
}

@Composable
private fun CommunityAppGroupCard(
    group: AppRuleGroup,
    onClick: () -> Unit
) {
    val hasRecommended = remember(group.rules) { group.rules.any { it.recommended } }
    val topRule = group.rules.firstOrNull()

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = group.packageName,
                contentDescription = group.displayAppName,
                initialBitmap = group.installedAppItem.iconBitmap,
                initialDrawable = group.installedAppItem.icon,
                size = 46.dp,
                cornerRadius = 12.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.displayAppName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (hasRecommended) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.community_badge_recommended),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = group.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallTagBadge(
                        text = stringResource(R.string.community_rule_count, group.rules.size),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (topRule != null) {
                        SmallTagBadge(
                            text = topRule.title,
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTagBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onAdded: (RuleSubscription) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var isNameManuallyEdited by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun applyPreset(url: String, defaultName: String) {
        urlInput = url
        if (!isNameManuallyEdited || nameInput.isBlank()) {
            nameInput = defaultName
            isNameManuallyEdited = false
        }
        errorMessage = null
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.community_add_sub_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.community_add_sub_url_label)) },
                    placeholder = { Text(stringResource(R.string.community_add_sub_url_placeholder)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        isNameManuallyEdited = it.isNotBlank()
                    },
                    label = { Text(stringResource(R.string.community_add_sub_name_label)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {
                            applyPreset(
                                url = SubscriptionPreferences.OFFICIAL_GITHUB_WEB,
                                defaultName = "GraceThings Official (GitHub)"
                            )
                        },
                        enabled = !isSubmitting,
                        label = { Text(stringResource(R.string.community_quick_github)) }
                    )
                    SuggestionChip(
                        onClick = {
                            applyPreset(
                                url = SubscriptionPreferences.OFFICIAL_GITEE_WEB,
                                defaultName = "GraceThings Official (Gitee)"
                            )
                        },
                        enabled = !isSubmitting,
                        label = { Text(stringResource(R.string.community_quick_gitee)) }
                    )
                }

                if (isSubmitting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && urlInput.isNotBlank(),
                onClick = {
                    coroutineScope.launch {
                        isSubmitting = true
                        errorMessage = null
                        val res = CommunityRuleRepository.addSubscription(
                            context = context,
                            rawUrlInput = urlInput,
                            customName = nameInput
                        )
                        isSubmitting = false
                        res.onSuccess { sub ->
                            onAdded(sub)
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Failed to add subscription"
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.community_btn_add_sync))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSubscriptionsSheet(
    subscriptions: List<RuleSubscription>,
    isRefreshing: Boolean,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRefreshOne: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.community_sub_manager_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAddNew) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.community_add_sub_fab))
                }
            }

            if (subscriptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.community_empty_sub_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subscriptions, key = { it.id }) { sub ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sub.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sub.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Switch(
                                        checked = sub.isEnabled,
                                        onCheckedChange = { onToggle(sub.id, it) }
                                    )
                                }

                                val timeLabel = if (sub.lastUpdated > 0L) {
                                    dateFormat.format(Date(sub.lastUpdated))
                                } else {
                                    stringResource(R.string.community_sub_never_synced)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stringResource(R.string.community_sub_info, sub.ruleCount, timeLabel),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        enabled = !isRefreshing,
                                        onClick = { onRefreshOne(sub.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDelete(sub.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.common_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                sub.lastError?.let { err ->
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRulesBottomSheet(
    group: AppRuleGroup,
    onDismiss: () -> Unit,
    onContribute: (AppItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var resetBeforeApply by remember { mutableStateOf(false) }
    var applyingRuleId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.displayAppName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = group.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onContribute(group.installedAppItem) }) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.community_contribute_btn))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { resetBeforeApply = !resetBeforeApply }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = resetBeforeApply,
                    onCheckedChange = { resetBeforeApply = it }
                )
                Text(
                    text = stringResource(R.string.community_apply_reset_toggle),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(group.rules, key = { idx, rule -> "${idx}_${rule.subscriptionId}_${rule.id}" }) { _, initialRule ->
                    var hydratedRule by remember(initialRule) { mutableStateOf(initialRule) }
                    var isLoadingDetail by remember { mutableStateOf(false) }
                    var detailExpanded by remember(initialRule) { mutableStateOf(true) }

                    // Lazy-load remote rule file if inline ops were omitted from index.json
                    LaunchedEffect(initialRule.id, initialRule.path) {
                        if (hydratedRule.ops.isEmpty() && !hydratedRule.path.isNullOrBlank()) {
                            isLoadingDetail = true
                            CommunityRuleRepository.resolveRuleOpsIfNeeded(hydratedRule)
                                .onSuccess { resolved -> hydratedRule = resolved }
                            isLoadingDetail = false
                        }
                    }

                    RuleDetailCard(
                        rule = hydratedRule,
                        isLoadingDetail = isLoadingDetail,
                        isApplying = applyingRuleId == hydratedRule.id,
                        expanded = detailExpanded,
                        onToggleExpand = { detailExpanded = !detailExpanded },
                        onApply = {
                            val appUid = group.installedAppItem.uid
                            if (!ShizukuManager.isAuthorized) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.community_apply_requires_shizuku),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@RuleDetailCard
                            }
                            coroutineScope.launch {
                                applyingRuleId = hydratedRule.id
                                val res = CommunityRuleRepository.applyRule(
                                    context = context,
                                    rule = hydratedRule,
                                    appUid = appUid,
                                    resetBeforeApply = resetBeforeApply
                                )
                                applyingRuleId = null
                                res.onSuccess { count ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.community_apply_success, hydratedRule.title, count),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismiss()
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_set_failed, err.message ?: "Error"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleDetailCard(
    rule: CommunityRule,
    isLoadingDetail: Boolean,
    isApplying: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current

    val levelLabel = when (rule.level) {
        RuleLevel.GENTLE -> stringResource(R.string.community_level_gentle)
        RuleLevel.BALANCED -> stringResource(R.string.community_level_balanced)
        RuleLevel.STRICT -> stringResource(R.string.community_level_strict)
    }
    val levelColor = when (rule.level) {
        RuleLevel.GENTLE -> MaterialTheme.colorScheme.secondary
        RuleLevel.BALANCED -> MaterialTheme.colorScheme.primary
        RuleLevel.STRICT -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (rule.recommended) {
                            SmallTagBadge(
                                text = stringResource(R.string.community_badge_recommended),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        SmallTagBadge(
                            text = levelLabel,
                            containerColor = levelColor.copy(alpha = 0.14f),
                            contentColor = levelColor
                        )
                        if (rule.updatedAt.isNotBlank()) {
                            Text(
                                text = rule.updatedAt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (rule.description.isNotBlank()) {
                Text(
                    text = rule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.community_author_format, rule.author, rule.subscriptionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (expanded) {
                if (isLoadingDetail) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rule.ops.forEach { (opName, mode) ->
                                val opCode = AppOpsConstants.getOpCode(opName)
                                val opTitle = opCode?.let { AppOpsConstants.getOpReadableName(context, it) } ?: opName
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = opTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SmallTagBadge(
                                        text = mode.name,
                                        containerColor = when (mode) {
                                            OpMode.IGNORE, OpMode.DENY -> MaterialTheme.colorScheme.errorContainer
                                            OpMode.ALLOW -> MaterialTheme.colorScheme.primaryContainer
                                            OpMode.DEFAULT -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        contentColor = when (mode) {
                                            OpMode.IGNORE, OpMode.DENY -> MaterialTheme.colorScheme.onErrorContainer
                                            OpMode.ALLOW -> MaterialTheme.colorScheme.onPrimaryContainer
                                            OpMode.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (rule.unsupportedOpsCount > 0) {
                Text(
                    text = stringResource(R.string.community_unsupported_ops_hint, rule.unsupportedOpsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                enabled = !isApplying && rule.ops.isNotEmpty(),
                onClick = onApply,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.community_apply_rule_btn, rule.ops.size))
                }
            }
        }
    }
}

@Composable
private fun PickAppForContributeDialog(
    apps: List<AppItem>,
    onDismiss: () -> Unit,
    onPicked: (AppItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val base = apps.sortedWith(compareBy<AppItem> { it.isSystemApp }.thenBy { it.appName.lowercase() })
        if (query.isBlank()) base
        else base.filter {
            it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.community_contribute_pick_app), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Surface(
                            onClick = { onPicked(app) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
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
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
