package com.flockyou.data.model

import java.util.UUID

/**
 * Database of known surveillance device signatures
 * Based on data from deflock.me, GainSec research, and FCC filings
 * 
 * Detection methodology:
 * - WiFi: SSID patterns, MAC OUI prefixes from LTE modems
 * - BLE: Device names, Service UUIDs (especially for Raven)
 * 
 * Flock cameras use cellular LTE modems (Quectel, Telit, Sierra Wireless)
 * and emit WiFi for configuration/management
 */
object DetectionPatterns {

    // ==================== CONSUMER TRACKER SPECIFICATIONS ====================
    // Comprehensive real-world knowledge about Bluetooth trackers for stalking detection

    /**
     * Detailed specifications and stalking-relevant information for consumer trackers.
     */
    data class TrackerSpecification(
        val manufacturerId: Int,
        val manufacturerName: String,
        val models: List<TrackerModel>,
        val antiStalkingFeatures: AntiStalkingFeatures,
        val confirmationMethods: List<String>,
        val physicalCharacteristics: PhysicalCharacteristics,
        val networkType: NetworkType,
        val stalkingRisk: StalkingRisk
    )

    data class TrackerModel(
        val name: String,
        val range: String,
        val soundLevel: String,
        val hasUwb: Boolean,
        val batteryType: String,
        val batteryLife: String,
        val dimensions: String,
        val weight: String
    )

    data class AntiStalkingFeatures(
        val alertsVictim: Boolean,
        val alertPlatform: String,
        val playsSoundAutomatically: Boolean,
        val soundDelayHours: IntRange?,
        val canBeScannedByOtherApps: Boolean,
        val ownerInfoAccessible: Boolean,
        val ownerInfoMethod: String?
    )

    data class PhysicalCharacteristics(
        val shape: String,
        val commonHidingSpots: List<String>,
        val visualIdentifiers: String,
        val nfcCapable: Boolean
    )

    enum class NetworkType {
        APPLE_FIND_MY, TILE_NETWORK, SAMSUNG_SMARTTHINGS, STANDALONE_BLE, IBEACON_COMPATIBLE
    }

    enum class StalkingRisk(val level: Int, val description: String) {
        CRITICAL(5, "Frequently used for stalking, hard to detect"),
        HIGH(4, "Often misused, moderate detection difficulty"),
        MEDIUM(3, "Can be misused, but has anti-stalking features"),
        LOW(2, "Rarely used for stalking, easy to detect"),
        MINIMAL(1, "Designed with anti-stalking as priority")
    }

    val trackerSpecifications = mapOf(
        DeviceType.AIRTAG to TrackerSpecification(
            manufacturerId = 0x004C, manufacturerName = "Apple",
            models = listOf(TrackerModel("AirTag", "30ft BLE + UWB Precision Finding", "60dB", true, "CR2032", "~1 year", "31.9mm x 8mm", "11g")),
            antiStalkingFeatures = AntiStalkingFeatures(true, "iOS (auto), Android (Tracker Detect app)", true, 8..24, true, true, "NFC tap shows partial phone number and serial"),
            confirmationMethods = listOf("Use Apple 'Tracker Detect' app (free on Android)", "NFC tap AirTag to see owner info and serial", "Wait for automatic sound (8-24 hours)", "iPhone: 'AirTag Found Moving With You' notification", "Search: bags, car wheel wells, jacket pockets, phone cases", "iPhone 11+: Use Precision Finding"),
            physicalCharacteristics = PhysicalCharacteristics("Circular disc, white/silver", listOf("Car wheel wells", "Bag pockets/lining", "Jacket pockets", "Phone cases", "Keychains", "Shoes", "OBD-II port area", "Under car seats", "Luggage"), "Apple logo, silver back, quarter-sized", true),
            networkType = NetworkType.APPLE_FIND_MY, stalkingRisk = StalkingRisk.HIGH
        ),
        DeviceType.TILE_TRACKER to TrackerSpecification(
            manufacturerId = 0x00C7, manufacturerName = "Tile (Life360)",
            models = listOf(
                TrackerModel("Tile Pro", "400ft", "Loudest", false, "CR2032", "~1 year", "42x42x6.5mm", "12g"),
                TrackerModel("Tile Mate", "250ft", "Medium", false, "CR1632", "~3 years", "38x38x7.2mm", "7.5g"),
                TrackerModel("Tile Slim", "250ft", "Medium", false, "Non-replaceable", "~3 years", "86x54x2.5mm (credit card)", "14g"),
                TrackerModel("Tile Sticker", "150ft", "Quietest", false, "Non-replaceable", "~3 years", "27mm x 7.3mm", "5g")
            ),
            antiStalkingFeatures = AntiStalkingFeatures(false, "None (opt-in Scan and Secure only)", false, null, true, false, "No owner info - must contact Tile/police"),
            confirmationMethods = listOf("Use Tile 'Scan and Secure' feature (opt-in)", "Tiles do NOT auto-alert like AirTags", "Press Tile button 3x to make it ring", "Tile Slim is credit-card sized - check wallets", "No NFC - cannot tap to identify"),
            physicalCharacteristics = PhysicalCharacteristics("Square/Card/Circular", listOf("Wallets (Slim)", "Key rings", "Bag pockets", "Stuck to objects (Sticker)", "Car interior", "Coat linings"), "Tile 'T' logo, white/black, button on side", false),
            networkType = NetworkType.TILE_NETWORK, stalkingRisk = StalkingRisk.CRITICAL
        ),
        DeviceType.SAMSUNG_SMARTTAG to TrackerSpecification(
            manufacturerId = 0x0075, manufacturerName = "Samsung",
            models = listOf(
                TrackerModel("SmartTag", "390ft", "89dB", false, "CR2032", "~300 days", "39x39x9.9mm", "13g"),
                TrackerModel("SmartTag+", "390ft + UWB", "89dB", true, "CR2032", "~165 days", "39x39x9.9mm", "13g"),
                TrackerModel("SmartTag2", "390ft", "Medium", false, "CR2032", "~500 days", "45x45x9mm", "14.5g")
            ),
            antiStalkingFeatures = AntiStalkingFeatures(true, "Samsung Galaxy with SmartThings", true, 8..24, true, false, "Samsung provides to law enforcement with warrant"),
            confirmationMethods = listOf("Galaxy: 'Unknown Tag Detected' auto-alerts", "Use SmartThings app to scan", "Non-Galaxy: 'SmartThings Find' app", "Press button to ring", "SmartTag+ AR finder on Galaxy"),
            physicalCharacteristics = PhysicalCharacteristics("Rounded square with keyring hole", listOf("Keychains", "Bags/pockets", "Car interior", "Pet collars", "Luggage"), "Samsung logo, button on front", false),
            networkType = NetworkType.SAMSUNG_SMARTTHINGS, stalkingRisk = StalkingRisk.MEDIUM
        ),
        DeviceType.GENERIC_BLE_TRACKER to TrackerSpecification(
            manufacturerId = 0x0000, manufacturerName = "Various (Chipolo, Eufy, Pebblebee, etc.)",
            models = listOf(
                TrackerModel("Chipolo ONE Spot", "200ft + Find My", "120dB (loudest)", false, "CR2032", "~2 years", "37.9x6.4mm", "8g"),
                TrackerModel("Eufy SmartTrack Link", "262ft + Find My", "Moderate", false, "CR2032", "~1 year", "37x37x6.5mm", "10g"),
                TrackerModel("Pebblebee Clip/Card", "500ft", "Moderate", false, "USB rechargeable", "~6 months", "Varies", "~10g"),
                TrackerModel("AliExpress Generic", "100-200ft", "Usually quiet", false, "CR2032", "6-12 months", "Varies", "5-15g")
            ),
            antiStalkingFeatures = AntiStalkingFeatures(true, "iOS (Find My compatible)", true, 8..24, true, false, "Contact manufacturer or law enforcement"),
            confirmationMethods = listOf("Find My compatible: iPhone alerts", "Use manufacturer's app", "Press button to ring (if present)", "Generic AliExpress trackers often have NO anti-stalking"),
            physicalCharacteristics = PhysicalCharacteristics("Varies: circular, square, card", listOf("Bags, car, clothes", "Pet collars", "Wallets (card type)"), "Brand logo, plastic, button for ring", false),
            networkType = NetworkType.APPLE_FIND_MY, stalkingRisk = StalkingRisk.HIGH
        )
    )

    // ==================== STALKING DETECTION HEURISTICS ====================

    data class StalkingHeuristic(val name: String, val condition: String, val suspicionLevel: SuspicionLevel, val interpretation: String, val actionRequired: String)

    enum class SuspicionLevel(val score: Int, val color: String) {
        CRITICAL(100, "RED"), HIGH(75, "ORANGE"), MEDIUM(50, "YELLOW"), LOW(25, "BLUE"), MINIMAL(10, "GREEN")
    }

    val stalkingHeuristics = listOf(
        StalkingHeuristic("Multiple Locations", "Same tracker at 3+ distinct locations", SuspicionLevel.CRITICAL, "Tracker is FOLLOWING you.", "Document and contact authorities."),
        StalkingHeuristic("Extended Presence", "Same tracker 30+ min while moving", SuspicionLevel.HIGH, "Tracker moving with you, hidden in belongings.", "Search belongings and vehicle."),
        StalkingHeuristic("Possession Signal", "Strong signal (-40 to -60 dBm) with low variance", SuspicionLevel.CRITICAL, "Tracker ON YOUR PERSON.", "Check pockets, bags, shoes immediately."),
        StalkingHeuristic("Home Departure", "Tracker appears when leaving home", SuspicionLevel.CRITICAL, "Planted at home or on vehicle.", "Check vehicle. Consider home security."),
        StalkingHeuristic("Person Correlation", "Disappears with specific person", SuspicionLevel.CRITICAL, "That person owns/planted it.", "Document pattern. May be domestic."),
        StalkingHeuristic("Work Hours Only", "Only appears during work hours", SuspicionLevel.HIGH, "Planted at workplace.", "Search work bag, laptop case, jacket."),
        StalkingHeuristic("Location-Triggered", "Appears after visiting a location", SuspicionLevel.HIGH, "Planted at that location.", "Think about when it first appeared."),
        StalkingHeuristic("Weak Fluctuating", "Weak signal with high variance", SuspicionLevel.MINIMAL, "Passing tracker, not targeting you.", "Monitor but likely safe.")
    )

    // ==================== STALKING RESPONSE GUIDANCE ====================

    object StalkingResponseGuidance {
        val immediateActions = listOf(
            "1. DOCUMENT - Screenshots with timestamps/locations",
            "2. DO NOT DESTROY - It's evidence. Removing battery is OK.",
            "3. If in danger, call 911",
            "4. Faraday bag/metal container stops transmission",
            "5. Note who had access to your belongings/vehicle/home"
        )

        val supportResources = mapOf(
            "National Domestic Violence Hotline" to "1-800-799-7233 (24/7)",
            "SPARC (Stalking Prevention)" to "stalkingawareness.org",
            "Cyber Civil Rights Initiative" to "cybercivilrights.org",
            "Tech Safety (NNEDV)" to "techsafety.org"
        )

        val whatNotToDo = listOf(
            "DO NOT confront stalker directly - can escalate",
            "DO NOT destroy tracker before documenting",
            "DO NOT ignore repeated detections",
            "DO NOT post on social media (alerts stalker)"
        )

        fun getGuidanceForSuspicionLevel(score: Int): String = when {
            score >= 80 -> "CRITICAL: Call 911 if danger. Document now. Hotline: 1-800-799-7233. DO NOT destroy tracker."
            score >= 60 -> "HIGH: Search belongings/vehicle/clothes. Document all. Consider police non-emergency line."
            score >= 40 -> "MODERATE: Monitor across locations. Casual search of items. Continue documenting."
            else -> "LOW: Likely passing tracker. Keep scanning to see if it reappears."
        }
    }

    // ==================== SURVEILLANCE EQUIPMENT CONTEXT ====================

    object SurveillanceEquipmentContext {
        val axonSignalInfo = mapOf(
            "description" to "Axon Signal Sidearm triggers body cameras when weapon drawn. ~1 pps normal, 20-50 pps activated.",
            "triggers" to listOf("Weapon unholstered", "TASER armed", "Siren activated", "Vehicle crash", "Manual button"),
            "what_it_means" to "Police engagement in progress. Multiple body cameras recording. You may be on video."
        )

        val ravenInfo = mapOf(
            "description" to "Flock Safety acoustic surveillance. Listens for gunfire AND 'human distress' (screaming). Solar, 24/7.",
            "vulnerability" to "GainSec research: leaks GPS, battery, network info, detection counts via BLE.",
            "concerns" to listOf("Continuous audio surveillance", "Vague 'distress' definition", "No warrant needed", "False positives trigger police")
        )
    }

    // ==================== SMART HOME PRIVACY CONTEXT ====================

    data class SmartHomeProfile(val manufacturer: String, val lawEnforcementSharing: Boolean, val details: String, val retention: String, val recommendations: List<String>)

    val smartHomeProfiles = mapOf(
        DeviceType.RING_DOORBELL to SmartHomeProfile("Ring (Amazon)", true, "2,500+ police partnerships. Can request footage without user consent.", "60 days", listOf("Disable Neighbors app", "Minimize cloud storage")),
        DeviceType.NEST_CAMERA to SmartHomeProfile("Google/Nest", true, "Can share via legal process. Always-on microphones.", "30 days (paid)", listOf("Review Google Activity", "Disable Familiar face")),
        DeviceType.EUFY_CAMERA to SmartHomeProfile("Eufy/Anker", false, "Claims 'local only' but caught sending thumbnails to cloud (2022).", "Local", listOf("Be skeptical of claims", "Monitor network traffic")),
        DeviceType.BLINK_CAMERA to SmartHomeProfile("Blink (Amazon)", true, "Same as Ring - Amazon ownership.", "60 days", listOf("Use local Sync Module", "Same Ring concerns"))
    )

    // ==================== MAC RANDOMIZATION CONTEXT ====================

    object MacRandomizationContext {
        val explanation = "Modern trackers rotate MACs (~15 min) but are identified by payload, manufacturer data, service UUIDs, and timing."
        val intervals = mapOf("AirTag" to "~15 min", "Tile" to "~10-15 min", "SmartTag" to "~15 min", "Generic" to "Varies")
    }

    // ==================== SSID Patterns ====================
    // Primary detection method - Flock cameras advertise specific SSIDs
    // Pattern data extracted to SsidPatterns.kt for maintainability
    val ssidPatterns: List<DetectionPattern> get() = SsidPatterns.entries

