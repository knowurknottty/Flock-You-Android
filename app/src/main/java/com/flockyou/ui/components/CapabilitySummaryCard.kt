package com.flockyou.ui.components

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flockyou.data.FeasibilityData
import com.flockyou.data.FeasibilityLevel
import com.flockyou.data.model.DetectionProtocol
import com.flockyou.privilege.PrivilegeMode

/**
 * Hardware facts that materially affect what Flock-You can observe on this phone.
 *
 * Keep these separate from [PrivilegeMode]: install privilege and physical hardware
 * are independent constraints and the UI should never imply otherwise.
 */
@Immutable
data class DeviceHardwareCapabilities(
    val hasBle: Boolean,
    val hasWifi: Boolean,
    val hasTelephony: Boolean,
    val hasGps: Boolean,
    val hasMicrophone: Boolean,
    val hasNfc: Boolean
)

@Immutable
data class CapabilityUiStatus(
    val protocol: DetectionProtocol,
    val title: String,
    val level: FeasibilityLevel,
    val statusLabel: String,
    val detail: String,
    val enabled: Boolean
)

/**
 * Resolve the honest capability shown to the operator by combining:
 * - physical phone hardware,
 * - Android API level,
 * - install privilege,
 * - optional external RF hardware.
 *
 * This deliberately does not turn heuristic evidence into a stronger claim.
 */
fun resolveCapabilityUiStatus(
    protocol: DetectionProtocol,
    mode: PrivilegeMode,
    hardware: DeviceHardwareCapabilities,
    androidSdk: Int,
    hasExternalRfHardware: Boolean
): CapabilityUiStatus {
    fun unavailable(detail: String) = CapabilityUiStatus(
        protocol = protocol,
        title = protocolTitle(protocol),
        level = FeasibilityLevel.NOT_FEASIBLE,
        statusLabel = "Unavailable",
        detail = detail,
        enabled = false
    )

    when (protocol) {
        DetectionProtocol.BLUETOOTH_LE -> if (!hardware.hasBle) {
            return unavailable("Bluetooth LE hardware is not present on this device")
        }
        DetectionProtocol.WIFI -> if (!hardware.hasWifi) {
            return unavailable("Wi-Fi hardware is not present on this device")
        }
        DetectionProtocol.CELLULAR -> if (!hardware.hasTelephony) {
            return unavailable("Cellular radio / telephony capability is not present")
        }
        DetectionProtocol.GNSS -> if (!hardware.hasGps) {
            return unavailable("GNSS/GPS hardware is not present on this device")
        }
        DetectionProtocol.AUDIO -> if (!hardware.hasMicrophone) {
            return unavailable("Microphone hardware is not present on this device")
        }
        DetectionProtocol.SATELLITE -> {
            if (!hardware.hasTelephony) {
                return unavailable("Satellite/NTN monitoring requires cellular telephony support")
            }
            if (androidSdk < 35) {
                return unavailable("Satellite/NTN monitoring requires Android 15 or newer")
            }
        }
        DetectionProtocol.RF -> {
            if (hasExternalRfHardware) {
                return CapabilityUiStatus(
                    protocol = protocol,
                    title = protocolTitle(protocol),
                    level = FeasibilityLevel.FULL,
                    statusLabel = "External ready",
                    detail = "Flipper Zero connected for direct RF / sub-GHz analysis",
                    enabled = true
                )
            }
        }
    }

    val feasibility = FeasibilityData.getProtocolFeasibility(protocol, mode)
    val label = when (feasibility.level) {
        FeasibilityLevel.FULL -> "Full"
        FeasibilityLevel.DEGRADED -> "Limited"
        FeasibilityLevel.HEURISTIC_ONLY -> "Heuristic"
        FeasibilityLevel.NOT_FEASIBLE -> if (protocol == DetectionProtocol.RF) "External" else "Unavailable"
    }

    return CapabilityUiStatus(
        protocol = protocol,
        title = protocolTitle(protocol),
        level = feasibility.level,
        statusLabel = label,
        detail = feasibility.summary,
        enabled = feasibility.level != FeasibilityLevel.NOT_FEASIBLE
    )
}

