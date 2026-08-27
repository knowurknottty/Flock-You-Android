package com.flockyou.ai

import android.util.Log
import com.flockyou.data.model.Detection
import com.flockyou.data.model.DeviceType
import com.flockyou.data.model.ThreatLevel
import com.flockyou.data.repository.DetectionRepository
import com.flockyou.detection.DetectionRegistry
import com.flockyou.detection.profile.DeviceTypeProfile as CentralizedProfile
import com.flockyou.detection.profile.DeviceTypeProfileRegistry
import com.flockyou.detection.handler.DeviceTypeProfile as HandlerProfile
import com.flockyou.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.*

private const val TAG = "PatternAnalyzer"

// ==================== CROSS-DETECTION PATTERN RECOGNITION ====================

/**
 * Analyze patterns across multiple detections to identify coordinated surveillance,
 * following patterns, timing correlations, and geographic clustering.
 *
 * @param timeWindowMs Time window to consider (default: 1 hour)
 * @param detectionRepository Repository to fetch detections from
 * @param mediaPipeLlmClient MediaPipe client for optional LLM-based pattern analysis
 * @return List of identified patterns with confidence scores
 */
internal suspend fun analyzeDetectionPatterns(
    timeWindowMs: Long = 3600000L,
    detectionRepository: DetectionRepository,
    mediaPipeLlmClient: MediaPipeLlmClient
): List<PatternInsight> = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val cutoffTime = now - timeWindowMs
    val recentDetections = detectionRepository.getAllDetectionsSnapshot()
        .filter { it.timestamp >= cutoffTime }
        .sortedByDescending { it.timestamp }

    if (recentDetections.size < 2) {
        return@withContext emptyList()
    }

    val patterns = mutableListOf<PatternInsight>()

    // Try LLM-based pattern analysis if available
    if (mediaPipeLlmClient.isReady()) {
        val timeDesc = when {
            timeWindowMs <= 3600000L -> "past hour"
            timeWindowMs <= 86400000L -> "past ${timeWindowMs / 3600000} hours"
            else -> "past ${timeWindowMs / 86400000} days"
        }

        val prompt = PromptTemplates.buildPatternRecognitionPrompt(recentDetections, timeDesc)
        val response = mediaPipeLlmClient.generateResponse(prompt)

        if (response != null) {
            val llmPatterns = LlmOutputParser.parsePatternAnalysis(response)
            if (llmPatterns.isNotEmpty()) {
                return@withContext llmPatterns
            }
        }
    }

    // Fall back to rule-based pattern detection
    patterns.addAll(detectCoordinatedSurveillance(recentDetections))
    patterns.addAll(detectFollowingPattern(recentDetections))
    patterns.addAll(detectTimingCorrelation(recentDetections))
    patterns.addAll(detectGeographicClustering(recentDetections))
    patterns.addAll(detectEscalationPattern(recentDetections))
    patterns.addAll(detectMultimodalSurveillance(recentDetections))

    patterns.sortedByDescending { it.confidence }
}

/**
 * Rule-based: Detect coordinated surveillance (multiple related devices active together)
 */
private fun detectCoordinatedSurveillance(detections: List<Detection>): List<PatternInsight> {
    // Group by time windows (5 minute buckets)
    val timeGroups = detections.groupBy { it.timestamp / 300000 }

    val patterns = mutableListOf<PatternInsight>()

    for ((_, group) in timeGroups) {
        if (group.size >= 3) {
            // Multiple devices detected within same 5-minute window
            val deviceTypes = group.map { it.deviceType }.distinct()
            if (deviceTypes.size >= 2) {
                patterns.add(PatternInsight(
                    patternType = PatternType.COORDINATED_SURVEILLANCE,
                    affectedDetections = group.map { it.id },
                    description = "${group.size} devices detected simultaneously: ${deviceTypes.joinToString { it.displayName }}",
                    implication = "Multiple surveillance devices operating together may indicate coordinated monitoring",
                    confidence = minOf(0.4f + (group.size * 0.1f), 0.9f)
                ))
            }
        }
    }

    return patterns
}

/**
 * Rule-based: Detect if devices appear to be following the user
 */
private fun detectFollowingPattern(detections: List<Detection>): List<PatternInsight> {
    // Group detections by device (MAC or SSID)
    val byDevice = detections.groupBy { it.macAddress ?: it.ssid ?: it.deviceName }
        .filter { it.key != null && it.value.size >= 2 }

    val patterns = mutableListOf<PatternInsight>()

    for ((_, deviceDetections) in byDevice) {
        val locations = deviceDetections.mapNotNull {
            if (it.latitude != null && it.longitude != null) it.latitude to it.longitude
            else null
        }.distinct()

        if (locations.size >= 2) {
            // Same device detected at multiple distinct locations
            val firstDet = deviceDetections.first()
            patterns.add(PatternInsight(
                patternType = PatternType.FOLLOWING_PATTERN,
                affectedDetections = deviceDetections.map { it.id },
                description = "${firstDet.deviceType.displayName} detected at ${locations.size} different locations",
                implication = "This device may be following you or is mobile surveillance equipment",
                confidence = minOf(0.5f + (locations.size * 0.15f), 0.95f)
            ))
        }
    }

    return patterns
}

/**
 * Rule-based: Detect timing correlations (devices activating at similar times)
 */
private fun detectTimingCorrelation(detections: List<Detection>): List<PatternInsight> {
    if (detections.size < 4) return emptyList()

    // Check for regular intervals
    val sorted = detections.sortedBy { it.timestamp }
    val intervals = sorted.zipWithNext { a, b -> b.timestamp - a.timestamp }

    if (intervals.size < 3) return emptyList()

    val avgInterval = intervals.average()
    val variance = intervals.map { (it - avgInterval) * (it - avgInterval) }.average()
    val stdDev = kotlin.math.sqrt(variance)

    // Low variance indicates regular timing
    if (stdDev < avgInterval * 0.3 && avgInterval < 600000) { // Less than 30% variance, intervals < 10 min
        return listOf(PatternInsight(
            patternType = PatternType.TIMING_CORRELATION,
            affectedDetections = detections.map { it.id },
            description = "Detections occurring at regular ${(avgInterval / 60000).toInt()}-minute intervals",
            implication = "Regular timing suggests automated or scheduled surveillance sweeps",
            confidence = 0.7f - (stdDev / avgInterval).toFloat().coerceIn(0f, 0.3f)
        ))
    }

    return emptyList()
}

/**
 * Rule-based: Detect geographic clustering
 */
private fun detectGeographicClustering(detections: List<Detection>): List<PatternInsight> {
    val locatedDetections = detections.filter { it.latitude != null && it.longitude != null }
    if (locatedDetections.size < 3) return emptyList()

    // Find clusters using simple distance-based grouping
    val clusters = mutableListOf<List<Detection>>()
    val used = mutableSetOf<String>()

    for (detection in locatedDetections) {
        if (detection.id in used) continue

        // Get coordinates with null safety
        val detLat = detection.latitude
        val detLon = detection.longitude
        if (detLat == null || detLon == null) continue

        val cluster = locatedDetections.filter { other ->
            val otherLat = other.latitude
            val otherLon = other.longitude
            other.id !in used &&
            otherLat != null && otherLon != null &&
            calculateDistanceMeters(detLat, detLon, otherLat, otherLon) < CLUSTER_RADIUS_METERS
        }

        if (cluster.size >= 3) {
            clusters.add(cluster)
            used.addAll(cluster.map { it.id })
        }
    }

    return clusters.map { cluster ->
        val types = cluster.map { it.deviceType.displayName }.distinct()
        PatternInsight(
            patternType = PatternType.GEOGRAPHIC_CLUSTERING,
            affectedDetections = cluster.map { it.id },
            description = "${cluster.size} devices clustered within ${CLUSTER_RADIUS_METERS.toInt()}m: ${types.joinToString()}",
            implication = "Concentrated surveillance infrastructure in this area",
            confidence = minOf(0.5f + (cluster.size * 0.1f), 0.9f)
        )
    }
}

