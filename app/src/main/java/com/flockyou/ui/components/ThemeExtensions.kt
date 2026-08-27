package com.flockyou.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.flockyou.data.model.*
import com.flockyou.ui.theme.*

/**
 * Get color for threat level
 */
@Composable
fun ThreatLevel.toColor(): Color = when (this) {
    ThreatLevel.CRITICAL -> ThreatCritical
    ThreatLevel.HIGH -> ThreatHigh
    ThreatLevel.MEDIUM -> ThreatMedium
    ThreatLevel.LOW -> ThreatLow
    ThreatLevel.INFO -> ThreatInfo
}

/**
 * Get color for signal strength
 */
@Composable
fun SignalStrength.toColor(): Color = when (this) {
    SignalStrength.EXCELLENT -> SignalExcellent
    SignalStrength.GOOD -> SignalGood
    SignalStrength.MEDIUM -> SignalMedium
    SignalStrength.WEAK -> SignalWeak
    SignalStrength.VERY_WEAK -> SignalVeryWeak
    SignalStrength.UNKNOWN -> Color.Gray
}

/**
 * Get icon for device type
 */
fun DeviceType.toIcon(): ImageVector = when (this) {
    DeviceType.FLOCK_SAFETY_CAMERA -> Icons.Default.CameraAlt
    DeviceType.PENGUIN_SURVEILLANCE -> Icons.Default.Videocam
    DeviceType.PIGVISION_SYSTEM -> Icons.Default.RemoveRedEye
    DeviceType.RAVEN_GUNSHOT_DETECTOR -> Icons.Default.Mic
    DeviceType.MOTOROLA_POLICE_TECH -> Icons.Default.SettingsInputAntenna
    DeviceType.AXON_POLICE_TECH -> Icons.Default.ElectricBolt
    DeviceType.L3HARRIS_SURVEILLANCE -> Icons.Default.SatelliteAlt
    DeviceType.CELLEBRITE_FORENSICS -> Icons.Default.PhoneAndroid
    DeviceType.BODY_CAMERA -> Icons.Default.Videocam
    DeviceType.POLICE_RADIO -> Icons.Default.Radio
    DeviceType.POLICE_VEHICLE -> Icons.Default.LocalPolice
    DeviceType.FLEET_VEHICLE -> Icons.Default.DirectionsCar
    DeviceType.TESLA_VEHICLE, DeviceType.WAYMO_VEHICLE -> Icons.Default.DirectionsCar
    DeviceType.STINGRAY_IMSI -> Icons.Default.CellTower
    DeviceType.ROGUE_AP -> Icons.Default.WifiOff
    DeviceType.HIDDEN_CAMERA -> Icons.Default.Visibility
    DeviceType.SURVEILLANCE_VAN -> Icons.Default.DirectionsCar
    DeviceType.TRACKING_DEVICE -> Icons.Default.LocationOn
    DeviceType.RF_JAMMER -> Icons.Default.SignalCellularOff
    DeviceType.DRONE -> Icons.Default.FlightTakeoff
    DeviceType.SURVEILLANCE_INFRASTRUCTURE -> Icons.Default.Business
    DeviceType.RF_INTERFERENCE -> Icons.Default.SettingsInputAntenna
    DeviceType.RF_ANOMALY -> Icons.Default.Insights
    DeviceType.HIDDEN_TRANSMITTER -> Icons.Default.SpeakerPhone
    DeviceType.ULTRASONIC_BEACON -> Icons.Default.Hearing
    DeviceType.SATELLITE_NTN -> Icons.Default.SatelliteAlt
    DeviceType.GNSS_SPOOFER -> Icons.Default.GpsOff
    DeviceType.GNSS_JAMMER -> Icons.Default.GpsOff
    // Smart home/IoT devices
    DeviceType.RING_DOORBELL -> Icons.Default.Doorbell
    DeviceType.NEST_CAMERA -> Icons.Default.CameraOutdoor
    DeviceType.AMAZON_SIDEWALK -> Icons.Default.SettingsInputAntenna
    DeviceType.WYZE_CAMERA -> Icons.Default.CameraAlt
    DeviceType.ARLO_CAMERA -> Icons.Default.Videocam
    DeviceType.EUFY_CAMERA -> Icons.Default.Home
    DeviceType.BLINK_CAMERA -> Icons.Default.RemoveRedEye
    DeviceType.SIMPLISAFE_DEVICE -> Icons.Default.Shield
    DeviceType.ADT_DEVICE -> Icons.Default.Security
    DeviceType.VIVINT_DEVICE -> Icons.Default.Home
    // Retail/commercial tracking
    DeviceType.BLUETOOTH_BEACON -> Icons.Default.Bluetooth
    DeviceType.RETAIL_TRACKER -> Icons.Default.Store
    DeviceType.CROWD_ANALYTICS -> Icons.Default.Groups
    DeviceType.FACIAL_RECOGNITION -> Icons.Default.Face
    // AirTag/tracker devices
    DeviceType.AIRTAG -> Icons.Default.LocationOn
    DeviceType.TILE_TRACKER -> Icons.Default.LocationSearching
    DeviceType.SAMSUNG_SMARTTAG -> Icons.Default.LocationOn
    DeviceType.GENERIC_BLE_TRACKER -> Icons.Default.BluetoothSearching
    // Traffic enforcement
    DeviceType.SPEED_CAMERA -> Icons.Default.Speed
    DeviceType.RED_LIGHT_CAMERA -> Icons.Default.Traffic
    DeviceType.TOLL_READER -> Icons.Default.CreditCard
    DeviceType.TRAFFIC_SENSOR -> Icons.Default.Sensors
    // Law enforcement specific
    DeviceType.SHOTSPOTTER -> Icons.Default.Mic
    DeviceType.CLEARVIEW_AI -> Icons.Default.Face
    DeviceType.PALANTIR_DEVICE -> Icons.Default.Hub
    DeviceType.GRAYKEY_DEVICE -> Icons.Default.PhonelinkLock
    // Network surveillance
    DeviceType.WIFI_PINEAPPLE -> Icons.Default.Router
    DeviceType.PACKET_SNIFFER -> Icons.Default.NetworkCheck
    DeviceType.MAN_IN_MIDDLE -> Icons.Default.SwapHoriz
    // Hacking tools
    DeviceType.FLIPPER_ZERO -> Icons.Default.SmartToy
    DeviceType.FLIPPER_ZERO_SPAM -> Icons.Default.WifiTethering
    DeviceType.HACKRF_SDR -> Icons.Default.SettingsInputAntenna
    DeviceType.PROXMARK -> Icons.Default.CreditCard
    DeviceType.USB_RUBBER_DUCKY -> Icons.Default.Usb
    DeviceType.LAN_TURTLE -> Icons.Default.Lan
    DeviceType.BASH_BUNNY -> Icons.Default.Terminal
    DeviceType.KEYCROC -> Icons.Default.Keyboard
    DeviceType.SHARK_JACK -> Icons.Default.Cable
    DeviceType.SCREEN_CRAB -> Icons.Default.ScreenShare
    DeviceType.GENERIC_HACKING_TOOL -> Icons.Default.Build
    // Misc surveillance
    DeviceType.LICENSE_PLATE_READER -> Icons.Default.DirectionsCar
    DeviceType.CCTV_CAMERA -> Icons.Default.Videocam
    DeviceType.PTZ_CAMERA -> Icons.Default.CameraAlt
    DeviceType.THERMAL_CAMERA -> Icons.Default.Thermostat
    DeviceType.NIGHT_VISION -> Icons.Default.NightsStay
    DeviceType.UNKNOWN_SURVEILLANCE -> Icons.Default.QuestionMark
}

/**
 * Get icon for detection protocol
 */
fun DetectionProtocol.toIcon(): ImageVector = when (this) {
    DetectionProtocol.WIFI -> Icons.Default.Wifi
    DetectionProtocol.BLUETOOTH_LE -> Icons.Default.Bluetooth
    DetectionProtocol.CELLULAR -> Icons.Default.CellTower
    DetectionProtocol.SATELLITE -> Icons.Default.SatelliteAlt
    DetectionProtocol.GNSS -> Icons.Default.GpsFixed
    DetectionProtocol.AUDIO -> Icons.Default.Hearing
    DetectionProtocol.RF -> Icons.Default.SettingsInputAntenna
}
