package io.github.gracethings.appoops.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import io.github.gracethings.appoops.data.model.AppItem
import io.github.gracethings.appoops.data.repository.AppRepository
import io.github.gracethings.appoops.data.shizuku.ShizukuManager
import io.github.gracethings.appoops.data.shizuku.ShizukuState
import io.github.gracethings.appoops.ui.components.AppListItemCard
import kotlinx.coroutines.launch

enum class AppFilter {
    ALL, SHAKE, CLIPBOARD, BACKGROUND, USER_ONLY, SYSTEM_ONLY
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onNavigateToDetail: (packageName: String, uid: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apps by AppRepository.appsFlow.collectAsState()
    val isLoading by AppRepository.isLoading.collectAsState()
    val isInitialLoadCompleted by AppRepository.isInitialLoadCompleted.collectAsState()
    val isEnriched by AppRepository.isEnriched.collectAsState()
    val shizukuStatus by ShizukuManager.status.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.ALL) }

    LaunchedEffect(isEnriched, shizukuStatus) {
        if (apps.isEmpty() || (!isEnriched && shizukuStatus == ShizukuState.AUTHORIZED)) {
            AppRepository.loadApps(context, forceRefresh = !isEnriched && apps.isNotEmpty())
        }
    }

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            val matchFilter = when (selectedFilter) {
                AppFilter.ALL -> true
                AppFilter.SHAKE -> app.isShakeImmunized
                AppFilter.CLIPBOARD -> app.isClipboardProtected
                AppFilter.BACKGROUND -> app.isBgRestricted
                AppFilter.USER_ONLY -> !app.isSystemApp
                AppFilter.SYSTEM_ONLY -> app.isSystemApp
            }
            matchSearch && matchFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.apps),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                coroutineScope.launch { AppRepository.loadApps(context, forceRefresh = true) }
            }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
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

        // Filter Chips Row
        val chipScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(chipScroll)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == AppFilter.ALL,
                onClick = { selectedFilter = AppFilter.ALL },
                label = { Text(stringResource(R.string.filter_all, apps.size)) }
            )
            FilterChip(
                selected = selectedFilter == AppFilter.USER_ONLY,
                onClick = { selectedFilter = AppFilter.USER_ONLY },
                label = { Text(stringResource(R.string.filter_user)) }
            )
            FilterChip(
                selected = selectedFilter == AppFilter.SHAKE,
                onClick = { selectedFilter = AppFilter.SHAKE },
                label = { Text(stringResource(R.string.filter_sensor_disabled)) }
            )
            FilterChip(
                selected = selectedFilter == AppFilter.CLIPBOARD,
                onClick = { selectedFilter = AppFilter.CLIPBOARD },
                label = { Text(stringResource(R.string.filter_clipboard_protected)) }
            )
            FilterChip(
                selected = selectedFilter == AppFilter.BACKGROUND,
                onClick = { selectedFilter = AppFilter.BACKGROUND },
                label = { Text(stringResource(R.string.filter_background_restricted)) }
            )
            FilterChip(
                selected = selectedFilter == AppFilter.SYSTEM_ONLY,
                onClick = { selectedFilter = AppFilter.SYSTEM_ONLY },
                label = { Text(stringResource(R.string.filter_system)) }
            )
        }
        
        // Background loading indicator when apps are already shown
        if (isLoading && apps.isNotEmpty()) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // App List Content
        if (!isInitialLoadCompleted || (apps.isEmpty() && isLoading)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ContainedLoadingIndicator()
                    Text(
                        text = stringResource(R.string.loading_apps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_apps_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppListItemCard(
                        app = app,
                        onClick = { onNavigateToDetail(app.packageName, app.uid) }
                    )
                }
            }
        }
    }
}

