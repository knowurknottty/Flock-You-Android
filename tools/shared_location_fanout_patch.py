#!/usr/bin/env python3
"""Replace five subsystem location polling loops with one event-driven fan-out.

Fail closed: every expected source fragment must match exactly once.
"""
from pathlib import Path
import re


def exact(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    n = text.count(old)
    if n != count:
        raise SystemExit(f"{path}: expected {count} matches, got {n}: {old[:160]!r}")
    p.write_text(text.replace(old, new, count))
    print(f"patched {path}: {old[:70]!r}")


def regex(path: str, pattern: str, replacement: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    out, n = re.subn(pattern, replacement, text, count=count, flags=re.MULTILINE | re.DOTALL)
    if n != count:
        raise SystemExit(f"{path}: expected {count} regex matches, got {n}: {pattern[:160]!r}")
    p.write_text(out)
    print(f"patched {path}: {pattern[:70]!r}")


service = "app/src/main/java/com/flockyou/service/ScanningService.kt"
manager = "app/src/main/java/com/flockyou/service/SubsystemManager.kt"

# ---------------------------------------------------------------------------
# ScanningService owns the single current-location event and fans it out to all
# currently constructed consumers. No extra timer/coroutine is needed.
# ---------------------------------------------------------------------------
exact(
    service,
    """    // Location update jobs (for proper lifecycle management)
    internal var cellularLocationJob: Job? = null
    internal var rogueWifiLocationJob: Job? = null
    internal var rfLocationJob: Job? = null
    internal var ultrasonicLocationJob: Job? = null
    internal var gnssLocationJob: Job? = null
    internal var suspiciousNetworksJob: Job? = null""",
    """    // Rogue WiFi suspicious-network collector. Location propagation is event-driven
    // through syncCurrentLocationToSubsystems(), not five polling coroutines.
    internal var suspiciousNetworksJob: Job? = null""",
)

exact(
    service,
    """        // Start GNSS satellite monitoring (uses location permission already granted)
        startGnssMonitoring()

        // Initialize and start detector health monitoring""",
    """        // Start GNSS satellite monitoring (uses location permission already granted)
        startGnssMonitoring()

        // If lastLocation resolved before all monitors were constructed, perform one
        // startup synchronization. Later location results fan out from updateLocation().
        syncCurrentLocationToSubsystems()

        // Initialize and start detector health monitoring""",
)

exact(
    service,
    """    @SuppressLint("MissingPermission")
    private fun updateLocation() {
        if (!hasLocationPermissions()) {
            locationStatus.value = SubsystemStatus.PermissionDenied("ACCESS_FINE_LOCATION")
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                currentLocation = location
                locationStatus.value = if (location != null) {
                    SubsystemStatus.Active
                } else {
                    SubsystemStatus.Error(-1, "No location available")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                locationStatus.value = SubsystemStatus.Error(-1, e.message ?: "Location error")
                logError("Location", -1, "Failed to get location: ${e.message}", recoverable = true)
            }
    }""",
    """    /**
     * Push the latest location to every active subsystem exactly when location state
     * changes or a subsystem starts. This replaces five independent 5-second loops.
     */
    internal fun syncCurrentLocationToSubsystems() {
        val location = currentLocation ?: return
        val latitude = location.latitude
        val longitude = location.longitude

        cellularMonitor?.updateLocation(latitude, longitude)
        rogueWifiMonitor?.updateLocation(latitude, longitude)
        rfSignalAnalyzer?.updateLocation(latitude, longitude)
        ultrasonicDetector?.updateLocation(latitude, longitude)
        gnssSatelliteMonitor?.updateLocation(latitude, longitude)
    }

    @SuppressLint("MissingPermission")
    private fun updateLocation() {
        if (!hasLocationPermissions()) {
            locationStatus.value = SubsystemStatus.PermissionDenied("ACCESS_FINE_LOCATION")
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                currentLocation = location
                locationStatus.value = if (location != null) {
                    syncCurrentLocationToSubsystems()
                    SubsystemStatus.Active
                } else {
                    SubsystemStatus.Error(-1, "No location available")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
                locationStatus.value = SubsystemStatus.Error(-1, e.message ?: "Location error")
                logError("Location", -1, "Failed to get location: ${e.message}", recoverable = true)
            }
    }""",
)

# ---------------------------------------------------------------------------
# SubsystemManager no longer owns location polling jobs. Each subsystem performs
# one immediate sync on start; future updates arrive from ScanningService.
# ---------------------------------------------------------------------------
for unused_import in (
    "import kotlinx.coroutines.Job\n",
    "import kotlinx.coroutines.delay\n",
    "import kotlinx.coroutines.isActive\n",
):
    exact(manager, unused_import, "")

pollers = [
    (
        "cellular",
        """    // Also update cellular location when we get GPS updates
    cellularLocationJob = serviceScope.launch {
        while (ScanningServiceState.isScanning.value) {
            currentLocation?.let { loc ->
                cellularMonitor?.updateLocation(loc.latitude, loc.longitude)
            }
            delay(5000)
        }
    }""",
    ),
    (
        "rogueWifi",
        """    // Update monitor location when GPS updates
    rogueWifiLocationJob = serviceScope.launch {
        while (ScanningServiceState.isScanning.value) {
            currentLocation?.let { loc ->
                rogueWifiMonitor?.updateLocation(loc.latitude, loc.longitude)
            }
            delay(5000)
        }
    }""",
    ),
    (
        "rf",
        """    // Update analyzer location
    rfLocationJob = serviceScope.launch {
        while (ScanningServiceState.isScanning.value) {
            currentLocation?.let { loc ->
                rfSignalAnalyzer?.updateLocation(loc.latitude, loc.longitude)
            }
            delay(5000)
        }
    }""",
    ),
    (
        "ultrasonic",
        """    // Update detector location
    ultrasonicLocationJob = serviceScope.launch {
        while (ScanningServiceState.isScanning.value) {
            currentLocation?.let { loc ->
                ultrasonicDetector?.updateLocation(loc.latitude, loc.longitude)
            }
            delay(5000)
        }
    }""",
    ),
    (
        "gnss",
        """    // Update monitor location
    gnssLocationJob = serviceScope.launch {
        while (ScanningServiceState.isScanning.value) {
            currentLocation?.let { loc ->
                gnssSatelliteMonitor?.updateLocation(loc.latitude, loc.longitude)
            }
            delay(5000)
        }
    }""",
    ),
]

for name, block in pollers:
    exact(manager, block, "    // Location is propagated by ScanningService.syncCurrentLocationToSubsystems().")

for job_name in (
    "cellularLocationJob",
    "rogueWifiLocationJob",
    "rfLocationJob",
    "ultrasonicLocationJob",
    "gnssLocationJob",
):
    exact(manager, f"    {job_name}?.cancel()\n    {job_name} = null\n", "")

# A monitor enabled after startup should immediately receive the latest cached
# location; this is a one-shot sync, not another timer.
start_anchors = [
    "    cellularMonitor?.startMonitoring()\n",
    "    rogueWifiMonitor?.startMonitoring()\n",
    "    rfSignalAnalyzer?.startMonitoring()\n",
    "    ultrasonicDetector?.startMonitoring()\n",
    "    gnssSatelliteMonitor?.startMonitoring()\n",
]
for anchor in start_anchors:
    exact(manager, anchor, anchor + "    syncCurrentLocationToSubsystems()\n")

# Fail loudly if the old architecture survives anywhere in the two files.
manager_text = Path(manager).read_text()
service_text = Path(service).read_text()
for forbidden in (
    "cellularLocationJob",
    "rogueWifiLocationJob",
    "rfLocationJob",
    "ultrasonicLocationJob",
    "gnssLocationJob",
    "delay(5000)",
):
    if forbidden in manager_text or forbidden in service_text:
        raise SystemExit(f"forbidden legacy location polling token remains: {forbidden}")

print("Shared location fan-out optimization applied successfully")
