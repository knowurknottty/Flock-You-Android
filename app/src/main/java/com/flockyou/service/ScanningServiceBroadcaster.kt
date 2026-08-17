package com.flockyou.service

import android.os.Bundle
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * IPC broadcast methods for ScanningService, extracted for file-size management.
 *
 * All functions here are extension functions on [ScanningService] so they have access
 * to the service's internal fields (ipcClients, serviceScope, threadingMonitor, etc.).
 *
 * Related files:
 * - ScanningService.kt: Service lifecycle, core scan loop
 * - ScanningServiceIpc.kt: IPC constants and client-side connection
 * - ScanningServiceState.kt: Shared state flows
 * - ScanningServiceModels.kt: Data classes
 */

private const val TAG = "ScanningService"

// ==================== Core IPC Helpers ====================

/**
 * Helper to broadcast a message to all IPC clients.
 * CopyOnWriteArrayList allows safe iteration while removing dead clients.
 */
internal inline fun ScanningService.broadcastToClients(createMessage: () -> Message) {
    if (ipcClients.isEmpty()) return
    val msg = createMessage()
    for (client in ipcClients) {
        try {
            // Create a copy of the message for each client
            val clientMsg = Message.obtain(msg)
            client.send(clientMsg)
        } catch (e: RemoteException) {
            // Remove dead client - safe with CopyOnWriteArrayList
            ipcClients.remove(client)
        }
    }
    msg.recycle()
}

// ==================== State & Lifecycle Broadcasts ====================

/**
 * Broadcast state update to all registered IPC clients.
 */
internal fun ScanningService.broadcastStateToClients() {
    for (client in ipcClients) {
        try {
            sendStateToClient(client)
        } catch (e: RemoteException) {
            ipcClients.remove(client)
        }
    }
}

/**
 * Send current state to a specific client (basic state only).
 */
internal fun ScanningService.sendStateToClient(client: Messenger) {
    Log.d(TAG, "sendStateToClient() starting")
    try {
        val isScanning = ScanningServiceState.isScanning
        val detectionCount = ScanningServiceState.detectionCount
        val scanStatus = ScanningServiceState.scanStatus
        val bleStatus = ScanningServiceState.bleStatus
        val wifiStatus = ScanningServiceState.wifiStatus
        val locationStatus = ScanningServiceState.locationStatus
        val cellularStatus = ScanningServiceState.cellularStatus
        val satelliteStatus = ScanningServiceState.satelliteStatus

        val msg = Message.obtain(null, ScanningServiceIpc.MSG_STATE_UPDATE)
        msg.data = Bundle().apply {
            putBoolean(ScanningServiceIpc.KEY_IS_SCANNING, isScanning.value)
            putInt(ScanningServiceIpc.KEY_DETECTION_COUNT, detectionCount.value)
            putString(ScanningServiceIpc.KEY_SCAN_STATUS, scanStatus.value.toIpcString())
            putString(ScanningServiceIpc.KEY_BLE_STATUS, bleStatus.value.toIpcString())
            putString(ScanningServiceIpc.KEY_WIFI_STATUS, wifiStatus.value.toIpcString())
            putString(ScanningServiceIpc.KEY_LOCATION_STATUS, locationStatus.value.toIpcString())
            putString(ScanningServiceIpc.KEY_CELLULAR_STATUS, cellularStatus.value.toIpcString())
            putString(ScanningServiceIpc.KEY_SATELLITE_STATUS, satelliteStatus.value.toIpcString())
        }
        Log.d(TAG, "Sending MSG_STATE_UPDATE: isScanning=${isScanning.value}, scanStatus=${scanStatus.value}")
        client.send(msg)
        Log.d(TAG, "MSG_STATE_UPDATE sent")
    } catch (e: RemoteException) {
        Log.e(TAG, "Failed to send state to client", e)
        ipcClients.remove(client)
    }
}

/**
 * Send all complex data to a specific client.
 */
