#!/usr/bin/env python3
"""P2 constrained-device pass: eliminate resource-policy overrides and idle churn.

Fail closed: every mutation must match the verified post-P0/P1 source exactly once.
"""
from pathlib import Path


def exact(path: str, old: str, new: str, count: int = 1) -> None:
    p = Path(path)
    text = p.read_text()
    n = text.count(old)
    if n != count:
        raise SystemExit(f"{path}: expected {count} matches, got {n}: {old[:140]!r}")
    p.write_text(text.replace(old, new, count))
    print(f"patched {path}: {old[:65]!r}")


path = "app/src/main/java/com/flockyou/service/ScanningService.kt"

# 1) Resource bookkeeping fields. Heartbeats use elapsed realtime so wall-clock
# changes cannot cause accidental bursts. Notification text is used as a cheap
# Binder-churn dedupe key.
exact(
    path,
    """        private const val SCAN_STATS_BROADCAST_THROTTLE_MS = 1_500L
        private const val SEEN_DEVICE_BROADCAST_THROTTLE_MS = 750L
        private const val BLE_WATCHDOG_THRESHOLD_MS = 60_000L""",
    """        private const val SCAN_STATS_BROADCAST_THROTTLE_MS = 1_500L
        private const val SEEN_DEVICE_BROADCAST_THROTTLE_MS = 750L
        private const val HEARTBEAT_RECORD_INTERVAL_MS = 60_000L
        private const val BLE_WATCHDOG_THRESHOLD_MS = 60_000L""",
)

exact(
    path,
    """    private var lastScanStatsBroadcastTime = 0L
    private var lastSeenBleBroadcastTime = 0L
    private val gson = Gson()""",
    """    private var lastScanStatsBroadcastTime = 0L
    private var lastSeenBleBroadcastTime = 0L
    private var lastHeartbeatRecordElapsed = 0L
    private var lastNotificationContentText: String? = null
    private val gson = Gson()""",
)

# 2) Do not rebuild and notify the same foreground notification every scan cycle.
exact(
    path,
    """    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }""",
    """    private fun updateNotification(contentText: String) {
        if (contentText == lastNotificationContentText) return
        lastNotificationContentText = contentText
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }""",
)

# 3) Charging must not override operator/device policy. Explicit manual PERFORMANCE
# still works because getEffectiveMode() returns the manually selected mode when AUTO
# is disabled.
exact(
    path,
    """    private fun updateEffectiveBatteryMode() {
        val effectiveMode = if (isCharging) {
            com.flockyou.data.BatteryAdaptiveMode.PERFORMANCE
        } else {
            currentScanSettings.getEffectiveMode(currentBatteryPercent)
        }

        if (currentBatteryMode.value != effectiveMode) {""",
    """    private fun updateEffectiveBatteryMode() {
        // Charging is not a performance capability signal. Respect the operator's
        // selected mode (or AUTO conservation policy) whether plugged in or not.
        val effectiveMode = currentScanSettings.getEffectiveMode(currentBatteryPercent)

        if (currentBatteryMode.value != effectiveMode) {""",
)

# 4) The service records one immediate startup heartbeat, then at most once/minute.
# The watchdog only needs liveness freshness at minute granularity; writing prefs on
# every scan cycle adds pointless flash/serialization churn.
exact(
    path,
    """        // Record heartbeat immediately so watchdog knows we're alive
        ServiceRestartReceiver.recordHeartbeat(this)

        // One bounded consumer replaces one coroutine per BLE advertisement.""",
    """        // Record heartbeat immediately so watchdog knows we're alive.
        ServiceRestartReceiver.recordHeartbeat(this)
        lastHeartbeatRecordElapsed = SystemClock.elapsedRealtime()

        // One bounded consumer replaces one coroutine per BLE advertisement.""",
)

exact(
    path,
    """                    // === HEARTBEAT ===
                    // Send heartbeat every cycle to prove we're alive
                    ServiceRestartReceiver.recordHeartbeat(this@ScanningService)

                    // === BLE BURST SCAN ===""",
    """                    // === HEARTBEAT ===
                    // Persist liveness at watchdog granularity, not every scan cycle.
                    val heartbeatNow = SystemClock.elapsedRealtime()
                    if (heartbeatNow - lastHeartbeatRecordElapsed >= HEARTBEAT_RECORD_INTERVAL_MS) {
                        ServiceRestartReceiver.recordHeartbeat(this@ScanningService)
                        lastHeartbeatRecordElapsed = heartbeatNow
                    }

                    // === BLE BURST SCAN ===""",
)

# The watchdog is an inexact repeating alarm scheduled when the service starts.
# Re-scheduling it every tenth scan cycle does not improve reliability and causes
# repeated AlarmManager work.
exact(
    path,
    """                    // Every 10 cycles, re-schedule the watchdog to ensure it stays active
                    if (scanCycleCount % 10 == 0) {
                        ServiceRestartReceiver.scheduleWatchdog(this@ScanningService)
                        Log.d(TAG, "Completed $scanCycleCount scan cycles")
                    }""",
    """                    if (scanCycleCount % 10 == 0) {
                        Log.d(TAG, "Completed $scanCycleCount scan cycles")
                    }""",
)

# 5) The health watchdog must preserve client-driven IPC semantics. P0 stopped the
# unconditional startup job; without this fix the health checker recreated it ~30s
# later even with no UI attached.
exact(
    path,
    """        if (ipcRefreshJob == null || ipcRefreshJob?.isActive != true) {
            Log.w(TAG, "WATCHDOG: IPC refresh job stopped, restarting...")
            startIpcRefreshJob()
        }""",
    """        if (ipcClients.isNotEmpty()) {
            if (ipcRefreshJob == null || ipcRefreshJob?.isActive != true) {
                Log.w(TAG, "WATCHDOG: IPC refresh job stopped with active clients, restarting...")
                startIpcRefreshJob()
            }
        } else if (ipcRefreshJob?.isActive == true) {
            Log.d(TAG, "WATCHDOG: no IPC clients; stopping unnecessary refresh job")
            stopIpcRefreshJob()
        }""",
)

print("P2 scanner churn fixes applied successfully")