    // ==================== BLE Device Name Patterns ====================
    // Pattern data extracted to BleNamePatterns.kt for maintainability
    val bleNamePatterns: List<DetectionPattern> get() = BleNamePatterns.entries

    // ==================== Raven Service UUIDs ====================
    // Based on GainSec research - raven_configurations.json
    // Firmware versions 1.1.7, 1.2.0, 1.3.1
    data class RavenServiceInfo(
        val uuid: UUID,
        val name: String,
        val description: String,
        val dataExposed: String,
        val firmwareVersions: List<String>
    )
    
    val ravenServiceUuids = listOf(
        RavenServiceInfo(
            uuid = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"),
            name = "Device Information",
            description = "Standard BLE Device Information Service",
            dataExposed = "Serial number, model number, firmware version, manufacturer",
            firmwareVersions = listOf("1.1.x", "1.2.x", "1.3.x")
        ),
        RavenServiceInfo(
            uuid = UUID.fromString("00003100-0000-1000-8000-00805f9b34fb"),
            name = "GPS Location",
            description = "Real-time GPS coordinates of the device",
            dataExposed = "Latitude, longitude, altitude, GPS fix status",
            firmwareVersions = listOf("1.2.x", "1.3.x")
        ),
        RavenServiceInfo(
            uuid = UUID.fromString("00003200-0000-1000-8000-00805f9b34fb"),
            name = "Power Management",
            description = "Battery and solar panel status",
            dataExposed = "Battery level, charging status, solar input voltage",
            firmwareVersions = listOf("1.2.x", "1.3.x")
        ),
        RavenServiceInfo(
            uuid = UUID.fromString("00003300-0000-1000-8000-00805f9b34fb"),
            name = "Network Status",
            description = "Cellular and WiFi connectivity info",
            dataExposed = "LTE signal strength, carrier, data usage, WiFi status",
            firmwareVersions = listOf("1.2.x", "1.3.x")
        ),
        RavenServiceInfo(
            uuid = UUID.fromString("00003400-0000-1000-8000-00805f9b34fb"),
            name = "Upload Statistics",
            description = "Data transmission metrics",
            dataExposed = "Bytes uploaded, detection count, last upload time",
            firmwareVersions = listOf("1.3.x")
        ),
        RavenServiceInfo(
            uuid = UUID.fromString("00003500-0000-1000-8000-00805f9b34fb"),
            name = "Error/Diagnostics",
            description = "System diagnostics and error logs",
            dataExposed = "Error codes, system health, diagnostic data",
            firmwareVersions = listOf("1.3.x")
        ),
        // Legacy services (firmware 1.1.x)
        RavenServiceInfo(
            uuid = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb"),
            name = "Health Thermometer (Legacy)",
            description = "Repurposed standard BLE service",
            dataExposed = "Device temperature, environmental data",
            firmwareVersions = listOf("1.1.x")
        ),
        RavenServiceInfo(
            uuid = UUID.fromString("00001819-0000-1000-8000-00805f9b34fb"),
            name = "Location/Navigation (Legacy)",
            description = "Repurposed standard BLE location service",
            dataExposed = "Basic location data",
            firmwareVersions = listOf("1.1.x")
        )
    )
    
    val ravenServiceUuidSet: Set<UUID> = ravenServiceUuids.map { it.uuid }.toSet()

    // ==================== CACHED REGEX PATTERNS ====================
    // Pre-compile all regex patterns for performance - avoids recompilation on every match

    /**
     * Cache of compiled Regex objects for SSID patterns.
     * Lazy-initialized on first access to avoid startup overhead.
     */
    private val ssidPatternRegexCache: Map<String, Regex> by lazy {
        ssidPatterns.mapNotNull { pattern ->
            try {
                pattern.pattern to Regex(pattern.pattern)
            } catch (e: Exception) {
                null // Skip invalid patterns
            }
        }.toMap()
    }

    /**
     * Cache of compiled Regex objects for BLE name patterns.
     * Lazy-initialized on first access to avoid startup overhead.
     */
    private val bleNamePatternRegexCache: Map<String, Regex> by lazy {
        bleNamePatterns.mapNotNull { pattern ->
            try {
                pattern.pattern to Regex(pattern.pattern)
            } catch (e: Exception) {
                null // Skip invalid patterns
            }
        }.toMap()
    }

    /**
     * Get a cached compiled Regex for the given pattern string.
     * Returns null if the pattern is invalid.
     */
    private fun getCachedSsidRegex(patternString: String): Regex? {
        return ssidPatternRegexCache[patternString]
    }

    /**
     * Get a cached compiled Regex for the given BLE name pattern string.
     * Returns null if the pattern is invalid.
     */
    private fun getCachedBleNameRegex(patternString: String): Regex? {
        return bleNamePatternRegexCache[patternString]
    }

    /**
     * Estimate Raven firmware version based on advertised services
     */
    fun estimateRavenFirmwareVersion(serviceUuids: List<UUID>): String {
        val hasLegacyHealth = serviceUuids.contains(UUID.fromString("00001809-0000-1000-8000-00805f9b34fb"))
        val hasLegacyLocation = serviceUuids.contains(UUID.fromString("00001819-0000-1000-8000-00805f9b34fb"))
        val hasGps = serviceUuids.contains(UUID.fromString("00003100-0000-1000-8000-00805f9b34fb"))
        val hasPower = serviceUuids.contains(UUID.fromString("00003200-0000-1000-8000-00805f9b34fb"))
        val hasUpload = serviceUuids.contains(UUID.fromString("00003400-0000-1000-8000-00805f9b34fb"))
        val hasError = serviceUuids.contains(UUID.fromString("00003500-0000-1000-8000-00805f9b34fb"))
        
        return when {
            hasUpload || hasError -> "1.3.x (Latest - Full diagnostics)"
            hasGps && hasPower -> "1.2.x (GPS + Power monitoring)"
            hasLegacyHealth || hasLegacyLocation -> "1.1.x (Legacy firmware)"
            else -> "Unknown version"
        }
    }
    
    // ==================== MAC Address Prefixes (OUI) ====================
    // LTE modems commonly used in surveillance equipment
    // Flock uses cellular connectivity - these are modem manufacturer OUIs
    val macPrefixes = listOf(
        // Quectel - Common LTE modem manufacturer
        MacPrefix("50:29:4D", DeviceType.FLOCK_SAFETY_CAMERA, "Quectel (LTE Modem)", 70,
            "Quectel LTE modem - commonly used in Flock cameras"),
        MacPrefix("86:25:19", DeviceType.FLOCK_SAFETY_CAMERA, "Quectel (LTE Modem)", 70,
            "Quectel cellular module"),

        // Telit - Another common IoT/LTE modem maker
        MacPrefix("00:14:2D", DeviceType.UNKNOWN_SURVEILLANCE, "Telit (LTE Modem)", 65,
            "Telit cellular modem - used in IoT surveillance"),
        MacPrefix("D8:C7:71", DeviceType.UNKNOWN_SURVEILLANCE, "Telit Wireless", 65,
            "Telit wireless module"),

        // ==================== Fleet Vehicle OUIs (Police Car WiFi APs) ====================
        // Sierra Wireless - Common in police/fleet vehicles
        MacPrefix("00:0E:8E", DeviceType.FLEET_VEHICLE, "Sierra Wireless", 85,
            "Sierra Wireless - common in police/fleet vehicle mobile routers"),
        MacPrefix("00:11:75", DeviceType.FLEET_VEHICLE, "Sierra Wireless", 85,
            "Sierra Wireless - fleet vehicle mobile hotspot"),
        MacPrefix("00:14:3E", DeviceType.FLEET_VEHICLE, "Sierra Wireless", 80,
            "Sierra Wireless modem - IoT/M2M/fleet applications"),
        MacPrefix("00:A0:D5", DeviceType.FLEET_VEHICLE, "Sierra Wireless", 80,
            "Sierra Wireless cellular module"),

        // Cradlepoint - Popular for mobile command/surveillance vehicles
        MacPrefix("00:30:44", DeviceType.FLEET_VEHICLE, "Cradlepoint", 85,
            "Cradlepoint - mobile router common in police/emergency vehicles"),
        MacPrefix("00:10:8B", DeviceType.FLEET_VEHICLE, "Cradlepoint", 85,
            "Cradlepoint router - often used for mobile surveillance/command"),
        MacPrefix("EC:F4:51", DeviceType.FLEET_VEHICLE, "Cradlepoint", 80,
            "Cradlepoint NetCloud - fleet management router"),

        // Digi International - Fleet telematics
        MacPrefix("00:40:9D", DeviceType.FLEET_VEHICLE, "Digi International", 75,
            "Digi - fleet telematics and mobile connectivity"),

        // CalAmp - Vehicle tracking and fleet management
        MacPrefix("00:07:F9", DeviceType.FLEET_VEHICLE, "CalAmp", 80,
            "CalAmp - vehicle tracking and fleet management"),

        // Geotab - Fleet telematics
        MacPrefix("00:1E:C0", DeviceType.FLEET_VEHICLE, "Geotab", 75,
            "Geotab - fleet telematics device"),

        // u-blox - GPS/cellular modules
        MacPrefix("D4:CA:6E", DeviceType.UNKNOWN_SURVEILLANCE, "u-blox", 60,
            "u-blox cellular/GPS module"),

        // Raspberry Pi (used in DIY/prototype ALPR)
        MacPrefix("B8:27:EB", DeviceType.UNKNOWN_SURVEILLANCE, "Raspberry Pi", 50,
            "Raspberry Pi - potential DIY ALPR system"),
        MacPrefix("DC:A6:32", DeviceType.UNKNOWN_SURVEILLANCE, "Raspberry Pi", 50,
            "Raspberry Pi 4 - potential DIY surveillance"),
        MacPrefix("E4:5F:01", DeviceType.UNKNOWN_SURVEILLANCE, "Raspberry Pi", 50,
            "Raspberry Pi - IoT device"),

        // ==================== Axon / Body Camera Manufacturer OUIs ====================
        // Nordic Semiconductor - used in Axon body cameras and Signal devices
        // Note: Nordic's IEEE OUI is F4:CE:36 (not 00:59 which is their BLE Company ID)
        MacPrefix("F4:CE:36", DeviceType.AXON_POLICE_TECH, "Nordic Semiconductor", 75,
            "Nordic Semiconductor - common in Axon body cameras/Signal triggers"),
        MacPrefix("C0:A5:3E", DeviceType.AXON_POLICE_TECH, "Nordic Semiconductor", 75,
            "Nordic Semiconductor BLE - Axon equipment"),
        MacPrefix("F0:5C:D5", DeviceType.AXON_POLICE_TECH, "Nordic Semiconductor", 70,
            "Nordic Semiconductor - police equipment BLE"),

        // Texas Instruments - used in some body cameras
        MacPrefix("D0:39:72", DeviceType.BODY_CAMERA, "Texas Instruments", 65,
            "TI BLE module - potential body camera"),

        // Dialog Semiconductor - BLE chips in wearables/cameras
        MacPrefix("80:EA:CA", DeviceType.BODY_CAMERA, "Dialog Semiconductor", 60,
            "Dialog Semiconductor - BLE wearable/camera")
    )
    
    data class MacPrefix(
        val prefix: String,
        val deviceType: DeviceType,
        val manufacturer: String,
        val threatScore: Int,
        val description: String = "",
        val sourceUrl: String? = null
    )
    
    /** True only for syntactically valid, globally administered unicast MAC addresses. */
    fun isGloballyAdministeredMac(macAddress: String): Boolean {
        val parts = macAddress.replace("-", ":").split(":")
        if (parts.size != 6 || parts.any { it.length != 2 || it.toIntOrNull(16) == null }) return false
        val firstOctet = parts.first().toInt(16)
        return (firstOctet and 0x03) == 0
    }

    /**
     * Check if a MAC address matches any known prefix. Randomized/locally administered
     * addresses are not valid manufacturer OUI evidence and are rejected up front.
     */
    fun matchMacPrefix(macAddress: String): MacPrefix? {
        if (!isGloballyAdministeredMac(macAddress)) return null
        val normalizedMac = macAddress.uppercase().replace("-", ":")
        return macPrefixes.find { normalizedMac.startsWith(it.prefix.uppercase()) }
    }

    val teslaVehicleCommandServiceUuid: UUID =
        UUID.fromString("00000211-b2d1-43f0-9b88-960cebf8b91e")

    fun isTeslaVehicleCommandService(serviceUuids: List<UUID>): Boolean =
        serviceUuids.any { it == teslaVehicleCommandServiceUuid }

    fun isTeslaVehicleAdvertisementName(deviceName: String): Boolean =
        Regex("^S[0-9a-f]{16}C$").matches(deviceName)
    
    /**
     * Check if SSID matches any known pattern.
     * Uses pre-compiled cached Regex objects for performance.
     */
    fun matchSsidPattern(ssid: String): DetectionPattern? {
        return ssidPatterns.find { pattern ->
            getCachedSsidRegex(pattern.pattern)?.matches(ssid) ?: false
        }
    }

    /**
     * Check if BLE device name matches any known pattern.
     * Uses pre-compiled cached Regex objects for performance.
     */
    fun matchBleNamePattern(deviceName: String): DetectionPattern? {
        return bleNamePatterns.find { pattern ->
            getCachedBleNameRegex(pattern.pattern)?.matches(deviceName) ?: false
        }
    }
    
    /**
     * Check if any service UUIDs match Raven patterns
     */
    fun matchRavenServices(serviceUuids: List<UUID>): List<RavenServiceInfo> {
        return ravenServiceUuids.filter { it.uuid in serviceUuids }
    }
    
    /**
     * Check if this is a Raven device based on service UUIDs
     */
    fun isRavenDevice(serviceUuids: List<UUID>): Boolean {
        // Need at least 2 Raven-specific services to confirm
        val matchCount = serviceUuids.count { it in ravenServiceUuidSet }
        return matchCount >= 2
    }
    
    /**
     * Get detailed info about a device type
     */
    // Device type info data extracted to DeviceTypeInfoEntries.kt for maintainability
    fun getDeviceTypeInfo(deviceType: DeviceType): DeviceTypeInfo {
        return DeviceTypeInfoEntries.getInfo(deviceType)
    }

    data class DeviceTypeInfo(
        val name: String,
        val shortDescription: String,
        val fullDescription: String,
        val capabilities: List<String>,
        val privacyConcerns: List<String>,
        val recommendations: List<String> = emptyList()
    )
    