/**
 * Rule-based: Detect escalation in threat levels over time
 */
private fun detectEscalationPattern(detections: List<Detection>): List<PatternInsight> {
    if (detections.size < 3) return emptyList()

    val sorted = detections.sortedBy { it.timestamp }
    val scores = sorted.map { it.threatScore }

    // Check if threat scores are generally increasing
    var increases = 0
    var decreases = 0
    for (i in 1 until scores.size) {
        if (scores[i] > scores[i - 1]) increases++
        else if (scores[i] < scores[i - 1]) decreases++
    }

    if (increases > decreases * 2 && increases >= 3) {
        val firstScore = scores.first()
        val lastScore = scores.last()
        return listOf(PatternInsight(
            patternType = PatternType.ESCALATION_PATTERN,
            affectedDetections = sorted.map { it.id },
            description = "Threat scores increasing from $firstScore to $lastScore",
            implication = "Surveillance activity appears to be escalating in your area",
            confidence = 0.6f + (increases.toFloat() / scores.size * 0.3f)
        ))
    }

    return emptyList()
}

/**
 * Rule-based: Detect multimodal surveillance (different detection types targeting same area)
 */
private fun detectMultimodalSurveillance(detections: List<Detection>): List<PatternInsight> {
    val protocols = detections.map { it.protocol }.distinct()

    if (protocols.size >= 3) {
        return listOf(PatternInsight(
            patternType = PatternType.MULTIMODAL_SURVEILLANCE,
            affectedDetections = detections.map { it.id },
            description = "Multiple surveillance modalities active: ${protocols.joinToString { it.displayName }}",
            implication = "Comprehensive surveillance using multiple technologies (WiFi, cellular, audio, etc.)",
            confidence = 0.7f + (protocols.size * 0.05f).coerceAtMost(0.2f)
        ))
    }

    return emptyList()
}

// ==================== RULE-BASED ANALYSIS ====================

/**
 * Comprehensive rule-based analysis covering all 50+ device types.
 * Uses the new enterprise description system for actionable, context-aware descriptions.
 */
internal fun generateRuleBasedAnalysis(
    detection: Detection,
    contextualInsights: ContextualInsights?,
    detectionRegistry: DetectionRegistry
): AiAnalysisResult {
    // Convert local ContextualInsights to PromptTemplates version
    val templateInsights = contextualInsights?.let {
        PromptTemplates.ContextualInsights(
            isKnownLocation = it.isKnownLocation,
            locationPattern = it.locationPattern,
            timePattern = it.timePattern,
            clusterInfo = it.clusterInfo,
            historicalContext = it.historicalContext
        )
    }

    // Generate enterprise-grade description
    val enterpriseDesc = PromptTemplates.generateEnterpriseDescription(
        detection = detection,
        enrichedData = null, // Enriched data handled separately via LLM path
        contextualInsights = templateInsights,
        falsePositiveResult = null // Will be populated by FP analyzer
    )

    // Format the enterprise description as user-facing analysis
    val analysis = PromptTemplates.formatEnterpriseDescriptionForUser(enterpriseDesc)

    // Generate recommendations from enterprise description
    val recommendations = mutableListOf<String>()
    enterpriseDesc.immediateAction?.let { recommendations.add(it.action) }
    recommendations.add(enterpriseDesc.monitoringRecommendation)
    enterpriseDesc.documentationSuggestion?.let { recommendations.add(it) }

    // Get legacy device info for structured data compatibility
    val deviceInfo = getComprehensiveDeviceInfo(detection.deviceType)
    val dataCollection = getDataCollectionCapabilities(detection.deviceType, detectionRegistry)

    // Build structured data with enterprise insights
    val structuredData = StructuredAnalysis(
        deviceCategory = deviceInfo.category,
        surveillanceType = deviceInfo.surveillanceType,
        dataCollectionTypes = dataCollection,
        riskScore = calculateRiskScore(detection, contextualInsights),
        riskFactors = getRiskFactors(detection, contextualInsights),
        mitigationActions = recommendations.mapIndexed { index, rec ->
            MitigationAction(
                action = rec,
                priority = when (index) {
                    0 -> if (enterpriseDesc.immediateAction != null) ActionPriority.IMMEDIATE else ActionPriority.MEDIUM
                    1 -> ActionPriority.HIGH
                    else -> ActionPriority.MEDIUM
                },
                description = rec
            )
        },
        contextualInsights = contextualInsights,
        // Add enterprise description metadata
        enterpriseDescription = enterpriseDesc
    )

    // Adjust confidence based on false positive likelihood
    val adjustedConfidence = if (enterpriseDesc.isMostLikelyBenign) {
        // Lower confidence when likely false positive
        (0.95f * (1f - enterpriseDesc.falsePositiveLikelihood / 100f)).coerceIn(0.3f, 0.95f)
    } else {
        0.95f
    }

    return AiAnalysisResult(
        success = true,
        analysis = analysis,
        recommendations = recommendations.distinct().take(6),
        confidence = adjustedConfidence,
        modelUsed = "rule-based-enterprise", // Indicates enhanced rule-based with enterprise templates
        wasOnDevice = true,
        structuredData = structuredData,
        // Add enterprise-specific fields
        isFalsePositiveLikely = enterpriseDesc.isMostLikelyBenign,
        falsePositiveLikelihoodPercent = enterpriseDesc.falsePositiveLikelihood,
        simpleExplanation = enterpriseDesc.simpleExplanation,
        technicalDetails = enterpriseDesc.technicalDetails
    )
}

// ==================== DEVICE INFO ====================

internal data class DeviceInfo(
    val description: String,
    val category: String,
    val surveillanceType: String,
    val typicalOperator: String? = null,
    val legalFramework: String? = null,
    // User-friendly fields for different explanation levels
    val simpleDescription: String? = null,      // Short, simple language for non-technical users
    val simplePrivacyImpact: String? = null,    // Privacy impact in simple terms
    val privacyImpact: String? = null           // Standard privacy impact explanation
)

/**
 * Extension function to convert CentralizedProfile to local DeviceInfo.
 */
internal fun CentralizedProfile.toDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        description = this.description,
        category = this.category,
        surveillanceType = this.surveillanceType,
        typicalOperator = this.typicalOperator,
        legalFramework = this.legalFramework,
        simpleDescription = this.simpleDescription,
        simplePrivacyImpact = this.simplePrivacyImpact,
        privacyImpact = this.privacyImpact.description
    )
}

/**
 * Extension function to convert HandlerProfile to local DeviceInfo.
 * HandlerProfile has fewer fields, so we use what's available.
 */