internal fun ScanningService.sendAllDataToClient(client: Messenger) {
    Log.d(TAG, "sendAllDataToClient() starting")
    try {
        val seenBleDevices = ScanningServiceState.seenBleDevices
        val seenWifiNetworks = ScanningServiceState.seenWifiNetworks
        val cellStatus = ScanningServiceState.cellStatus
        val seenCellTowers = ScanningServiceState.seenCellTowers
        val cellularAnomalies = ScanningServiceState.cellularAnomalies
        val cellularEvents = ScanningServiceState.cellularEvents
        val satelliteState = ScanningServiceState.satelliteState
        val satelliteAnomalies = ScanningServiceState.satelliteAnomalies
        val satelliteHistory = ScanningServiceState.satelliteHistory
        val rogueWifiStatus = ScanningServiceState.rogueWifiStatus
        val rogueWifiAnomalies = ScanningServiceState.rogueWifiAnomalies
        val suspiciousNetworks = ScanningServiceState.suspiciousNetworks
        val rfStatus = ScanningServiceState.rfStatus
        val rfAnomalies = ScanningServiceState.rfAnomalies
        val detectedDrones = ScanningServiceState.detectedDrones
        val ultrasonicStatus = ScanningServiceState.ultrasonicStatus
        val ultrasonicAnomalies = ScanningServiceState.ultrasonicAnomalies
        val ultrasonicBeacons = ScanningServiceState.ultrasonicBeacons
        val gnssStatus = ScanningServiceState.gnssStatus
        val gnssSatellites = ScanningServiceState.gnssSatellites
        val gnssAnomalies = ScanningServiceState.gnssAnomalies
        val gnssEvents = ScanningServiceState.gnssEvents
        val gnssMeasurements = ScanningServiceState.gnssMeasurements
        val lastDetection = ScanningServiceState.lastDetection
        val detectorHealth = ScanningServiceState.detectorHealth
        val errorLog = ScanningServiceState.errorLog
        val scanStats = ScanningServiceState.scanStats

        // Send seen BLE devices
        Log.d(TAG, "Sending BLE devices: ${seenBleDevices.value.size} devices")
        val bleMsg = Message.obtain(null, ScanningServiceIpc.MSG_SEEN_BLE_DEVICES)
        bleMsg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_JSON_DATA, ScanningServiceIpc.gson.toJson(seenBleDevices.value))
        }
        client.send(bleMsg)

        // Send seen WiFi networks
        val wifiMsg = Message.obtain(null, ScanningServiceIpc.MSG_SEEN_WIFI_NETWORKS)
        wifiMsg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_JSON_DATA, ScanningServiceIpc.gson.toJson(seenWifiNetworks.value))
        }
        client.send(wifiMsg)

        // Send cellular data
        val cellularMsg = Message.obtain(null, ScanningServiceIpc.MSG_CELLULAR_DATA)
        cellularMsg.data = Bundle().apply {
            cellStatus.value?.let { putString(ScanningServiceIpc.KEY_CELL_STATUS_JSON, ScanningServiceIpc.gson.toJson(it)) }
            putString(ScanningServiceIpc.KEY_SEEN_TOWERS_JSON, ScanningServiceIpc.gson.toJson(seenCellTowers.value))
            putString(ScanningServiceIpc.KEY_CELLULAR_ANOMALIES_JSON, ScanningServiceIpc.gson.toJson(cellularAnomalies.value))
            putString(ScanningServiceIpc.KEY_CELLULAR_EVENTS_JSON, ScanningServiceIpc.gson.toJson(cellularEvents.value))
        }
        client.send(cellularMsg)

        // Send satellite data
        val satelliteMsg = Message.obtain(null, ScanningServiceIpc.MSG_SATELLITE_DATA)
        satelliteMsg.data = Bundle().apply {
            satelliteState.value?.let { putString(ScanningServiceIpc.KEY_SATELLITE_STATE_JSON, ScanningServiceIpc.gson.toJson(it)) }
            putString(ScanningServiceIpc.KEY_SATELLITE_ANOMALIES_JSON, ScanningServiceIpc.gson.toJson(satelliteAnomalies.value))
            putString(ScanningServiceIpc.KEY_SATELLITE_HISTORY_JSON, ScanningServiceIpc.gson.toJson(satelliteHistory.value))
        }
        client.send(satelliteMsg)

        // Send rogue WiFi data
        val rogueWifiMsg = Message.obtain(null, ScanningServiceIpc.MSG_ROGUE_WIFI_DATA)
        rogueWifiMsg.data = Bundle().apply {
            rogueWifiStatus.value?.let { putString(ScanningServiceIpc.KEY_ROGUE_WIFI_STATUS_JSON, ScanningServiceIpc.gson.toJson(it)) }
            putString(ScanningServiceIpc.KEY_ROGUE_WIFI_ANOMALIES_JSON, ScanningServiceIpc.gson.toJson(rogueWifiAnomalies.value))
            putString(ScanningServiceIpc.KEY_SUSPICIOUS_NETWORKS_JSON, ScanningServiceIpc.gson.toJson(suspiciousNetworks.value))
        }
        client.send(rogueWifiMsg)

        // Send RF data
        val rfMsg = Message.obtain(null, ScanningServiceIpc.MSG_RF_DATA)
        rfMsg.data = Bundle().apply {
            rfStatus.value?.let { putString(ScanningServiceIpc.KEY_RF_STATUS_JSON, ScanningServiceIpc.gson.toJson(it)) }
            putString(ScanningServiceIpc.KEY_RF_ANOMALIES_JSON, ScanningServiceIpc.gson.toJson(rfAnomalies.value))
            putString(ScanningServiceIpc.KEY_DETECTED_DRONES_JSON, ScanningServiceIpc.gson.toJson(detectedDrones.value))
        }
        client.send(rfMsg)

        // Send ultrasonic data
        val ultrasonicMsg = Message.obtain(null, ScanningServiceIpc.MSG_ULTRASONIC_DATA)
        ultrasonicMsg.data = Bundle().apply {
            ultrasonicStatus.value?.let { putString(ScanningServiceIpc.KEY_ULTRASONIC_STATUS_JSON, ScanningServiceIpc.gson.toJson(it)) }
            putString(ScanningServiceIpc.KEY_ULTRASONIC_ANOMALIES_JSON, ScanningServiceIpc.gson.toJson(ultrasonicAnomalies.value))
            putString(ScanningServiceIpc.KEY_ULTRASONIC_BEACONS_JSON, ScanningServiceIpc.gson.toJson(ultrasonicBeacons.value))
        }
        client.send(ultrasonicMsg)

        // Send GNSS data
        val gnssMsg = Message.obtain(null, ScanningServiceIpc.MSG_GNSS_DATA)
        gnssMsg.data = Bundle().apply {
            gnssStatus.value?.let { putString(ScanningServiceIpc.KEY_GNSS_STATUS_JSON, ScanningServiceIpc.gson.toJson(it)) }
            putString(ScanningServiceIpc.KEY_GNSS_SATELLITES_JSON, ScanningServiceIpc.gson.toJson(gnssSatellites.value))
            putString(ScanningServiceIpc.KEY_GNSS_ANOMALIES_JSON, ScanningServiceIpc.gson.toJson(gnssAnomalies.value))
            putString(ScanningServiceIpc.KEY_GNSS_EVENTS_JSON, ScanningServiceIpc.gson.toJson(gnssEvents.value))
            gnssMeasurements.value?.let { putString(ScanningServiceIpc.KEY_GNSS_MEASUREMENTS_JSON, ScanningServiceIpc.gson.toJson(it)) }
        }
        client.send(gnssMsg)

        // Send last detection
        val detectionMsg = Message.obtain(null, ScanningServiceIpc.MSG_LAST_DETECTION)
        detectionMsg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_LAST_DETECTION_JSON, lastDetection.value?.let { ScanningServiceIpc.gson.toJson(it) })
        }
        client.send(detectionMsg)

        // Send detector health
        val healthJson = ScanningServiceIpc.gson.toJson(detectorHealth.value)
        Log.d(TAG, "Sending detector health to client: ${detectorHealth.value.size} detectors, running=${detectorHealth.value.values.count { it.isRunning }}")
        val healthMsg = Message.obtain(null, ScanningServiceIpc.MSG_DETECTOR_HEALTH)
        healthMsg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_DETECTOR_HEALTH_JSON, healthJson)
        }
        client.send(healthMsg)

        // Send error log
        val errorMsg = Message.obtain(null, ScanningServiceIpc.MSG_ERROR_LOG)
        errorMsg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_ERROR_LOG_JSON, ScanningServiceIpc.gson.toJson(errorLog.value))
        }
        client.send(errorMsg)

        // Send scan statistics
        val statsMsg = Message.obtain(null, ScanningServiceIpc.MSG_SCAN_STATS)
        statsMsg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_SCAN_STATS_JSON, ScanningServiceIpc.gson.toJson(scanStats.value))
        }
        client.send(statsMsg)

        // Threading diagnostics are opt-in and may not be sampling at all.
        if (threadingMonitor.isMonitoring) {
            sendThreadingDataToClient(client)
        }
    } catch (e: RemoteException) {
        Log.e(TAG, "Failed to send all data to client", e)
    }
}

