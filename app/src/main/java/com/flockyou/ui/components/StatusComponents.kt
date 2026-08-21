package com.flockyou.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flockyou.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Animated scanning radar effect
 */
@Composable
fun ScanningRadar(
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isScanning) {
            // Animated rings
            repeat(3) { index ->
                val delay = index * 500
                val ringScale by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing, delayMillis = delay),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ringScale$index"
                )
                val ringAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing, delayMillis = delay),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ringAlpha$index"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize(ringScale)
                        .clip(CircleShape)
                        .border(2.dp, color.copy(alpha = ringAlpha), CircleShape)
                )
            }
        }

        // Center dot
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isScanning) color else Color.Gray)
        )
    }
}

/**
 * Status card showing scanning status
 */
@Composable
fun StatusCard(
    isScanning: Boolean,
    totalDetections: Int,
    highThreatCount: Int,
    onToggleScan: () -> Unit,
    modifier: Modifier = Modifier,
    scanStatus: com.flockyou.service.ScanStatus = com.flockyou.service.ScanStatus.Idle,
    bleStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    wifiStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    locationStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    cellularStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    satelliteStatus: com.flockyou.service.SubsystemStatus = com.flockyou.service.SubsystemStatus.Idle,
    recentErrors: List<com.flockyou.service.ScanError> = emptyList(),
    onClearErrors: () -> Unit = {}
) {
    var showErrorDetails by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when (scanStatus) {
                            is com.flockyou.service.ScanStatus.Idle -> "IDLE"
                            is com.flockyou.service.ScanStatus.Starting -> "STARTING..."
                            is com.flockyou.service.ScanStatus.Active -> "SCANNING"
                            is com.flockyou.service.ScanStatus.Stopping -> "STOPPING..."
                            is com.flockyou.service.ScanStatus.Error -> "ERROR"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = when (scanStatus) {
                            is com.flockyou.service.ScanStatus.Active -> MaterialTheme.colorScheme.primary
                            is com.flockyou.service.ScanStatus.Error -> MaterialTheme.colorScheme.error
                            is com.flockyou.service.ScanStatus.Starting,
                            is com.flockyou.service.ScanStatus.Stopping -> MaterialTheme.colorScheme.tertiary
                            else -> Color.Gray
                        }
                    )
                    Text(
                        text = "Surveillance Detection System",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ScanningRadar(isScanning = isScanning)
                }
            }

            // Subsystem status indicators
            if (isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SubsystemIndicator(
                        name = "BLE",
                        status = bleStatus,
                        modifier = Modifier.weight(1f)
                    )
                    SubsystemIndicator(
                        name = "WiFi",
                        status = wifiStatus,
                        modifier = Modifier.weight(1f)
                    )
                    SubsystemIndicator(
                        name = "GPS",
                        status = locationStatus,
                        modifier = Modifier.weight(1f)
                    )
                    SubsystemIndicator(
                        name = "Cell",
                        status = cellularStatus,
                        modifier = Modifier.weight(1f)
                    )
                    SubsystemIndicator(
                        name = "Sat",
                        status = satelliteStatus,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Error banner
            if (recentErrors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showErrorDetails = !showErrorDetails },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning: ${recentErrors.size} recent error${if (recentErrors.size > 1) "s" else ""}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${recentErrors.size} recent error${if (recentErrors.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = recentErrors.firstOrNull()?.message ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = if (showErrorDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle error details",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                // Expandable error details
                AnimatedVisibility(visible = showErrorDetails) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        recentErrors.take(5).forEach { error ->
                            ErrorLogItem(error = error)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        TextButton(
                            onClick = onClearErrors,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear Errors")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "DETECTIONS",
                    value = totalDetections.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                StatItem(
                    label = "HIGH THREAT",
                    value = highThreatCount.toString(),
                    color = ThreatHigh
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleScan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isScanning) "Stop scanning" else "Start scanning",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) "STOP SCANNING" else "START SCANNING",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SubsystemIndicator(
    name: String,
    status: com.flockyou.service.SubsystemStatus,
    modifier: Modifier = Modifier
) {
    val (color, icon, statusDescription) = when (status) {
        is com.flockyou.service.SubsystemStatus.Active -> Triple(
            StatusActive,
            Icons.Default.CheckCircle,
            "Active"
        )
        is com.flockyou.service.SubsystemStatus.Idle -> Triple(
            Color.Gray,
            Icons.Default.RadioButtonUnchecked,
            "Idle"
        )
        is com.flockyou.service.SubsystemStatus.Disabled -> Triple(
            StatusDisabled,
            Icons.Default.Block,
            "Disabled"
        )
        is com.flockyou.service.SubsystemStatus.Error -> Triple(
            StatusError,
            Icons.Default.Error,
            "Error"
        )
        is com.flockyou.service.SubsystemStatus.PermissionDenied -> Triple(
            StatusWarning,
            Icons.Default.Lock,
            "No Perm"
        )
    }

    Surface(
        modifier = modifier.semantics { stateDescription = "$name subsystem: $statusDescription" },
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "$name: $statusDescription",
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ErrorLogItem(
    error: com.flockyou.service.ScanError,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormat.format(Date(error.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (error.recoverable)
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            ) {
                Text(
                    text = error.subsystem,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (error.recoverable)
                        MaterialTheme.colorScheme.tertiary
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