internal fun HandlerProfile.toDeviceInfo(): DeviceInfo {
    return DeviceInfo(
        description = this.description,
        category = this.manufacturer, // Use manufacturer as category approximation
        surveillanceType = "Detection via ${this.typicalThreatLevel.displayName} threat patterns",
        typicalOperator = null,
        legalFramework = this.legalConsiderations.ifEmpty { null },
        simpleDescription = null,
        simplePrivacyImpact = null,
        privacyImpact = null
    )
}

/**
 * Create a default DeviceInfo for unknown device types.
 */
@Suppress("unused")
internal fun createDefaultDeviceInfo(deviceType: DeviceType): DeviceInfo {
    return DeviceInfo(
        description = "Unknown surveillance device detected based on wireless signature patterns. Unable to determine specific type, but characteristics suggest surveillance capability.",
        category = "Unknown",
        surveillanceType = "Unknown",
        typicalOperator = null,
        legalFramework = null,
        simpleDescription = "Unknown device with potential surveillance capability",
        simplePrivacyImpact = "Privacy implications unknown - treat with caution"
    )
}

/**
 * Get device information from the profile registry.
 * Uses DeviceTypeProfileRegistry (centralized profiles) as the primary source,
 * with fallback to DetectionRegistry handler profiles for additional data.
 */
internal fun getComprehensiveDeviceInfo(deviceType: DeviceType): DeviceInfo {
    // Primary: Use DeviceTypeProfileRegistry (comprehensive profile system)
    val centralizedProfile = DeviceTypeProfileRegistry.getProfile(deviceType)

    // The centralized profile always returns a valid profile (with defaults for unknown types)
    return centralizedProfile.toDeviceInfo()
}

