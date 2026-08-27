@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.flockyou.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flockyou.data.model.*
import com.flockyou.scanner.flipper.FlipperConnectionState
import com.flockyou.service.CellularMonitor
import com.flockyou.privilege.PrivilegeMode
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.flockyou.R
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.flockyou.ui.components.*
import com.flockyou.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import kotlinx.coroutines.delay
import androidx.compose.material3.OutlinedTextFieldDefaults

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToMap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToNearby: () -> Unit = {},
    onNavigateToRfDetection: () -> Unit = {},
    onNavigateToUltrasonicDetection: () -> Unit = {},
    onNavigateToSatelliteDetection: () -> Unit = {},
    onNavigateToWifiSecurity: () -> Unit = {},
    onNavigateToServiceHealth: () -> Unit = {},
    onNavigateToActiveProbes: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredHistoryDetections by viewModel.filteredHistoryDetections.collectAsStateWithLifecycle()
    val prioritizedEnrichmentIds by viewModel.prioritizedEnrichmentIds.collectAsStateWithLifecycle()
    val flipperUiSettings by viewModel.flipperUiSettings.collectAsStateWithLifecycle()
    val flipperSettings by viewModel.flipperSettings.collectAsStateWithLifecycle()
    val flipperIsInstalling by viewModel.flipperIsInstalling.collectAsStateWithLifecycle()
    val flipperInstallProgress by viewModel.flipperInstallProgress.collectAsStateWithLifecycle()
    val relatedDetections by viewModel.relatedDetections.collectAsStateWithLifecycle()

    // Filtered anomalies (excludes FP-marked detections)
    val filteredCellularAnomalies = remember(uiState.cellularAnomalies, uiState.detections, uiState.hideFalsePositives, uiState.fpFilterThreshold) {
        viewModel.getFilteredCellularAnomalies()
    }
    val filteredSatelliteAnomalies = remember(uiState.satelliteAnomalies, uiState.detections, uiState.hideFalsePositives, uiState.fpFilterThreshold) {
        viewModel.getFilteredSatelliteAnomalies()
    }
    val filteredRogueWifiAnomalies = remember(uiState.rogueWifiAnomalies, uiState.detections, uiState.hideFalsePositives, uiState.fpFilterThreshold) {
        viewModel.getFilteredRogueWifiAnomalies()
    }

    val context = LocalContext.current
    val hardwareCapabilities = rememberDeviceHardwareCapabilities()
    val isConstrainedDevice = rememberConstrainedDevice()
    val hasExternalRfHardware = uiState.flipperConnectionState == FlipperConnectionState.READY
    var showFilterSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedDetection by remember { mutableStateOf<Detection?>(null) }

    // Snackbar host state for showing refresh completion toast
    val snackbarHostState = remember { SnackbarHostState() }

    // Pager state for swipe navigation between tabs
    // Use pagerState as single source of truth for tab position
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { 4 }  // Home, History, Cellular, Flipper
    )
    val coroutineScope = rememberCoroutineScope()

    // Track detection count before refresh for delta calculation
    var preRefreshCount by remember { mutableStateOf(0) }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            preRefreshCount = uiState.totalCount
            isRefreshing = true
            viewModel.requestRefresh()
            coroutineScope.launch {
                delay(1500) // Give time for data to refresh
                isRefreshing = false
                // Show completion snackbar with detection delta
                // Read current value directly from viewModel to avoid stale closure
                val currentCount = viewModel.uiState.value.totalCount
                val newDetections = currentCount - preRefreshCount
                val message = when {
                    newDetections > 0 -> "Updated - $newDetections new detection${if (newDetections > 1) "s" else ""}"
                    newDetections < 0 -> "Updated - Removed ${-newDetections} detection${if (-newDetections > 1) "s" else ""}"
                    else -> "Updated - No new detections"
                }
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    )

    // Track if we're currently animating from a programmatic navigation
    // This prevents the pager from fighting with the ViewModel during animations
    var isNavigatingProgrammatically by remember { mutableStateOf(false) }

    // Sync ViewModel state when pager settles after user swipe
    // Use snapshotFlow with settledPage to only trigger when page is fully settled
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collectLatest { settledPage ->
            // Only update ViewModel if this wasn't a programmatic navigation
            // and the values are actually different
            if (!isNavigatingProgrammatically && viewModel.uiState.value.selectedTab != settledPage) {
                viewModel.selectTab(settledPage)
            }
            isNavigatingProgrammatically = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_title_flock),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " ${stringResource(R.string.app_title_you)}",
                            fontWeight = FontWeight.Light
                        )
                    }
                },
                actions = {
                    // Show filter button on history and home tabs with badge showing active filter count
                    if (uiState.selectedTab == 0 || uiState.selectedTab == 1) {
                        val filterCount = viewModel.getActiveFilterCount()
                        FilterButton(
                            filterCount = filterCount,
                            onClick = { showFilterSheet = true }
                        )
                    }
                    IconButton(onClick = onNavigateToNearby) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Nearby Devices"
                        )
                    }
                    IconButton(onClick = onNavigateToMap) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Map"
                        )
                    }
                    // Export debug info button - only shown in advanced mode
                    if (uiState.advancedMode) {
                        IconButton(
                            onClick = {
                                val debugInfo = viewModel.exportAllDebugInfo()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.debug_export_subject))
                                    putExtra(Intent.EXTRA_TEXT, debugInfo)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Debug Info"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Export Debug Info"
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            // Helper function to navigate to a page with debounce protection
            val navigateToPage: (Int) -> Unit = { targetPage ->
                // Skip if we're already on this page or animation is in progress
                if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
                    isNavigatingProgrammatically = true
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetPage)
                    }
                }
            }

            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Now") },
                    selected = pagerState.currentPage == 0,
                    onClick = { navigateToPage(0) }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = pagerState.currentPage == 1,
                    onClick = { navigateToPage(1) }
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (filteredCellularAnomalies.isNotEmpty()) {
                                    Badge { Text(filteredCellularAnomalies.size.toString()) }
                                } else {
                                    // Show checkmark badge when no anomalies
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.CellTower, contentDescription = "Cellular")
                        }
                    },
                    label = { Text("Cellular") },
                    selected = pagerState.currentPage == 2,
                    onClick = { navigateToPage(2) }
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                when (uiState.flipperConnectionState) {
                                    FlipperConnectionState.READY -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiary
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    FlipperConnectionState.CONNECTING,
                                    FlipperConnectionState.CONNECTED,
                                    FlipperConnectionState.DISCOVERING_SERVICES -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        ) {
                                            Icon(
                                                Icons.Default.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    FlipperConnectionState.ERROR -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    else -> {} // No badge when disconnected
                                }
                            }
                        ) {
                            Icon(Icons.Default.Usb, contentDescription = "Flipper")
                        }
                    },
                    label = { Text("Flipper") },
                    selected = pagerState.currentPage == 3,
                    onClick = { navigateToPage(3) }
                )
            }
        }
    ) { paddingValues ->
        // Swipeable HorizontalPager for tab navigation with pull-to-refresh
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Satellite connection banner - shown across all tabs when satellite is active
            val satState = uiState.satelliteState
            if (satState?.isConnected == true) {
                SatelliteConnectionBanner(satelliteState = satState)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) { page ->
                when (page) {
                    0 -> {
                        // Home tab - Status and modules only (no errors, no recent detections)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        // Status card without errors
                        item(key = "status_card") {
                            StatusCard(
                                isScanning = uiState.isScanning,
                                totalDetections = uiState.totalCount,
                                highThreatCount = uiState.highThreatCount,
                                onToggleScan = { viewModel.toggleScanning() },
                                scanStatus = uiState.scanStatus,
                                bleStatus = uiState.bleStatus,
                                wifiStatus = uiState.wifiStatus,
                                locationStatus = uiState.locationStatus,
                                cellularStatus = uiState.cellularStatus,
                                satelliteStatus = uiState.satelliteStatus,
                                recentErrors = emptyList(), // Don't show errors on home
                                onClearErrors = { }
                            )
                        }

                        item(key = "capability_summary") {
                            CapabilitySummaryCard(
                                privilegeMode = viewModel.privilegeMode,
                                hardware = hardwareCapabilities,
                                isConstrainedDevice = isConstrainedDevice,
                                hasExternalRfHardware = hasExternalRfHardware
                            )
                        }

                        // Keep detailed cellular telemetry out of the default "Now" surface
                        // unless there is something to inspect or Advanced mode is enabled.
                        if (filteredCellularAnomalies.isNotEmpty() || uiState.advancedMode) {
                            item(key = "cellular_status_card") {
                                CellularStatusCard(
                                    cellStatus = uiState.cellStatus,
                                    anomalies = filteredCellularAnomalies,
                                    isMonitoring = uiState.cellularStatus == com.flockyou.service.SubsystemStatus.Active
                                )
                            }
                        }

                        // Detection Modules section
                        item(key = "detection_modules_header") {
                            Text(
                                text = "SENSORS & TOOLS",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item(key = "detection_modules_grid") {
                            DetectionModulesGrid(
                                onNavigateToRfDetection = onNavigateToRfDetection,
                                onNavigateToUltrasonicDetection = onNavigateToUltrasonicDetection,
                                onNavigateToSatelliteDetection = onNavigateToSatelliteDetection,
                                onNavigateToWifiSecurity = onNavigateToWifiSecurity,
                                privilegeMode = viewModel.privilegeMode,
                                hardwareCapabilities = hardwareCapabilities,
                                hasExternalRfHardware = hasExternalRfHardware,
                                wifiAnomalyCount = filteredRogueWifiAnomalies.size,
                                rfAnomalyCount = viewModel.getFilteredRfAnomalies().size,
                                ultrasonicBeaconCount = uiState.ultrasonicBeacons.size,
                                satelliteAnomalyCount = filteredSatelliteAnomalies.size
                            )
                        }

                        // Service Health shortcut card
                        item(key = "service_health_shortcut") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ),
                                onClick = onNavigateToServiceHealth
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MonitorHeart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Service Health",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "View detector status and errors",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Go",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        }
                    }
                    1 -> {
                        // History tab - precomputed narrow projection. Unrelated service telemetry
                        // can recompose MainScreen without re-filtering and re-sorting the full history.
                        val filteredDetections = filteredHistoryDetections

                        // Track expanded detection IDs (persists during scroll)
                        val expandedDetectionIds = remember { mutableStateMapOf<String, Boolean>() }

                        // Track last action for undo
                        var lastMarkedDetection by remember { mutableStateOf<Detection?>(null) }
                        var lastActionType by remember { mutableStateOf<String?>(null) }

                        // Handle undo action
                        LaunchedEffect(lastMarkedDetection, lastActionType) {
                            if (lastMarkedDetection != null && lastActionType != null) {
                                val result = snackbarHostState.showSnackbar(
                                    message = when (lastActionType) {
                                        "reviewed" -> "Marked as reviewed"
                                        "false_positive" -> "Marked as false positive"
                                        else -> "Updated"
                                    },
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    lastMarkedDetection?.let { viewModel.undoMarkDetection(it) }
                                }
                                lastMarkedDetection = null
                                lastActionType = null
                            }
                        }

                        // Compute grouped detections and visible correlations outside LazyColumn
                        // (LazyListScope is non-composable, so remember/composables can't be called inside it)
                        val groupedDetections = remember(filteredDetections) {
                            groupDetectionsByTime(filteredDetections)
                        }
                        val visibleCorrelations = remember(uiState.correlatedThreats, uiState.dismissedCorrelationIds) {
                            uiState.correlatedThreats.filter { it.id !in uiState.dismissedCorrelationIds }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        // Filter chips if filters active (only on history tab)
                        if (uiState.filterThreatLevel != null || uiState.filterDeviceTypes.isNotEmpty()) {
                            item(key = "filter_chips") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Filter mode indicator
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Filter mode:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FilterChip(
                                            selected = uiState.filterMatchAll,
                                            onClick = { viewModel.setFilterMatchAll(!uiState.filterMatchAll) },
                                            label = { Text(if (uiState.filterMatchAll) "Match ALL" else "Match ANY") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (uiState.filterMatchAll) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }

                                    // Active filter chips
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        uiState.filterThreatLevel?.let { level ->
                                            FilterChip(
                                                selected = true,
                                                onClick = { viewModel.setThreatFilter(null) },
                                                label = { Text(level.name) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove filter",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // Device type filter chips (can have multiple now)
                                    if (uiState.filterDeviceTypes.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            uiState.filterDeviceTypes.take(3).forEach { type ->
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { viewModel.removeDeviceTypeFilter(type) },
                                                    label = { Text(type.name.replace("_", " ")) },
                                                    trailingIcon = {
                                                        Icon(
                                                            Icons.Default.Close,
                                                            contentDescription = "Remove filter",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            if (uiState.filterDeviceTypes.size > 3) {
                                                FilterChip(
                                                    selected = true,
                                                    onClick = { showFilterSheet = true },
                                                    label = { Text("+${uiState.filterDeviceTypes.size - 3} more") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search bar
                        item(key = "search_bar") {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search detections...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search"
                                    )
                                },
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            )
                        }

                        // Section header with sort and FP toggle
                        item(key = "section_header") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DETECTION HISTORY",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // View mode toggle (Timeline / Incidents)
                                        if (visibleCorrelations.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    viewModel.setViewMode(
                                                        if (uiState.viewMode == DetectionViewMode.TIMELINE)
                                                            DetectionViewMode.INCIDENTS
                                                        else
                                                            DetectionViewMode.TIMELINE
                                                    )
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.viewMode == DetectionViewMode.INCIDENTS)
                                                        Icons.Default.ViewTimeline
                                                    else
                                                        Icons.Default.Layers,
                                                    contentDescription = "Switch to ${if (uiState.viewMode == DetectionViewMode.TIMELINE) "Incidents" else "Timeline"}",
                                                    tint = if (uiState.viewMode == DetectionViewMode.INCIDENTS)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        // Sort button with dropdown
                                        var showSortMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(
                                                onClick = { showSortMenu = true },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Sort,
                                                    contentDescription = "Sort by ${uiState.sortOrder.label}",
                                                    tint = if (uiState.sortOrder != SortOrder.NEWEST_FIRST)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false }
                                            ) {
                                                SortOrder.entries.forEach { order ->
                                                    DropdownMenuItem(
                                                        text = { Text(order.label) },
                                                        onClick = {
                                                            viewModel.setSortOrder(order)
                                                            showSortMenu = false
                                                        },
                                                        leadingIcon = {
                                                            if (uiState.sortOrder == order) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = "Selected",
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // FP filter toggle
                                        val fpCount = viewModel.getFalsePositiveCount()
                                        FilterChip(
                                            selected = !uiState.hideFalsePositives,
                                            onClick = { viewModel.toggleHideFalsePositives() },
                                            label = {
                                                Text(
                                                    text = if (uiState.hideFalsePositives) {
                                                        if (fpCount > 0) "Show FPs ($fpCount hidden)" else "FP filter on"
                                                    } else {
                                                        "Showing all"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (uiState.hideFalsePositives)
                                                        Icons.Default.VisibilityOff
                                                    else
                                                        Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        when {
                            uiState.isLoading -> {
                                item(key = "loading_state") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Loading detections...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            filteredDetections.isEmpty() -> {
                                item(key = "empty_state") {
                                    if (viewModel.hasActiveFilters() && uiState.detections.isNotEmpty()) {
                                        // Filters are hiding all results
                                        FilteredEmptyState(
                                            onClearFilters = { viewModel.clearFilters() }
                                        )
                                    } else {
                                        // Genuinely no detections
                                        EmptyState(
                                            isScanning = uiState.isScanning,
                                            onStartScanning = { viewModel.toggleScanning() }
                                        )
                                    }
                                }
                            }
                            else -> {
                                // Pattern alerts banner
                                if (visibleCorrelations.isNotEmpty()) {
                                    item(key = "pattern_alerts") {
                                        PatternAlertBanner(
                                            correlatedThreats = visibleCorrelations,
                                            onDismiss = { viewModel.dismissCorrelation(it) }
                                        )
                                    }
                                }

                                // Selection mode action bar
                                if (uiState.selectionMode) {
                                    item(key = "selection_bar") {
                                        SelectionActionBar(
                                            selectedCount = uiState.selectedDetectionIds.size,
                                            totalCount = filteredDetections.size,
                                            onSelectAll = { viewModel.selectAllDetections(filteredDetections.map { it.id }) },
                                            onCancel = { viewModel.toggleSelectionMode() },
                                            onMarkReviewed = { viewModel.batchMarkReviewed() },
                                            onMarkFalsePositive = { viewModel.batchMarkFalsePositive() }
                                        )
                                    }
                                }

                                if (uiState.viewMode == DetectionViewMode.INCIDENTS && visibleCorrelations.isNotEmpty()) {
                                    // Incident-grouped view: correlated threats as incident cards,
                                    // then uncorrelated detections below
                                    val correlatedDetectionIds = visibleCorrelations
                                        .flatMap { it.detections.map { d -> d.id } }
                                        .toSet()

                                    // Incident cards
                                    items(
                                        items = visibleCorrelations,
                                        key = { "incident_${it.id}" }
                                    ) { threat ->
                                        IncidentCard(
                                            correlatedThreat = threat,
                                            onDetectionClick = { selectedDetection = it }
                                        )
                                    }

                                    // Uncorrelated detections header
                                    val uncorrelated = filteredDetections.filter { it.id !in correlatedDetectionIds }
                                    if (uncorrelated.isNotEmpty()) {
                                        stickyHeader(key = "header_uncorrelated") {
                                            TimeGroupHeader(
                                                label = "Other Detections",
                                                count = uncorrelated.size,
                                                highestThreat = uncorrelated.maxOfOrNull { it.threatScore } ?: 0
                                            )
                                        }
                                        items(
                                            items = uncorrelated,
                                            key = { it.id }
                                        ) { detection ->
                                            SwipeableDetectionCard(
                                                detection = detection,
                                                onClick = { selectedDetection = detection },
                                                onMarkReviewed = { det ->
                                                    viewModel.markAsReviewed(det)
                                                    lastMarkedDetection = det
                                                    lastActionType = "reviewed"
                                                },
                                                onMarkFalsePositive = { det ->
                                                    viewModel.markAsFalsePositive(det)
                                                    lastMarkedDetection = det
                                                    lastActionType = "false_positive"
                                                },
                                                advancedMode = uiState.advancedMode,
                                                isExpanded = expandedDetectionIds[detection.id] == true,
                                                onExpandToggle = {
                                                    expandedDetectionIds[detection.id] = !(expandedDetectionIds[detection.id] ?: false)
                                                },
                                                onAnalyzeClick = if (viewModel.isAiAnalysisAvailable()) {
                                                    { viewModel.analyzeDetection(it) }
                                                } else null,
                                                isAnalyzing = uiState.analyzingDetectionId == detection.id,
                                                onPrioritizeEnrichment = { viewModel.prioritizeEnrichment(it) },
                                                isEnrichmentPending = prioritizedEnrichmentIds.contains(detection.id),
                                                relatedCount = uiState.relatedDetectionCounts[detection.id] ?: 0,
                                                selectionMode = uiState.selectionMode,
                                                isSelected = detection.id in uiState.selectedDetectionIds,
                                                onToggleSelection = { viewModel.toggleDetectionSelection(detection.id) }
                                            )
                                        }
                                    }
                                } else {
                                    // Time-grouped timeline view (default)
                                    groupedDetections.forEach { (header, detections) ->
                                        stickyHeader(key = "header_$header") {
                                            TimeGroupHeader(
                                                label = header,
                                                count = detections.size,
                                                highestThreat = detections.maxOfOrNull { it.threatScore } ?: 0
                                            )
                                        }

                                        items(
                                            items = detections,
                                            key = { it.id }
                                        ) { detection ->
                                            SwipeableDetectionCard(
                                                detection = detection,
                                                onClick = { selectedDetection = detection },
                                                onMarkReviewed = { det ->
                                                    viewModel.markAsReviewed(det)
                                                    lastMarkedDetection = det
                                                    lastActionType = "reviewed"
                                                },
                                                onMarkFalsePositive = { det ->
                                                    viewModel.markAsFalsePositive(det)
                                                    lastMarkedDetection = det
                                                    lastActionType = "false_positive"
                                                },
                                                advancedMode = uiState.advancedMode,
                                                isExpanded = expandedDetectionIds[detection.id] == true,
                                                onExpandToggle = {
                                                    expandedDetectionIds[detection.id] = !(expandedDetectionIds[detection.id] ?: false)
                                                },
                                                onAnalyzeClick = if (viewModel.isAiAnalysisAvailable()) {
                                                    { viewModel.analyzeDetection(it) }
                                                } else null,
                                                isAnalyzing = uiState.analyzingDetectionId == detection.id,
                                                onPrioritizeEnrichment = { viewModel.prioritizeEnrichment(it) },
                                                isEnrichmentPending = prioritizedEnrichmentIds.contains(detection.id),
                                                relatedCount = uiState.relatedDetectionCounts[detection.id] ?: 0,
                                                selectionMode = uiState.selectionMode,
                                                isSelected = detection.id in uiState.selectedDetectionIds,
                                                onToggleSelection = { viewModel.toggleDetectionSelection(detection.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    2 -> {
                        // Cellular tab content
                        CellularTabContent(
                            modifier = Modifier.fillMaxSize(),
                            cellStatus = uiState.cellStatus,
                            cellularStatus = uiState.cellularStatus,
                            cellularAnomalies = filteredCellularAnomalies,
                            seenCellTowers = uiState.seenCellTowers,
                            cellularEvents = uiState.cellularEvents,
                            satelliteState = uiState.satelliteState,
                            satelliteAnomalies = filteredSatelliteAnomalies,
                            isScanning = uiState.isScanning,
                            onToggleScan = { viewModel.toggleScanning() },
                            onClearCellularHistory = { viewModel.clearCellularHistory() },
                            onClearSatelliteHistory = { viewModel.clearSatelliteHistory() }
                        )
                    }
                    3 -> {
                        // Flipper Zero tab content
                        FlipperTabContent(
                            modifier = Modifier.fillMaxSize(),
                            connectionState = uiState.flipperConnectionState,
                            connectionType = uiState.flipperConnectionType,
                            flipperStatus = uiState.flipperStatus,
                            isScanning = uiState.flipperIsScanning,
                            detectionCount = uiState.flipperDetectionCount,
                            wipsAlertCount = uiState.flipperWipsAlertCount,
                            lastError = uiState.flipperLastError,
                            advancedMode = uiState.advancedMode,
                            scanSchedulerStatus = uiState.flipperScanSchedulerStatus,
                            // UX improvement parameters
                            autoReconnectState = uiState.flipperAutoReconnectState,
                            discoveredDevices = uiState.flipperDiscoveredDevices,
                            recentDevices = uiState.flipperRecentDevices,
                            isScanningForDevices = uiState.flipperIsScanningForDevices,
                            connectionRssi = uiState.flipperConnectionRssi,
                            showDevicePicker = uiState.flipperShowDevicePicker,
                            // Settings parameters
                            flipperSettings = flipperSettings,
                            isInstalling = flipperIsInstalling,
                            installProgress = flipperInstallProgress,
                            // Callbacks
                            flipperUiSettings = flipperUiSettings,
                            onConnect = { viewModel.showFlipperDevicePicker() },
                            onDisconnect = { viewModel.disconnectFlipper() },
                            onTogglePause = { viewModel.toggleFlipperPause() },
                            onTriggerManualScan = { scanType -> viewModel.triggerFlipperManualScan(scanType) },
                            onViewModeChange = { viewModel.setFlipperViewMode(it) },
                            onStatusCardExpandedChange = { viewModel.setFlipperStatusCardExpanded(it) },
                            onSchedulerCardExpandedChange = { viewModel.setFlipperSchedulerCardExpanded(it) },
                            onStatsCardExpandedChange = { viewModel.setFlipperStatsCardExpanded(it) },
                            onCapabilitiesCardExpandedChange = { viewModel.setFlipperCapabilitiesCardExpanded(it) },
                            onAdvancedCardExpandedChange = { viewModel.setFlipperAdvancedCardExpanded(it) },
                            // Device picker callbacks
                            onShowDevicePicker = { viewModel.showFlipperDevicePicker() },
                            onHideDevicePicker = { viewModel.hideFlipperDevicePicker() },
                            onStartDeviceScan = { viewModel.startFlipperDeviceScan() },
                            onStopDeviceScan = { viewModel.stopFlipperDeviceScan() },
                            onSelectDiscoveredDevice = { viewModel.connectToDiscoveredFlipper(it) },
                            onSelectRecentDevice = { viewModel.connectToRecentFlipper(it) },
                            onRemoveRecentDevice = { viewModel.removeFlipperFromHistory(it) },
                            onCancelAutoReconnect = { viewModel.cancelFlipperAutoReconnect() },
                            onConnectUsb = { viewModel.connectFlipperViaUsb() },
                            // Settings callbacks
                            onInstallFap = { viewModel.installFapToFlipper() },
                            onPreferredConnectionChange = { viewModel.setFlipperPreferredConnection(it) },
                            onAutoConnectUsbChange = { viewModel.setFlipperAutoConnectUsb(it) },
                            onAutoConnectBluetoothChange = { viewModel.setFlipperAutoConnectBluetooth(it) },
                            onEnableWifiScanningChange = { viewModel.setFlipperEnableWifiScanning(it) },
                            onEnableSubGhzScanningChange = { viewModel.setFlipperEnableSubGhzScanning(it) },
                            onEnableBleScanningChange = { viewModel.setFlipperEnableBleScanning(it) },
                            onEnableIrScanningChange = { viewModel.setFlipperEnableIrScanning(it) },
                            onEnableNfcScanningChange = { viewModel.setFlipperEnableNfcScanning(it) },
                            onWipsEnabledChange = { viewModel.setFlipperWipsEnabled(it) },
                            onWipsEvilTwinChange = { viewModel.setFlipperWipsEvilTwinDetection(it) },
                            onWipsDeauthChange = { viewModel.setFlipperWipsDeauthDetection(it) },
                            onWipsKarmaChange = { viewModel.setFlipperWipsKarmaDetection(it) },
                            onWipsRogueApChange = { viewModel.setFlipperWipsRogueApDetection(it) },
                            onNavigateToActiveProbes = onNavigateToActiveProbes
                        )
                    }
                }
            }

            // Pull-to-refresh indicator with text
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    contentColor = MaterialTheme.colorScheme.primary
                )
                // Show refreshing text below the indicator
                AnimatedVisibility(
                    visible = isRefreshing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Refreshing detections...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } // Close weighted Box
        } // Close outer Column
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            currentThreatFilter = uiState.filterThreatLevel,
            currentTypeFilters = uiState.filterDeviceTypes,
            filterMatchAll = uiState.filterMatchAll,
            filterProtocols = uiState.filterProtocols,
            filterTimeRange = uiState.filterTimeRange,
            filterCustomStartTime = uiState.filterCustomStartTime,
            filterCustomEndTime = uiState.filterCustomEndTime,
            filterSignalStrength = uiState.filterSignalStrength,
            filterActiveOnly = uiState.filterActiveOnly,
            onThreatFilterChange = { viewModel.setThreatFilter(it) },
            onTypeFilterToggle = { viewModel.toggleDeviceTypeFilter(it) },
            onMatchAllChange = { viewModel.setFilterMatchAll(it) },
            onProtocolToggle = { viewModel.toggleProtocolFilter(it) },
            onTimeRangeChange = { viewModel.setTimeRange(it) },
            onCustomTimeRangeChange = { start, end -> viewModel.setCustomTimeRange(start, end) },
            onSignalStrengthToggle = { viewModel.toggleSignalStrengthFilter(it) },
            onActiveOnlyChange = { viewModel.setActiveOnly(it) },
            onClearFilters = { viewModel.clearFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Clear all confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            title = { Text("Clear All Detections?") },
            text = { Text("This will permanently delete all detection history. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDetections()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detection detail sheet
    selectedDetection?.let { detection ->
        // Load related detections when the sheet is shown
        LaunchedEffect(detection.id) {
            viewModel.loadRelatedDetections(detection)
        }

        DetectionDetailSheet(
            detection = detection,
            enrichedData = viewModel.getEnrichedData(detection.id),
            privilegeMode = viewModel.privilegeMode,
            onDismiss = {
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onDelete = {
                viewModel.deleteDetection(detection)
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onMarkSafe = {
                viewModel.markAsFalsePositive(detection)
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onMarkThreat = {
                viewModel.markAsConfirmedThreat(detection)
                viewModel.clearRelatedDetections()
                selectedDetection = null
            },
            onShare = {
                val shareText = buildDetectionShareText(detection, context)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "${context.getString(R.string.share_detection_subject)}: ${detection.deviceType.displayName}")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Detection"))
            },
            onNavigate = if (detection.latitude != null && detection.longitude != null) {
                {
                    val geoUri = Uri.parse("geo:${detection.latitude},${detection.longitude}?q=${detection.latitude},${detection.longitude}(${detection.deviceType.displayName})")
                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(mapIntent)
                    }
                }
            } else null,
            onAddNote = { note ->
                viewModel.updateUserNote(detection, note)
            },
            onExport = {
                val exportText = buildDetectionExportJson(detection)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_detection_export_subject))
                    putExtra(Intent.EXTRA_TEXT, exportText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Detection"))
            },
            advancedMode = uiState.advancedMode,
            relatedDetections = relatedDetections,
            onRelatedDetectionClick = { relatedDetection ->
                // Select the clicked related detection
                selectedDetection = relatedDetection
                viewModel.onRelatedDetectionSelected(relatedDetection)
            },
            onSeeAllRelatedClick = null
        )
    }

    // AI Analysis result dialog
    uiState.analysisResult?.let { result ->
        AiAnalysisResultDialog(
            result = result,
            onDismiss = { viewModel.clearAnalysisResult() }
        )
    }
}


/**
 * Detection modules grid with capability-aware quick access.
 *
 * The previous UI presented every module as equally available. That is misleading on
 * normal sideloaded phones and especially on older hardware. These cards now preserve
 * FeasibilityData's epistemic level and disable truly unavailable surfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionModulesGrid(
    onNavigateToRfDetection: () -> Unit,
    onNavigateToUltrasonicDetection: () -> Unit,
    onNavigateToSatelliteDetection: () -> Unit,
    onNavigateToWifiSecurity: () -> Unit,
    privilegeMode: PrivilegeMode,
    hardwareCapabilities: DeviceHardwareCapabilities,
    hasExternalRfHardware: Boolean,
    wifiAnomalyCount: Int,
    rfAnomalyCount: Int,
    ultrasonicBeaconCount: Int,
    satelliteAnomalyCount: Int
) {
    val androidSdk = android.os.Build.VERSION.SDK_INT
    val wifiCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.WIFI,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }
    val rfCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.RF,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }
    val ultrasonicCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.AUDIO,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }
    val satelliteCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.SATELLITE,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "WiFi Security",
                description = "Evil twin & rogue AP detection",
                icon = Icons.Default.Wifi,
                badgeCount = wifiAnomalyCount,
                iconTint = Color(0xFF2196F3),
                capability = wifiCapability,
                onClick = onNavigateToWifiSecurity
            )
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "RF Analysis",
                description = "Jammers, drones & spectrum",
                icon = Icons.Default.Radio,
                badgeCount = rfAnomalyCount,
                iconTint = Color(0xFF9C27B0),
                capability = rfCapability,
                onClick = onNavigateToRfDetection
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "Ultrasonic",
                description = "Audio tracking beacons",
                icon = Icons.Default.GraphicEq,
                badgeCount = ultrasonicBeaconCount,
                iconTint = Color(0xFFFF9800),
                capability = ultrasonicCapability,
                onClick = onNavigateToUltrasonicDetection
            )
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "Satellite",
                description = "NTN & Direct-to-Cell",
                icon = Icons.Default.SatelliteAlt,
                badgeCount = satelliteAnomalyCount,
                iconTint = Color(0xFF4CAF50),
                capability = satelliteCapability,
                onClick = onNavigateToSatelliteDetection
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionModuleCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    iconTint: Color,
    capability: CapabilityUiStatus,
    onClick: () -> Unit
) {
    val enabled = capability.enabled
    val hasAnomalies = enabled && badgeCount > 0
    val effectiveTint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant
    val capabilityTint = when (capability.level) {
        com.flockyou.data.FeasibilityLevel.FULL -> MaterialTheme.colorScheme.tertiary
        com.flockyou.data.FeasibilityLevel.DEGRADED -> MaterialTheme.colorScheme.secondary
        com.flockyou.data.FeasibilityLevel.HEURISTIC_ONLY -> MaterialTheme.colorScheme.primary
        com.flockyou.data.FeasibilityLevel.NOT_FEASIBLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.then(
            if (hasAnomalies) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
            } else Modifier
        ),
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                hasAnomalies -> iconTint.copy(alpha = 0.2f)
                else -> iconTint.copy(alpha = 0.1f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasAnomalies) 3.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveTint,
                    modifier = Modifier.size(28.dp)
                )
                if (hasAnomalies) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(text = badgeCount.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = when {
                    !enabled -> capability.detail
                    hasAnomalies -> "$badgeCount anomal${if (badgeCount > 1) "ies" else "y"} detected"
                    else -> description
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    hasAnomalies -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (hasAnomalies) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = capability.statusLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = capabilityTint
            )
        }
    }
}

/**
 * Permission recovery button that opens app settings
 * Used when a permission is denied and needs to be granted manually
 */

// ========== Time-Grouped Detection Helpers ==========

/**
 * Groups detections into time buckets for sticky headers.
 */
internal fun groupDetectionsByTime(detections: List<Detection>): List<Pair<String, List<Detection>>> {
    if (detections.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val fiveMinAgo = now - 5 * 60_000
    val oneHourAgo = now - 60 * 60_000
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86_400_000
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    val groups = linkedMapOf<String, MutableList<Detection>>()
    for (detection in detections) {
        val key = when {
            detection.timestamp >= fiveMinAgo -> "Just Now"
            detection.timestamp >= oneHourAgo -> "Last Hour"
            detection.timestamp >= todayStart -> "Today"
            detection.timestamp >= yesterdayStart -> "Yesterday"
            else -> dateFormat.format(Date(detection.timestamp))
        }
        groups.getOrPut(key) { mutableListOf() }.add(detection)
    }
    return groups.map { (k, v) -> k to v.toList() }
}

/**
 * Sticky header for time-grouped detection list.
 */
@Composable
internal fun TimeGroupHeader(
    label: String,
    count: Int,
    highestThreat: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (highestThreat >= 70) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (highestThreat >= 90) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

/**
 * Bottom action bar shown when batch selection mode is active.
 */
@Composable
internal fun SelectionActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onMarkReviewed: () -> Unit,
    onMarkFalsePositive: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount of $totalCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAll) { Text("Select All") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            if (selectedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMarkReviewed,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Reviewed")
                    }
                    OutlinedButton(
                        onClick = onMarkFalsePositive,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark FP")
                    }
                }
            }
        }
    }
}