    // ==================== OUI Manufacturer Lookup ====================
    // Common manufacturer OUIs for quick identification
    private val ouiManufacturers = mapOf(
        "00:00:0C" to "Cisco",
        "00:01:42" to "Cisco",
        "00:0C:29" to "VMware",
        "00:0D:3A" to "Microsoft",
        "00:14:22" to "Dell",
        "00:17:88" to "Philips",
        "00:1A:11" to "Google",
        "00:1E:C2" to "Apple",
        "00:23:32" to "Apple",
        "00:25:00" to "Apple",
        "00:26:BB" to "Apple",
        "00:50:56" to "VMware",
        "08:00:27" to "Oracle VirtualBox",
        "14:13:46" to "Xiaomi",
        "18:65:90" to "Apple",
        "28:6A:B8" to "Apple",
        "2C:BE:08" to "Apple",
        "34:23:BA" to "Xiaomi",
        "38:F9:D3" to "Apple",
        "3C:06:30" to "Apple",
        "40:4E:36" to "HP",
        "44:D9:E7" to "Ubiquiti",
        "50:29:4D" to "Quectel",
        "54:60:09" to "Google",
        "58:CB:52" to "Google",
        "5C:CF:7F" to "Espressif",
        "60:01:94" to "Espressif",
        "70:B3:D5" to "IEEE Registration",
        "78:4F:43" to "Apple",
        "80:6D:97" to "Samsung",
        "84:D8:1B" to "Apple",
        "88:E9:FE" to "Apple",
        "8C:85:90" to "Apple",
        "94:65:2D" to "OnePlus",
        "98:D6:F7" to "LG",
        "9C:8E:99" to "Hewlett Packard",
        "A4:77:33" to "Google",
        "A4:C6:39" to "Intel",
        "AC:37:43" to "HTC",
        "B0:34:95" to "Apple",
        "B4:F1:DA" to "LG",
        "B8:27:EB" to "Raspberry Pi",
        "BC:83:85" to "Microsoft",
        "C0:EE:FB" to "OnePlus",
        "C8:3D:D4" to "CyberTAN",
        "CC:46:D6" to "Cisco",
        "D0:03:4B" to "Apple",
        "D4:61:9D" to "Apple",
        "D4:CA:6E" to "u-blox",
        "D8:C7:71" to "Telit",
        "DC:A6:32" to "Raspberry Pi",
        "E0:5F:45" to "Apple",
        "E4:5F:01" to "Raspberry Pi",
        "EC:85:2F" to "Apple",
        "F0:18:98" to "Apple",
        "F4:F5:D8" to "Google",
        "F8:1A:67" to "TP-Link",
        "FC:A1:3E" to "Samsung"
    )
    
    /**
     * Try to identify manufacturer from MAC OUI (first 3 octets)
     */
    fun getManufacturerFromOui(oui: String): String? {
        val normalizedOui = oui.uppercase().replace("-", ":").take(8)
        return ouiManufacturers[normalizedOui] ?: macPrefixes.find {
            normalizedOui.startsWith(it.prefix.uppercase())
        }?.manufacturer
    }

    // ==================== UNKNOWN DEVICE ANALYSIS ====================

    /**
     * Comprehensive analysis result for an unknown BLE device.
     */
    data class UnknownDeviceAnalysis(
        val macAddress: String,
        val manufacturerFromOui: String?,
        val manufacturerCategory: ManufacturerCategory,
        val serviceUuidAnalysis: ServiceUuidAnalysis,
        val advertisingBehavior: AdvertisingBehavior,
        val signalCharacteristics: SignalCharacteristics,
        val classificationConfidence: Float,
        val suggestedDeviceType: DeviceType?,
        val threatAssessment: String,
        val investigationPriority: InvestigationPriority
    )

    /**
     * Categories of manufacturers for quick risk assessment.
     */
    enum class ManufacturerCategory(val riskLevel: Int, val description: String) {
        CONSUMER_ELECTRONICS(1, "Major consumer electronics (Apple, Samsung, Google)"),
        IOT_CHIPMAKER(2, "IoT chip manufacturers (Espressif, Nordic, TI)"),
        TELECOM_MODEM(3, "Cellular/LTE modem makers (Quectel, Telit, Sierra)"),
        NETWORKING(2, "Networking equipment (Cisco, Ubiquiti, TP-Link)"),
        LAW_ENFORCEMENT(5, "Known law enforcement suppliers"),
        SURVEILLANCE(5, "Surveillance equipment manufacturers"),
        UNKNOWN(3, "Unknown manufacturer - requires investigation")
    }

    /**
     * Service UUID analysis for unknown devices.
     */
    data class ServiceUuidAnalysis(
        val totalUuids: Int,
        val standardUuids: List<StandardUuidInfo>,
        val customUuids: List<String>,
        val suspiciousPatterns: List<String>
    )

    /**
     * Information about a standard BLE service UUID.
     */
    data class StandardUuidInfo(
        val uuid: String,
        val name: String,
        val description: String,
        val commonUsage: String
    )

    /**
     * Advertising behavior analysis.
     */
    data class AdvertisingBehavior(
        val advertisingRate: Float,
        val rateCategory: RateCategory,
        val isConsistent: Boolean,
        val behavioralNotes: List<String>
    )

    /**
     * Advertising rate categories.
     */
    enum class RateCategory(val description: String) {
        VERY_LOW("< 0.5 pps - power saving mode or beacon"),
        NORMAL("0.5-2 pps - typical BLE device"),
        ELEVATED("2-10 pps - active device or tracking"),
        HIGH("10-20 pps - aggressive advertising"),
        SPIKE("> 20 pps - activation event or attack")
    }

    /**
     * Signal characteristics analysis.
     */
    data class SignalCharacteristics(
        val rssi: Int,
        val estimatedDistance: String,
        val proximityCategory: ProximityCategory
    )

    /**
     * Proximity categories based on RSSI.
     */
    enum class ProximityCategory(val description: String) {
        IMMEDIATE("On your person or in direct contact"),
        NEAR("Within a few meters - same room"),
        MEDIUM("Within 10-20 meters - nearby"),
        FAR("Beyond 20 meters - could be incidental"),
        EDGE("At detection limit - may be unreliable")
    }

    /**
     * Investigation priority for unknown devices.
     */
    enum class InvestigationPriority(val urgency: Int, val action: String) {
        CRITICAL(4, "Investigate immediately - potential active surveillance"),
        HIGH(3, "Investigate soon - suspicious characteristics"),
        MEDIUM(2, "Monitor over time - gather more data"),
        LOW(1, "Note and continue - likely benign"),
        IGNORE(0, "No action needed - clearly benign")
    }

    // Standard BLE service UUIDs for identification
    private val standardServiceUuids = mapOf(
        "1800" to StandardUuidInfo("1800", "Generic Access", "Basic device info", "All BLE devices"),
        "1801" to StandardUuidInfo("1801", "Generic Attribute", "GATT service discovery", "All BLE devices"),
        "180A" to StandardUuidInfo("180A", "Device Information", "Manufacturer, model, serial", "All BLE devices"),
        "180F" to StandardUuidInfo("180F", "Battery Service", "Battery level", "Consumer devices"),
        "1809" to StandardUuidInfo("1809", "Health Thermometer", "Temperature readings", "Medical/health devices"),
        "1819" to StandardUuidInfo("1819", "Location and Navigation", "GPS/location data", "Fitness/tracking"),
        "FD5A" to StandardUuidInfo("FD5A", "Samsung SmartTag", "Samsung tracker", "Samsung trackers"),
        "FEED" to StandardUuidInfo("FEED", "Tile Tracker", "Tile service", "Tile trackers"),
        "7DFC9000" to StandardUuidInfo("7DFC9000", "Apple Find My", "Find My network", "AirTags, Find My devices"),
        "FE9F" to StandardUuidInfo("FE9F", "Google Fast Pair", "Quick pairing", "Android devices"),
        "FEAA" to StandardUuidInfo("FEAA", "Eddystone", "Google beacon", "Retail/location beacons"),
        "0000" to StandardUuidInfo("0000", "Generic Service", "Custom implementation", "Various")
    )

    /**
     * Analyze an unknown BLE device comprehensively.
     */
    fun analyzeUnknownDevice(
        macAddress: String,
        deviceName: String?,
        serviceUuids: List<java.util.UUID>,
        manufacturerData: Map<Int, String>,
        rssi: Int,
        advertisingRate: Float
    ): UnknownDeviceAnalysis {
        // Get manufacturer from OUI
        val normalizedMac = macAddress.uppercase().replace("-", ":")
        val oui = normalizedMac.take(8)
        val manufacturer = getManufacturerFromOui(oui)

        // Categorize manufacturer
        val manufacturerCategory = categorizeManufacturer(manufacturer, manufacturerData)

        // Analyze service UUIDs
        val serviceUuidAnalysis = analyzeServiceUuids(serviceUuids)

        // Analyze advertising behavior
        val advertisingBehavior = analyzeAdvertisingBehavior(advertisingRate)

        // Analyze signal characteristics
        val signalCharacteristics = analyzeSignalCharacteristics(rssi)

        // Calculate classification confidence
        val classificationConfidence = calculateClassificationConfidence(
            manufacturer, deviceName, serviceUuids, manufacturerData
        )

        // Suggest device type
        val suggestedDeviceType = suggestDeviceType(
            manufacturer, deviceName, serviceUuids, manufacturerData, advertisingRate
        )

        // Build threat assessment
        val threatAssessment = buildThreatAssessment(
            manufacturerCategory, serviceUuidAnalysis, advertisingBehavior,
            signalCharacteristics, suggestedDeviceType
        )

        // Determine investigation priority
        val investigationPriority = determineInvestigationPriority(
            manufacturerCategory, serviceUuidAnalysis, advertisingBehavior,
            signalCharacteristics, classificationConfidence
        )

        return UnknownDeviceAnalysis(
            macAddress = macAddress,
            manufacturerFromOui = manufacturer,
            manufacturerCategory = manufacturerCategory,
            serviceUuidAnalysis = serviceUuidAnalysis,
            advertisingBehavior = advertisingBehavior,
            signalCharacteristics = signalCharacteristics,
            classificationConfidence = classificationConfidence,
            suggestedDeviceType = suggestedDeviceType,
            threatAssessment = threatAssessment,
            investigationPriority = investigationPriority
        )
    }

    private fun categorizeManufacturer(manufacturer: String?, manufacturerData: Map<Int, String>): ManufacturerCategory {
        // Check manufacturer data IDs first
        if (manufacturerData.containsKey(0x004C)) return ManufacturerCategory.CONSUMER_ELECTRONICS // Apple
        if (manufacturerData.containsKey(0x0075)) return ManufacturerCategory.CONSUMER_ELECTRONICS // Samsung
        if (manufacturerData.containsKey(0x00E0)) return ManufacturerCategory.CONSUMER_ELECTRONICS // Google
        if (manufacturerData.containsKey(0x0059)) return ManufacturerCategory.IOT_CHIPMAKER // Nordic

        return when (manufacturer?.lowercase()) {
            "apple", "samsung", "google", "lg", "oneplus", "htc", "xiaomi" ->
                ManufacturerCategory.CONSUMER_ELECTRONICS
            "espressif", "nordic semiconductor", "texas instruments", "dialog semiconductor" ->
                ManufacturerCategory.IOT_CHIPMAKER
            "quectel", "telit", "sierra wireless", "u-blox" ->
                ManufacturerCategory.TELECOM_MODEM
            "cisco", "ubiquiti", "tp-link", "cradlepoint", "digi international" ->
                ManufacturerCategory.NETWORKING
            "axon", "motorola solutions", "l3harris", "digital ally" ->
                ManufacturerCategory.LAW_ENFORCEMENT
            "flock safety", "soundthinking", "shotspotter" ->
                ManufacturerCategory.SURVEILLANCE
            null -> ManufacturerCategory.UNKNOWN
            else -> ManufacturerCategory.UNKNOWN
        }
    }

    private fun analyzeServiceUuids(serviceUuids: List<java.util.UUID>): ServiceUuidAnalysis {
        val standardUuidInfos = mutableListOf<StandardUuidInfo>()
        val customUuidStrings = mutableListOf<String>()
        val suspiciousPatterns = mutableListOf<String>()

        for (uuid in serviceUuids) {
            val uuidStr = uuid.toString().uppercase()
            val shortForm = uuidStr.substring(4, 8)

            // Check for standard UUID
            val standardInfo = standardServiceUuids[shortForm]
            if (standardInfo != null) {
                standardUuidInfos.add(standardInfo)

                // Check for suspicious standard services
                when (shortForm) {
                    "1819" -> suspiciousPatterns.add("Location/Navigation service - device may track position")
                    "1809" -> suspiciousPatterns.add("Health Thermometer - may be repurposed for data exfiltration")
                }
            } else {
                customUuidStrings.add(uuidStr)

                // Check for known suspicious patterns
                if (uuidStr.startsWith("00003")) {
                    suspiciousPatterns.add("Raven-like custom service UUID detected: $shortForm")
                }
                if (uuidStr.contains("7DFC9000", ignoreCase = true)) {
                    suspiciousPatterns.add("Apple Find My network service detected")
                }
            }
        }

        // Check for suspicious combinations
        if (serviceUuids.size > 5) {
            suspiciousPatterns.add("Unusually high number of services (${serviceUuids.size}) - may indicate complex device")
        }

        return ServiceUuidAnalysis(
            totalUuids = serviceUuids.size,
            standardUuids = standardUuidInfos,
            customUuids = customUuidStrings,
            suspiciousPatterns = suspiciousPatterns
        )
    }

    private fun analyzeAdvertisingBehavior(advertisingRate: Float): AdvertisingBehavior {
        val rateCategory = when {
            advertisingRate < 0.5f -> RateCategory.VERY_LOW
            advertisingRate < 2f -> RateCategory.NORMAL
            advertisingRate < 10f -> RateCategory.ELEVATED
            advertisingRate < 20f -> RateCategory.HIGH
            else -> RateCategory.SPIKE
        }

        val behavioralNotes = mutableListOf<String>()
        when (rateCategory) {
            RateCategory.VERY_LOW -> behavioralNotes.add("Low power mode - beacon or sleeping device")
            RateCategory.NORMAL -> behavioralNotes.add("Standard BLE advertising - typical consumer device")
            RateCategory.ELEVATED -> behavioralNotes.add("Elevated rate - active communication or tracking")
            RateCategory.HIGH -> behavioralNotes.add("High rate - aggressive advertising, potential tracker")
            RateCategory.SPIKE -> behavioralNotes.add("SPIKE DETECTED - possible activation event or attack")
        }

        return AdvertisingBehavior(
            advertisingRate = advertisingRate,
            rateCategory = rateCategory,
            isConsistent = true, // Would need historical data to determine
            behavioralNotes = behavioralNotes
        )
    }