// ==================== LEGACY DEVICE INFO (kept for reference during migration) ====================
// The following when block has been replaced by profile lookups above.
// Keeping as private function for backward compatibility during testing.
@Suppress("unused")
private fun getComprehensiveDeviceInfoLegacy(deviceType: DeviceType): DeviceInfo {
    return when (deviceType) {
        // ALPR & Traffic Cameras
        DeviceType.FLOCK_SAFETY_CAMERA -> DeviceInfo(
            description = "Flock Safety is an Automatic License Plate Recognition (ALPR) camera system. It captures images of all passing vehicles, extracting license plates, vehicle make/model/color, and timestamps. Data is stored in searchable databases accessible to law enforcement agencies and shared across jurisdictions.",
            category = "ALPR System",
            surveillanceType = "Vehicle Tracking",
            typicalOperator = "Law enforcement, HOAs, businesses",
            legalFramework = "Varies by state; some states restrict ALPR data retention"
        )
        DeviceType.LICENSE_PLATE_READER -> DeviceInfo(
            description = "Generic license plate reader system that captures and stores vehicle plate data. May be stationary or mobile-mounted on police vehicles. Creates detailed records of vehicle movements over time.",
            category = "ALPR System",
            surveillanceType = "Vehicle Tracking",
            typicalOperator = "Law enforcement, parking enforcement",
            legalFramework = "Subject to local ALPR regulations"
        )
        DeviceType.SPEED_CAMERA -> DeviceInfo(
            description = "Automated speed enforcement camera that captures vehicle speed and plate data. May issue automated citations. Stores vehicle images and speed records.",
            category = "Traffic Enforcement",
            surveillanceType = "Vehicle Monitoring",
            typicalOperator = "Municipal traffic enforcement",
            legalFramework = "Varies by jurisdiction; some states ban automated enforcement"
        )
        DeviceType.RED_LIGHT_CAMERA -> DeviceInfo(
            description = "Intersection camera that captures vehicles running red lights. Records vehicle images, plates, and violation evidence. May be combined with speed enforcement.",
            category = "Traffic Enforcement",
            surveillanceType = "Vehicle Monitoring",
            typicalOperator = "Municipal traffic enforcement"
        )
        DeviceType.TOLL_READER -> DeviceInfo(
            description = "Electronic toll collection reader (E-ZPass, SunPass, etc.). Tracks vehicle movements through toll points. Data may be subpoenaed for investigations.",
            category = "Toll System",
            surveillanceType = "Vehicle Tracking",
            typicalOperator = "Toll authorities, DOT"
        )
        DeviceType.TRAFFIC_SENSOR -> DeviceInfo(
            description = "Traffic monitoring sensor for flow analysis. May use radar, cameras, or induction loops. Some systems capture individual vehicle data.",
            category = "Traffic Infrastructure",
            surveillanceType = "Traffic Analysis"
        )

        // Acoustic Surveillance
        DeviceType.RAVEN_GUNSHOT_DETECTOR -> DeviceInfo(
            description = "Raven is an acoustic gunshot detection system using networked microphones to detect and triangulate gunfire. While designed for public safety, it continuously monitors ambient audio in the area and may capture conversations.",
            category = "Acoustic Surveillance",
            surveillanceType = "Audio Monitoring",
            typicalOperator = "Law enforcement",
            legalFramework = "Generally considered public space monitoring"
        )
        DeviceType.SHOTSPOTTER -> DeviceInfo(
            description = "ShotSpotter is a citywide acoustic surveillance network. Uses arrays of sensitive microphones that continuously record and analyze ambient audio for gunshot-like sounds. Audio snippets are reviewed by analysts.",
            category = "Acoustic Surveillance",
            surveillanceType = "Continuous Audio Monitoring",
            typicalOperator = "Law enforcement (contracted service)",
            legalFramework = "Has faced legal challenges over audio retention"
        )

        // Cell Site Simulators
        DeviceType.STINGRAY_IMSI -> DeviceInfo(
            description = "Cell-site simulator (IMSI catcher/Stingray) that mimics a cell tower to force phones to connect. " +
                "Detection analysis includes: encryption downgrade chain tracking (5G->4G->3G->2G), " +
                "signal spike correlation with new tower appearances, IMSI catcher signature scoring (0-100%), " +
                "movement analysis via Haversine distance calculations, and cell trust scoring based on " +
                "historical tower observations. Key indicators: forced encryption downgrades with simultaneous " +
                "signal spikes, unfamiliar cell IDs in familiar areas, and impossible movement speeds suggesting " +
                "tower location jumps. Can intercept calls, texts, and precisely track device locations.",
            category = "Cell Site Simulator",
            surveillanceType = "Communications Interception",
            typicalOperator = "Law enforcement (requires warrant in most jurisdictions)",
            legalFramework = "Carpenter v. US requires warrant for historical location data"
        )

        // Forensic Equipment
        DeviceType.CELLEBRITE_FORENSICS -> DeviceInfo(
            description = "Cellebrite is mobile forensics equipment used to extract data from phones including deleted content, encrypted data, and app data. Detection suggests active forensic operations nearby.",
            category = "Mobile Forensics",
            surveillanceType = "Device Data Extraction",
            typicalOperator = "Law enforcement, private investigators",
            legalFramework = "Generally requires warrant for search"
        )
        DeviceType.GRAYKEY_DEVICE -> DeviceInfo(
            description = "GrayKey is an iPhone unlocking and forensics device. Can bypass iOS security to extract device contents. Indicates active mobile forensics operation.",
            category = "Mobile Forensics",
            surveillanceType = "Device Data Extraction",
            typicalOperator = "Law enforcement"
        )

        // Smart Home Cameras
        DeviceType.RING_DOORBELL -> DeviceInfo(
            description = "Amazon Ring doorbell/camera. Records video and audio of public areas. Footage may be shared with law enforcement through Ring's Neighbors program or via subpoena without owner notification.",
            category = "Smart Home Camera",
            surveillanceType = "Video/Audio Recording",
            typicalOperator = "Private homeowners",
            legalFramework = "Amazon partners with 2,000+ police departments"
        )
        DeviceType.NEST_CAMERA -> DeviceInfo(
            description = "Google Nest camera/doorbell. Provides 24/7 video recording with cloud storage. Google may comply with law enforcement requests for footage. Features AI-powered person detection.",
            category = "Smart Home Camera",
            surveillanceType = "Video Recording",
            typicalOperator = "Private homeowners"
        )
        DeviceType.ARLO_CAMERA -> DeviceInfo(
            description = "Arlo security camera with cloud storage. May record continuously or on motion detection. Footage accessible to law enforcement via subpoena.",
            category = "Smart Home Camera",
            surveillanceType = "Video Recording"
        )
        DeviceType.WYZE_CAMERA -> DeviceInfo(
            description = "Wyze smart camera. Low-cost camera with cloud connectivity. Has had security vulnerabilities in the past. May share data with third parties.",
            category = "Smart Home Camera",
            surveillanceType = "Video Recording"
        )
        DeviceType.EUFY_CAMERA -> DeviceInfo(
            description = "Eufy security camera. Marketed as local-only storage but has sent data to cloud. Be aware of potential data collection beyond stated privacy policy.",
            category = "Smart Home Camera",
            surveillanceType = "Video Recording"
        )
        DeviceType.BLINK_CAMERA -> DeviceInfo(
            description = "Amazon Blink camera. Part of Amazon's home security ecosystem. May participate in Sidewalk mesh network and share footage with law enforcement.",
            category = "Smart Home Camera",
            surveillanceType = "Video Recording"
        )

        // Security Systems
        DeviceType.SIMPLISAFE_DEVICE -> DeviceInfo(
            description = "SimpliSafe security system component. Professional monitoring service may share data with authorities. Includes cameras, sensors, and alarm systems.",
            category = "Security System",
            surveillanceType = "Home Monitoring"
        )
        DeviceType.ADT_DEVICE -> DeviceInfo(
            description = "ADT security system component. One of the largest security providers. Professional monitoring with law enforcement partnerships.",
            category = "Security System",
            surveillanceType = "Home Monitoring"
        )
        DeviceType.VIVINT_DEVICE -> DeviceInfo(
            description = "Vivint smart home security device. Full home automation and security monitoring with cloud connectivity and professional monitoring.",
            category = "Security System",
            surveillanceType = "Home Monitoring"
        )

        // Personal Trackers
        DeviceType.AIRTAG -> DeviceInfo(
            description = "Apple AirTag Bluetooth tracker. Uses Apple's Find My network (billions of devices) for location tracking. If you don't own this and see it repeatedly, it may be tracking you.",
            category = "Personal Tracker",
            surveillanceType = "Location Tracking",
            typicalOperator = "Private individuals",
            legalFramework = "Apple added anti-stalking alerts; illegal to track without consent"
        )
        DeviceType.TILE_TRACKER -> DeviceInfo(
            description = "Tile Bluetooth tracker. Uses Tile's network for location tracking. Check your belongings if you see this repeatedly and don't own a Tile.",
            category = "Personal Tracker",
            surveillanceType = "Location Tracking"
        )
        DeviceType.SAMSUNG_SMARTTAG -> DeviceInfo(
            description = "Samsung SmartTag tracker. Uses Samsung's Galaxy Find Network. Can track items or potentially be used for unwanted tracking.",
            category = "Personal Tracker",
            surveillanceType = "Location Tracking"
        )
        DeviceType.GENERIC_BLE_TRACKER -> DeviceInfo(
            description = "Generic Bluetooth Low Energy tracker detected. Could be a legitimate item tracker or potentially used for unwanted surveillance.",
            category = "Personal Tracker",
            surveillanceType = "Location Tracking"
        )

        // Mesh Networks
        DeviceType.AMAZON_SIDEWALK -> DeviceInfo(
            description = "Amazon Sidewalk is a shared mesh network using Ring and Echo devices. Can track Sidewalk-enabled devices across the network and raises privacy concerns about shared bandwidth.",
            category = "Mesh Network",
            surveillanceType = "Network Tracking",
            typicalOperator = "Amazon (opt-out required)"
        )

        // Network Attack Devices
        DeviceType.WIFI_PINEAPPLE -> DeviceInfo(
            description = "WiFi Pineapple is a penetration testing device capable of man-in-the-middle attacks, credential capture, and network manipulation. Detection suggests active security testing or potential attack.",
            category = "Network Attack Tool",
            surveillanceType = "Network Interception",
            legalFramework = "Illegal to use without authorization"
        )
        DeviceType.ROGUE_AP -> DeviceInfo(
            description = "Unauthorized or suspicious access point detected. May be attempting evil twin attacks or network interception. Do not connect to unknown networks.",
            category = "Rogue Network",
            surveillanceType = "Network Interception"
        )
        DeviceType.MAN_IN_MIDDLE -> DeviceInfo(
            description = "Potential man-in-the-middle attack device detected. May be intercepting network traffic. Use VPN and verify HTTPS connections.",
            category = "Network Attack",
            surveillanceType = "Traffic Interception"
        )
        DeviceType.PACKET_SNIFFER -> DeviceInfo(
            description = "Network packet capture device detected. May be monitoring network traffic for reconnaissance or data exfiltration.",
            category = "Network Monitoring",
            surveillanceType = "Traffic Analysis"
        )

        // Drones
        DeviceType.DRONE -> DeviceInfo(
            description = "Aerial drone/UAV detected via WiFi signal. Could be recreational, commercial, or surveillance-related. Drones can carry cameras, thermal sensors, and other surveillance equipment.",
            category = "Aerial Surveillance",
            surveillanceType = "Aerial Monitoring",
            legalFramework = "FAA regulations; privacy laws vary by state"
        )

        // Commercial Surveillance
        DeviceType.CCTV_CAMERA -> DeviceInfo(
            description = "Closed-circuit television camera. May be part of business or municipal surveillance system. Footage typically retained for days to months.",
            category = "Video Surveillance",
            surveillanceType = "Video Recording"
        )
        DeviceType.PTZ_CAMERA -> DeviceInfo(
            description = "Pan-tilt-zoom camera with remote control capabilities. Can actively track subjects and provide detailed surveillance coverage.",
            category = "Video Surveillance",
            surveillanceType = "Active Video Tracking"
        )
        DeviceType.THERMAL_CAMERA -> DeviceInfo(
            description = "Thermal/infrared camera that can see heat signatures through walls, detect people in darkness, and identify concealed individuals.",
            category = "Thermal Surveillance",
            surveillanceType = "Thermal Imaging",
            legalFramework = "Kyllo v. US restricts warrantless thermal imaging of homes"
        )
        DeviceType.NIGHT_VISION -> DeviceInfo(
            description = "Night vision device capable of surveillance in low-light conditions. May be handheld or camera-mounted.",
            category = "Night Surveillance",
            surveillanceType = "Low-Light Monitoring"
        )
        DeviceType.HIDDEN_CAMERA -> DeviceInfo(
            description = "Covert camera detected. May be hidden in everyday objects. Check for recording devices in private spaces.",
            category = "Covert Surveillance",
            surveillanceType = "Hidden Video Recording",
            legalFramework = "Generally illegal in private spaces without consent"
        )

        // Retail & Commercial Tracking
        DeviceType.BLUETOOTH_BEACON -> DeviceInfo(
            description = "Bluetooth beacon for indoor positioning and tracking. Used in retail stores to track customer movements and send targeted advertisements.",
            category = "Retail Tracking",
            surveillanceType = "Indoor Location Tracking"
        )
        DeviceType.RETAIL_TRACKER -> DeviceInfo(
            description = "Retail tracking device for customer analytics. Monitors shopping patterns, dwell time, and movement through stores.",
            category = "Retail Analytics",
            surveillanceType = "Customer Tracking"
        )
        DeviceType.CROWD_ANALYTICS -> DeviceInfo(
            description = "Crowd analytics sensor for counting and tracking people. May use WiFi probe requests, cameras, or other sensors to monitor crowds.",
            category = "People Counting",
            surveillanceType = "Crowd Monitoring"
        )

        // Facial Recognition
        DeviceType.FACIAL_RECOGNITION -> DeviceInfo(
            description = "Facial recognition system detected. Captures and analyzes faces for identification. May be connected to law enforcement databases.",
            category = "Biometric Surveillance",
            surveillanceType = "Facial Recognition",
            typicalOperator = "Law enforcement, businesses, venues",
            legalFramework = "Banned in some cities; BIPA in Illinois"
        )
        DeviceType.CLEARVIEW_AI -> DeviceInfo(
            description = "Clearview AI facial recognition system. Uses scraped social media photos to identify individuals. Highly controversial with 30+ billion face database.",
            category = "Biometric Surveillance",
            surveillanceType = "Facial Recognition",
            typicalOperator = "Law enforcement",
            legalFramework = "Banned in several countries; multiple lawsuits pending"
        )

        // Law Enforcement Specific
        DeviceType.BODY_CAMERA -> DeviceInfo(
            description = "Police body-worn camera detected. Records video and audio of interactions. Footage may be subject to FOIA requests.",
            category = "Body Camera",
            surveillanceType = "Video/Audio Recording",
            typicalOperator = "Law enforcement"
        )
        DeviceType.POLICE_RADIO -> DeviceInfo(
            description = "Police radio system detected. Indicates law enforcement presence in the area.",
            category = "Communications",
            surveillanceType = "Radio Communications",
            typicalOperator = "Law enforcement"
        )
        DeviceType.POLICE_VEHICLE -> DeviceInfo(
            description = "Police or emergency vehicle wireless system detected. May include ALPR, mobile data terminals, and radio equipment.",
            category = "Mobile Surveillance",
            surveillanceType = "Vehicle-based Monitoring",
            typicalOperator = "Law enforcement"
        )
        DeviceType.MOTOROLA_POLICE_TECH -> DeviceInfo(
            description = "Motorola Solutions law enforcement technology detected. May include radios, body cameras, or command systems.",
            category = "Law Enforcement Tech",
            surveillanceType = "Police Technology"
        )
        DeviceType.AXON_POLICE_TECH -> DeviceInfo(
            description = "Axon (formerly Taser) law enforcement technology. May include body cameras, Tasers, or fleet management systems.",
            category = "Law Enforcement Tech",
            surveillanceType = "Police Technology"
        )
        DeviceType.PALANTIR_DEVICE -> DeviceInfo(
            description = "Palantir data integration system. Powerful analytics platform used by law enforcement to aggregate and analyze data from multiple sources.",
            category = "Data Analytics",
            surveillanceType = "Data Aggregation",
            typicalOperator = "Law enforcement, intelligence agencies"
        )

        // Military/Government
        DeviceType.L3HARRIS_SURVEILLANCE -> DeviceInfo(
            description = "L3Harris surveillance technology detected. Major defense contractor providing military-grade surveillance, communications, and intelligence equipment.",
            category = "Military Surveillance",
            surveillanceType = "Advanced Surveillance"
        )

        // Ultrasonic
        DeviceType.ULTRASONIC_BEACON -> DeviceInfo(
            description = "Ultrasonic tracking beacon detected. Uses inaudible sound (18-22 kHz) to track users across devices. " +
                "Detection analysis includes: amplitude fingerprinting (steady vs pulsing vs modulated patterns), " +
                "source attribution against known beacon types (SilverPush, Alphonso, Signal360, LISNR, Shopkick), " +
                "cross-location correlation to detect beacons following the user across multiple locations, " +
                "signal-to-noise ratio analysis, and tracking likelihood scoring (0-100%). Key indicators: " +
                "same frequency/amplitude profile detected at multiple distinct locations, pulsing amplitude " +
                "patterns matching known ad-tech signatures, and persistence score indicating dedicated tracking. " +
                "Often used for advertising attribution and cross-device identity resolution.",
            category = "Cross-Device Tracking",
            surveillanceType = "Ultrasonic Tracking",
            legalFramework = "FTC has taken action against undisclosed tracking"
        )

        // Satellite
        DeviceType.SATELLITE_NTN -> DeviceInfo(
            description = "Non-terrestrial network (satellite) device detected. Could be legitimate satellite connectivity or spoofed signal.",
            category = "Satellite Communication",
            surveillanceType = "Satellite Monitoring"
        )

        // GNSS Threats
        DeviceType.GNSS_SPOOFER -> DeviceInfo(
            description = "GPS/GNSS spoofing device detected. Transmits fake satellite signals to manipulate location data. " +
                "Detection analysis includes: constellation fingerprinting (expected vs observed GPS/GLONASS/Galileo/BeiDou), " +
                "C/N0 baseline deviation (abnormal signal strength uniformity indicates fake signals), " +
                "clock drift accumulation tracking (spoofed signals often show erratic drift patterns), " +
                "satellite geometry analysis (spoofed signals may show unnaturally uniform spacing or angles), " +
                "and composite spoofing likelihood scoring (0-100%). Key indicators: missing expected constellations, " +
                "C/N0 values deviating >2 sigma from baseline, erratic clock drift trends, and unnaturally uniform signal strengths. " +
                "Your reported position may be inaccurate.",
            category = "GNSS Attack",
            surveillanceType = "Location Manipulation",
            legalFramework = "Federal crime to interfere with GPS signals"
        )
        DeviceType.GNSS_JAMMER -> DeviceInfo(
            description = "GPS/GNSS jamming device detected. Blocks legitimate satellite signals, preventing accurate positioning.",
            category = "GNSS Attack",
            surveillanceType = "Signal Denial",
            legalFramework = "Federal crime under Communications Act"
        )

        // RF Threats
        DeviceType.RF_JAMMER -> DeviceInfo(
            description = "RF jamming device detected. Blocks wireless communications in the area. May affect cellular, WiFi, and GPS signals.",
            category = "Signal Jamming",
            surveillanceType = "Communications Denial",
            legalFramework = "Illegal under FCC regulations"
        )
        DeviceType.HIDDEN_TRANSMITTER -> DeviceInfo(
            description = "Hidden RF transmitter detected. Could be a covert listening device (bug) or other surveillance equipment.",
            category = "Covert Surveillance",
            surveillanceType = "Audio/Video Transmission"
        )
        DeviceType.RF_INTERFERENCE -> DeviceInfo(
            description = "Significant RF interference detected. May indicate jamming, environmental factors, or equipment malfunction.",
            category = "RF Anomaly",
            surveillanceType = "Signal Analysis"
        )
        DeviceType.RF_ANOMALY -> DeviceInfo(
            description = "Unusual RF activity pattern detected indicating potential covert surveillance infrastructure. " +
                "Analysis shows anomalous hidden WiFi network characteristics including signal patterns, " +
                "manufacturer clustering, temporal behavior, and channel distribution that deviate from " +
                "typical residential or commercial environments. Hidden networks with stronger signals than " +
                "visible ones, low signal variance (same hardware), known surveillance vendor OUIs, or " +
                "simultaneous appearance patterns are strong indicators of coordinated surveillance deployment.",
            category = "RF Anomaly",
            surveillanceType = "Signal Analysis",
            typicalOperator = "Law enforcement, private investigators, corporate security, government agencies",
            legalFramework = "Varies by jurisdiction; covert surveillance generally requires warrants"
        )

        // Fleet/Commercial Vehicles
        DeviceType.FLEET_VEHICLE -> DeviceInfo(
            description = "Commercial fleet vehicle tracking system detected. May include GPS tracking, cameras, and telemetry systems.",
            category = "Fleet Management",
            surveillanceType = "Vehicle Tracking"
        )
        DeviceType.SURVEILLANCE_VAN -> DeviceInfo(
            description = "Possible mobile surveillance van detected. May contain advanced monitoring equipment including IMSI catchers, cameras, or listening devices.",
            category = "Mobile Surveillance",
            surveillanceType = "Multi-Modal Surveillance"
        )

        // Misc Surveillance
        DeviceType.SURVEILLANCE_INFRASTRUCTURE -> DeviceInfo(
            description = "General surveillance infrastructure detected. May be part of a larger monitoring system.",
            category = "Infrastructure",
            surveillanceType = "General Surveillance"
        )
        DeviceType.TRACKING_DEVICE -> DeviceInfo(
            description = "Generic tracking device detected. May be used for asset tracking or personal surveillance.",
            category = "Tracking",
            surveillanceType = "Location Tracking"
        )

        // Vendor Specific
        DeviceType.PENGUIN_SURVEILLANCE -> DeviceInfo(
            description = "Penguin Surveillance system detected. Commercial surveillance platform.",
            category = "Commercial Surveillance",
            surveillanceType = "Video Surveillance"
        )
        DeviceType.PIGVISION_SYSTEM -> DeviceInfo(
            description = "Pigvision surveillance system detected. Agricultural/industrial monitoring system.",
            category = "Commercial Surveillance",
            surveillanceType = "Industrial Monitoring"
        )

        // Flipper Zero and Hacking Tools
        DeviceType.FLIPPER_ZERO -> DeviceInfo(
            description = "Flipper Zero multi-tool hacking device detected. Capable of interacting with Sub-GHz, RFID, NFC, IR, and BLE protocols. Can clone access cards, capture garage door signals, and perform BLE attacks. May be used for legitimate security research or malicious purposes.",
            category = "Hacking Tool",
            surveillanceType = "Multi-Protocol Attack Tool",
            typicalOperator = "Security researchers, pentesters, hobbyists, or malicious actors",
            legalFramework = "Device itself is legal; usage for unauthorized access is illegal"
        )
        DeviceType.FLIPPER_ZERO_SPAM -> DeviceInfo(
            description = "Active Flipper Zero BLE spam attack detected. Device is flooding Bluetooth with fake device advertisements causing popup floods on iPhones or notification spam on Android. This is malicious use with no legitimate purpose.",
            category = "Active Attack",
            surveillanceType = "BLE Spam Attack",
            typicalOperator = "Malicious actor",
            legalFramework = "May violate computer fraud laws, harassment statutes, or FCC regulations"
        )
        DeviceType.HACKRF_SDR -> DeviceInfo(
            description = "Software Defined Radio (HackRF or similar) detected. Capable of wide-spectrum RF reception and transmission. Used for RF research, amateur radio, and security testing.",
            category = "RF Analysis Tool",
            surveillanceType = "RF Monitoring",
            typicalOperator = "Radio hobbyists, security researchers"
        )
        DeviceType.PROXMARK -> DeviceInfo(
            description = "Proxmark RFID/NFC research tool detected. Powerful device for reading, writing, and emulating RFID/NFC cards. Can clone access cards and building badges.",
            category = "RFID/NFC Tool",
            surveillanceType = "Card Cloning",
            typicalOperator = "Security researchers, physical pentesters",
            legalFramework = "Cloning cards without authorization is illegal"
        )
        DeviceType.USB_RUBBER_DUCKY -> DeviceInfo(
            description = "USB Rubber Ducky keystroke injection device detected. Looks like USB drive but acts as keyboard, injecting pre-programmed keystrokes at high speed.",
            category = "USB Attack Tool",
            surveillanceType = "Keystroke Injection",
            legalFramework = "Unauthorized use is computer fraud"
        )
        DeviceType.BASH_BUNNY -> DeviceInfo(
            description = "Hak5 Bash Bunny USB attack platform detected. Advanced multi-function USB attack tool capable of keystroke injection, network attacks, and data exfiltration.",
            category = "USB Attack Tool",
            surveillanceType = "Multi-Function USB Attack"
        )
        DeviceType.LAN_TURTLE -> DeviceInfo(
            description = "Hak5 LAN Turtle covert network access device detected. Appears as USB ethernet adapter but provides persistent remote access to networks.",
            category = "Network Attack Tool",
            surveillanceType = "Covert Network Access"
        )
        DeviceType.KEYCROC -> DeviceInfo(
            description = "Hak5 Key Croc inline keylogger detected. Captures all keystrokes and exfiltrates them over WiFi. Sits between keyboard and computer.",
            category = "Keylogger",
            surveillanceType = "Keystroke Capture"
        )
        DeviceType.SHARK_JACK -> DeviceInfo(
            description = "Hak5 Shark Jack portable network attack tool detected. Pocket-sized device for network reconnaissance and attacks.",
            category = "Network Attack Tool",
            surveillanceType = "Network Reconnaissance"
        )
        DeviceType.SCREEN_CRAB -> DeviceInfo(
            description = "Hak5 Screen Crab HDMI interception device detected. Captures screenshots from HDMI video stream and exfiltrates over WiFi.",
            category = "Video Interception",
            surveillanceType = "Screen Capture"
        )
        DeviceType.GENERIC_HACKING_TOOL -> DeviceInfo(
            description = "Security testing or hacking tool detected. Device matches patterns associated with penetration testing equipment.",
            category = "Hacking Tool",
            surveillanceType = "Security Testing"
        )

        // Catch-all
        DeviceType.UNKNOWN_SURVEILLANCE -> DeviceInfo(
            description = "Unknown surveillance device detected based on wireless signature patterns. Unable to determine specific type, but characteristics suggest surveillance capability.",
            category = "Unknown",
            surveillanceType = "Unknown"
        )
    }
}

