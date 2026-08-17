#!/usr/bin/env python3
"""Apply the R1 capability-aware / low-overhead MainScreen changes.

This patch is intentionally fail-closed so UI edits never partially apply against an
unexpected source revision.
"""
from pathlib import Path

path = Path("app/src/main/java/com/flockyou/ui/screens/MainScreen.kt")
text = path.read_text()


def exact(old: str, new: str, count: int = 1) -> None:
    global text
    actual = text.count(old)
    if actual != count:
        raise SystemExit(f"MainScreen.kt: expected {count} matches, got {actual}: {old[:160]!r}")
    text = text.replace(old, new, count)


# Lifecycle-aware flow collection: do not keep seven UI collectors active when the
# screen is stopped/backgrounded.
exact("import androidx.compose.animation.core.animateFloatAsState\n", "")
exact("import androidx.compose.animation.core.spring\n", "")
exact("import androidx.compose.ui.graphics.graphicsLayer\n", "")
exact(
    "import androidx.hilt.navigation.compose.hiltViewModel\n",
    "import androidx.hilt.navigation.compose.hiltViewModel\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle\n",
)
exact(
    "import com.flockyou.service.CellularMonitor\n",
    "import com.flockyou.service.CellularMonitor\nimport com.flockyou.privilege.PrivilegeMode\n",
)

if text.count(".collectAsState()") != 7:
    raise SystemExit(f"MainScreen.kt: expected 7 collectAsState() calls, got {text.count('.collectAsState()')}")
text = text.replace(".collectAsState()", ".collectAsStateWithLifecycle()")

# Resolve real hardware once and reuse it throughout the home surface.
exact(
    """    val context = LocalContext.current
    var showFilterSheet by remember { mutableStateOf(false) }""",
    """    val context = LocalContext.current
    val hardwareCapabilities = rememberDeviceHardwareCapabilities()
    val isConstrainedDevice = rememberConstrainedDevice()
    val hasExternalRfHardware = uiState.flipperConnectionState == FlipperConnectionState.READY
    var showFilterSheet by remember { mutableStateOf(false) }""",
)

# Avoid a stale uiState capture inside a long-lived LaunchedEffect.
exact(
    """            if (!isNavigatingProgrammatically && uiState.selectedTab != settledPage) {
                viewModel.selectTab(settledPage)
            }""",
    """            if (!isNavigatingProgrammatically && viewModel.uiState.value.selectedTab != settledPage) {
                viewModel.selectTab(settledPage)
            }""",
)

# Tactical wording: this page answers what is happening now, not generic "home".
exact('label = { Text("Home") },', 'label = { Text("Now") },')

# Capability summary directly after the primary scanner status.
exact(
    """                        }

                        // Cellular status card (show when scanning or has anomalies)""",
    """                        }

                        item(key = "capability_summary") {
                            CapabilitySummaryCard(
                                privilegeMode = viewModel.privilegeMode,
                                hardware = hardwareCapabilities,
                                isConstrainedDevice = isConstrainedDevice,
                                hasExternalRfHardware = hasExternalRfHardware
                            )
                        }

                        // Keep detailed cellular telemetry out of the default "Now" surface
                        // unless there is something to inspect or Advanced mode is enabled.""",
    count=1,
)

# Normal mode stays calm; advanced mode still exposes live cellular telemetry.
exact(
    """                        if (uiState.isScanning || filteredCellularAnomalies.isNotEmpty()) {""",
    """                        if (filteredCellularAnomalies.isNotEmpty() || uiState.advancedMode) {""",
)

exact('text = "DETECTION MODULES",', 'text = "SENSORS & TOOLS",')

# Feed actual hardware/install context into module cards.
exact(
    """                                onNavigateToWifiSecurity = onNavigateToWifiSecurity,
                                wifiAnomalyCount = filteredRogueWifiAnomalies.size,""",
    """                                onNavigateToWifiSecurity = onNavigateToWifiSecurity,
                                privilegeMode = viewModel.privilegeMode,
                                hardwareCapabilities = hardwareCapabilities,
                                hasExternalRfHardware = hasExternalRfHardware,
                                wifiAnomalyCount = filteredRogueWifiAnomalies.size,""",
)

# Replace the module grid/cards as one governed block. This removes the gratuitous
# spring/graphicsLayer pulse and makes unavailable features actually disabled.
start_marker = "/**\n * Detection modules grid with quick access to specialized detection screens\n */"
end_marker = "/**\n * Permission recovery button that opens app settings"
if text.count(start_marker) != 1 or text.count(end_marker) != 1:
    raise SystemExit("MainScreen.kt: detection module block markers are not unique")