/**
 * Send threading monitor data to a specific client.
 */
internal fun ScanningService.sendThreadingDataToClient(client: Messenger) {
    try {
        val systemState = threadingMonitor.systemState.value
        val scannerStates = threadingMonitor.scannerStates.value
        val alerts = threadingMonitor.threadingAlerts.value

        val msg = Message.obtain(null, ScanningServiceIpc.MSG_THREADING_DATA)
        msg.data = Bundle().apply {
            putString(ScanningServiceIpc.KEY_THREADING_SYSTEM_STATE_JSON, ScanningServiceIpc.gson.toJson(systemState))
            putString(ScanningServiceIpc.KEY_THREADING_SCANNER_STATES_JSON, ScanningServiceIpc.gson.toJson(scannerStates))
            putString(ScanningServiceIpc.KEY_THREADING_ALERTS_JSON, ScanningServiceIpc.gson.toJson(alerts))
        }
        client.send(msg)
    } catch (e: RemoteException) {
        Log.e(TAG, "Failed to send threading data to client", e)
    }
}

// ==================== Data Broadcasts ====================

/**
 * Broadcast scanning started to all registered IPC clients.
 */
internal fun ScanningService.broadcastScanningStarted() {
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SCANNING_STARTED)
    }
}

/**
 * Broadcast scanning stopped to all registered IPC clients.
 */
