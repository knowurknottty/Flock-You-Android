package com.flockyou.data

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scan_settings")

/**
 * Battery-adaptive scanning mode.
 * Controls how aggressively the app scans based on battery considerations.
 */
enum class BatteryAdaptiveMode(
    val id: String,
    val displayName: String,
    val description: String,
    /** Multiplier for scan intervals (higher = less frequent) */
    val intervalMultiplier: Float,
    /** Multiplier for scan durations (lower = shorter scans) */
    val durationMultiplier: Float,
    /** Whether to disable non-essential subsystems (ultrasonic, RF) */
    val disableNonEssential: Boolean,
    /** Battery threshold below which this mode activates (for AUTO mode) */
    val batteryThreshold: Int
) {
    /** Maximum detection speed, highest battery usage */
    PERFORMANCE(
        id = "performance",
        displayName = "Performance",
        description = "Maximum scan frequency for best detection. High battery usage.",
        intervalMultiplier = 0.5f,  // More aggressive scanning
        durationMultiplier = 1.3f,
        disableNonEssential = false,
        batteryThreshold = 0
    ),
    /** Balanced scanning - default behavior */
    BALANCED(
        id = "balanced",
        displayName = "Balanced",
        description = "Good detection with moderate battery usage. Recommended for most users.",
        intervalMultiplier = 1.0f,
        durationMultiplier = 1.0f,
        disableNonEssential = false,
        batteryThreshold = 50
    ),
    /** Reduced scanning to preserve battery */
    BATTERY_SAVER(
        id = "battery_saver",
        displayName = "Battery Saver",
        description = "Reduced scan frequency to extend battery life. Disables ultrasonic and RF.",
        intervalMultiplier = 1.5f,
        durationMultiplier = 0.7f,
        disableNonEssential = true,
        batteryThreshold = 30
    ),
    /** Minimal scanning for critical battery */
    MINIMAL(
        id = "minimal",
        displayName = "Minimal",
        description = "Essential BLE/WiFi only with extended intervals. For critical battery.",
        intervalMultiplier = 2.5f,
        durationMultiplier = 0.5f,
        disableNonEssential = true,
        batteryThreshold = 15
    );

    companion object {
        fun fromId(id: String): BatteryAdaptiveMode =
            entries.find { it.id == id } ?: BALANCED

        /**
         * Get the appropriate mode for a given battery level when in AUTO mode.
         */
        fun forBatteryLevel(batteryPercent: Int): BatteryAdaptiveMode = when {
            batteryPercent <= MINIMAL.batteryThreshold -> MINIMAL
            batteryPercent <= BATTERY_SAVER.batteryThreshold -> BATTERY_SAVER
            // AUTO is conservation-only. PERFORMANCE is explicit opt-in.
            // Battery charge is not a proxy for device performance.
            else -> BALANCED
        }
    }
}

data class ScanSettings(
    val wifiScanIntervalSeconds: Int = 25,   // Reduced from 35s for more frequent scanning
    val bleScanDurationSeconds: Int = 10,
    val inactiveTimeoutSeconds: Int = 60,
    val seenDeviceTimeoutMinutes: Int = 5,
    val enableBleScanning: Boolean = true,
    val enableWifiScanning: Boolean = true,
    val trackSeenDevices: Boolean = true,
    // RF detection timing
    val rfScanIntervalSeconds: Int = 15,     // Reduced from 30s for more frequent scanning
    // Ultrasonic detection timing
    val ultrasonicScanIntervalSeconds: Int = 20,  // Reduced from 30s for more frequent scanning
    val ultrasonicScanDurationSeconds: Int = 5,
    // GNSS/Satellite detection timing
    val gnssScanIntervalSeconds: Int = 3,    // Reduced from 5s for more frequent scanning
    val satelliteScanIntervalSeconds: Int = 5,   // Reduced from 10s for more frequent scanning
    // Cellular detection timing
    val cellularScanIntervalSeconds: Int = 3,    // Reduced from 5s for more frequent scanning
    // Battery-adaptive mode settings
    val batteryAdaptiveMode: String = "balanced",
    val autoBatteryAdaptive: Boolean = true // When true, automatically adjust based on battery level
) {
    /**
     * Get the current battery mode setting.
     */
    fun getBatteryMode(): BatteryAdaptiveMode = BatteryAdaptiveMode.fromId(batteryAdaptiveMode)

    /**
     * Get the effective battery mode, considering auto-adaptive and current battery level.
     */
    fun getEffectiveMode(currentBatteryPercent: Int): BatteryAdaptiveMode {
        return if (autoBatteryAdaptive) {
            BatteryAdaptiveMode.forBatteryLevel(currentBatteryPercent)
        } else {
            getBatteryMode()
        }
    }

    /**
     * Apply battery mode adjustments to WiFi scan interval.
     */
    fun getEffectiveWifiInterval(batteryPercent: Int): Int {
        val mode = getEffectiveMode(batteryPercent)
        return (wifiScanIntervalSeconds * mode.intervalMultiplier).toInt().coerceIn(15, 300)
    }

    /**
     * Apply battery mode adjustments to BLE scan duration.
     */
    fun getEffectiveBleDuration(batteryPercent: Int): Int {
        val mode = getEffectiveMode(batteryPercent)
        return (bleScanDurationSeconds * mode.durationMultiplier).toInt().coerceIn(5, 30)
    }

    /**
     * Check if non-essential subsystems should be disabled for current battery mode.
     */
    fun shouldDisableNonEssential(batteryPercent: Int): Boolean {
        return getEffectiveMode(batteryPercent).disableNonEssential
    }
}