// ==================== DATA COLLECTION & RISK ====================

/**
 * Get data collection capabilities for each device type.
 * Uses DeviceTypeProfileRegistry (centralized profile system) as primary source.
 * HandlerProfile has 'capabilities' which is more generic; we prefer the detailed
 * 'dataCollected' from the centralized profile.
 */
internal fun getDataCollectionCapabilities(deviceType: DeviceType, detectionRegistry: DetectionRegistry): List<String> {
    // Primary: Use DeviceTypeProfileRegistry for detailed data collection info
    val centralizedProfile = DeviceTypeProfileRegistry.getProfile(deviceType)
    if (centralizedProfile.dataCollected.isNotEmpty()) {
        return centralizedProfile.dataCollected
    }

    // Fallback: Try handler profile's capabilities as approximation
    val handlerProfile = detectionRegistry.getProfile(deviceType)
    if (handlerProfile != null && handlerProfile.capabilities.isNotEmpty()) {
        return handlerProfile.capabilities
    }

    // Default fallback for unknown device types
    return listOf(
        "Device-specific data collection varies",
        "May include location and identifiers",
        "Behavioral patterns possible",
        "Check device documentation"
    )
}

@Suppress("unused")
internal fun getRiskAssessment(detection: Detection): String {
    return when (detection.threatLevel) {
        ThreatLevel.CRITICAL -> "CRITICAL RISK: This device poses immediate and significant privacy concerns. It can actively collect sensitive personal data, intercept communications, or perform invasive surveillance. Take protective measures immediately."
        ThreatLevel.HIGH -> "HIGH RISK: This surveillance device can collect identifying information, track your movements, or record your activities. Data may be stored indefinitely and shared with law enforcement or third parties without your knowledge."
        ThreatLevel.MEDIUM -> "MODERATE RISK: This device collects data that could be used for tracking or profiling. While not immediately dangerous, prolonged exposure or pattern analysis could reveal sensitive information about your habits."
        ThreatLevel.LOW -> "LOW RISK: This device has limited surveillance capabilities. It may collect some metadata but poses minimal immediate privacy concerns for most users."
        ThreatLevel.INFO -> "INFORMATIONAL: Device detected but poses minimal direct privacy risk. May be standard infrastructure or consumer electronics."
    }
}