internal fun ScanningService.broadcastScanningStopped() {
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SCANNING_STOPPED)
    }
}

/**
 * Broadcast seen BLE devices to all registered IPC clients.
 */
internal fun ScanningService.broadcastSeenBleDevices() {
    if (ipcClients.isEmpty()) return
    val json = ScanningServiceIpc.gson.toJson(ScanningServiceState.seenBleDevices.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SEEN_BLE_DEVICES).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_JSON_DATA, json)
            }
        }
    }
}

/**
 * Broadcast seen WiFi networks to all registered IPC clients.
 */
internal fun ScanningService.broadcastSeenWifiNetworks() {
    if (ipcClients.isEmpty()) return
    val json = ScanningServiceIpc.gson.toJson(ScanningServiceState.seenWifiNetworks.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SEEN_WIFI_NETWORKS).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_JSON_DATA, json)
            }
        }
    }
}

/**
 * Broadcast cellular monitoring data to all registered IPC clients.
 */
internal fun ScanningService.broadcastCellularData() {
    if (ipcClients.isEmpty()) return
    val cellStatusJson = ScanningServiceState.cellStatus.value?.let { ScanningServiceIpc.gson.toJson(it) }
    val towersJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.seenCellTowers.value)
    val anomaliesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.cellularAnomalies.value)
    val eventsJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.cellularEvents.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_CELLULAR_DATA).apply {
            data = Bundle().apply {
                cellStatusJson?.let { putString(ScanningServiceIpc.KEY_CELL_STATUS_JSON, it) }
                putString(ScanningServiceIpc.KEY_SEEN_TOWERS_JSON, towersJson)
                putString(ScanningServiceIpc.KEY_CELLULAR_ANOMALIES_JSON, anomaliesJson)
                putString(ScanningServiceIpc.KEY_CELLULAR_EVENTS_JSON, eventsJson)
            }
        }
    }
}

/**
 * Broadcast satellite monitoring data to all registered IPC clients.
 */