    private fun analyzeSignalCharacteristics(rssi: Int): SignalCharacteristics {
        val estimatedDistance = when {
            rssi > -40 -> "< 1m (direct contact)"
            rssi > -50 -> "1-3m (very close)"
            rssi > -60 -> "3-10m (same room)"
            rssi > -70 -> "10-20m (nearby)"
            rssi > -80 -> "20-50m (medium distance)"
            else -> "> 50m (far/unreliable)"
        }

        val proximityCategory = when {
            rssi > -45 -> ProximityCategory.IMMEDIATE
            rssi > -55 -> ProximityCategory.NEAR
            rssi > -70 -> ProximityCategory.MEDIUM
            rssi > -85 -> ProximityCategory.FAR
            else -> ProximityCategory.EDGE
        }

        return SignalCharacteristics(
            rssi = rssi,
            estimatedDistance = estimatedDistance,
            proximityCategory = proximityCategory
        )
    }

    private fun calculateClassificationConfidence(
        manufacturer: String?,
        deviceName: String?,
        serviceUuids: List<java.util.UUID>,
        manufacturerData: Map<Int, String>
    ): Float {
        var confidence = 0.2f // Base confidence for unknown

        if (manufacturer != null) confidence += 0.2f
        if (deviceName != null && deviceName.isNotBlank()) confidence += 0.25f
        if (serviceUuids.isNotEmpty()) confidence += 0.15f
        if (manufacturerData.isNotEmpty()) confidence += 0.2f

        return confidence.coerceIn(0f, 1f)
    }

    private fun suggestDeviceType(
        manufacturer: String?,
        deviceName: String?,
        serviceUuids: List<java.util.UUID>,
        manufacturerData: Map<Int, String>,
        advertisingRate: Float
    ): DeviceType? {
        // Check manufacturer data for known trackers
        if (manufacturerData.containsKey(0x004C)) {
            // Apple - check for AirTag patterns
            val data = manufacturerData[0x004C] ?: ""
            if (data.startsWith("12") || data.startsWith("07")) {
                return DeviceType.AIRTAG
            }
        }
        if (manufacturerData.containsKey(0x00C7)) return DeviceType.TILE_TRACKER
        if (manufacturerData.containsKey(0x0075)) return DeviceType.SAMSUNG_SMARTTAG

        // Check service UUIDs
        for (uuid in serviceUuids) {
            val shortForm = uuid.toString().uppercase().substring(4, 8)
            when {
                shortForm == "FD5A" -> return DeviceType.SAMSUNG_SMARTTAG
                shortForm.startsWith("FEED") -> return DeviceType.TILE_TRACKER
            }
        }

        // Check device name patterns
        deviceName?.let { name ->
            matchBleNamePattern(name)?.let { return it.deviceType }
        }

        // High advertising rate suggests activation
        if (advertisingRate > 20f) {
            if (manufacturerData.containsKey(0x0059)) { // Nordic
                return DeviceType.AXON_POLICE_TECH
            }
        }

        return null
    }

    private fun buildThreatAssessment(
        manufacturerCategory: ManufacturerCategory,
        serviceUuidAnalysis: ServiceUuidAnalysis,
        advertisingBehavior: AdvertisingBehavior,
        signalCharacteristics: SignalCharacteristics,
        suggestedDeviceType: DeviceType?
    ): String {
        val assessmentParts = mutableListOf<String>()

        // Manufacturer assessment
        when (manufacturerCategory) {
            ManufacturerCategory.LAW_ENFORCEMENT,
            ManufacturerCategory.SURVEILLANCE -> {
                assessmentParts.add("HIGH RISK: Manufacturer associated with surveillance equipment")
            }
            ManufacturerCategory.TELECOM_MODEM -> {
                assessmentParts.add("MODERATE RISK: LTE modem chip - used in IoT surveillance devices")
            }
            ManufacturerCategory.IOT_CHIPMAKER -> {
                assessmentParts.add("MODERATE RISK: IoT chipmaker - used in various devices including trackers")
            }
            ManufacturerCategory.UNKNOWN -> {
                assessmentParts.add("UNKNOWN RISK: Cannot identify manufacturer")
            }
            else -> {
                assessmentParts.add("LOW RISK: Common consumer electronics manufacturer")
            }
        }

        // Service UUID assessment
        if (serviceUuidAnalysis.suspiciousPatterns.isNotEmpty()) {
            assessmentParts.add("SUSPICIOUS: ${serviceUuidAnalysis.suspiciousPatterns.joinToString("; ")}")
        }

        // Advertising behavior assessment
        if (advertisingBehavior.rateCategory in listOf(RateCategory.HIGH, RateCategory.SPIKE)) {
            assessmentParts.add("WARNING: Abnormal advertising rate detected")
        }

        // Proximity assessment
        if (signalCharacteristics.proximityCategory == ProximityCategory.IMMEDIATE) {
            assessmentParts.add("PROXIMITY: Device is very close - check your belongings")
        }

        return assessmentParts.joinToString("\n")
    }

    private fun determineInvestigationPriority(
        manufacturerCategory: ManufacturerCategory,
        serviceUuidAnalysis: ServiceUuidAnalysis,
        advertisingBehavior: AdvertisingBehavior,
        signalCharacteristics: SignalCharacteristics,
        classificationConfidence: Float
    ): InvestigationPriority {
        var score = 0

        // Manufacturer risk
        score += manufacturerCategory.riskLevel

        // Suspicious patterns
        score += serviceUuidAnalysis.suspiciousPatterns.size

        // Advertising behavior
        when (advertisingBehavior.rateCategory) {
            RateCategory.HIGH -> score += 2
            RateCategory.SPIKE -> score += 4
            else -> {}
        }

        // Proximity
        when (signalCharacteristics.proximityCategory) {
            ProximityCategory.IMMEDIATE -> score += 3
            ProximityCategory.NEAR -> score += 2
            else -> {}
        }

        // Low confidence = more investigation needed
        if (classificationConfidence < 0.5f) score += 1

        return when {
            score >= 10 -> InvestigationPriority.CRITICAL
            score >= 7 -> InvestigationPriority.HIGH
            score >= 4 -> InvestigationPriority.MEDIUM
            score >= 2 -> InvestigationPriority.LOW
            else -> InvestigationPriority.IGNORE
        }
    }

    /**
     * Get all built-in patterns as SurveillancePattern objects.
     * Used for merging with custom/downloaded patterns.
     */
    val allPatterns: List<SurveillancePattern> by lazy {
        val patterns = mutableListOf<SurveillancePattern>()

        // Group SSID patterns by device type
        val ssidByDevice = ssidPatterns.groupBy { it.deviceType }
        ssidByDevice.forEach { (deviceType, detectionPatterns) ->
            patterns.add(
                SurveillancePattern(
                    id = "builtin_ssid_${deviceType.name.lowercase()}",
                    name = deviceType.displayName,
                    description = detectionPatterns.first().description,
                    deviceType = deviceType,
                    manufacturer = detectionPatterns.first().manufacturer,
                    threatLevel = scoreToThreatLevel(detectionPatterns.maxOf { it.threatScore }),
                    ssidPatterns = detectionPatterns.map { it.pattern },
                    isBuiltIn = true
                )
            )
        }

        // Group BLE name patterns by device type
        val bleByDevice = bleNamePatterns.groupBy { it.deviceType }
        bleByDevice.forEach { (deviceType, detectionPatterns) ->
            // Check if we already have an entry for this device
            val existingIndex = patterns.indexOfFirst { it.deviceType == deviceType }
            if (existingIndex >= 0) {
                // Merge BLE patterns into existing entry
                val existing = patterns[existingIndex]
                patterns[existingIndex] = existing.copy(
                    bleNamePatterns = detectionPatterns.map { it.pattern }
                )
            } else {
                patterns.add(
                    SurveillancePattern(
                        id = "builtin_ble_${deviceType.name.lowercase()}",
                        name = deviceType.displayName,
                        description = detectionPatterns.first().description,
                        deviceType = deviceType,
                        manufacturer = detectionPatterns.first().manufacturer,
                        threatLevel = scoreToThreatLevel(detectionPatterns.maxOf { it.threatScore }),
                        bleNamePatterns = detectionPatterns.map { it.pattern },
                        isBuiltIn = true
                    )
                )
            }
        }

        // Group MAC prefixes by device type
        val macByDevice = macPrefixes.groupBy { it.deviceType }
        macByDevice.forEach { (deviceType, macPrefixList) ->
            val existingIndex = patterns.indexOfFirst { it.deviceType == deviceType }
            if (existingIndex >= 0) {
                val existing = patterns[existingIndex]
                patterns[existingIndex] = existing.copy(
                    macPrefixes = macPrefixList.map { it.prefix }
                )
            } else {
                patterns.add(
                    SurveillancePattern(
                        id = "builtin_mac_${deviceType.name.lowercase()}",
                        name = deviceType.displayName,
                        description = macPrefixList.first().description,
                        deviceType = deviceType,
                        manufacturer = macPrefixList.first().manufacturer,
                        threatLevel = scoreToThreatLevel(macPrefixList.maxOf { it.threatScore }),
                        macPrefixes = macPrefixList.map { it.prefix },
                        isBuiltIn = true
                    )
                )
            }
        }

        patterns.toList()
    }

    // ==================== REAL-WORLD RF SURVEILLANCE KNOWLEDGE ====================
    // Comprehensive reference data for RF-based surveillance detection

    /**
     * Common surveillance RF frequencies and their typical uses.
     * Essential for understanding what devices operate on which bands.
     */
    object RfFrequencyReference {
        // ==================== Hidden Camera / Bug Detection ====================

        /** Older analog video transmitters - lower quality, easier to detect */
        const val FREQ_900_MHZ = 900_000_000L      // Analog video, old devices
        const val FREQ_1200_MHZ = 1_200_000_000L   // Video transmitters (1.2 GHz)
        const val FREQ_2400_MHZ = 2_400_000_000L   // WiFi cameras, cheap bugs, most IoT
        const val FREQ_5800_MHZ = 5_800_000_000L   // Higher quality video, FPV drones

        /** Remote controls and triggers */
        const val FREQ_315_MHZ = 315_000_000L      // US garage doors, remotes
        const val FREQ_433_MHZ = 433_920_000L      // EU remotes, cheap IoT, key fobs
        const val FREQ_868_MHZ = 868_000_000L      // EU ISM band, LoRa
        const val FREQ_915_MHZ = 915_000_000L      // US ISM band, Amazon Sidewalk

        /** GPS frequencies (for detecting jammers/spoofers) */
        const val GPS_L1_FREQ = 1_575_420_000L     // GPS L1 (primary civilian)
        const val GPS_L2_FREQ = 1_227_600_000L     // GPS L2
        const val GPS_L5_FREQ = 1_176_450_000L     // GPS L5 (newer)
        const val GLONASS_L1_FREQ = 1_602_000_000L // GLONASS
        const val GALILEO_E1_FREQ = 1_575_420_000L // Galileo E1

        /** Cellular bands (for IMSI catcher detection) */
        const val CELL_850_MHZ = 850_000_000L      // 2G/3G
        const val CELL_900_MHZ = 900_000_000L      // 2G/3G GSM
        const val CELL_1800_MHZ = 1_800_000_000L   // 2G DCS
        const val CELL_1900_MHZ = 1_900_000_000L   // 2G/3G PCS
        const val CELL_700_MHZ = 700_000_000L      // LTE Band 12/13/17
        const val CELL_2100_MHZ = 2_100_000_000L   // 3G UMTS
        const val CELL_2600_MHZ = 2_600_000_000L   // LTE Band 7

        val hiddenCameraBugFrequencies = listOf(
            FrequencyBand(900_000_000L, 50_000_000L, "900 MHz Analog Video",
                "Older analog devices - lower quality, easier to detect"),
            FrequencyBand(1_200_000_000L, 100_000_000L, "1.2 GHz Video Transmitters",
                "Higher quality analog video, moderate detection difficulty"),
            FrequencyBand(2_400_000_000L, 100_000_000L, "2.4 GHz WiFi/IoT",
                "Most cheap bugs, WiFi cameras, IoT devices - very common"),
            FrequencyBand(5_800_000_000L, 150_000_000L, "5.8 GHz High Quality",
                "Higher quality video, FPV systems, harder to detect")
        )

        val remoteControlFrequencies = listOf(
            FrequencyBand(315_000_000L, 5_000_000L, "315 MHz (US)",
                "US garage doors, car remotes, some trackers"),
            FrequencyBand(433_920_000L, 5_000_000L, "433 MHz (EU/US)",
                "EU remotes, cheap IoT, key fobs, many trackers")
        )

        data class FrequencyBand(
            val centerHz: Long,
            val bandwidthHz: Long,
            val name: String,
            val description: String
        ) {
            fun contains(frequency: Long): Boolean {
                val halfBandwidth = bandwidthHz / 2
                return frequency in (centerHz - halfBandwidth)..(centerHz + halfBandwidth)
            }

            val startHz: Long get() = centerHz - bandwidthHz / 2
            val endHz: Long get() = centerHz + bandwidthHz / 2

            fun formatRange(): String {
                val startMhz = startHz / 1_000_000.0
                val endMhz = endHz / 1_000_000.0
                return String.format("%.1f - %.1f MHz", startMhz, endMhz)
            }
        }
    }

    // ==================== GPS TRACKER PROFILES ====================

    /**
     * Comprehensive GPS tracker profiles based on real-world deployment patterns.
     * Includes OBD-II, magnetic, and hardwired trackers.
     */
    data class GpsTrackerProfile(
        val id: String,
        val name: String,
        val category: GpsTrackerCategory,
        val manufacturer: String?,
        val powerSource: PowerSource,
        val backhaul: BackhaulType,
        val typicalDeployment: List<String>,
        val physicalDescription: String,
        val detectionMethods: List<String>,
        val dataCollected: List<String>,
        val batteryLife: String?, // null for powered devices
        val commonLocations: List<String>,
        val legalStatus: String,
        val threatScore: Int
    )

    enum class GpsTrackerCategory(val displayName: String) {
        OBD_II_PORT("OBD-II Port Tracker"),
        MAGNETIC("Magnetic/Battery Tracker"),
        HARDWIRED("Hardwired Tracker"),
        PERSONAL("Personal/Asset Tracker"),
        FLEET("Fleet Management")
    }

    enum class PowerSource(val displayName: String) {
        OBD_VEHICLE_POWER("OBD-II Vehicle Power (always on)"),
        VEHICLE_HARDWIRE("Hardwired to Vehicle (always on)"),
        INTERNAL_BATTERY("Internal Battery"),
        BATTERY_WITH_SOLAR("Battery + Solar"),
        EXTERNAL_BATTERY_PACK("External Battery Pack")
    }