internal fun calculateRiskScore(detection: Detection, context: ContextualInsights?): Int {
    var score = detection.threatScore

    // Adjust based on context
    context?.let {
        if (it.clusterInfo != null) score += 10 // Part of surveillance cluster
        if (it.historicalContext?.contains("detected") == true) {
            val times = Regex("(\\d+) times").find(it.historicalContext)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            score += minOf(times * 2, 15) // More frequent = higher risk
        }
    }

    return score.coerceIn(0, 100)
}

internal fun getRiskFactors(detection: Detection, context: ContextualInsights?): List<String> {
    val factors = mutableListOf<String>()

    when (detection.threatLevel) {
        ThreatLevel.CRITICAL -> factors.add("Active surveillance capability")
        ThreatLevel.HIGH -> factors.add("Confirmed surveillance device")
        else -> {}
    }

    if (detection.signalStrength.ordinal <= 1) { // Excellent or Good
        factors.add("Close proximity (strong signal)")
    }

    context?.let {
        if (it.clusterInfo != null) factors.add("Part of surveillance network")
        if (it.isKnownLocation) factors.add("Persistent presence at location")
    }

    if (detection.seenCount > 5) {
        factors.add("Repeatedly detected (${detection.seenCount} times)")
    }

    return factors
}

internal fun getSmartRecommendations(detection: Detection, context: ContextualInsights?, detectionRegistry: DetectionRegistry): List<String> {
    val recommendations = mutableListOf<String>()

    // Threat-level based recommendations (dynamic based on detection context)
    when (detection.threatLevel) {
        ThreatLevel.CRITICAL -> {
            recommendations.add("Consider leaving the area immediately if safety allows")
            recommendations.add("Enable airplane mode or use a Faraday bag for your devices")
            recommendations.add("Use only end-to-end encrypted communications")
            recommendations.add("Document this detection with timestamp and location")
        }
        ThreatLevel.HIGH -> {
            recommendations.add("Be aware that your presence/vehicle is being recorded")
            recommendations.add("Consider varying your routes and patterns")
            recommendations.add("Use VPN and encrypted messaging apps")
        }
        ThreatLevel.MEDIUM -> {
            recommendations.add("Note this location for future awareness")
            recommendations.add("Review privacy settings on your devices")
        }
        ThreatLevel.LOW, ThreatLevel.INFO -> {
            recommendations.add("No immediate action required")
        }
    }

    // Device-specific recommendations from profile system
    val profileRecommendations = getProfileRecommendations(detection.deviceType, detectionRegistry)
    recommendations.addAll(profileRecommendations)

    // Context-based recommendations
    context?.let {
        if (it.clusterInfo != null) {
            recommendations.add("This is a high-surveillance area - multiple devices detected")
        }
    }

    return recommendations.distinct().take(6)
}

