from pathlib import Path

path = Path("app/src/main/java/com/flockyou/ui/screens/MapScreen.kt")
text = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    "import androidx.hilt.navigation.compose.hiltViewModel\n",
    "import androidx.hilt.navigation.compose.hiltViewModel\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
    "lifecycle import",
)

replace_once(
    """    val uiState by viewModel.uiState.collectAsState()\n    val filteredDetections by viewModel.detectionsWithLocation.collectAsState()\n    var selectedDetection by remember { mutableStateOf<Detection?>(null) }\n    var selectedCluster by remember { mutableStateOf<DetectionCluster?>(null) }\n    var mapView by remember { mutableStateOf<MapView?>(null) }\n    var currentZoom by remember { mutableStateOf(4.0) }\n    var showFilterSheet by remember { mutableStateOf(false) }\n""",
    """    val uiState by viewModel.uiState.collectAsStateWithLifecycle()\n    val filteredDetections by viewModel.detectionsWithLocation.collectAsStateWithLifecycle()\n    var selectedDetection by remember { mutableStateOf<Detection?>(null) }\n    var mapView by remember { mutableStateOf<MapView?>(null) }\n    var zoomBucket by remember { mutableStateOf(MapZoomBucket.WORLD) }\n    var didAutoFit by remember { mutableStateOf(false) }\n    var showFilterSheet by remember { mutableStateOf(false) }\n""",
    "map state",
)

replace_once(
    """        filteredDetections\n            .filter { it.latitude != null && it.longitude != null }\n            .maxByOrNull { it.timestamp }\n            ?.let { latest ->\n""",
    """        filteredDetections\n            .maxByOrNull { it.timestamp }\n            ?.let { latest ->\n""",
    "gps latest pass",
)

replace_once(
    "LaunchedEffect(filteredDetections, mapView, currentZoom) {",
    "LaunchedEffect(filteredDetections, mapView, zoomBucket, userLocation, gpsAccuracyMeters) {",
    "overlay effect key",
)

replace_once(
    """            val detectionsWithCoords = filteredDetections.filter {\n                it.latitude != null && it.longitude != null\n            }\n""",
    """            // DetectionRepository.detectionsWithLocation already guarantees coordinates.\n            val detectionsWithCoords = filteredDetections\n""",
    "coordinate pass",
)

replace_once(
    """            // Determine if we should cluster based on zoom level\n            val shouldCluster = currentZoom < 15.0\n\n            if (shouldCluster) {\n                // Create clusters\n                val clusters = clusterDetections(detectionsWithCoords)\n\n                clusters.forEach { cluster ->\n                    points.add(cluster.center)\n""",
    """            if (zoomBucket != MapZoomBucket.DETAIL) {\n                // Cluster on coarse zoom tiers. The policy uses bounded grid bucketing rather than\n                // comparing every detection with every other detection.\n                val clusters = MapPresentationPolicy.clusterDetections(detectionsWithCoords, zoomBucket)\n\n                clusters.forEach { cluster ->\n                    val clusterCenter = GeoPoint(cluster.centerLatitude, cluster.centerLongitude)\n                    points.add(clusterCenter)\n""",
    "cluster policy",
)

replace_once(
    "position = cluster.center\n",
    "position = clusterCenter\n",
    "single cluster center",
)
replace_once(
    "position = cluster.center\n",
    "position = clusterCenter\n",
    "multi cluster center",
)

replace_once(
    """            if (points.isNotEmpty() && currentZoom == 4.0) {\n                zoomToFitPoints(map, points)\n            }\n""",
    """            if (points.isNotEmpty() && !didAutoFit) {\n                didAutoFit = true\n                zoomToFitPoints(map, points)\n            }\n""",
    "auto fit",
)

replace_once(
    """    // Track zoom level changes\n    LaunchedEffect(mapView) {\n        mapView?.let { map ->\n            map.addMapListener(object : org.osmdroid.events.MapListener {\n                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {\n                    currentZoom = map.zoomLevelDouble\n                    return false\n                }\n\n                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {\n                    currentZoom = map.zoomLevelDouble\n                    return false\n                }\n            })\n        }\n    }\n""",
    """    // Track only meaningful zoom-tier changes and remove the listener when the MapView changes.\n    // Assigning the same enum value is a no-op, so fractional zoom updates inside one tier no longer\n    // invalidate and rebuild the complete marker overlay.\n    DisposableEffect(mapView) {\n        val map = mapView\n        if (map == null) {\n            onDispose { }\n        } else {\n            val listener = object : org.osmdroid.events.MapListener {\n                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false\n\n                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {\n                    zoomBucket = MapZoomBucket.forZoom(map.zoomLevelDouble)\n                    return false\n                }\n            }\n            map.addMapListener(listener)\n            onDispose { map.removeMapListener(listener) }\n        }\n    }\n""",
    "zoom listener",
)

replace_once(
    """                            val points = filteredDetections\n                                .filter { it.latitude != null && it.longitude != null }\n                                .map { GeoPoint(it.latitude!!, it.longitude!!) }\n""",
    """                            val points = filteredDetections\n                                .map { GeoPoint(it.latitude!!, it.longitude!!) }\n""",
    "reset points",
)

replace_once(
    """                            val latest = filteredDetections\n                                .filter { it.latitude != null && it.longitude != null }\n                                .maxByOrNull { it.timestamp }\n""",
    """                            val latest = filteredDetections\n                                .maxByOrNull { it.timestamp }\n""",
    "center latest",
)

replace_once(
    """            val hasLocationData = filteredDetections.any {\n                it.latitude != null && it.longitude != null\n            }\n""",
    """            val hasLocationData = filteredDetections.isNotEmpty()\n""",
    "has location",
)

replace_once(
    'text = "${filteredDetections.filter { it.latitude != null }.size} locations",\n',
    'text = "${filteredDetections.size} locations",\n',
    "location count",
)

# Remove the obsolete pairwise clustering helpers. zoomToFitPoints remains immediately before
# this block and createAccuracyCircle remains immediately after it.
start_marker = "/**\n * Cluster detections that are within close proximity\n */\nprivate fun clusterDetections"
end_marker = "/**\n * Create GPS accuracy circle overlay\n */\nprivate fun createAccuracyCircle"
start = text.find(start_marker)
end = text.find(end_marker)
if start == -1 or end == -1 or end <= start:
    raise SystemExit("obsolete clustering block markers not found")
text = text[:start] + text[end:]

# sqrt was only used by the removed pairwise distance helper.
text = text.replace("import kotlin.math.sqrt\n", "")

path.write_text(text)
