from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)

# --- MapViewModel: distinguish 'history exists but no geolocation' without stuffing history into UI state.
vm_path = Path("app/src/main/java/com/flockyou/ui/screens/MapViewModel.kt")
vm = vm_path.read_text()
vm = replace_once(
    vm,
    '''    private val _uiState = MutableStateFlow(MapUiState())\n    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()\n\n''',
    '''    private val _uiState = MutableStateFlow(MapUiState())\n    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()\n\n    val hasAnyDetections: StateFlow<Boolean> = repository.totalDetectionCount\n        .map { it > 0 }\n        .distinctUntilChanged()\n        .stateIn(\n            scope = viewModelScope,\n            started = SharingStarted.WhileSubscribed(5_000),\n            initialValue = false\n        )\n\n''',
    "MapViewModel total-count projection",
)
vm_path.write_text(vm)

# --- MapScreen: remove invented GPS precision derived from surveillance RSSI.
map_path = Path("app/src/main/java/com/flockyou/ui/screens/MapScreen.kt")
map_text = map_path.read_text()
for old in (
    "import org.osmdroid.views.overlay.Polygon\n",
    "import kotlin.math.cos\n",
):
    if map_text.count(old) != 1:
        raise SystemExit(f"Map import {old!r}: expected 1 match, found {map_text.count(old)}")
    map_text = map_text.replace(old, "", 1)

cluster_header = '''// Cluster radius in degrees (approximately 100m at equator)\nprivate const val CLUSTER_RADIUS_DEGREES = 0.001\n\n/**\n * Represents a cluster of detections on the map\n */\nprivate data class DetectionCluster(\n    val center: GeoPoint,\n    val detections: List<Detection>,\n    val highestThreatLevel: ThreatLevel\n)\n\n'''
map_text = replace_once(map_text, cluster_header, "", "obsolete cluster declarations")

map_text = replace_once(
    map_text,
    '''    val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n    val filteredDetections by viewModel.detectionsWithLocation.collectAsStateWithLifecycle()\n''',
    '''    val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n    val filteredDetections by viewModel.detectionsWithLocation.collectAsStateWithLifecycle()\n    val hasAnyDetections by viewModel.hasAnyDetections.collectAsStateWithLifecycle()\n''',
    "map collectors",
)

map_text = replace_once(
    map_text,
    '''    var gpsStatus by remember { mutableStateOf(GpsStatus.SEARCHING) }\n    var gpsAccuracyMeters by remember { mutableStateOf<Float?>(null) }\n    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }\n''',
    '''    var gpsStatus by remember { mutableStateOf(GpsStatus.SEARCHING) }\n    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }\n''',
    "gps state",
)

map_text = replace_once(
    map_text,
    '''    // Update GPS status based on detections with location\n    LaunchedEffect(filteredDetections) {\n        val hasLocationData = filteredDetections.any {\n            it.latitude != null && it.longitude != null\n        }\n        gpsStatus = when {\n            hasLocationData -> GpsStatus.ACTIVE\n            filteredDetections.isEmpty() -> GpsStatus.SEARCHING\n            else -> GpsStatus.DISABLED\n        }\n\n        // Get user location from most recent detection\n        filteredDetections\n            .maxByOrNull { it.timestamp }\n            ?.let { latest ->\n                userLocation = GeoPoint(latest.latitude!!, latest.longitude!!)\n                // Estimate accuracy based on signal strength (rough approximation)\n                gpsAccuracyMeters = when (latest.signalStrength) {\n                    SignalStrength.EXCELLENT -> 10f\n                    SignalStrength.GOOD -> 25f\n                    SignalStrength.MEDIUM -> 50f\n                    SignalStrength.WEAK -> 100f\n                    else -> 150f\n                }\n            }\n    }\n''',
    '''    // This map has stored detection coordinates, not an Android Location accuracy fix.\n    // Never infer GPS +/- meters from the surveillance device RSSI/signal-strength field.\n    LaunchedEffect(filteredDetections, hasAnyDetections) {\n        gpsStatus = when {\n            filteredDetections.isNotEmpty() -> GpsStatus.ACTIVE\n            hasAnyDetections -> GpsStatus.DISABLED\n            else -> GpsStatus.SEARCHING\n        }\n        userLocation = filteredDetections\n            .maxByOrNull { it.timestamp }\n            ?.let { latest -> GeoPoint(latest.latitude!!, latest.longitude!!) }\n    }\n''',
    "gps truthfulness effect",
)