@Singleton
class ScanSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val constrainedDeviceDefaults: ScanSettings by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val fourGiB = 4L * 1024L * 1024L * 1024L
        val constrained = am.isLowRamDevice || am.memoryClass <= 256 || memoryInfo.totalMem <= fourGiB

        if (constrained) {
            ScanSettings(
                wifiScanIntervalSeconds = 45,
                bleScanDurationSeconds = 8,
                inactiveTimeoutSeconds = 90,
                rfScanIntervalSeconds = 60,
                ultrasonicScanIntervalSeconds = 60,
                ultrasonicScanDurationSeconds = 4,
                gnssScanIntervalSeconds = 10,
                satelliteScanIntervalSeconds = 30,
                cellularScanIntervalSeconds = 10,
                batteryAdaptiveMode = "balanced",
                autoBatteryAdaptive = true
            )
        } else {
            ScanSettings()
        }
    }

    private object PreferencesKeys {
        val WIFI_SCAN_INTERVAL = intPreferencesKey("wifi_scan_interval_seconds")
        val BLE_SCAN_DURATION = intPreferencesKey("ble_scan_duration_seconds")
        val INACTIVE_TIMEOUT = intPreferencesKey("inactive_timeout_seconds")
        val SEEN_DEVICE_TIMEOUT = intPreferencesKey("seen_device_timeout_minutes")
        val ENABLE_BLE = booleanPreferencesKey("enable_ble_scanning")
        val ENABLE_WIFI = booleanPreferencesKey("enable_wifi_scanning")
        val TRACK_SEEN_DEVICES = booleanPreferencesKey("track_seen_devices")
        // RF detection timing
        val RF_SCAN_INTERVAL = intPreferencesKey("rf_scan_interval_seconds")
        // Ultrasonic detection timing
        val ULTRASONIC_SCAN_INTERVAL = intPreferencesKey("ultrasonic_scan_interval_seconds")
        val ULTRASONIC_SCAN_DURATION = intPreferencesKey("ultrasonic_scan_duration_seconds")
        // GNSS/Satellite detection timing
        val GNSS_SCAN_INTERVAL = intPreferencesKey("gnss_scan_interval_seconds")
        val SATELLITE_SCAN_INTERVAL = intPreferencesKey("satellite_scan_interval_seconds")
        // Cellular detection timing
        val CELLULAR_SCAN_INTERVAL = intPreferencesKey("cellular_scan_interval_seconds")
        // Battery-adaptive mode
        val BATTERY_ADAPTIVE_MODE = stringPreferencesKey("battery_adaptive_mode")
        val AUTO_BATTERY_ADAPTIVE = booleanPreferencesKey("auto_battery_adaptive")
    }
    
    val settings: Flow<ScanSettings> = context.dataStore.data.map { preferences ->
        val defaults = constrainedDeviceDefaults
        ScanSettings(
            wifiScanIntervalSeconds = preferences[PreferencesKeys.WIFI_SCAN_INTERVAL] ?: defaults.wifiScanIntervalSeconds,
            bleScanDurationSeconds = preferences[PreferencesKeys.BLE_SCAN_DURATION] ?: defaults.bleScanDurationSeconds,
            inactiveTimeoutSeconds = preferences[PreferencesKeys.INACTIVE_TIMEOUT] ?: defaults.inactiveTimeoutSeconds,
            seenDeviceTimeoutMinutes = preferences[PreferencesKeys.SEEN_DEVICE_TIMEOUT] ?: defaults.seenDeviceTimeoutMinutes,
            enableBleScanning = preferences[PreferencesKeys.ENABLE_BLE] ?: defaults.enableBleScanning,
            enableWifiScanning = preferences[PreferencesKeys.ENABLE_WIFI] ?: defaults.enableWifiScanning,
            trackSeenDevices = preferences[PreferencesKeys.TRACK_SEEN_DEVICES] ?: defaults.trackSeenDevices,
            rfScanIntervalSeconds = preferences[PreferencesKeys.RF_SCAN_INTERVAL] ?: defaults.rfScanIntervalSeconds,
            ultrasonicScanIntervalSeconds = preferences[PreferencesKeys.ULTRASONIC_SCAN_INTERVAL] ?: defaults.ultrasonicScanIntervalSeconds,
            ultrasonicScanDurationSeconds = preferences[PreferencesKeys.ULTRASONIC_SCAN_DURATION] ?: defaults.ultrasonicScanDurationSeconds,
            gnssScanIntervalSeconds = preferences[PreferencesKeys.GNSS_SCAN_INTERVAL] ?: defaults.gnssScanIntervalSeconds,
            satelliteScanIntervalSeconds = preferences[PreferencesKeys.SATELLITE_SCAN_INTERVAL] ?: defaults.satelliteScanIntervalSeconds,
            cellularScanIntervalSeconds = preferences[PreferencesKeys.CELLULAR_SCAN_INTERVAL] ?: defaults.cellularScanIntervalSeconds,
            batteryAdaptiveMode = preferences[PreferencesKeys.BATTERY_ADAPTIVE_MODE] ?: defaults.batteryAdaptiveMode,
            autoBatteryAdaptive = preferences[PreferencesKeys.AUTO_BATTERY_ADAPTIVE] ?: defaults.autoBatteryAdaptive
        )
    }
    
    suspend fun updateWifiScanInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_SCAN_INTERVAL] = seconds.coerceIn(20, 120)
        }
    }
    
    suspend fun updateBleScanDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLE_SCAN_DURATION] = seconds.coerceIn(5, 30)
        }
    }
    
    suspend fun updateInactiveTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INACTIVE_TIMEOUT] = seconds.coerceIn(30, 300)
        }
    }
    
    suspend fun updateSeenDeviceTimeout(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEEN_DEVICE_TIMEOUT] = minutes.coerceIn(1, 30)
        }
    }
    
    suspend fun setEnableBle(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_BLE] = enabled
        }
    }
    
    suspend fun setEnableWifi(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_WIFI] = enabled
        }
    }
    
    suspend fun setTrackSeenDevices(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRACK_SEEN_DEVICES] = enabled
        }
    }

    suspend fun updateRfScanInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RF_SCAN_INTERVAL] = seconds.coerceIn(5, 120)
        }
    }

    suspend fun updateUltrasonicScanInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ULTRASONIC_SCAN_INTERVAL] = seconds.coerceIn(10, 120)
        }
    }

    suspend fun updateUltrasonicScanDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ULTRASONIC_SCAN_DURATION] = seconds.coerceIn(3, 15)
        }
    }

    suspend fun updateGnssScanInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GNSS_SCAN_INTERVAL] = seconds.coerceIn(1, 30)
        }
    }

    suspend fun updateSatelliteScanInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SATELLITE_SCAN_INTERVAL] = seconds.coerceIn(5, 60)
        }
    }

    suspend fun updateCellularScanInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CELLULAR_SCAN_INTERVAL] = seconds.coerceIn(1, 30)
        }
    }

    /**
     * Set the battery-adaptive mode.
     * @param modeId One of: "performance", "balanced", "battery_saver", "minimal"
     */
    suspend fun setBatteryAdaptiveMode(modeId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BATTERY_ADAPTIVE_MODE] = modeId
        }
    }

    /**
     * Enable or disable automatic battery-adaptive scanning.
     * When enabled, the app automatically adjusts scan intensity based on battery level.
     */
    suspend fun setAutoBatteryAdaptive(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_BATTERY_ADAPTIVE] = enabled
        }
    }
}