start = text.index(start_marker)
end = text.index(end_marker, start)

new_block = '''/**
 * Detection modules grid with capability-aware quick access.
 *
 * The previous UI presented every module as equally available. That is misleading on
 * normal sideloaded phones and especially on older hardware. These cards now preserve
 * FeasibilityData's epistemic level and disable truly unavailable surfaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionModulesGrid(
    onNavigateToRfDetection: () -> Unit,
    onNavigateToUltrasonicDetection: () -> Unit,
    onNavigateToSatelliteDetection: () -> Unit,
    onNavigateToWifiSecurity: () -> Unit,
    privilegeMode: PrivilegeMode,
    hardwareCapabilities: DeviceHardwareCapabilities,
    hasExternalRfHardware: Boolean,
    wifiAnomalyCount: Int,
    rfAnomalyCount: Int,
    ultrasonicBeaconCount: Int,
    satelliteAnomalyCount: Int
) {
    val androidSdk = android.os.Build.VERSION.SDK_INT
    val wifiCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.WIFI,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }
    val rfCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.RF,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }
    val ultrasonicCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.AUDIO,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }
    val satelliteCapability = remember(privilegeMode, hardwareCapabilities, androidSdk, hasExternalRfHardware) {
        resolveCapabilityUiStatus(
            DetectionProtocol.SATELLITE,
            privilegeMode,
            hardwareCapabilities,
            androidSdk,
            hasExternalRfHardware
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "WiFi Security",
                description = "Evil twin & rogue AP detection",
                icon = Icons.Default.Wifi,
                badgeCount = wifiAnomalyCount,
                iconTint = Color(0xFF2196F3),
                capability = wifiCapability,
                onClick = onNavigateToWifiSecurity
            )
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "RF Analysis",
                description = "Jammers, drones & spectrum",
                icon = Icons.Default.Radio,
                badgeCount = rfAnomalyCount,
                iconTint = Color(0xFF9C27B0),
                capability = rfCapability,
                onClick = onNavigateToRfDetection
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "Ultrasonic",
                description = "Audio tracking beacons",
                icon = Icons.Default.GraphicEq,
                badgeCount = ultrasonicBeaconCount,
                iconTint = Color(0xFFFF9800),
                capability = ultrasonicCapability,
                onClick = onNavigateToUltrasonicDetection
            )
            DetectionModuleCard(
                modifier = Modifier.weight(1f),
                title = "Satellite",
                description = "NTN & Direct-to-Cell",
                icon = Icons.Default.SatelliteAlt,
                badgeCount = satelliteAnomalyCount,
                iconTint = Color(0xFF4CAF50),
                capability = satelliteCapability,
                onClick = onNavigateToSatelliteDetection
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetectionModuleCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    iconTint: Color,
    capability: CapabilityUiStatus,
    onClick: () -> Unit
) {
    val enabled = capability.enabled
    val hasAnomalies = enabled && badgeCount > 0
    val effectiveTint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant
    val capabilityTint = when (capability.level) {
        com.flockyou.data.FeasibilityLevel.FULL -> MaterialTheme.colorScheme.tertiary
        com.flockyou.data.FeasibilityLevel.DEGRADED -> MaterialTheme.colorScheme.secondary
        com.flockyou.data.FeasibilityLevel.HEURISTIC_ONLY -> MaterialTheme.colorScheme.primary
        com.flockyou.data.FeasibilityLevel.NOT_FEASIBLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.then(
            if (hasAnomalies) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
            } else Modifier
        ),
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                hasAnomalies -> iconTint.copy(alpha = 0.2f)
                else -> iconTint.copy(alpha = 0.1f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (hasAnomalies) 3.dp else 0.dp
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveTint,
                    modifier = Modifier.size(28.dp)
                )
                if (hasAnomalies) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text(text = badgeCount.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = when {
                    !enabled -> capability.detail
                    hasAnomalies -> "$badgeCount anomal${if (badgeCount > 1) "ies" else "y"} detected"
                    else -> description
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    hasAnomalies -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (hasAnomalies) FontWeight.Medium else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = capability.statusLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = capabilityTint
            )
        }
    }
}

'''
text = text[:start] + new_block + text[end:]

path.write_text(text)
print("Adaptive MainScreen R1 patch applied successfully")