internal fun ScanningService.broadcastSatelliteData() {
    if (ipcClients.isEmpty()) return
    val stateJson = ScanningServiceState.satelliteState.value?.let { ScanningServiceIpc.gson.toJson(it) }
    val anomaliesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.satelliteAnomalies.value)
    val historyJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.satelliteHistory.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SATELLITE_DATA).apply {
            data = Bundle().apply {
                stateJson?.let { putString(ScanningServiceIpc.KEY_SATELLITE_STATE_JSON, it) }
                putString(ScanningServiceIpc.KEY_SATELLITE_ANOMALIES_JSON, anomaliesJson)
                putString(ScanningServiceIpc.KEY_SATELLITE_HISTORY_JSON, historyJson)
            }
        }
    }
}

/**
 * Broadcast rogue WiFi monitoring data to all registered IPC clients.
 */
internal fun ScanningService.broadcastRogueWifiData() {
    if (ipcClients.isEmpty()) return
    val statusJson = ScanningServiceState.rogueWifiStatus.value?.let { ScanningServiceIpc.gson.toJson(it) }
    val anomaliesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.rogueWifiAnomalies.value)
    val suspiciousJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.suspiciousNetworks.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_ROGUE_WIFI_DATA).apply {
            data = Bundle().apply {
                statusJson?.let { putString(ScanningServiceIpc.KEY_ROGUE_WIFI_STATUS_JSON, it) }
                putString(ScanningServiceIpc.KEY_ROGUE_WIFI_ANOMALIES_JSON, anomaliesJson)
                putString(ScanningServiceIpc.KEY_SUSPICIOUS_NETWORKS_JSON, suspiciousJson)
            }
        }
    }
}

/**
 * Broadcast RF signal analysis data to all registered IPC clients.
 */
internal fun ScanningService.broadcastRfData() {
    if (ipcClients.isEmpty()) return
    val statusJson = ScanningServiceState.rfStatus.value?.let { ScanningServiceIpc.gson.toJson(it) }
    val anomaliesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.rfAnomalies.value)
    val dronesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.detectedDrones.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_RF_DATA).apply {
            data = Bundle().apply {
                statusJson?.let { putString(ScanningServiceIpc.KEY_RF_STATUS_JSON, it) }
                putString(ScanningServiceIpc.KEY_RF_ANOMALIES_JSON, anomaliesJson)
                putString(ScanningServiceIpc.KEY_DETECTED_DRONES_JSON, dronesJson)
            }
        }
    }
}

/**
 * Broadcast ultrasonic detection data to all registered IPC clients.
 */
internal fun ScanningService.broadcastUltrasonicData() {
    if (ipcClients.isEmpty()) return
    val statusJson = ScanningServiceState.ultrasonicStatus.value?.let { ScanningServiceIpc.gson.toJson(it) }
    val anomaliesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.ultrasonicAnomalies.value)
    val beaconsJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.ultrasonicBeacons.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_ULTRASONIC_DATA).apply {
            data = Bundle().apply {
                statusJson?.let { putString(ScanningServiceIpc.KEY_ULTRASONIC_STATUS_JSON, it) }
                putString(ScanningServiceIpc.KEY_ULTRASONIC_ANOMALIES_JSON, anomaliesJson)
                putString(ScanningServiceIpc.KEY_ULTRASONIC_BEACONS_JSON, beaconsJson)
            }
        }
    }
}

/**
 * Broadcast GNSS satellite monitoring data to all registered IPC clients.
 */
internal fun ScanningService.broadcastGnssData() {
    if (ipcClients.isEmpty()) return
    val statusJson = ScanningServiceState.gnssStatus.value?.let { ScanningServiceIpc.gson.toJson(it) }
    val satellitesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.gnssSatellites.value)
    val anomaliesJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.gnssAnomalies.value)
    val eventsJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.gnssEvents.value)
    val measurementsJson = ScanningServiceState.gnssMeasurements.value?.let { ScanningServiceIpc.gson.toJson(it) }
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_GNSS_DATA).apply {
            data = Bundle().apply {
                statusJson?.let { putString(ScanningServiceIpc.KEY_GNSS_STATUS_JSON, it) }
                putString(ScanningServiceIpc.KEY_GNSS_SATELLITES_JSON, satellitesJson)
                putString(ScanningServiceIpc.KEY_GNSS_ANOMALIES_JSON, anomaliesJson)
                putString(ScanningServiceIpc.KEY_GNSS_EVENTS_JSON, eventsJson)
                measurementsJson?.let { putString(ScanningServiceIpc.KEY_GNSS_MEASUREMENTS_JSON, it) }
            }
        }
    }
}