private fun protocolTitle(protocol: DetectionProtocol): String = when (protocol) {
    DetectionProtocol.BLUETOOTH_LE -> "BLE"
    DetectionProtocol.WIFI -> "Wi-Fi"
    DetectionProtocol.CELLULAR -> "Cellular"
    DetectionProtocol.GNSS -> "GNSS"
    DetectionProtocol.RF -> "RF"
    DetectionProtocol.AUDIO -> "Ultrasonic"
    DetectionProtocol.SATELLITE -> "Satellite"
}

private fun protocolIcon(protocol: DetectionProtocol): ImageVector = when (protocol) {
    DetectionProtocol.BLUETOOTH_LE -> Icons.Default.Bluetooth
    DetectionProtocol.WIFI -> Icons.Default.Wifi
    DetectionProtocol.CELLULAR -> Icons.Default.CellTower
    DetectionProtocol.GNSS -> Icons.Default.GpsFixed
    DetectionProtocol.RF -> Icons.Default.Radio
    DetectionProtocol.AUDIO -> Icons.Default.GraphicEq
    DetectionProtocol.SATELLITE -> Icons.Default.SatelliteAlt
}

private fun installModeLabel(mode: PrivilegeMode): String = when (mode) {
    is PrivilegeMode.Sideload -> "Standard install"
    is PrivilegeMode.System -> "System install"
    is PrivilegeMode.OEM -> "OEM install"
}

fun detectConstrainedHardware(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
    val fourGiB = 4L * 1024L * 1024L * 1024L
    val totalMemoryIsConstrained = memoryInfo.totalMem > 0L && memoryInfo.totalMem <= fourGiB
    return activityManager.isLowRamDevice || activityManager.memoryClass <= 256 || totalMemoryIsConstrained
}

@Composable
fun rememberDeviceHardwareCapabilities(): DeviceHardwareCapabilities {
    val context = LocalContext.current
    return remember(context) {
        val packageManager = context.packageManager
        DeviceHardwareCapabilities(
            hasBle = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            hasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI),
            hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY),
            hasGps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            hasMicrophone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            hasNfc = packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        )
    }
}

@Composable
fun rememberConstrainedDevice(): Boolean {
    val context = LocalContext.current
    return remember(context) { detectConstrainedHardware(context) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilitySummaryCard(
    privilegeMode: PrivilegeMode,
    hardware: DeviceHardwareCapabilities,
    isConstrainedDevice: Boolean,
    hasExternalRfHardware: Boolean,
    modifier: Modifier = Modifier
) {
    val androidSdk = Build.VERSION.SDK_INT
    val capabilities = remember(privilegeMode, hardware, androidSdk, hasExternalRfHardware) {
        listOf(
            DetectionProtocol.BLUETOOTH_LE,
            DetectionProtocol.WIFI,
            DetectionProtocol.CELLULAR,
            DetectionProtocol.GNSS,
            DetectionProtocol.RF,
            DetectionProtocol.AUDIO,
            DetectionProtocol.SATELLITE
        ).map { protocol ->
            resolveCapabilityUiStatus(
                protocol = protocol,
                mode = privilegeMode,
                hardware = hardware,
                androidSdk = androidSdk,
                hasExternalRfHardware = hasExternalRfHardware
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "  This phone",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isConstrainedDevice) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = if (isConstrainedDevice) "Constrained" else "Standard",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${installModeLabel(privilegeMode)} · Android ${Build.VERSION.RELEASE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                capabilities.forEach { capability ->
                    CapabilityChip(capability)
                }
            }

            if (!hardware.hasNfc) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No NFC detected — core BLE, Wi-Fi, cellular and GNSS scanning is unaffected.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CapabilityChip(status: CapabilityUiStatus) {
    val tint: Color = when (status.level) {
        FeasibilityLevel.FULL -> MaterialTheme.colorScheme.tertiary
        FeasibilityLevel.DEGRADED -> MaterialTheme.colorScheme.secondary
        FeasibilityLevel.HEURISTIC_ONLY -> MaterialTheme.colorScheme.primary
        FeasibilityLevel.NOT_FEASIBLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = protocolIcon(status.protocol),
                contentDescription = null,
                tint = tint
            )
            Text(
                text = " ${status.title} · ${status.statusLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