    enum class BackhaulType(val displayName: String) {
        CELLULAR_4G("4G LTE Cellular"),
        CELLULAR_3G("3G Cellular"),
        CELLULAR_2G("2G GSM (older)"),
        WIFI("WiFi (config only)"),
        BLUETOOTH("Bluetooth (proximity only)"),
        LORA("LoRa (long range, low power)"),
        SATELLITE("Satellite (Iridium/Globalstar)")
    }

    val gpsTrackerProfiles = listOf(
        // OBD-II Port Trackers
        GpsTrackerProfile(
            id = "bouncie",
            name = "Bouncie GPS Tracker",
            category = GpsTrackerCategory.OBD_II_PORT,
            manufacturer = "Bouncie",
            powerSource = PowerSource.OBD_VEHICLE_POWER,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Consumer vehicle tracking",
                "Teen driver monitoring",
                "Fleet management"
            ),
            physicalDescription = "Small OBD-II plug-in device, usually blue or black, ~2x3 inches",
            detectionMethods = listOf(
                "Check OBD-II port under dashboard (driver's side)",
                "Look for small device plugged into diagnostic port",
                "Use OBD-II scanner to detect unknown device",
                "Check for cellular signal near OBD port area"
            ),
            dataCollected = listOf(
                "GPS location (real-time)",
                "Vehicle speed",
                "Trip history",
                "Rapid acceleration/braking events",
                "Vehicle diagnostics (DTCs)",
                "Geofence alerts"
            ),
            batteryLife = null, // Vehicle powered
            commonLocations = listOf("OBD-II port under driver's side dashboard"),
            legalStatus = "Legal for vehicle owners; requires consent for others",
            threatScore = 75
        ),