/**
 * Broadcast last detection to all registered IPC clients.
 */
internal fun ScanningService.broadcastLastDetection() {
    if (ipcClients.isEmpty()) return
    val json = ScanningServiceState.lastDetection.value?.let { ScanningServiceIpc.gson.toJson(it) }
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_LAST_DETECTION).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_LAST_DETECTION_JSON, json)
            }
        }
    }
}

/**
 * Broadcast detection refresh event to all IPC clients.
 * Notifies UI to reload detections from database.
 * Also broadcasts active detections list for cross-process clients like Android Auto.
 */
internal fun ScanningService.broadcastDetectionRefresh() {
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_DETECTION_REFRESH)
    }
    // Also broadcast active detections for cross-process clients (e.g., Android Auto)
    // that cannot access the database directly
    broadcastActiveDetections()
}

/**
 * Broadcast boost mode status to all IPC clients.
 * Used by Android Auto to know when boost mode is active.
 */
internal fun ScanningService.broadcastBoostStatus() {
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_BOOST_STATUS).apply {
            data = Bundle().apply {
                putBoolean(ScanningServiceIpc.KEY_BOOST_MODE_ACTIVE, isBoostModeActive)
            }
        }
    }
}

/**
 * Broadcast active detections list to all IPC clients.
 * Used by cross-process clients like Android Auto that cannot access the database directly.
 */
internal fun ScanningService.broadcastActiveDetections() {
    if (ipcClients.isEmpty()) return
    serviceScope.launch {
        try {
            val activeDetections = repository.activeDetections.first()
            val json = ScanningServiceIpc.gson.toJson(activeDetections)
            broadcastToClients {
                Message.obtain(null, ScanningServiceIpc.MSG_ACTIVE_DETECTIONS).apply {
                    data = Bundle().apply {
                        putString(ScanningServiceIpc.KEY_ACTIVE_DETECTIONS_JSON, json)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting active detections", e)
        }
    }
}

/**
 * Broadcast error log to all registered IPC clients.
 */
internal fun ScanningService.broadcastErrorLog() {
    if (ipcClients.isEmpty()) return
    val json = ScanningServiceIpc.gson.toJson(ScanningServiceState.errorLog.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_ERROR_LOG).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_ERROR_LOG_JSON, json)
            }
        }
    }
}

/**
 * Broadcast scan statistics to all registered IPC clients.
 */
internal fun ScanningService.broadcastScanStats() {
    if (ipcClients.isEmpty()) return
    val json = ScanningServiceIpc.gson.toJson(ScanningServiceState.scanStats.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SCAN_STATS).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_SCAN_STATS_JSON, json)
            }
        }
    }
}

/**
 * Broadcast subsystem status updates to all registered IPC clients.
 * This should be called whenever any subsystem status changes.
 */
internal fun ScanningService.broadcastSubsystemStatus() {
    if (ipcClients.isEmpty()) return
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_SUBSYSTEM_STATUS).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_BLE_STATUS, ScanningServiceState.bleStatus.value.toIpcString())
                putString(ScanningServiceIpc.KEY_WIFI_STATUS, ScanningServiceState.wifiStatus.value.toIpcString())
                putString(ScanningServiceIpc.KEY_LOCATION_STATUS, ScanningServiceState.locationStatus.value.toIpcString())
                putString(ScanningServiceIpc.KEY_CELLULAR_STATUS, ScanningServiceState.cellularStatus.value.toIpcString())
                putString(ScanningServiceIpc.KEY_SATELLITE_STATUS, ScanningServiceState.satelliteStatus.value.toIpcString())
            }
        }
    }
}

/**
 * Broadcast battery state to IPC clients.
 */
