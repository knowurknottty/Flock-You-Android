package com.flockyou.ui.screens

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flockyou.config.NetworkConfig
import com.flockyou.data.FeasibilityData
import com.flockyou.data.model.*
import com.flockyou.detection.ThreatScoring
import com.flockyou.network.ShodanSearch
import com.flockyou.privilege.PrivilegeMode
import com.flockyou.ui.components.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionDetailSheet(
    detection: Detection,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onMarkSafe: () -> Unit = {},
    onMarkThreat: () -> Unit = {},
    onShare: () -> Unit = {},
    onNavigate: (() -> Unit)? = null,
    onAddNote: (String?) -> Unit = {},
    onExport: () -> Unit = {},
    advancedMode: Boolean = false,
    enrichedData: com.flockyou.ai.EnrichedDetectorData? = null,
    relatedDetections: List<Detection> = emptyList(),
    onRelatedDetectionClick: (Detection) -> Unit = {},
    onSeeAllRelatedClick: (() -> Unit)? = null,
    privilegeMode: PrivilegeMode? = null
) {
    val context = LocalContext.current
    val threatColor = detection.threatLevel.toColor()
    val deviceInfo = DetectionPatterns.getDeviceTypeInfo(detection.deviceType)
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }

    // State for the Add Note dialog
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(detection.userNote ?: "") }

    // Note Dialog
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            icon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
            title = { Text(if (detection.userNote != null) "Edit Note" else "Add Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note") },
                    placeholder = { Text("Add your notes about this detection...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddNote(noteText.takeIf { it.isNotBlank() })
                        showNoteDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            // New ThreatHeader component at the top
            item {
                ThreatHeader(
                    threatLevel = detection.threatLevel,
                    threatScore = detection.threatScore,
                    deviceType = detection.deviceType,
                    isActive = detection.isActive,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Detection Timeline
            item {
                DetectionTimeline(
                    firstSeen = detection.timestamp,
                    lastSeen = if (detection.lastSeenTimestamp != detection.timestamp) detection.lastSeenTimestamp else null,
                    events = if (detection.seenCount > 1) {
                        listOf(
                            TimelineEvent(timestamp = detection.timestamp, signalStrength = detection.rssi, isActive = false),
                            TimelineEvent(timestamp = detection.lastSeenTimestamp, signalStrength = detection.rssi, isActive = detection.isActive)
                        )
                    } else {
                        listOf(TimelineEvent(timestamp = detection.timestamp, signalStrength = detection.rssi, isActive = detection.isActive))
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Device Description - using CollapsibleSection
            item {
                CollapsibleSection(
                    title = "About This Device",
                    icon = Icons.Default.Info,
                    defaultExpanded = true,
                    persistKey = "detection_about_device"
                ) {
                    Text(
                        text = deviceInfo.fullDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Detection Reliability Note (feasibility-aware)
            if (privilegeMode != null) {
                val reliabilityNote = FeasibilityData.getDetectionReliabilityNote(detection, privilegeMode)
                if (reliabilityNote != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = reliabilityNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // AI Analysis Section (show if any FP analysis was performed - LLM or rule-based)
            if (detection.fpScore != null) {
                item {
                    CollapsibleSection(
                        title = if (detection.llmAnalyzed) "AI Analysis" else "Analysis",
                        icon = if (detection.llmAnalyzed) Icons.Default.AutoAwesome else Icons.Default.Psychology,
                        defaultExpanded = true,
                        persistKey = "detection_ai_analysis"
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Analysis type badge
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (detection.llmAnalyzed) Icons.Default.AutoAwesome else Icons.Default.Rule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (detection.llmAnalyzed) "On-device LLM" else "Rule-based",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    // Show "Pending LLM" indicator if not yet LLM analyzed
                                    if (!detection.llmAnalyzed) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = "LLM pending",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // FP verdict (fpScore is guaranteed non-null in this block)
                                Spacer(modifier = Modifier.height(10.dp))
                                val fpPercent = (detection.fpScore!! * 100).toInt()
                                val verdictText = when {
                                    fpPercent >= 70 -> "Likely false positive"
                                    fpPercent >= 40 -> "Possibly false positive"
                                    else -> "Likely genuine threat"
                                }
                                val verdictColor = when {
                                    fpPercent >= 70 -> MaterialTheme.colorScheme.tertiary
                                    fpPercent >= 40 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.error
                                }
                                val verdictIcon = when {
                                    fpPercent >= 70 -> Icons.Default.CheckCircle
                                    fpPercent >= 40 -> Icons.Default.Help
                                    else -> Icons.Default.Warning
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = verdictIcon,
                                        contentDescription = null,
                                        tint = verdictColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = verdictText,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = verdictColor
                                        )
                                        Text(
                                            text = "$fpPercent% confidence",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = verdictColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                // Confidence bar
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = detection.fpScore!!,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = verdictColor,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                                )

                                // AI-generated reason
                                detection.fpReason?.let { reason ->
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Analysis:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                // Category
                                detection.fpCategory?.let { category ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Label,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Category: ${category.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                // Analysis method and timestamp
                                detection.analyzedAt?.let { timestamp ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (detection.llmAnalyzed) Icons.Default.AutoAwesome else Icons.Default.Rule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (detection.llmAnalyzed) "AI analysis" else "Rule-based",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "• ${dateFormat.format(Date(timestamp))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Technical Details
            item {
                CollapsibleSection(
                    title = "Detection Details",
                    icon = Icons.Default.List,
                    defaultExpanded = true,
                    persistKey = "detection_details"
                ) {
                    Column {
                        DetailRow(
                            label = "First Detected",
                            value = dateFormat.format(Date(detection.timestamp))
                        )

                        if (detection.lastSeenTimestamp != detection.timestamp) {
                            DetailRow(
                                label = "Last Seen",
                                value = dateFormat.format(Date(detection.lastSeenTimestamp))
                            )
                        }

                        if (detection.seenCount > 1) {
                            DetailRow(
                                label = "Times Seen",
                                value = "${detection.seenCount}x"
                            )
                        }

                        DetailRow(
                            label = "Status",
                            value = if (detection.isActive) "Active" else "Inactive"
                        )

                        DetailRow(
                            label = "Protocol",
                            value = "${detection.protocol.icon} ${detection.protocol.displayName}"
                        )

                        DetailRow(
                            label = "Method",
                            value = detection.detectionMethod.displayName
                        )

                        // Show different fields based on protocol
                        if (detection.protocol == DetectionProtocol.CELLULAR) {
                            // Cellular-specific fields
                            detection.firmwareVersion?.let { cellId ->
                                DetailRow(label = "Cell Info", value = cellId)
                            }

                            detection.macAddress?.let { mccMnc ->
                                DetailRow(label = "MCC-MNC", value = mccMnc)
                            }

                            detection.manufacturer?.let { networkType ->
                                DetailRow(label = "Network Type", value = networkType)
                            }
                        } else {
                            // WiFi/BLE fields
                            detection.macAddress?.let { mac ->
                                DetailRow(label = "MAC Address", value = mac)
                            }

                            detection.ssid?.let { ssid ->
                                DetailRow(label = "SSID", value = ssid)
                            }

                            detection.manufacturer?.let { mfr ->
                                DetailRow(label = "Manufacturer", value = mfr)
                            }

                            detection.firmwareVersion?.let { fw ->
                                DetailRow(label = "Firmware", value = fw)
                            }
                        }

                        detection.deviceName?.let { name ->
                            DetailRow(label = "Device Name", value = name)
                        }

                        DetailRow(
                            label = "Signal",
                            value = "${detection.rssi} dBm (${detection.signalStrength.displayName})"
                        )

                        DetailRow(
                            label = "Est. Distance",
                            value = rssiToDistance(detection.rssi)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Location Section with Embedded Map
            if (detection.latitude != null && detection.longitude != null) {
                item {
                    val context = LocalContext.current

                    // Initialize osmdroid config
                    LaunchedEffect(Unit) {
                        Configuration.getInstance().apply {
                            userAgentValue = context.packageName
                        }
                    }

                    CollapsibleSection(
                        title = "Location",
                        icon = Icons.Default.LocationOn,
                        defaultExpanded = false,
                        persistKey = "detection_location"
                    ) {
                        Column {
                            // Coordinates display
                            Text(
                                text = "%.6f, %.6f".format(detection.latitude, detection.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Embedded OSM Map
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                val lat = detection.latitude!!
                                val lon = detection.longitude!!

                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        MapView(ctx).apply {
                                            setTileSource(DETAIL_SHEET_TILE_SOURCE)
                                            setMultiTouchControls(true)
                                            controller.setZoom(16.0)
                                            controller.setCenter(GeoPoint(lat, lon))

                                            // Add marker for detection location
                                            val marker = Marker(this).apply {
                                                position = GeoPoint(lat, lon)
                                                title = detection.deviceType.displayName
                                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                                icon = createDetailMapMarkerDrawable(detection.threatLevel)
                                            }
                                            overlays.add(marker)
                                        }
                                    },
                                    update = { map ->
                                        map.controller.setCenter(GeoPoint(lat, lon))
                                        map.invalidate()
                                    }
                                )

                                // OSM Attribution overlay
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = "\u00a9 OpenStreetMap",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Capabilities Section
            if (deviceInfo.capabilities.isNotEmpty()) {
                item {
                    CollapsibleSection(
                        title = "Known Capabilities",
                        icon = Icons.Default.Build,
                        defaultExpanded = false,
                        persistKey = "detection_capabilities",
                        badge = "${deviceInfo.capabilities.size}"
                    ) {
                        Column {
                            deviceInfo.capabilities.forEach { capability ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "\u2022",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = capability,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Privacy Concerns Section
            if (deviceInfo.privacyConcerns.isNotEmpty()) {
                item {
                    CollapsibleSection(
                        title = "Privacy Concerns",
                        icon = Icons.Default.PrivacyTip,
                        defaultExpanded = false,
                        persistKey = "detection_privacy_concerns",
                        badge = "${deviceInfo.privacyConcerns.size}"
                    ) {
                        Column {
                            deviceInfo.privacyConcerns.forEach { concern ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "\u2022",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = concern,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Recommendations Section
            if (deviceInfo.recommendations.isNotEmpty()) {
                item {
                    CollapsibleSection(
                        title = "What You Can Do",
                        icon = Icons.Default.Lightbulb,
                        defaultExpanded = false,
                        persistKey = "detection_recommendations",
                        badge = "${deviceInfo.recommendations.size}"
                    ) {
                        Column {
                            deviceInfo.recommendations.forEach { recommendation ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = recommendation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Heuristics & Scoring Analysis Section (visible in all modes, collapsed by default in simple mode)
            run {
                item {
                    CollapsibleSection(
                        title = "Heuristics Analysis",
                        icon = Icons.Default.Analytics,
                        defaultExpanded = false,
                        persistKey = "detection_heuristics"
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                            // Impact Factor Analysis
                            val impactFactor = remember(detection.deviceType) {
                                ThreatScoring.getImpactFactor(detection.deviceType)
                            }
                            val impactDescription = when {
                                impactFactor >= 2.0 -> "Critical - Can intercept all communications"
                                impactFactor >= 1.8 -> "Severe - Can cause physical harm"
                                impactFactor >= 1.5 -> "High - Stalking/tracking risk"
                                impactFactor >= 1.2 -> "Moderate - Privacy violation"
                                impactFactor >= 1.0 -> "Standard - Known surveillance"
                                impactFactor >= 0.7 -> "Low - Consumer IoT device"
                                else -> "Minimal - Infrastructure/Traffic"
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Impact Factor",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when {
                                            impactFactor >= 1.8 -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                            impactFactor >= 1.2 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        }
                                    ) {
                                        Text(
                                            text = "%.1fx".format(impactFactor),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = when {
                                                impactFactor >= 1.8 -> MaterialTheme.colorScheme.error
                                                impactFactor >= 1.2 -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = impactDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Signal Confidence Analysis
                            val signalConfidenceData = remember(detection.rssi) {
                                when {
                                    detection.rssi > -50 -> Pair("Excellent", "+10%")
                                    detection.rssi > -60 -> Pair("Good", "+5%")
                                    detection.rssi > -80 -> Pair("Medium", "\u00b10%")
                                    detection.rssi > -90 -> Pair("Weak", "-10%")
                                    else -> Pair("Very Weak", "-20%")
                                }
                            }
                            val signalConfidenceColor = when {
                                detection.rssi > -60 -> MaterialTheme.colorScheme.primary
                                detection.rssi > -80 -> MaterialTheme.colorScheme.onSurfaceVariant
                                detection.rssi > -90 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Signal Confidence",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${signalConfidenceData.first} (${signalConfidenceData.second})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = signalConfidenceColor
                                    )
                                }
                                Text(
                                    text = "Based on RSSI: ${detection.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Persistence Analysis
                            val persistenceBonus = remember(detection.seenCount) {
                                when {
                                    detection.seenCount >= 10 -> Triple("High Persistence", "+20%", "Seen frequently - increased confidence")
                                    detection.seenCount >= 5 -> Triple("Moderate Persistence", "+10%", "Multiple sightings confirm presence")
                                    detection.seenCount >= 2 -> Triple("Low Persistence", "+5%", "Seen more than once")
                                    else -> Triple("Single Detection", "-20%", "Only seen once - lower confidence")
                                }
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Persistence Factor",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${persistenceBonus.first} (${persistenceBonus.second})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (detection.seenCount > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(
                                    text = "${persistenceBonus.third} (seen ${detection.seenCount}x)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // Detection Method Analysis
                            Column {
                                Text(
                                    text = "Detection Method",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = detection.detectionMethod.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = detection.detectionMethod.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Matched Patterns (if available) - rendered as human-readable chips
                            val parsedPatterns = remember(detection.matchedPatterns) {
                                com.flockyou.ui.components.parseMatchedPatterns(detection.matchedPatterns)
                            }
                            if (parsedPatterns.isNotEmpty()) {
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Column {
                                        Text(
                                            text = "Matched Patterns",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            parsedPatterns.forEach { (type, value) ->
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                ) {
                                                    Text(
                                                        text = "$type: $value",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                            }

                            // Score Calculation Summary
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Column {
                                Text(
                                    text = "Score Calculation",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "threat_score = base_likelihood \u00d7 impact_factor \u00d7 confidence",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Final Score:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${detection.threatScore}/100 \u2192 ${detection.threatLevel.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = threatColor
                                    )
                                }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Enriched Analysis Section (only when enriched data available)
                if (enrichedData != null) {
                    item {
                        EnrichedDataSection(enrichedData = enrichedData)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Advanced Mode: Raw Technical Data Section
                item {
                    CollapsibleSection(
                        title = "Raw Technical Data",
                        icon = Icons.Default.Code,
                        defaultExpanded = false,
                        persistKey = "detection_raw_data"
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Detection ID
                                Text(
                                    text = "Detection ID:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = detection.id,
                                    style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Protocol and Method
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Protocol:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detection.protocol.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Method:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detection.detectionMethod.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Device Type
                            Text(
                                text = "Device Type:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = detection.deviceType.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )

                            // Threat Score
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Threat Level:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detection.threatLevel.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = threatColor
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Threat Score:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${detection.threatScore}/100",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = threatColor
                                    )
                                }
                            }

                            // Signal Strength Raw
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "RSSI:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${detection.rssi} dBm",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Signal Category:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detection.signalStrength.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Timestamps
                            Text(
                                text = "First Seen (Unix):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = detection.timestamp.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )

                            Text(
                                text = "Last Seen (Unix):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = detection.lastSeenTimestamp.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )

                            // Seen Count and Active Status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Seen Count:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detection.seenCount.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Is Active:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detection.isActive.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Service UUIDs if present
                            detection.serviceUuids?.let { uuids ->
                                if (uuids.isNotEmpty() && uuids != "[]") {
                                    Text(
                                        text = "Service UUIDs:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = uuids,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Matched Patterns if present
                            detection.matchedPatterns?.let { patterns ->
                                if (patterns.isNotEmpty() && patterns != "[]") {
                                    Text(
                                        text = "Matched Patterns:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = patterns,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                                // Location coordinates if present
                                if (detection.latitude != null && detection.longitude != null) {
                                    Text(
                                        text = "Raw Coordinates:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "lat=${detection.latitude}, lng=${detection.longitude}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Explicit external enrichment. This only opens a browser search; it does not
            // treat local radio identifiers as a Shodan host or mutate scanner evidence.
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("External Intelligence", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Search Shodan in your browser using this detection's visible identifiers. " +
                                "Results are external research, not scanner-confirmed evidence.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ShodanSearch.buildSearchUrl(detection)))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Search Shodan")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Related Detections Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                RelatedDetectionsSection(
                    relatedDetections = relatedDetections,
                    onDetectionClick = onRelatedDetectionClick,
                    onSeeAllClick = onSeeAllRelatedClick ?: {},
                    showSeeAll = onSeeAllRelatedClick != null
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Sticky Action Bar at the bottom
        DetectionActionBar(
            onMarkSafe = onMarkSafe,
            onMarkThreat = onMarkThreat,
            onShare = onShare,
            onNavigate = onNavigate,
            onAddNote = { showNoteDialog = true },
            onExport = onExport,
            isSafeEnabled = detection.fpCategory != "USER_MARKED_FP",
            isThreatEnabled = !detection.confirmedThreat
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = if (label.contains("MAC") || label.contains("SSID")) FontFamily.Monospace else FontFamily.Default
        )
    }
}

/**
 * HTTPS tile source for OpenStreetMap embedded maps.
 * Tile server URLs are configurable via NetworkConfig for OEM customization.
 */
internal val DETAIL_SHEET_TILE_SOURCE: OnlineTileSourceBase by lazy {
    object : OnlineTileSourceBase(
        "Mapnik-HTTPS-Detail",
        0,
        19,
        256,
        ".png",
        NetworkConfig.MAP_TILE_SERVERS.toTypedArray()
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}$zoom/$x/$y$mImageFilenameEnding"
        }
    }
}

/**
 * Creates a marker drawable for the embedded map
 */
internal fun createDetailMapMarkerDrawable(threatLevel: ThreatLevel): android.graphics.drawable.Drawable {
    val color = when (threatLevel) {
        ThreatLevel.CRITICAL -> android.graphics.Color.parseColor("#D32F2F")
        ThreatLevel.HIGH -> android.graphics.Color.parseColor("#F57C00")
        ThreatLevel.MEDIUM -> android.graphics.Color.parseColor("#FBC02D")
        ThreatLevel.LOW -> android.graphics.Color.parseColor("#388E3C")
        ThreatLevel.INFO -> android.graphics.Color.parseColor("#1976D2")
    }

    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(3, android.graphics.Color.WHITE)
        setSize(32, 32)
    }
}

/**
 * Build a shareable text summary of a detection
 */
internal fun buildDetectionShareText(detection: Detection, context: android.content.Context): String {
    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault())
    return buildString {
        appendLine(context.getString(com.flockyou.R.string.share_detection_alert_title))
        appendLine("=" .repeat(30))
        appendLine()
        appendLine("Device: ${detection.deviceType.displayName}")
        appendLine("Threat Level: ${detection.threatLevel.displayName}")
        appendLine("Protocol: ${detection.protocol.displayName}")
        appendLine("Detection Method: ${detection.detectionMethod.displayName}")
        appendLine()
        appendLine("Signal: ${detection.rssi} dBm (${detection.signalStrength.displayName})")
        appendLine("First Seen: ${dateFormat.format(java.util.Date(detection.timestamp))}")
        if (detection.seenCount > 1) {
            appendLine("Times Seen: ${detection.seenCount}")
            appendLine("Last Seen: ${dateFormat.format(java.util.Date(detection.lastSeenTimestamp))}")
        }
        appendLine()
        detection.macAddress?.let { appendLine("MAC Address: $it") }
        detection.ssid?.let { appendLine("SSID: $it") }
        detection.deviceName?.let { appendLine("Device Name: $it") }
        detection.manufacturer?.let { appendLine("Manufacturer: $it") }
        if (detection.latitude != null && detection.longitude != null) {
            appendLine()
            appendLine("Location: ${detection.latitude}, ${detection.longitude}")
        }
        detection.userNote?.let {
            appendLine()
            appendLine("Note: $it")
        }
        appendLine()
        appendLine(context.getString(com.flockyou.R.string.share_detection_signature))
    }
}

/**
 * Build a JSON export of a detection
 */
internal fun buildDetectionExportJson(detection: Detection): String {
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.getDefault())
    return buildString {
        appendLine("{")
        appendLine("  \"id\": \"${detection.id}\",")
        appendLine("  \"timestamp\": \"${dateFormat.format(java.util.Date(detection.timestamp))}\",")
        appendLine("  \"lastSeenTimestamp\": \"${dateFormat.format(java.util.Date(detection.lastSeenTimestamp))}\",")
        appendLine("  \"deviceType\": \"${detection.deviceType.name}\",")
        appendLine("  \"deviceTypeDisplayName\": \"${detection.deviceType.displayName}\",")
        appendLine("  \"threatLevel\": \"${detection.threatLevel.name}\",")
        appendLine("  \"threatScore\": ${detection.threatScore},")
        appendLine("  \"protocol\": \"${detection.protocol.name}\",")
        appendLine("  \"detectionMethod\": \"${detection.detectionMethod.name}\",")
        appendLine("  \"rssi\": ${detection.rssi},")
        appendLine("  \"signalStrength\": \"${detection.signalStrength.name}\",")
        detection.macAddress?.let { appendLine("  \"macAddress\": \"$it\",") }
        detection.ssid?.let { appendLine("  \"ssid\": \"${it.replace("\"", "\\\"")}\",") }
        detection.deviceName?.let { appendLine("  \"deviceName\": \"${it.replace("\"", "\\\"")}\",") }
        detection.manufacturer?.let { appendLine("  \"manufacturer\": \"${it.replace("\"", "\\\"")}\",") }
        detection.latitude?.let { appendLine("  \"latitude\": $it,") }
        detection.longitude?.let { appendLine("  \"longitude\": $it,") }
        appendLine("  \"seenCount\": ${detection.seenCount},")
        appendLine("  \"isActive\": ${detection.isActive},")
        appendLine("  \"detectionSource\": \"${detection.detectionSource.name}\",")
        detection.fpScore?.let { appendLine("  \"fpScore\": $it,") }
        detection.fpReason?.let { appendLine("  \"fpReason\": \"${it.replace("\"", "\\\"")}\",") }
        detection.fpCategory?.let { appendLine("  \"fpCategory\": \"$it\",") }
        detection.userNote?.let { appendLine("  \"userNote\": \"${it.replace("\"", "\\\"").replace("\n", "\\n")}\",") }
        appendLine("  \"confirmedThreat\": ${detection.confirmedThreat},")
        appendLine("  \"exportedAt\": \"${dateFormat.format(java.util.Date())}\"")
        appendLine("}")
    }
}