/**
 * Get device-specific recommendations from the profile system.
 * Uses DeviceTypeProfileRegistry (centralized profile) as primary source.
 * HandlerProfile has 'mitigationAdvice' (string) which we can use as fallback.
 */
internal fun getProfileRecommendations(deviceType: DeviceType, detectionRegistry: DetectionRegistry): List<String> {
    // Primary: Use DeviceTypeProfileRegistry for structured recommendations
    val centralizedProfile = DeviceTypeProfileRegistry.getProfile(deviceType)
    if (centralizedProfile.recommendations.isNotEmpty()) {
        return centralizedProfile.recommendations
            .sortedBy { it.priority }
            .map { it.action }
    }

    // Fallback: Try handler profile's mitigationAdvice
    val handlerProfile = detectionRegistry.getProfile(deviceType)
    if (handlerProfile != null && handlerProfile.mitigationAdvice.isNotEmpty()) {
        // Split mitigation advice into individual recommendations if it contains multiple sentences
        return handlerProfile.mitigationAdvice
            .split(". ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    // Empty list if no recommendations found
    return emptyList()
}

// ==================== BATCH ANALYSIS ====================

/**
 * Batch analysis for surveillance density mapping.
 */
internal suspend fun performBatchAnalysisInternal(
    detections: List<Detection>,
    aiSettingsRepository: AiSettingsRepository
): BatchAnalysisResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    try {
        val settings = aiSettingsRepository.settingsSnapshot()
        if (!settings.enabled || !settings.enableBatchAnalysis) {
            return@withContext BatchAnalysisResult(
                success = false,
                totalDevicesAnalyzed = 0,
                surveillanceDensityScore = 0,
                hotspots = emptyList(),
                anomalies = emptyList(),
                processingTimeMs = 0,
                error = "Batch analysis is disabled"
            )
        }

        // Find clusters/hotspots
        val hotspots = findSurveillanceHotspots(detections)

        // Calculate density score
        val densityScore = calculateDensityScore(detections, hotspots)

        // Find anomalies
        val anomalies = detectAnomalies(detections)

        BatchAnalysisResult(
            success = true,
            totalDevicesAnalyzed = detections.size,
            surveillanceDensityScore = densityScore,
            hotspots = hotspots,
            anomalies = anomalies,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    } catch (e: Exception) {
        Log.e(TAG, "Batch analysis failed", e)
        BatchAnalysisResult(
            success = false,
            totalDevicesAnalyzed = 0,
            surveillanceDensityScore = 0,
            hotspots = emptyList(),
            anomalies = listOf(e.message ?: "Unknown error"),
            processingTimeMs = System.currentTimeMillis() - startTime,
            error = e.message
        )
    }
}

internal fun findSurveillanceHotspots(detections: List<Detection>): List<SurveillanceHotspot> {
    val geoDetections = detections.filter { it.latitude != null && it.longitude != null }
    if (geoDetections.isEmpty()) return emptyList()

    val hotspots = mutableListOf<SurveillanceHotspot>()
    val processed = mutableSetOf<String>()

    for (detection in geoDetections) {
        if (detection.id in processed) continue

        val nearby = geoDetections.filter { other ->
            other.id !in processed &&
            calculateDistanceMeters(
                detection.latitude!!, detection.longitude!!,
                other.latitude!!, other.longitude!!
            ) < CLUSTER_RADIUS_METERS
        }

        if (nearby.size >= 2) {
            // Found a cluster
            nearby.forEach { processed.add(it.id) }

            val avgLat = nearby.mapNotNull { it.latitude }.average()
            val avgLon = nearby.mapNotNull { it.longitude }.average()
            val dominantType = nearby.groupBy { it.deviceType }
                .maxByOrNull { it.value.size }?.key ?: DeviceType.UNKNOWN_SURVEILLANCE
            val maxThreat = nearby.maxOfOrNull { it.threatLevel.ordinal } ?: 0

            hotspots.add(SurveillanceHotspot(
                latitude = avgLat,
                longitude = avgLon,
                radiusMeters = CLUSTER_RADIUS_METERS.toInt(),
                deviceCount = nearby.size,
                threatLevel = ThreatLevel.entries[maxThreat].displayName,
                dominantDeviceType = dominantType.displayName
            ))
        }
    }

    return hotspots.sortedByDescending { it.deviceCount }
}

internal fun calculateDensityScore(detections: List<Detection>, hotspots: List<SurveillanceHotspot>): Int {
    if (detections.isEmpty()) return 0

    var score = 0

    // Base score from device count
    score += minOf(detections.size * 5, 30)

    // Score from threat levels
    score += detections.count { it.threatLevel == ThreatLevel.CRITICAL } * 15
    score += detections.count { it.threatLevel == ThreatLevel.HIGH } * 10
    score += detections.count { it.threatLevel == ThreatLevel.MEDIUM } * 5

    // Score from clusters
    score += hotspots.size * 10
    score += hotspots.sumOf { minOf(it.deviceCount, 10) }

    return score.coerceIn(0, 100)
}

internal fun detectAnomalies(detections: List<Detection>): List<String> {
    val anomalies = mutableListOf<String>()

    // Check for unusual patterns
    val recentDetections = detections.filter {
        System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000
    }

    if (recentDetections.count { it.threatLevel == ThreatLevel.CRITICAL } > 2) {
        anomalies.add("Multiple critical-level devices detected in 24 hours")
    }

    val imsiCatchers = recentDetections.count { it.deviceType == DeviceType.STINGRAY_IMSI }
    if (imsiCatchers > 0) {
        anomalies.add("Cell-site simulator activity detected")
    }

    val trackers = recentDetections.filter {
        it.deviceType in listOf(DeviceType.AIRTAG, DeviceType.TILE_TRACKER, DeviceType.SAMSUNG_SMARTTAG)
    }
    if (trackers.size > 1) {
        anomalies.add("Multiple personal trackers detected - possible tracking attempt")
    }

    return anomalies
}

// ==================== THREAT ASSESSMENT ====================

/**
 * Generate threat assessment for environment.
 */
internal suspend fun generateThreatAssessmentInternal(
    detections: List<Detection>,
    criticalCount: Int,
    highCount: Int,
    mediumCount: Int,
    lowCount: Int,
    aiSettingsRepository: AiSettingsRepository,
    currentModelId: String,
    setAnalyzing: (Boolean) -> Unit
): AiAnalysisResult = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    try {
        val settings = aiSettingsRepository.settingsSnapshot()
        if (!settings.enabled || !settings.generateThreatAssessments) {
            return@withContext AiAnalysisResult(
                success = false,
                error = "Threat assessment is disabled"
            )
        }

        setAnalyzing(true)

        val overallLevel = when {
            criticalCount > 0 -> "CRITICAL"
            highCount > 2 -> "HIGH"
            highCount > 0 || mediumCount > 3 -> "ELEVATED"
            mediumCount > 0 -> "MODERATE"
            else -> "LOW"
        }

        val assessment = buildString {
            appendLine("## Environment Threat Assessment")
            appendLine()
            appendLine("### Overall Level: $overallLevel")
            appendLine()
            appendLine("### Summary")
            appendLine("- Total devices: ${detections.size}")
            if (criticalCount > 0) appendLine("- Critical: $criticalCount")
            if (highCount > 0) appendLine("- High: $highCount")
            if (mediumCount > 0) appendLine("- Medium: $mediumCount")
            if (lowCount > 0) appendLine("- Low/Info: $lowCount")
            appendLine()

            if (criticalCount > 0 || highCount > 0) {
                appendLine("### Priority Concerns")
                detections.filter { it.threatLevel in listOf(ThreatLevel.CRITICAL, ThreatLevel.HIGH) }
                    .take(5)
                    .forEach { appendLine("- ${it.deviceType.displayName} (${it.threatLevel.displayName})") }
                appendLine()
            }

            appendLine("### Recommendations")
            when (overallLevel) {
                "CRITICAL" -> {
                    appendLine("1. Exercise extreme caution - active surveillance detected")
                    appendLine("2. Consider limiting electronic device usage")
                    appendLine("3. Use only encrypted communications")
                    appendLine("4. Document all detections")
                }
                "HIGH" -> {
                    appendLine("1. Be aware of active surveillance in this area")
                    appendLine("2. Review your digital privacy practices")
                    appendLine("3. Consider your exposure to data collection")
                }
                else -> {
                    appendLine("1. Standard privacy precautions recommended")
                    appendLine("2. Continue monitoring for changes")
                }
            }
        }

        AiAnalysisResult(
            success = true,
            threatAssessment = assessment,
            processingTimeMs = System.currentTimeMillis() - startTime,
            modelUsed = currentModelId,
            wasOnDevice = true
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error generating threat assessment", e)
        AiAnalysisResult(
            success = false,
            error = e.message,
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    } finally {
        setAnalyzing(false)
    }
}

// ==================== SHARED UTILITY ====================

/** Shared constant for cluster radius in meters */
internal const val CLUSTER_RADIUS_METERS = 100.0

/**
 * Calculate distance between two geographic coordinates using Haversine formula.
 * Returns distance in meters.
 */
internal fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

// ==================== STRUCTURED DATA BUILDING ====================

/**
 * Build structured analysis data for a detection result.
 */
internal fun buildStructuredData(
    detection: Detection,
    contextualInsights: ContextualInsights?,
    detectionRegistry: DetectionRegistry
): StructuredAnalysis {
    val deviceInfo = getComprehensiveDeviceInfo(detection.deviceType)
    val dataCollection = getDataCollectionCapabilities(detection.deviceType, detectionRegistry)
    val recommendations = getSmartRecommendations(detection, contextualInsights, detectionRegistry)

    return StructuredAnalysis(
        deviceCategory = deviceInfo.category,
        surveillanceType = deviceInfo.surveillanceType,
        dataCollectionTypes = dataCollection,
        riskScore = calculateRiskScore(detection, contextualInsights),
        riskFactors = getRiskFactors(detection, contextualInsights),
        mitigationActions = recommendations.mapIndexed { index, rec ->
            MitigationAction(
                action = rec,
                priority = when (index) {
                    0 -> ActionPriority.IMMEDIATE
                    1 -> ActionPriority.HIGH
                    else -> ActionPriority.MEDIUM
                },
                description = rec
            )
        },
        contextualInsights = contextualInsights
    )
}
