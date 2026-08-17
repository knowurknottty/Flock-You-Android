#!/usr/bin/env python3
"""P3 constrained-device pass: remove duplicated IPC payloads and idle diagnostics."""
from pathlib import Path


def exact(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    n = text.count(old)
    if n != count:
        raise SystemExit(f"{path}: expected {count} matches, got {n}: {old[:160]!r}")
    p.write_text(text.replace(old, new, count))
    print(f"patched {path}: {old[:70]!r}")


# ---------------------------------------------------------------------------
# Threading diagnostics: preserve the feature, but do not sample the entire
# process every second unless a diagnostics consumer explicitly requests it.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/monitoring/ScannerThreadingMonitor.kt"

exact(
    path,
    """    fun startMonitoring() {
        Log.i(TAG, "Starting scanner threading monitor")
        snapshotJob?.cancel()
        snapshotJob = monitorScope.launch {
            while (isActive) {
                captureSnapshot()
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
    }""",
    """    val isMonitoring: Boolean
        get() = snapshotJob?.isActive == true

    fun startMonitoring() {
        if (snapshotJob?.isActive == true) return
        Log.i(TAG, "Starting scanner threading monitor on demand")
        snapshotJob = monitorScope.launch {
            while (isActive) {
                captureSnapshot()
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }
    }""",
)

# ---------------------------------------------------------------------------
# Scanning service: basic state stays frequent; expensive full snapshots become
# a 30-second recovery/synchronization safety net. Diagnostics are demand-driven.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/service/ScanningService.kt"

exact(
    path,
    """    private val IPC_REFRESH_INTERVAL_MS = 5000L  // Refresh every 5 seconds""",
    """    private val IPC_REFRESH_INTERVAL_MS = 30_000L  // Full resync safety net; event updates remain immediate""",
)

exact(
    path,
    """                            // Stop IPC refresh if no clients remain
                            if (ipcClients.isEmpty()) {
                                stopIpcRefreshJob()
                            }""",
    """                            // Stop UI-only background work when no clients remain.
                            if (ipcClients.isEmpty()) {
                                stopIpcRefreshJob()
                                threadingMonitor.stopMonitoring()
                            }""",
)

exact(
    path,
    """                    ScanningServiceIpc.MSG_REQUEST_STATE -> {
                        Log.d(TAG, "MSG_REQUEST_STATE received, replyTo=${msg.replyTo}")
                        msg.replyTo?.let { client ->
                            Log.d(TAG, "Sending state to client...")
                            sendStateToClient(client)
                            Log.d(TAG, "State sent to client")
                        } ?: Log.w(TAG, "MSG_REQUEST_STATE received but replyTo is null!")
                    }""",
    """                    ScanningServiceIpc.MSG_REQUEST_STATE -> {
                        Log.d(TAG, "MSG_REQUEST_STATE received, replyTo=${msg.replyTo}")
                        msg.replyTo?.let { client ->
                            // Explicit state requests are the authoritative full-resync path.
                            // Routine state broadcasts below are intentionally basic-only.
                            Log.d(TAG, "Sending full state sync to client...")
                            sendStateToClient(client)
                            sendAllDataToClient(client)
                            Log.d(TAG, "Full state sync sent")
                        } ?: Log.w(TAG, "MSG_REQUEST_STATE received but replyTo is null!")
                    }""",
)

exact(
    path,
    """                    ScanningServiceIpc.MSG_REQUEST_THREADING_DATA -> {
                        msg.replyTo?.let { client ->
                            sendThreadingDataToClient(client)
                        }
                    }""",
    """                    ScanningServiceIpc.MSG_REQUEST_THREADING_DATA -> {
                        msg.replyTo?.let { client ->
                            // Thread/process sampling is diagnostic work, not scanner work.
                            // Start it only when a diagnostics consumer asks for it.
                            threadingMonitor.startMonitoring()
                            sendThreadingDataToClient(client)
                        }
                    }""",
)

exact(
    path,
    """        // Start threading monitor for scanner performance tracking
        threadingMonitor.startMonitoring()
        threadingMonitor.updateIpcClientCount(ipcClients.size)""",
    """        // Keep counters available, but the 1 Hz heap/thread sampler is started
        // only by MSG_REQUEST_THREADING_DATA and stops with the last UI client.
        threadingMonitor.updateIpcClientCount(ipcClients.size)""",
)

exact(
    path,
    """                    // No attached UI means no broad IPC serialization work.
                    if (ipcClients.isNotEmpty()) {
                        try {
                            broadcastAllDataToClients()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error broadcasting to IPC clients", e)
                        }
                    }""",
    """                    // Routine scan cycles only need the compact state message.
                    // Device/anomaly streams already broadcast event-driven updates, and
                    // the 30-second full resync job repairs any missed client state.
                    if (ipcClients.isNotEmpty()) {
                        try {
                            broadcastStateToClients()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error broadcasting state to IPC clients", e)
                        }
                    }""",
)

# ---------------------------------------------------------------------------
# Broadcaster: remove hidden full-state recursion from the "basic" state send.
# This was causing aggregate refreshes to serialize/send every subsystem twice.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/flockyou/service/ScanningServiceBroadcaster.kt"

exact(
    path,
    """        Log.d(TAG, "MSG_STATE_UPDATE sent")

        // Also send all complex data on state request (initial sync)
        Log.d(TAG, "Calling sendAllDataToClient...")
        sendAllDataToClient(client)
        Log.d(TAG, "sendAllDataToClient completed")""",
    """        Log.d(TAG, "MSG_STATE_UPDATE sent")""",
)

exact(
    path,
    """        // Send threading data
        sendThreadingDataToClient(client)""",
    """        // Threading diagnostics are opt-in and may not be sampling at all.
        if (threadingMonitor.isMonitoring) {
            sendThreadingDataToClient(client)
        }""",
)

exact(
    path,
    """    broadcastGnssData()
    broadcastThreadingData()
}""",
    """    broadcastGnssData()
    if (threadingMonitor.isMonitoring) {
        broadcastThreadingData()
    }
}""",
)

print("P3 IPC/diagnostics optimization applied successfully")
