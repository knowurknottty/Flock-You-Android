package com.flockyou.detection

import com.flockyou.data.model.DeviceType

/**
 * Single authoritative source of impact factors for all device types.
 *
 * Used by both ThreatScoring.calculateThreat() and Detection.calculateThreatLevel().
 * Every DeviceType enum value MUST have an explicit entry here - enforced by ImpactFactorsTest.
 *
 * Scale:
 * - 2.0: Intercepts all communications, can cause physical harm
 * - 1.8: Can cause physical harm via misdirection, active attack tools
 * - 1.5-1.7: Stalking/tracking, hacking tools (context-dependent)
 * - 1.2-1.3: Privacy violation but not physical threat
 * - 1.0: Standard privacy concern
 * - 0.7-0.8: Minor privacy concern, consumer IoT
 * - 0.5-0.6: Infrastructure, not an attack
 */
object ImpactFactors {

    private val factors: Map<DeviceType, Double> = mapOf(
        // Maximum impact - intercepts all communications
        DeviceType.STINGRAY_IMSI to 2.0,
        DeviceType.CELLEBRITE_FORENSICS to 2.0,
        DeviceType.GRAYKEY_DEVICE to 2.0,
        DeviceType.MAN_IN_MIDDLE to 2.0,

        // High impact - can cause physical harm or intercept communications
        DeviceType.GNSS_SPOOFER to 1.8,
        DeviceType.GNSS_JAMMER to 1.8,
        DeviceType.RF_JAMMER to 1.8,
        DeviceType.WIFI_PINEAPPLE to 1.8,
        DeviceType.ROGUE_AP to 1.7,

        // Hacking tools - context-dependent impact
        DeviceType.FLIPPER_ZERO to 1.5,
        DeviceType.FLIPPER_ZERO_SPAM to 1.9,
        DeviceType.HACKRF_SDR to 1.6,
        DeviceType.PROXMARK to 1.7,
        DeviceType.USB_RUBBER_DUCKY to 1.8,
        DeviceType.LAN_TURTLE to 1.7,
        DeviceType.BASH_BUNNY to 1.8,
        DeviceType.KEYCROC to 1.8,
        DeviceType.SHARK_JACK to 1.7,
        DeviceType.SCREEN_CRAB to 1.6,
        DeviceType.GENERIC_HACKING_TOOL to 1.5,

        // Significant impact - stalking/tracking concern
        DeviceType.AIRTAG to 1.5,
        DeviceType.TILE_TRACKER to 1.5,
        DeviceType.SAMSUNG_SMARTTAG to 1.5,
        DeviceType.GENERIC_BLE_TRACKER to 1.5,
        DeviceType.TRACKING_DEVICE to 1.5,
        DeviceType.SURVEILLANCE_VAN to 1.5,
        DeviceType.DRONE to 1.4,

        // Moderate impact - privacy violations
        DeviceType.HIDDEN_CAMERA to 1.3,
        DeviceType.HIDDEN_TRANSMITTER to 1.3,
        DeviceType.PACKET_SNIFFER to 1.3,
        DeviceType.FLOCK_SAFETY_CAMERA to 1.2,
        DeviceType.LICENSE_PLATE_READER to 1.2,
        DeviceType.FACIAL_RECOGNITION to 1.2,
        DeviceType.CLEARVIEW_AI to 1.2,
        DeviceType.PALANTIR_DEVICE to 1.2,
        DeviceType.RAVEN_GUNSHOT_DETECTOR to 1.2,
        DeviceType.SHOTSPOTTER to 1.2,

        // Standard impact - surveillance but known type
        DeviceType.BODY_CAMERA to 1.0,
        DeviceType.POLICE_VEHICLE to 1.0,
        DeviceType.POLICE_RADIO to 1.0,
        DeviceType.MOTOROLA_POLICE_TECH to 1.0,
        DeviceType.AXON_POLICE_TECH to 1.0,
        DeviceType.L3HARRIS_SURVEILLANCE to 1.0,
        DeviceType.CCTV_CAMERA to 1.0,
        DeviceType.PTZ_CAMERA to 1.0,
        DeviceType.THERMAL_CAMERA to 1.0,
        DeviceType.NIGHT_VISION to 1.0,
        DeviceType.ULTRASONIC_BEACON to 1.0,
        DeviceType.SATELLITE_NTN to 1.0,
        DeviceType.UNKNOWN_SURVEILLANCE to 1.0,
        DeviceType.PENGUIN_SURVEILLANCE to 1.0,
        DeviceType.PIGVISION_SYSTEM to 1.0,

        // Lower impact - consumer IoT devices
        DeviceType.RING_DOORBELL to 0.8,
        DeviceType.NEST_CAMERA to 0.8,
        DeviceType.WYZE_CAMERA to 0.8,
        DeviceType.ARLO_CAMERA to 0.8,
        DeviceType.EUFY_CAMERA to 0.8,
        DeviceType.BLINK_CAMERA to 0.8,
        DeviceType.SIMPLISAFE_DEVICE to 0.8,
        DeviceType.ADT_DEVICE to 0.8,
        DeviceType.VIVINT_DEVICE to 0.8,
        DeviceType.AMAZON_SIDEWALK to 0.7,
        DeviceType.BLUETOOTH_BEACON to 0.7,
        DeviceType.RETAIL_TRACKER to 0.7,
        DeviceType.CROWD_ANALYTICS to 0.7,

        // Minimal impact - traffic/infrastructure
        DeviceType.SPEED_CAMERA to 0.6,
        DeviceType.RED_LIGHT_CAMERA to 0.6,
        DeviceType.TOLL_READER to 0.6,
        DeviceType.SURVEILLANCE_INFRASTRUCTURE to 0.6,
        DeviceType.TRAFFIC_SENSOR to 0.5,
        DeviceType.FLEET_VEHICLE to 0.5,
        DeviceType.TESLA_VEHICLE to 0.5,
        DeviceType.WAYMO_VEHICLE to 0.5,
        DeviceType.RF_INTERFERENCE to 0.5,
        DeviceType.RF_ANOMALY to 0.5
    )

    /**
     * Get the impact factor for a device type.
     * Returns 1.0 for any type not in the map (should not happen if tests pass).
     */
    fun get(deviceType: DeviceType): Double = factors[deviceType] ?: 1.0

    /**
     * Returns the set of device types that have explicit entries.
     * Used by tests to verify completeness.
     */
    fun coveredTypes(): Set<DeviceType> = factors.keys
}