        GpsTrackerProfile(
            id = "vyncs",
            name = "Vyncs GPS Tracker",
            category = GpsTrackerCategory.OBD_II_PORT,
            manufacturer = "Vyncs (Agnik)",
            powerSource = PowerSource.OBD_VEHICLE_POWER,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Consumer vehicle tracking",
                "Insurance telematics",
                "Parental monitoring"
            ),
            physicalDescription = "OBD-II dongle, black plastic, ~2x2 inches",
            detectionMethods = listOf(
                "Visual inspection of OBD-II port",
                "Device has LED indicators when active",
                "Check for unfamiliar device in vehicle"
            ),
            dataCollected = listOf(
                "GPS location",
                "Driving behavior analytics",
                "Vehicle health data",
                "Fuel economy",
                "Trip summaries"
            ),
            batteryLife = null,
            commonLocations = listOf("OBD-II port"),
            legalStatus = "Legal for vehicle owners",
            threatScore = 70
        ),

        GpsTrackerProfile(
            id = "motosafety",
            name = "MOTOsafety GPS Tracker",
            category = GpsTrackerCategory.OBD_II_PORT,
            manufacturer = "MOTOsafety",
            powerSource = PowerSource.OBD_VEHICLE_POWER,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Teen driver monitoring",
                "Business fleet tracking",
                "Family vehicle monitoring"
            ),
            physicalDescription = "OBD-II plug device, branded logo visible",
            detectionMethods = listOf(
                "Inspect OBD-II port",
                "Look for 'MOTOsafety' branding",
                "Check mobile app stores for active subscriptions"
            ),
            dataCollected = listOf(
                "Real-time GPS tracking",
                "Speed alerts",
                "Curfew violations",
                "Rapid acceleration/braking",
                "Idle time"
            ),
            batteryLife = null,
            commonLocations = listOf("OBD-II port"),
            legalStatus = "Legal for vehicle owners; consent required for non-owners",
            threatScore = 70
        ),

        // Magnetic GPS Trackers
        GpsTrackerProfile(
            id = "landairsea_overdrive",
            name = "LandAirSea Overdrive",
            category = GpsTrackerCategory.MAGNETIC,
            manufacturer = "LandAirSea",
            powerSource = PowerSource.INTERNAL_BATTERY,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Private investigation",
                "Law enforcement (with warrant)",
                "Asset tracking",
                "Stalking (illegal use)"
            ),
            physicalDescription = "Small black box, ~3x2x1 inches, strong magnet on bottom, waterproof",
            detectionMethods = listOf(
                "Physical search of vehicle undercarriage",
                "Check wheel wells (all four)",
                "Inspect behind bumpers",
                "Look in trunk spare tire area",
                "Use flashlight to check frame rails",
                "Feel for magnetic attachment points"
            ),
            dataCollected = listOf(
                "GPS location (configurable intervals)",
                "Movement history",
                "Geofence alerts",
                "Speed tracking",
                "Battery level"
            ),
            batteryLife = "Up to 2 weeks active / 6 months standby",
            commonLocations = listOf(
                "Under vehicle frame",
                "Inside wheel wells",
                "Behind bumpers (front/rear)",
                "Under trunk/cargo area",
                "Attached to metal frame components"
            ),
            legalStatus = "Requires warrant for law enforcement; illegal for unauthorized tracking",
            threatScore = 85
        ),

        GpsTrackerProfile(
            id = "spytec_gl300",
            name = "SpyTec GL300",
            category = GpsTrackerCategory.MAGNETIC,
            manufacturer = "SpyTec",
            powerSource = PowerSource.INTERNAL_BATTERY,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Personal asset tracking",
                "Vehicle tracking",
                "Private investigation",
                "Rental car monitoring"
            ),
            physicalDescription = "Small rectangular device, ~3x1.5x1 inches, waterproof case available",
            detectionMethods = listOf(
                "Thorough vehicle search",
                "Magnetic sweep of undercarriage",
                "Check all hidden compartments",
                "Inspect wheel wells"
            ),
            dataCollected = listOf(
                "Real-time GPS location",
                "Historical tracking data",
                "Geofence alerts",
                "Speed monitoring"
            ),
            batteryLife = "Up to 2.5 weeks",
            commonLocations = listOf(
                "Vehicle undercarriage",
                "Wheel wells",
                "Inside personal bags/belongings"
            ),
            legalStatus = "Legal for own property; illegal for tracking others without consent",
            threatScore = 80
        ),

        GpsTrackerProfile(
            id = "optimus_2",
            name = "Optimus 2.0 GPS Tracker",
            category = GpsTrackerCategory.MAGNETIC,
            manufacturer = "Optimus",
            powerSource = PowerSource.INTERNAL_BATTERY,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Vehicle tracking",
                "Teen monitoring",
                "Asset protection"
            ),
            physicalDescription = "Compact black device with magnetic case option",
            detectionMethods = listOf(
                "Physical vehicle inspection",
                "Magnetic wand sweep",
                "Check common hiding spots"
            ),
            dataCollected = listOf(
                "GPS coordinates",
                "Speed",
                "Trip history",
                "SOS alerts"
            ),
            batteryLife = "Up to 2 weeks",
            commonLocations = listOf(
                "Under vehicle",
                "In wheel wells",
                "Attached to frame"
            ),
            legalStatus = "Requires consent for tracking individuals",
            threatScore = 80
        ),

        // Hardwired Trackers
        GpsTrackerProfile(
            id = "fleet_hardwired",
            name = "Hardwired Fleet Tracker",
            category = GpsTrackerCategory.HARDWIRED,
            manufacturer = null,
            powerSource = PowerSource.VEHICLE_HARDWIRE,
            backhaul = BackhaulType.CELLULAR_4G,
            typicalDeployment = listOf(
                "Commercial fleet management",
                "Professional installation",
                "Long-term vehicle tracking",
                "Law enforcement (with warrant)"
            ),
            physicalDescription = "Small black box connected to vehicle wiring, often hidden in dashboard or under seats",
            detectionMethods = listOf(
                "Professional TSCM sweep",
                "Check for non-factory wiring",
                "Inspect under dashboard",
                "Look behind panels",
                "Trace unusual wires to hidden devices",
                "Use RF detector near wiring harness"
            ),
            dataCollected = listOf(
                "Continuous GPS tracking",
                "Vehicle ignition status",
                "Mileage",
                "Driver behavior",
                "Engine diagnostics"
            ),
            batteryLife = null, // Hardwired
            commonLocations = listOf(
                "Behind dashboard panels",
                "Under driver/passenger seats",
                "In center console",
                "Near OBD-II port (tapped into)",
                "Inside door panels"
            ),
            legalStatus = "Professional installation; requires consent or warrant",
            threatScore = 90
        )
    )

    // ==================== DRONE DETECTION PROFILES ====================

    /**
     * Comprehensive drone profiles with Remote ID information.
     * Remote ID became mandatory in the US starting September 2023.
     */
    data class DroneProfile(
        val id: String,
        val name: String,
        val manufacturer: String,
        val category: DroneCategory,
        val controlFrequencies: List<Long>,
        val controlProtocol: String,
        val hasRemoteId: Boolean, // Required since Sept 2023 in US
        val remoteIdBroadcast: RemoteIdBroadcastType?,
        val wifiPatterns: List<String>,
        val blePatterns: List<String>,
        val ouiPrefixes: List<String>,
        val typicalRange: String,
        val suspiciousIndicators: List<String>,
        val legalRequirements: List<String>
    )

    enum class DroneCategory(val displayName: String) {
        CONSUMER("Consumer/Prosumer"),
        COMMERCIAL("Commercial"),
        RACING_FPV("Racing/FPV"),
        ENTERPRISE("Enterprise"),
        GOVERNMENT("Government/Law Enforcement"),
        DIY("DIY/Custom Built")
    }

    enum class RemoteIdBroadcastType(val displayName: String) {
        WIFI_BEACON("WiFi Beacon Advertisement"),
        BLUETOOTH_5("Bluetooth 5 Long Range"),
        BOTH("WiFi + Bluetooth"),
        NONE("No Remote ID (illegal in US since 9/2023)")
    }

    val droneProfiles = listOf(
        DroneProfile(
            id = "dji_mavic",
            name = "DJI Mavic Series",
            manufacturer = "DJI",
            category = DroneCategory.CONSUMER,
            controlFrequencies = listOf(2_400_000_000L, 5_800_000_000L),
            controlProtocol = "OcuSync 2.0/3.0 (proprietary)",
            hasRemoteId = true,
            remoteIdBroadcast = RemoteIdBroadcastType.WIFI_BEACON,
            wifiPatterns = listOf("(?i)^mavic[-_]?(pro|air|mini|[0-9]).*"),
            blePatterns = listOf("(?i)^(mavic|dji)[-_]?.*"),
            ouiPrefixes = listOf("60:60:1F", "34:D2:62", "48:1C:B9", "60:C7:98"),
            typicalRange = "Up to 10km (OcuSync 3.0)",
            suspiciousIndicators = listOf(
                "Hovering over private property",
                "Following specific person/vehicle",
                "Operating at night without lights",
                "No Remote ID broadcast (illegal)",
                "Operating near airports/restricted areas"
            ),
            legalRequirements = listOf(
                "Remote ID broadcast required (US since 9/2023)",
                "FAA registration required for >250g",
                "Visual line of sight required (unless waiver)",
                "Cannot fly over people without certification"
            )
        ),

        DroneProfile(
            id = "dji_phantom",
            name = "DJI Phantom Series",
            manufacturer = "DJI",
            category = DroneCategory.CONSUMER,
            controlFrequencies = listOf(2_400_000_000L, 5_800_000_000L),
            controlProtocol = "Lightbridge/OcuSync",
            hasRemoteId = true,
            remoteIdBroadcast = RemoteIdBroadcastType.WIFI_BEACON,
            wifiPatterns = listOf("(?i)^phantom[-_]?[0-9].*"),
            blePatterns = listOf("(?i)^phantom.*"),
            ouiPrefixes = listOf("60:60:1F", "60:C7:98"),
            typicalRange = "Up to 7km",
            suspiciousIndicators = listOf(
                "Large drone near residence",
                "Extended hovering",
                "Camera pointed at windows"
            ),
            legalRequirements = listOf(
                "Remote ID required",
                "FAA registration required"
            )
        ),

        DroneProfile(
            id = "autel_evo",
            name = "Autel EVO Series",
            manufacturer = "Autel Robotics",
            category = DroneCategory.CONSUMER,
            controlFrequencies = listOf(2_400_000_000L, 5_800_000_000L),
            controlProtocol = "Autel SkyLink",
            hasRemoteId = true,
            remoteIdBroadcast = RemoteIdBroadcastType.WIFI_BEACON,
            wifiPatterns = listOf("(?i)^(autel|evo)[-_]?(ii|2|lite).*"),
            blePatterns = listOf("(?i)^autel.*"),
            ouiPrefixes = listOf(), // Add when known
            typicalRange = "Up to 9km",
            suspiciousIndicators = listOf(
                "US-made drone alternative to DJI",
                "May be used by government/enterprises"
            ),
            legalRequirements = listOf(
                "Remote ID required",
                "FAA registration required"
            )
        ),

        DroneProfile(
            id = "skydio",
            name = "Skydio Drones",
            manufacturer = "Skydio (USA)",
            category = DroneCategory.ENTERPRISE,
            controlFrequencies = listOf(2_400_000_000L, 5_800_000_000L),
            controlProtocol = "Skydio Autonomy",
            hasRemoteId = true,
            remoteIdBroadcast = RemoteIdBroadcastType.BOTH,
            wifiPatterns = listOf("(?i)^skydio[-_]?[0-9x].*"),
            blePatterns = listOf("(?i)^skydio.*"),
            ouiPrefixes = listOf(),
            typicalRange = "Up to 6km",
            suspiciousIndicators = listOf(
                "US-made autonomous drone",
                "Used by law enforcement/military",
                "Autonomous following capability"
            ),
            legalRequirements = listOf(
                "Remote ID required",
                "Often used by government agencies"
            )
        ),

        DroneProfile(
            id = "parrot_anafi",
            name = "Parrot Anafi",
            manufacturer = "Parrot (France)",
            category = DroneCategory.CONSUMER,
            controlFrequencies = listOf(2_400_000_000L, 5_800_000_000L),
            controlProtocol = "WiFi Direct",
            hasRemoteId = true,
            remoteIdBroadcast = RemoteIdBroadcastType.WIFI_BEACON,
            wifiPatterns = listOf("(?i)^(parrot|anafi|bebop)[-_]?.*"),
            blePatterns = listOf("(?i)^parrot.*"),
            ouiPrefixes = listOf("A0:14:3D", "90:03:B7", "00:12:1C", "00:26:7E"),
            typicalRange = "Up to 4km",
            suspiciousIndicators = listOf(
                "French-made drone",
                "Thermal imaging variants exist"
            ),
            legalRequirements = listOf(
                "Remote ID required in US",
                "CE marking for EU operation"
            )
        )
    )

    /**
     * Remote ID information - broadcasts from drones since Sept 2023.
     * Per FAA regulations, drones must broadcast this information.
     */
    data class RemoteIdInfo(
        val serialNumber: String?,      // Drone serial number
        val latitude: Double?,          // Drone current location
        val longitude: Double?,
        val altitude: Double?,          // Altitude in meters
        val speed: Double?,             // Ground speed
        val heading: Double?,           // Direction of travel
        val operatorLatitude: Double?,  // Operator/controller location
        val operatorLongitude: Double?,
        val timestamp: Long,
        val emergencyStatus: Boolean
    )

    /**
     * Check Remote ID apps for drone detection:
     * - DroneScout (available on iOS/Android)
     * - OpenDroneID (open source reference)
     * - AirMap
     */
    val remoteIdDetectionApps = listOf(
        "DroneScout" to "Official FAA-recommended app",
        "OpenDroneID" to "Open source reference implementation",
        "AirMap" to "Commercial drone airspace management"
    )

    // ==================== JAMMING DEVICE SIGNATURES ====================

    /**
     * RF jamming device signatures and detection patterns.
     * NOTE: Jamming devices are ILLEGAL to operate in the US (FCC violation).
     */
    data class JammerProfile(
        val id: String,
        val name: String,
        val targetedBands: List<JammedBand>,
        val detectionSigns: List<String>,
        val typicalUsers: List<String>,
        val legalStatus: String,
        val countermeasures: List<String>
    )

    data class JammedBand(
        val name: String,
        val frequencyRange: String,
        val affectedServices: List<String>
    )

    val jammerProfiles = listOf(
        JammerProfile(
            id = "gps_jammer",
            name = "GPS/GNSS Jammer",
            targetedBands = listOf(
                JammedBand("GPS L1", "1575.42 MHz", listOf("GPS navigation", "Fleet tracking", "Timing systems")),
                JammedBand("GPS L2", "1227.60 MHz", listOf("Precision GPS", "Survey equipment")),
                JammedBand("GLONASS L1", "1602 MHz", listOf("Russian navigation"))
            ),
            detectionSigns = listOf(
                "Sudden GPS signal loss while stationary",
                "GPS 'jumps' or erratic position",
                "GNSS receiver reports no satellites",
                "Multiple devices lose GPS simultaneously",
                "GPS accuracy degrades dramatically"
            ),
            typicalUsers = listOf(
                "Truckers avoiding fleet tracking (illegal)",
                "Criminals avoiding location tracking",
                "Car thieves defeating GPS trackers"
            ),
            legalStatus = "ILLEGAL in US (FCC violation) - up to \$100K fine + criminal charges",
            countermeasures = listOf(
                "Note time and location of jamming",
                "Report to FCC if persistent",
                "Use cellular-based tracking as backup",
                "Professional TSCM equipment can locate jammer"
            )
        ),

        JammerProfile(
            id = "cell_jammer",
            name = "Cellular Phone Jammer",
            targetedBands = listOf(
                JammedBand("2G/GSM", "850/900/1800/1900 MHz", listOf("2G voice calls", "SMS")),
                JammedBand("3G/UMTS", "850/1900/2100 MHz", listOf("3G calls/data")),
                JammedBand("4G/LTE", "700-2600 MHz", listOf("LTE data/calls", "Most smartphones"))
            ),
            detectionSigns = listOf(
                "All phones lose signal simultaneously",
                "Phones show 'No Service' or 'Emergency Only'",
                "Calls drop immediately when entering area",
                "Data connections fail completely",
                "Multiple carriers affected simultaneously"
            ),
            typicalUsers = listOf(
                "Prisons (with legal waiver)",
                "Theaters (ILLEGALLY)",
                "Criminals during robberies",
                "Exam cheating prevention (illegal)"
            ),
            legalStatus = "ILLEGAL in US except for federal government with waiver",
            countermeasures = listOf(
                "Leave the area if possible",
                "Use WiFi calling if WiFi is available",
                "Note location for reporting to FCC",
                "Cannot make 911 calls in jammed area - DANGER"
            )
        ),

        JammerProfile(
            id = "wifi_jammer",
            name = "WiFi Jammer",
            targetedBands = listOf(
                JammedBand("2.4 GHz", "2400-2483 MHz", listOf("WiFi", "Bluetooth", "IoT devices")),
                JammedBand("5 GHz", "5150-5850 MHz", listOf("WiFi 5/6", "FPV drones"))
            ),
            detectionSigns = listOf(
                "Massive deauth packets on all channels",
                "All WiFi networks become unreachable",
                "Bluetooth devices disconnect",
                "Smart home devices go offline",
                "Persistent interference across all channels"
            ),
            typicalUsers = listOf(
                "Criminals defeating security cameras",
                "Burglars disabling WiFi alarms",
                "Corporate espionage"
            ),
            legalStatus = "ILLEGAL - FCC violation",
            countermeasures = listOf(
                "Use wired security cameras as backup",
                "Local recording (SD card) for cameras",
                "Cellular backup for alarm systems",
                "Report persistent jamming to FCC"
            )
        )
    )

    // ==================== PROFESSIONAL SURVEILLANCE EQUIPMENT ====================

    /**
     * Professional-grade surveillance equipment profiles.
     * Used by law enforcement, private investigators, and TSCM professionals.
     */
    data class ProfessionalSurveillanceProfile(
        val id: String,
        val name: String,
        val category: ProfessionalCategory,
        val manufacturer: String?,
        val typicalUsers: List<String>,
        val detectionMethods: List<String>,
        val dataCollected: List<String>,
        val legalFramework: String,
        val rfCharacteristics: RfCharacteristics?
    )

    enum class ProfessionalCategory(val displayName: String) {
        LAW_ENFORCEMENT_TRACKER("Law Enforcement GPS Tracker"),
        AUDIO_BUG("Audio Transmitter/Bug"),
        GSM_BUG("GSM/Cellular Bug"),
        TSCM_EQUIPMENT("TSCM Sweep Equipment"),
        FORENSIC_TOOL("Digital Forensics Tool")
    }

    data class RfCharacteristics(
        val frequencyBands: List<String>,
        val modulationType: String?,
        val transmitPower: String?,
        val detectionDifficulty: String
    )

    val professionalSurveillanceProfiles = listOf(
        ProfessionalSurveillanceProfile(
            id = "le_vehicle_tracker",
            name = "Law Enforcement Vehicle Tracker",
            category = ProfessionalCategory.LAW_ENFORCEMENT_TRACKER,
            manufacturer = null, // Various
            typicalUsers = listOf(
                "FBI",
                "DEA",
                "State/Local Law Enforcement",
                "Federal agencies"
            ),
            detectionMethods = listOf(
                "Professional TSCM vehicle sweep",
                "RF detector sweep of vehicle",
                "Physical inspection by trained professional",
                "Check for unusual cellular activity from vehicle",
                "Look for magnetic devices under vehicle"
            ),
            dataCollected = listOf(
                "Real-time GPS location",
                "Historical movement patterns",
                "Speed and direction",
                "Stop locations and duration",
                "Pattern of life analysis"
            ),
            legalFramework = "Requires court-approved warrant (US v. Jones 2012)",
            rfCharacteristics = RfCharacteristics(
                frequencyBands = listOf("4G LTE cellular bands"),
                modulationType = "Cellular protocol",
                transmitPower = "Low (battery conservation)",
                detectionDifficulty = "High - professional sweep required"
            )
        ),

        ProfessionalSurveillanceProfile(
            id = "uhf_audio_bug",
            name = "UHF Audio Transmitter (Bug)",
            category = ProfessionalCategory.AUDIO_BUG,
            manufacturer = null,
            typicalUsers = listOf(
                "Law enforcement (with warrant)",
                "Corporate espionage (illegal)",
                "Private investigators",
                "Stalkers (illegal)"
            ),
            detectionMethods = listOf(
                "Sweep with RF detector in 300-500 MHz range",
                "Non-linear junction detector (NLJD)",
                "Physical search for hidden devices",
                "Check power outlets, lamps, smoke detectors",
                "Thermal imaging for active electronics"
            ),
            dataCollected = listOf(
                "Audio conversations",
                "Ambient sounds",
                "Voice recordings"
            ),
            legalFramework = "Wiretapping laws vary by state; generally requires warrant or consent",
            rfCharacteristics = RfCharacteristics(
                frequencyBands = listOf("300-500 MHz (UHF)"),
                modulationType = "FM/AM narrowband",
                transmitPower = "10-100 mW typically",
                detectionDifficulty = "Medium - RF sweep can detect active transmission"
            )
        ),

        ProfessionalSurveillanceProfile(
            id = "vhf_audio_bug",
            name = "VHF Audio Transmitter",
            category = ProfessionalCategory.AUDIO_BUG,
            manufacturer = null,
            typicalUsers = listOf(
                "Older surveillance equipment",
                "Budget devices"
            ),
            detectionMethods = listOf(
                "RF detector sweep in 100-300 MHz range",
                "Broadband receiver scan",
                "Physical inspection"
            ),
            dataCollected = listOf(
                "Audio conversations"
            ),
            legalFramework = "Wiretapping laws apply",
            rfCharacteristics = RfCharacteristics(
                frequencyBands = listOf("100-300 MHz (VHF)"),
                modulationType = "FM typically",
                transmitPower = "Variable",
                detectionDifficulty = "Medium-Low - older technology, easier to detect"
            )
        ),

        ProfessionalSurveillanceProfile(
            id = "gsm_bug",
            name = "GSM/Cellular Audio Bug",
            category = ProfessionalCategory.GSM_BUG,
            manufacturer = null,
            typicalUsers = listOf(
                "Surveillance professionals",
                "Corporate espionage",
                "Private investigators"
            ),
            detectionMethods = listOf(
                "Call the room and listen for ringtone",
                "RF detector checking for cellular activity",
                "Cell network analyzer looking for unknown devices",
                "Physical search of room",
                "Check for SIM cards in unusual objects"
            ),
            dataCollected = listOf(
                "Audio - caller can listen remotely",
                "Some models have GPS",
                "Can be activated remotely via phone call"
            ),
            legalFramework = "Wiretapping laws apply; illegal without consent/warrant",
            rfCharacteristics = RfCharacteristics(
                frequencyBands = listOf("Cellular bands (850/900/1800/1900 MHz)"),
                modulationType = "GSM/LTE cellular",
                transmitPower = "Standard cellular",
                detectionDifficulty = "High - only transmits when activated"
            )
        ),

        ProfessionalSurveillanceProfile(
            id = "tscm_equipment",
            name = "TSCM Sweep Equipment",
            category = ProfessionalCategory.TSCM_EQUIPMENT,
            manufacturer = "REI, JJN Digital, Others",
            typicalUsers = listOf(
                "TSCM professionals",
                "Corporate security",
                "Government counter-intelligence"
            ),
            detectionMethods = listOf(
                "If detected, may indicate sweep in progress",
                "Look for professional vehicles/personnel",
                "High-end RF detection equipment signatures"
            ),
            dataCollected = listOf(
                "Detects bugs, not collects data",
                "Spectrum analysis",
                "NLJD signatures"
            ),
            legalFramework = "Legal for authorized security sweeps",
            rfCharacteristics = RfCharacteristics(
                frequencyBands = listOf("Wideband receivers", "0-6 GHz coverage typically"),
                modulationType = "Receiver only",
                transmitPower = "N/A - detection equipment",
                detectionDifficulty = "N/A"
            )
        )
    )

    // ==================== SMART HOME / IoT SECURITY PATTERNS ====================

    /**
     * Extended smart home and IoT security profiles with privacy context.
     */
    data class SmartHomeSecurityProfile(
        val id: String,
        val name: String,
        val manufacturer: String,
        val deviceType: SmartHomeDeviceType,
        val wifiCharacteristics: WifiCharacteristics,
        val bleCharacteristics: BleCharacteristics?,
        val cloudDependency: CloudDependency,
        val lawEnforcementAccess: LawEnforcementAccessProfile,
        val privacyConcerns: List<String>,
        val networkPatterns: List<String>
    )

    enum class SmartHomeDeviceType(val displayName: String) {
        VIDEO_DOORBELL("Video Doorbell"),
        SECURITY_CAMERA("Security Camera"),
        MESH_NETWORK("Mesh Network/Sidewalk"),
        SMART_SPEAKER("Smart Speaker"),
        SMART_LOCK("Smart Lock"),
        BABY_MONITOR("Baby Monitor"),
        MATTER_THREAD("Matter/Thread Device")
    }

    data class WifiCharacteristics(
        val ssidPatterns: List<String>,
        val macPrefixes: List<String>,
        val defaultPorts: List<Int>
    )

    data class BleCharacteristics(
        val namePatterns: List<String>,
        val serviceUuids: List<String>
    )

    enum class CloudDependency(val displayName: String, val description: String) {
        MANDATORY("Cloud Required", "Device requires cloud connection to function"),
        OPTIONAL("Cloud Optional", "Local control possible but cloud available"),
        LOCAL_ONLY("Local Only", "No cloud connectivity - fully local"),
        HYBRID("Hybrid", "Some features require cloud, basic function local")
    }

    data class LawEnforcementAccessProfile(
        val hasPartnership: Boolean,
        val partnershipDetails: String?,
        val canRequestWithoutWarrant: Boolean,
        val ownerNotified: Boolean
    )

    val smartHomeSecurityProfiles = listOf(
        SmartHomeSecurityProfile(
            id = "ring_doorbell",
            name = "Ring Video Doorbell",
            manufacturer = "Ring (Amazon)",
            deviceType = SmartHomeDeviceType.VIDEO_DOORBELL,
            wifiCharacteristics = WifiCharacteristics(
                ssidPatterns = listOf("(?i)^ring[_-]?(doorbell|cam|setup|stick).*"),
                macPrefixes = listOf("44:73:D6", "18:B4:30", "0C:47:C9"),
                defaultPorts = listOf(443, 9999)
            ),
            bleCharacteristics = BleCharacteristics(
                namePatterns = listOf("(?i)^ring[_-]?.*"),
                serviceUuids = listOf()
            ),
            cloudDependency = CloudDependency.MANDATORY,
            lawEnforcementAccess = LawEnforcementAccessProfile(
                hasPartnership = true,
                partnershipDetails = "Partners with 2,500+ police departments via Neighbors app. " +
                    "Police can request footage through Neighbors Public Safety Service.",
                canRequestWithoutWarrant = true,
                ownerNotified = false // Under new policy (2022), Ring requires warrant unless emergency
            ),
            privacyConcerns = listOf(
                "Footage shared with 2,500+ law enforcement agencies",
                "Neighbors app creates surveillance network",
                "Audio recording range: 15-25 feet",
                "Video recording: 180 degree view",
                "Cloud storage on Amazon servers",
                "Facial recognition capability (Neighbors app)",
                "Always-on microphone"
            ),
            networkPatterns = listOf(
                "Constant outbound connection to Ring servers",
                "Video upload on motion detection",
                "Periodic health check packets"
            )
        ),

        SmartHomeSecurityProfile(
            id = "amazon_sidewalk",
            name = "Amazon Sidewalk Network",
            manufacturer = "Amazon",
            deviceType = SmartHomeDeviceType.MESH_NETWORK,
            wifiCharacteristics = WifiCharacteristics(
                ssidPatterns = listOf("(?i)^(amazon[_-]?sidewalk|sidewalk[_-]?bridge).*"),
                macPrefixes = listOf("44:73:D6", "18:B4:30"),
                defaultPorts = listOf()
            ),
            bleCharacteristics = BleCharacteristics(
                namePatterns = listOf("(?i)^sidewalk.*"),
                serviceUuids = listOf()
            ),
            cloudDependency = CloudDependency.MANDATORY,
            lawEnforcementAccess = LawEnforcementAccessProfile(
                hasPartnership = true,
                partnershipDetails = "Part of Amazon ecosystem with Ring partnerships",
                canRequestWithoutWarrant = false,
                ownerNotified = true
            ),
            privacyConcerns = listOf(
                "Uses 900 MHz (LoRa) and Bluetooth for mesh network",
                "Borrows bandwidth from Ring/Echo devices",
                "Can track Tile trackers via network",
                "Privacy: Shared with neighbors' devices",
                "Creates neighborhood-wide tracking network",
                "Low-bandwidth but persistent connectivity"
            ),
            networkPatterns = listOf(
                "900 MHz LoRa transmissions",
                "BLE advertisements",
                "Mesh routing between Amazon devices"
            )
        ),

        SmartHomeSecurityProfile(
            id = "matter_thread",
            name = "Matter/Thread Device",
            manufacturer = "Various (Standard)",
            deviceType = SmartHomeDeviceType.MATTER_THREAD,
            wifiCharacteristics = WifiCharacteristics(
                ssidPatterns = listOf(),
                macPrefixes = listOf(),
                defaultPorts = listOf(5540) // Matter port
            ),
            bleCharacteristics = BleCharacteristics(
                namePatterns = listOf(),
                serviceUuids = listOf("FFF6") // Matter commissioning
            ),
            cloudDependency = CloudDependency.OPTIONAL,
            lawEnforcementAccess = LawEnforcementAccessProfile(
                hasPartnership = false,
                partnershipDetails = "Local control standard - varies by manufacturer",
                canRequestWithoutWarrant = false,
                ownerNotified = true
            ),
            privacyConcerns = listOf(
                "New smart home standard (2022+)",
                "Uses 2.4 GHz (Thread) or WiFi",
                "Local control possible - more private than cloud-dependent",
                "Interoperability across ecosystems",
                "Privacy depends on manufacturer implementation"
            ),
            networkPatterns = listOf(
                "Thread mesh on 2.4 GHz (IEEE 802.15.4)",
                "Matter over WiFi",
                "Local mDNS/DNS-SD discovery"
            )
        )
    )

    // ==================== LAW ENFORCEMENT EQUIPMENT PROFILES ====================

    /**
     * Detailed law enforcement equipment profiles for detection.
     */
    data class LawEnforcementEquipmentProfile(
        val id: String,
        val name: String,
        val category: LEEquipmentCategory,
        val manufacturer: String,
        val wifiPatterns: List<String>,
        val blePatterns: List<String>,
        val macPrefixes: List<String>,
        val capabilities: List<String>,
        val dataUploaded: String,
        val detectionIndicators: List<String>
    )

    enum class LEEquipmentCategory(val displayName: String) {
        BODY_CAMERA("Body Worn Camera"),
        IN_CAR_VIDEO("In-Car Video System"),
        LPR_ALPR("License Plate Reader"),
        RADIO_SYSTEM("Radio Communication"),
        FORENSIC_TOOL("Forensic Tool")
    }

    val lawEnforcementEquipmentProfiles = listOf(
        // Body Worn Cameras
        LawEnforcementEquipmentProfile(
            id = "axon_body_3",
            name = "Axon Body 3/4",
            category = LEEquipmentCategory.BODY_CAMERA,
            manufacturer = "Axon Enterprise",
            wifiPatterns = listOf("(?i)^axon[_-]?(body|signal).*", "(?i)^ab[234].*"),
            blePatterns = listOf("(?i)^axon.*", "(?i)^(body|flex)[_-]?[234].*"),
            macPrefixes = listOf(), // Nordic Semiconductor typically
            capabilities = listOf(
                "HD video recording",
                "GPS location logging",
                "Automatic activation via Axon Signal",
                "Real-time streaming (Axon Respond)",
                "WiFi and LTE upload",
                "10+ hour battery life"
            ),
            dataUploaded = "Evidence.com cloud storage",
            detectionIndicators = listOf(
                "BLE beacon for Axon Signal triggers",
                "WiFi connection for docking/upload",
                "Axon-specific BLE service UUIDs"
            )
        ),

        LawEnforcementEquipmentProfile(
            id = "motorola_si500",
            name = "Motorola Si500",
            category = LEEquipmentCategory.BODY_CAMERA,
            manufacturer = "Motorola Solutions",
            wifiPatterns = listOf("(?i)^(moto|si)[_-]?500.*", "(?i)^v[35]00.*"),
            blePatterns = listOf("(?i)^(si|v)[0-9]+.*"),
            macPrefixes = listOf(),
            capabilities = listOf(
                "Video recording",
                "WiFi upload",
                "BLE connectivity",
                "Integration with Motorola radios"
            ),
            dataUploaded = "CommandCentral Vault",
            detectionIndicators = listOf(
                "Motorola BLE advertising",
                "WiFi upload patterns"
            )
        ),

        LawEnforcementEquipmentProfile(
            id = "digital_ally_firstvu",
            name = "Digital Ally FirstVU",
            category = LEEquipmentCategory.BODY_CAMERA,
            manufacturer = "Digital Ally",
            wifiPatterns = listOf("(?i)^(digital[_-]?ally|firstvu).*"),
            blePatterns = listOf("(?i)^(da|firstvu).*"),
            macPrefixes = listOf(),
            capabilities = listOf(
                "HD video",
                "WiFi upload",
                "Cloud storage"
            ),
            dataUploaded = "Digital Ally cloud",
            detectionIndicators = listOf(
                "WiFi direct for upload",
                "Digital Ally branding in SSID"
            )
        ),

        LawEnforcementEquipmentProfile(
            id = "watchguard_4re",
            name = "WatchGuard 4RE",
            category = LEEquipmentCategory.IN_CAR_VIDEO,
            manufacturer = "Motorola Solutions (WatchGuard)",
            wifiPatterns = listOf("(?i)^watchguard.*", "(?i)^4re.*"),
            blePatterns = listOf("(?i)^watchguard.*"),
            macPrefixes = listOf(),
            capabilities = listOf(
                "Multiple camera angles",
                "In-car video recording",
                "Body camera integration",
                "WiFi/cellular upload",
                "Automatic trigger on lights/siren"
            ),
            dataUploaded = "Evidence Library cloud",
            detectionIndicators = listOf(
                "WiFi AP in patrol vehicle",
                "Multiple camera streams",
                "Integration with body cameras"
            )
        ),

        // License Plate Readers
        LawEnforcementEquipmentProfile(
            id = "vigilant_motorola",
            name = "Vigilant ALPR (Motorola)",
            category = LEEquipmentCategory.LPR_ALPR,
            manufacturer = "Motorola Solutions",
            wifiPatterns = listOf("(?i)^vigilant.*"),
            blePatterns = listOf(),
            macPrefixes = listOf(),
            capabilities = listOf(
                "Mobile and fixed ALPR",
                "Real-time plate lookups",
                "Integration with LEARN database",
                "Hot list alerts"
            ),
            dataUploaded = "Vigilant LEARN database",
            detectionIndicators = listOf(
                "WiFi upload from mobile unit",
                "Cellular connectivity",
                "IR illumination visible at night"
            )
        ),

        LawEnforcementEquipmentProfile(
            id = "flock_safety_lpr",
            name = "Flock Safety ALPR",
            category = LEEquipmentCategory.LPR_ALPR,
            manufacturer = "Flock Safety",
            wifiPatterns = listOf("(?i)^flock.*", "(?i)^falcon.*", "(?i)^sparrow.*", "(?i)^condor.*"),
            blePatterns = listOf("(?i)^flock.*"),
            macPrefixes = listOf("50:29:4D", "86:25:19"), // Quectel modems
            capabilities = listOf(
                "License plate capture",
                "Vehicle fingerprint (make/model/color)",
                "Direction of travel",
                "Real-time alerts",
                "Cross-jurisdiction sharing"
            ),
            dataUploaded = "Flock Safety cloud (30-day retention typically)",
            detectionIndicators = listOf(
                "Solar-powered pole mount",
                "Quectel LTE modem OUI",
                "Flock SSID patterns"
            )
        ),

        LawEnforcementEquipmentProfile(
            id = "elsag_alpr",
            name = "ELSAG ALPR",
            category = LEEquipmentCategory.LPR_ALPR,
            manufacturer = "Leonardo DRS",
            wifiPatterns = listOf("(?i)^elsag.*"),
            blePatterns = listOf(),
            macPrefixes = listOf(),
            capabilities = listOf(
                "Mobile ALPR on patrol vehicles",
                "Fixed position cameras",
                "EOC integration"
            ),
            dataUploaded = "ELSAG Enterprise Operations Center",
            detectionIndicators = listOf(
                "Mounted on patrol vehicle",
                "Multiple cameras per vehicle",
                "IR flash at night"
            )
        )
    )

    // ==================== CONFIRMATION METHODS ====================

    /**
     * Confirmation methods for suspected surveillance devices.
     * Practical steps users can take to verify detections.
     */
    data class ConfirmationMethod(
        val deviceCategory: String,
        val steps: List<ConfirmationStep>,
        val equipment: List<String>,
        val warnings: List<String>
    )

    data class ConfirmationStep(
        val order: Int,
        val action: String,
        val details: String,
        val difficulty: ConfirmationDifficulty
    )

    enum class ConfirmationDifficulty(val displayName: String) {
        EASY("Easy - No special equipment"),
        MODERATE("Moderate - Basic tools/apps"),
        DIFFICULT("Difficult - Special equipment"),
        PROFESSIONAL("Professional - TSCM expertise required")
    }

    val confirmationMethods = mapOf(
        "hidden_camera" to ConfirmationMethod(
            deviceCategory = "Hidden Cameras",
            steps = listOf(
                ConfirmationStep(1, "Visual inspection with flashlight",
                    "Shine a bright flashlight around the room looking for reflections from camera lenses. " +
                    "Camera lenses will reflect light differently than other surfaces.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(2, "Scan with phone camera for IR LEDs",
                    "Use your phone's front camera (less IR filtering) to look for invisible IR LEDs. " +
                    "Night vision cameras emit IR light that phone cameras can detect as purple/white glow.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(3, "Check for small holes in objects",
                    "Examine smoke detectors, clocks, USB chargers, air fresheners, picture frames, " +
                    "and other common objects for tiny holes that could house a camera.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(4, "Use RF detector in sweep mode",
                    "If the camera transmits wirelessly, an RF detector can locate it. " +
                    "Sweep the room slowly, focusing on areas where detection app showed signals.",
                    ConfirmationDifficulty.MODERATE),
                ConfirmationStep(5, "Network scan for unknown devices",
                    "Use a network scanner app to identify all devices on the WiFi network. " +
                    "Unknown devices with video-related ports may be cameras.",
                    ConfirmationDifficulty.MODERATE)
            ),
            equipment = listOf(
                "Flashlight (bright)",
                "Smartphone camera (front camera preferred)",
                "RF detector (optional)",
                "WiFi network scanner app"
            ),
            warnings = listOf(
                "Do not tamper with devices in rental properties - document and report instead",
                "Hidden cameras in private spaces are illegal in most jurisdictions",
                "If you find a camera, take photos before touching it"
            )
        ),

        "gps_tracker" to ConfirmationMethod(
            deviceCategory = "GPS Trackers",
            steps = listOf(
                ConfirmationStep(1, "Inspect OBD-II port",
                    "Check the OBD-II diagnostic port under the driver's side dashboard. " +
                    "Look for any plugged-in device that isn't a standard code reader.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(2, "Check vehicle undercarriage",
                    "Use a flashlight to inspect under the vehicle, focusing on flat metal surfaces " +
                    "where a magnetic tracker could attach. Check frame rails and crossmembers.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(3, "Inspect wheel wells",
                    "Feel inside all four wheel wells for magnetic devices. " +
                    "Trackers are often placed in the plastic liner or on metal components.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(4, "Check bumpers and trunk",
                    "Look behind front and rear bumpers (accessible from below). " +
                    "Check spare tire compartment and trunk lining.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(5, "Professional TSCM sweep",
                    "For hardwired trackers or persistent suspicion, hire a professional " +
                    "TSCM (Technical Surveillance Countermeasures) expert.",
                    ConfirmationDifficulty.PROFESSIONAL)
            ),
            equipment = listOf(
                "Flashlight",
                "Mirror on stick (for hard to see areas)",
                "Mechanic's creeper (for under-vehicle inspection)",
                "Magnetic wand or stud finder (helps locate magnetic devices)"
            ),
            warnings = listOf(
                "If you find a tracker, DO NOT remove it immediately - it may be law enforcement",
                "Document the device with photos before any action",
                "Removing a tracker may be destruction of property if it belongs to someone else",
                "Consult an attorney if you suspect illegal tracking"
            )
        ),

        "drone" to ConfirmationMethod(
            deviceCategory = "Drones",
            steps = listOf(
                ConfirmationStep(1, "Visual scan of sky",
                    "Look up and scan the sky for the drone. Most consumer drones are visible " +
                    "and audible within 100 meters. Look for flashing lights at night.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(2, "Listen for motor sound",
                    "Drones make a distinctive buzzing/whirring sound from their propellers. " +
                    "The sound is most noticeable when the drone is stationary/hovering.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(3, "Check Remote ID apps",
                    "Use DroneScout, OpenDroneID, or AirMap apps to detect Remote ID broadcasts. " +
                    "Since Sept 2023, most drones must broadcast their ID and location.",
                    ConfirmationDifficulty.MODERATE),
                ConfirmationStep(4, "WiFi scan for drone networks",
                    "Scan for WiFi networks with drone manufacturer names (DJI, Parrot, etc.). " +
                    "Drone control WiFi networks are usually visible when nearby.",
                    ConfirmationDifficulty.MODERATE),
                ConfirmationStep(5, "Track signal direction",
                    "Use the app's signal strength indicator to determine the drone's direction. " +
                    "Walk in the direction of stronger signal to locate.",
                    ConfirmationDifficulty.MODERATE)
            ),
            equipment = listOf(
                "Remote ID app (DroneScout, OpenDroneID)",
                "Binoculars (for distant drones)",
                "WiFi scanner app"
            ),
            warnings = listOf(
                "Do not attempt to shoot down or interfere with drones - this is illegal",
                "Drones without Remote ID are illegal in US since Sept 2023",
                "If drone is over your property, document and report to local authorities",
                "Commercial/government drones may be operating legally with permissions"
            )
        ),

        "audio_bug" to ConfirmationMethod(
            deviceCategory = "Audio Bugs/Transmitters",
            steps = listOf(
                ConfirmationStep(1, "Visual inspection of room",
                    "Check electrical outlets, power strips, smoke detectors, lamps, and USB chargers. " +
                    "Bugs are often hidden in or near power sources.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(2, "Listen for feedback",
                    "Call your own phone and listen for electronic feedback or clicking. " +
                    "Some bugs cause interference with phone signals.",
                    ConfirmationDifficulty.EASY),
                ConfirmationStep(3, "RF detector sweep",
                    "Use an RF detector to sweep the room. Focus on the frequency ranges: " +
                    "100-300 MHz (VHF) and 300-500 MHz (UHF) for traditional bugs.",
                    ConfirmationDifficulty.MODERATE),
                ConfirmationStep(4, "GSM bug detection",
                    "Use a cellular activity detector or make calls to find GSM bugs. " +
                    "These only transmit when active, making them harder to detect.",
                    ConfirmationDifficulty.DIFFICULT),
                ConfirmationStep(5, "Professional TSCM sweep",
                    "For high-assurance situations, hire a TSCM professional with " +
                    "spectrum analyzers and non-linear junction detectors.",
                    ConfirmationDifficulty.PROFESSIONAL)
            ),
            equipment = listOf(
                "Flashlight",
                "RF detector (pocket-sized available ~\$20-100)",
                "Cell phone for interference testing",
                "NLJD (professional equipment)"
            ),
            warnings = listOf(
                "Do not discuss sensitive topics while searching - assume you're being heard",
                "If found, document before removal",
                "Wiretapping is illegal - report to law enforcement if you find a bug",
                "In some states, all-party consent is required for recording"
            )
        )
    )

    // ==================== DEVICE METADATA EXTENSIONS ====================

    /**
     * Extended metadata for device profiles including operational context.
     */
    data class DeviceOperationalMetadata(
        val deviceType: DeviceType,
        val estimatedRange: String,
        val powerRequirements: String,
        val typicalDeploymentScenarios: List<String>,
        val legalStatus: LegalStatus,
        val dataCollected: List<String>,
        val dataRetention: String,
        val typicalOperators: List<String>,
        val counterDetectionDifficulty: String,
        val recommendedResponse: List<String>
    )

    data class LegalStatus(
        val generalStatus: String,
        val variances: String?,
        val relevantLaws: List<String>
    )

    val deviceOperationalMetadata = mapOf(
        DeviceType.HIDDEN_CAMERA to DeviceOperationalMetadata(
            deviceType = DeviceType.HIDDEN_CAMERA,
            estimatedRange = "WiFi cameras: network dependent; Analog: 50-500m depending on power",
            powerRequirements = "Battery (hours to days) or hardwired (continuous)",
            typicalDeploymentScenarios = listOf(
                "Airbnb/hotel room surveillance (illegal)",
                "Workplace monitoring (varies by jurisdiction)",
                "Nanny cams (legal with notice in most states)",
                "Voyeurism (illegal everywhere)"
            ),
            legalStatus = LegalStatus(
                generalStatus = "Illegal in private spaces without consent",
                variances = "Workplace rules vary; bathrooms/changing rooms always illegal",
                relevantLaws = listOf(
                    "Video Voyeurism Prevention Act (federal)",
                    "State wiretapping/eavesdropping laws",
                    "Invasion of privacy torts"
                )
            ),
            dataCollected = listOf(
                "Video footage (continuous or motion-triggered)",
                "Audio (if microphone equipped)",
                "Timestamps",
                "Motion events"
            ),
            dataRetention = "Varies - SD card storage or cloud (days to indefinite)",
            typicalOperators = listOf(
                "Airbnb/hotel guests checking for cameras",
                "Property owners (legal on own property)",
                "Stalkers/voyeurs (illegal)",
                "Private investigators"
            ),
            counterDetectionDifficulty = "Easy-Moderate: RF detection, lens reflection, IR detection",
            recommendedResponse = listOf(
                "Document the device with photos",
                "Do not touch or tamper with the device",
                "Report to property management/police",
                "Leave the area if in rental property"
            )
        ),

        DeviceType.DRONE to DeviceOperationalMetadata(
            deviceType = DeviceType.DRONE,
            estimatedRange = "Control: 1-10km depending on model; Visual: typically <500m",
            powerRequirements = "Battery - 15-45 minutes typical flight time",
            typicalDeploymentScenarios = listOf(
                "Recreational flying (legal with FAA rules)",
                "Photography/videography",
                "Real estate photography",
                "Law enforcement surveillance",
                "Stalking/harassment (illegal)",
                "Package delivery (Amazon, etc.)"
            ),
            legalStatus = LegalStatus(
                generalStatus = "Legal with FAA registration and Remote ID (since 9/2023)",
                variances = "Restricted near airports, over crowds, at night without waiver",
                relevantLaws = listOf(
                    "FAA Part 107 (commercial)",
                    "FAA recreational rules",
                    "State/local drone laws",
                    "Remote ID rule (effective 9/16/2023)"
                )
            ),
            dataCollected = listOf(
                "Video/photos",
                "GPS coordinates of footage",
                "Flight path/telemetry",
                "Potentially facial recognition"
            ),
            dataRetention = "Varies by operator",
            typicalOperators = listOf(
                "Recreational hobbyists",
                "Commercial photographers",
                "Law enforcement",
                "Real estate agents",
                "Infrastructure inspectors"
            ),
            counterDetectionDifficulty = "Easy: Visual, audible, Remote ID detection",
            recommendedResponse = listOf(
                "Check Remote ID apps for registration info",
                "Document time, location, behavior",
                "If persistent/suspicious, report to local police",
                "Do NOT attempt to shoot down or interfere"
            )
        ),

        DeviceType.RF_JAMMER to DeviceOperationalMetadata(
            deviceType = DeviceType.RF_JAMMER,
            estimatedRange = "GPS jammers: 5-50m typical; Cell jammers: 10-100m; WiFi: 10-50m",
            powerRequirements = "Battery (cigarette lighter adapter) or wall power",
            typicalDeploymentScenarios = listOf(
                "Criminals avoiding GPS tracking",
                "Car thieves defeating trackers",
                "Burglars disabling WiFi security",
                "Exam cheaters (illegal)",
                "Prisons (legal with federal waiver)"
            ),
            legalStatus = LegalStatus(
                generalStatus = "ILLEGAL to operate, sell, or market in the US",
                variances = "Only federal government can authorize use",
                relevantLaws = listOf(
                    "Communications Act of 1934",
                    "47 U.S.C. Section 333",
                    "FCC enforcement actions - up to \$100K+ fines",
                    "Criminal penalties possible"
                )
            ),
            dataCollected = listOf("N/A - jamming devices collect no data"),
            dataRetention = "N/A",
            typicalOperators = listOf(
                "Criminals",
                "Fleet drivers avoiding tracking (illegal)",
                "Prisons (federally authorized)"
            ),
            counterDetectionDifficulty = "Moderate: Detection by signal loss pattern analysis",
            recommendedResponse = listOf(
                "DANGER: Cannot make emergency calls in jammed area",
                "Leave the area immediately if possible",
                "Note time, location, duration for FCC report",
                "Report persistent jamming to FCC Enforcement Bureau",
                "Consider using wired alternatives"
            )
        ),

        DeviceType.STINGRAY_IMSI to DeviceOperationalMetadata(
            deviceType = DeviceType.STINGRAY_IMSI,
            estimatedRange = "Effective range: 1-2 km radius",
            powerRequirements = "High power - typically vehicle-mounted with generator",
            typicalDeploymentScenarios = listOf(
                "Law enforcement surveillance (warrant varies by jurisdiction)",
                "Mass event monitoring (protests, gatherings)",
                "Locating specific phones/individuals",
                "Foreign intelligence (illegal domestic use)"
            ),
            legalStatus = LegalStatus(
                generalStatus = "Legal for law enforcement with appropriate legal process",
                variances = "DOJ requires warrant except for exigent circumstances",
                relevantLaws = listOf(
                    "Fourth Amendment (search/seizure)",
                    "Carpenter v. United States (2018)",
                    "State cell-site simulator laws",
                    "DOJ policy requires warrant"
                )
            ),
            dataCollected = listOf(
                "IMSI (SIM identifier) of all nearby phones",
                "IMEI (device identifier)",
                "Phone calls (with active interception)",
                "SMS messages",
                "Precise location",
                "Device model information"
            ),
            dataRetention = "Varies - often destroyed after investigation",
            typicalOperators = listOf(
                "FBI",
                "DEA",
                "US Marshals",
                "State/local law enforcement",
                "Foreign intelligence (illegal in US)"
            ),
            counterDetectionDifficulty = "Difficult: Requires cellular protocol analysis",
            recommendedResponse = listOf(
                "Enable airplane mode for complete privacy",
                "Use encrypted messaging (Signal) if phone must stay on",
                "Disable 2G if phone supports it",
                "Note location/time for potential legal challenges",
                "Use WiFi calling on trusted network"
            )
        ),

        DeviceType.AIRTAG to DeviceOperationalMetadata(
            deviceType = DeviceType.AIRTAG,
            estimatedRange = "BLE: ~10-30m direct; Find My network: global",
            powerRequirements = "CR2032 battery - ~1 year lifespan",
            typicalDeploymentScenarios = listOf(
                "Personal item tracking (keys, bags)",
                "Pet tracking",
                "Vehicle tracking",
                "Stalking (illegal use)"
            ),
            legalStatus = LegalStatus(
                generalStatus = "Legal for personal property; illegal to track others without consent",
                variances = "Anti-stalking laws apply in all states",
                relevantLaws = listOf(
                    "State stalking/cyberstalking laws",
                    "Federal stalking statutes (18 U.S.C. 2261A)",
                    "Apple's anti-stalking measures"
                )
            ),
            dataCollected = listOf(
                "Real-time location via Find My network",
                "Location history (on owner's device)",
                "Timestamps of location updates"
            ),
            dataRetention = "Apple: 24 hours; Owner device: varies",
            typicalOperators = listOf(
                "Consumers tracking belongings",
                "Parents tracking children's items",
                "Stalkers/abusers (illegal)"
            ),
            counterDetectionDifficulty = "Easy: iOS alerts, Android AirGuard app, audio chirp",
            recommendedResponse = listOf(
                "If unknown AirTag found, disable it (remove battery)",
                "Use AirGuard app on Android for detection",
                "iOS will alert you to unknown AirTags traveling with you",
                "Report to police if you believe you're being stalked"
            )
        ),

        DeviceType.RING_DOORBELL to DeviceOperationalMetadata(
            deviceType = DeviceType.RING_DOORBELL,
            estimatedRange = "WiFi dependent; Audio: 15-25 feet; Video: 180 degrees",
            powerRequirements = "Battery (rechargeable) or hardwired",
            typicalDeploymentScenarios = listOf(
                "Home security",
                "Package theft prevention",
                "Visitor monitoring",
                "Neighborhood surveillance (Neighbors app)"
            ),
            legalStatus = LegalStatus(
                generalStatus = "Legal on own property; recordings of public areas generally legal",
                variances = "Audio recording laws vary by state (one-party vs all-party consent)",
                relevantLaws = listOf(
                    "State wiretapping laws (audio)",
                    "Privacy laws for public areas",
                    "Amazon/Ring law enforcement partnerships"
                )
            ),
            dataCollected = listOf(
                "Video footage (motion-triggered or continuous)",
                "Audio recordings",
                "Motion detection events",
                "Facial recognition (via Neighbors app)",
                "Visitor patterns"
            ),
            dataRetention = "Cloud: 60 days (Ring Protect); Can be shared with police",
            typicalOperators = listOf(
                "Homeowners",
                "Landlords",
                "Business owners",
                "Law enforcement (via requests)"
            ),
            counterDetectionDifficulty = "Easy: Visible device, WiFi scan",
            recommendedResponse = listOf(
                "Be aware you may be recorded approaching property",
                "Ring footage may be shared with 2,500+ police departments",
                "Audio is captured - be mindful of conversations",
                "Check if property has Ring via visible device or Neighbors app"
            )
        ),

        DeviceType.FLOCK_SAFETY_CAMERA to DeviceOperationalMetadata(
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            estimatedRange = "ALPR capture: effective on passing vehicles at normal speeds",
            powerRequirements = "Solar powered with cellular backhaul",
            typicalDeploymentScenarios = listOf(
                "Neighborhood entrance monitoring",
                "HOA/private community surveillance",
                "Law enforcement vehicle tracking",
                "Business parking lot monitoring"
            ),
            legalStatus = LegalStatus(
                generalStatus = "Legal - no expectation of privacy for license plates in public",
                variances = "Some states have ALPR data retention limits",
                relevantLaws = listOf(
                    "State ALPR laws (CA, ME, NH have restrictions)",
                    "Fourth Amendment considerations",
                    "Data retention policies vary"
                )
            ),
            dataCollected = listOf(
                "License plate numbers and images",
                "Vehicle make, model, color",
                "Direction of travel",
                "Timestamps and GPS coordinates",
                "Vehicle 'fingerprint' for re-identification"
            ),
            dataRetention = "Flock: 30 days standard; Law enforcement may retain longer",
            typicalOperators = listOf(
                "HOAs and private communities",
                "Law enforcement agencies",
                "Business parks",
                "Schools"
            ),
            counterDetectionDifficulty = "Easy: Visible pole-mounted cameras, WiFi/BLE detection",
            recommendedResponse = listOf(
                "Your vehicle movements are being tracked in this area",
                "Data may be shared across 1,500+ law enforcement agencies",
                "Consider varying routes if concerned about pattern analysis",
                "Check flockos.com or local government for camera locations"
            )
        )
    )
}