map_text = replace_once(
    map_text,
    "LaunchedEffect(filteredDetections, mapView, zoomBucket, userLocation, gpsAccuracyMeters) {",
    "LaunchedEffect(filteredDetections, mapView, zoomBucket) {",
    "map effect key",
)
map_text = replace_once(
    map_text,
    "map.overlays.removeAll { it is Marker || it is Polygon }",
    "map.overlays.removeAll { it is Marker }",
    "overlay cleanup",
)

accuracy_overlay = '''            // Add GPS accuracy circle if user location is available\n            userLocation?.let { location ->\n                gpsAccuracyMeters?.let { accuracy ->\n                    val accuracyCircle = createAccuracyCircle(location, accuracy.toDouble())\n                    map.overlays.add(0, accuracyCircle) // Add at bottom so markers are on top\n                }\n            }\n\n'''
map_text = replace_once(map_text, accuracy_overlay, "", "fake accuracy overlay")

map_text = replace_once(
    map_text,
    '''            // Check if we have location data\n            val hasLocationData = filteredDetections.isNotEmpty()\n\n            if (!hasLocationData && filteredDetections.isNotEmpty()) {\n                // Empty state - detections exist but no location data\n                MapEmptyState(\n                    hasDetections = true,\n                    onRequestPermissions = requestLocationPermissions\n                )\n            } else if (filteredDetections.isEmpty()) {\n                // Empty state - no detections at all\n                MapEmptyState(\n                    hasDetections = false,\n                    onRequestPermissions = startScanning\n                )\n            } else {\n''',
    '''            val hasLocationData = filteredDetections.isNotEmpty()\n\n            if (!hasLocationData) {\n                MapEmptyState(\n                    hasDetections = hasAnyDetections,\n                    onRequestPermissions = if (hasAnyDetections) requestLocationPermissions else startScanning\n                )\n            } else {\n''',
    "map empty-state truthfulness",
)

map_text = replace_once(
    map_text,
    '''            GpsStatusIndicator(\n                status = gpsStatus,\n                accuracyMeters = gpsAccuracyMeters,\n''',
    '''            GpsStatusIndicator(\n                status = gpsStatus,\n                accuracyMeters = null,\n''',
    "gps indicator",
)

start_marker = '''/**\n * Create GPS accuracy circle overlay\n */\nprivate fun createAccuracyCircle'''
end_marker = '''private fun createMarkerDrawable'''
start = map_text.find(start_marker)
end = map_text.find(end_marker, start)
if start == -1 or end == -1 or end <= start:
    raise SystemExit("fake accuracy helper block markers not found")
map_text = map_text[:start] + end_marker + map_text[end + len(end_marker):]
map_path.write_text(map_text)

# --- Lifecycle-aware settings collectors on large screens.
all_path = Path("app/src/main/java/com/flockyou/ui/screens/AllDetectionsScreen.kt")
all_text = all_path.read_text()
all_text = replace_once(
    all_text,
    "import androidx.hilt.navigation.compose.hiltViewModel\n",
    "import androidx.hilt.navigation.compose.hiltViewModel\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "AllDetections lifecycle import",
)
all_text = replace_once(
    all_text,
    "    val settings by viewModel.settings.collectAsState()\n",
    "    val settings by viewModel.settings.collectAsStateWithLifecycle()\n",
    "AllDetections settings collector",
)
all_path.write_text(all_text)

settings_path = Path("app/src/main/java/com/flockyou/ui/screens/SettingsScreen.kt")
settings_text = settings_path.read_text()
settings_text = replace_once(
    settings_text,
    "import androidx.lifecycle.LifecycleEventObserver\n",
    "import androidx.lifecycle.LifecycleEventObserver\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "Settings lifecycle import",
)
settings_text = replace_once(
    settings_text,
    "    val uiState by viewModel.uiState.collectAsState()\n",
    "    val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n",
    "Settings ui collector",
)
settings_text = replace_once(
    settings_text,
    "    val ouiSettings by viewModel.ouiSettings.collectAsState()\n",
    "    val ouiSettings by viewModel.ouiSettings.collectAsStateWithLifecycle()\n",
    "Settings OUI collector",
)
settings_text = replace_once(
    settings_text,
    "    val isOuiUpdating by viewModel.isOuiUpdating.collectAsState()\n",
    "    val isOuiUpdating by viewModel.isOuiUpdating.collectAsStateWithLifecycle()\n",
    "Settings update collector",
)
settings_text = replace_once(
    settings_text,
    "    val detectionSettings by viewModel.detectionSettings.collectAsState()\n",
    "    val detectionSettings by viewModel.detectionSettings.collectAsStateWithLifecycle()\n",
    "Settings detection collector",
)
settings_path.write_text(settings_text)