internal fun ScanningService.broadcastBatteryState() {
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_BATTERY_STATE).apply {
            data = Bundle().apply {
                putString("mode", ScanningServiceState.currentBatteryMode.value.id)
                putString("modeName", ScanningServiceState.currentBatteryMode.value.displayName)
                putInt("batteryPercent", currentBatteryPercent)
                putBoolean("isCharging", isCharging)
            }
        }
    }
}

/**
 * Broadcast detector health status to all IPC clients.
 */
internal fun ScanningService.broadcastDetectorHealth() {
    if (ipcClients.isEmpty()) return
    val healthJson = ScanningServiceIpc.gson.toJson(ScanningServiceState.detectorHealth.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_DETECTOR_HEALTH).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_DETECTOR_HEALTH_JSON, healthJson)
            }
        }
    }
}

/**
 * Broadcast threading monitor data to all registered IPC clients.
 */
internal fun ScanningService.broadcastThreadingData() {
    if (ipcClients.isEmpty()) return
    val systemStateJson = ScanningServiceIpc.gson.toJson(threadingMonitor.systemState.value)
    val scannerStatesJson = ScanningServiceIpc.gson.toJson(threadingMonitor.scannerStates.value)
    val alertsJson = ScanningServiceIpc.gson.toJson(threadingMonitor.threadingAlerts.value)
    broadcastToClients {
        Message.obtain(null, ScanningServiceIpc.MSG_THREADING_DATA).apply {
            data = Bundle().apply {
                putString(ScanningServiceIpc.KEY_THREADING_SYSTEM_STATE_JSON, systemStateJson)
                putString(ScanningServiceIpc.KEY_THREADING_SCANNER_STATES_JSON, scannerStatesJson)
                putString(ScanningServiceIpc.KEY_THREADING_ALERTS_JSON, alertsJson)
            }
        }
    }
}

// ==================== Aggregate Broadcasts ====================

/**
 * Broadcast all data to IPC clients. Called periodically to keep clients in sync.
 */
internal fun ScanningService.broadcastAllDataToClients() {
    if (ipcClients.isEmpty()) return
    broadcastStateToClients()
    broadcastSeenBleDevices()
    broadcastSeenWifiNetworks()
    broadcastCellularData()
    broadcastSatelliteData()
    broadcastRogueWifiData()
    broadcastRfData()
    broadcastUltrasonicData()
    broadcastGnssData()
    broadcastLastDetection()
    broadcastActiveDetections()
    broadcastDetectorHealth()
}

/**
 * Broadcast all subsystem data to connected IPC clients.
 * Called periodically to ensure UI stays up-to-date.
 */
internal fun ScanningService.broadcastAllSubsystemData() {
    broadcastStateToClients()
    broadcastDetectorHealth()
    broadcastScanStats()
    broadcastSubsystemStatus()
    broadcastSeenBleDevices()
    broadcastSeenWifiNetworks()
    broadcastCellularData()
    broadcastSatelliteData()
    broadcastRogueWifiData()
    broadcastRfData()
    broadcastUltrasonicData()
    broadcastGnssData()
    if (threadingMonitor.isMonitoring) {
        broadcastThreadingData()
    }
}

/**
 * Broadcast correlation analysis results to connected IPC clients.
 */
internal fun ScanningService.broadcastCorrelationResults(result: com.flockyou.ai.correlation.CorrelationAnalysisResult) {
    if (ipcClients.isEmpty()) return

    try {
        broadcastToClients {
            android.os.Message.obtain(null, ScanningServiceIpc.MSG_CORRELATION_RESULTS).apply {
                data = android.os.Bundle().apply {
                    putInt("correlation_count", result.correlatedThreats.size)
                    putString("summary", result.summary)
                    putBoolean("has_critical", result.hasCriticalCorrelations)
                    putString("highest_threat", result.highestThreatLevel?.displayName)
                    putLong("timestamp", result.analysisTimestamp)
                    // Serialize correlation types found
                    val types = result.correlationsByType.entries.map { "${it.key.name}:${it.value}" }
                    putStringArrayList("correlation_types", ArrayList(types))
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to broadcast correlation results: ${e.message}", e)
    }
}
