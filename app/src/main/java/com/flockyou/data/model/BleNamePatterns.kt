package com.flockyou.data.model

/**
 * BLE device name pattern definitions for surveillance device detection.
 * Extracted from DetectionPatterns for maintainability.
 */
internal object BleNamePatterns {
    val entries: List<DetectionPattern> = listOf(
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^flock[_-]?.*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 95,
            description = "Flock Safety BLE Configuration Interface"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^falcon[_-]?.*",
            deviceType = DeviceType.FLOCK_SAFETY_CAMERA,
            manufacturer = "Flock Safety",
            threatScore = 90,
            description = "Flock Falcon Camera BLE"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^raven[_-]?.*",
            deviceType = DeviceType.RAVEN_GUNSHOT_DETECTOR,
            manufacturer = "Flock Safety / SoundThinking",
            threatScore = 100,
            description = "Raven Acoustic Gunshot Detector - listens for gunfire and 'human distress'"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^shotspotter[_-]?.*",
            deviceType = DeviceType.RAVEN_GUNSHOT_DETECTOR,
            manufacturer = "SoundThinking (formerly ShotSpotter)",
            threatScore = 100,
            description = "ShotSpotter Acoustic Sensor - gunfire detection system"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^soundthinking[_-]?.*",
            deviceType = DeviceType.RAVEN_GUNSHOT_DETECTOR,
            manufacturer = "SoundThinking",
            threatScore = 100,
            description = "SoundThinking Acoustic Surveillance Device"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^penguin[_-]?.*",
            deviceType = DeviceType.PENGUIN_SURVEILLANCE,
            manufacturer = "Penguin",
            threatScore = 85,
            description = "Penguin BLE Device"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^pigvision[_-]?.*",
            deviceType = DeviceType.PIGVISION_SYSTEM,
            manufacturer = "Pigvision",
            threatScore = 85,
            description = "Pigvision BLE Device"
        ),
        
        // ==================== Vehicle BLE Patterns ====================

        // Tesla vehicle-command BLE protocol: exact local-name form S<16 lowercase hex>C.
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "^S[0-9a-f]{16}C$",
            deviceType = DeviceType.TESLA_VEHICLE,
            manufacturer = "Tesla",
            threatScore = 5,
            description = "Tesla vehicle-command BLE advertisement name; nearby vehicle radio, not surveillance evidence"
        ),
        // Waymo publishes Bluetooth/Nearby Devices use for vehicle proximity, but no stable public UUID.
        // Match only bounded self-identifying names and keep this as spoofable INFO evidence.
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^waymo(?:[_-][A-Za-z0-9]{1,16})?$",
            deviceType = DeviceType.WAYMO_VEHICLE,
            manufacturer = "Waymo",
            threatScore = 5,
            description = "Self-identifying Waymo BLE name; spoofable candidate evidence only"
        ),

        // ==================== Police Technology BLE Patterns ====================
        
        // Axon Body Cameras
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^axon[_-]?.*",
            deviceType = DeviceType.AXON_POLICE_TECH,
            manufacturer = "Axon Enterprise",
            threatScore = 80,
            description = "Axon device (body camera, TASER, etc.)"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(body|flex)[_-]?[234]?[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Axon Enterprise",
            threatScore = 80,
            description = "Axon Body Camera"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^ab[234][_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Axon Enterprise",
            threatScore = 80,
            description = "Axon Body Camera (AB2/AB3/AB4)"
        ),
        
        // Motorola Body Cameras
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(moto|si)[_-]?[v][_-]?[0-9]+.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Motorola Solutions",
            threatScore = 80,
            description = "Motorola Body Camera"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^watchguard[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Motorola Solutions (WatchGuard)",
            threatScore = 80,
            description = "WatchGuard Body/Dash Camera"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^apx[_-]?.*",
            deviceType = DeviceType.POLICE_RADIO,
            manufacturer = "Motorola Solutions",
            threatScore = 70,
            description = "Motorola APX Radio"
        ),
        
        // Digital Ally
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(da|firstvu)[_-]?.*",
            deviceType = DeviceType.BODY_CAMERA,
            manufacturer = "Digital Ally",
            threatScore = 75,
            description = "Digital Ally Body Camera"
        ),
        
        // L3Harris
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(l3|harris|l3harris)[_-]?.*",
            deviceType = DeviceType.L3HARRIS_SURVEILLANCE,
            manufacturer = "L3Harris Technologies",
            threatScore = 80,
            description = "L3Harris equipment"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^xg[_-]?[0-9]+.*",
            deviceType = DeviceType.POLICE_RADIO,
            manufacturer = "L3Harris Technologies",
            threatScore = 70,
            description = "L3Harris XG Radio"
        ),
        
        // Cellebrite / Forensics
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(cellebrite|ufed)[_-]?.*",
            deviceType = DeviceType.CELLEBRITE_FORENSICS,
            manufacturer = "Cellebrite",
            threatScore = 95,
            description = "Cellebrite forensics device"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^graykey[_-]?.*",
            deviceType = DeviceType.GRAYKEY_DEVICE,
            manufacturer = "Grayshift",
            threatScore = 95,
            description = "GrayKey forensics device"
        ),

        // ==================== Whelen Lightbar / Emergency Vehicle Patterns ====================

        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^cencom[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Whelen Engineering",
            threatScore = 90,
            description = "Whelen CenCom Lightbar Controller - police/emergency vehicle lightbar sync"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^wecan[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Whelen Engineering",
            threatScore = 90,
            description = "Whelen WeCAN Network - vehicle lighting controller"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^whelen[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Whelen Engineering",
            threatScore = 85,
            description = "Whelen emergency lighting equipment"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^whelen[_-]?core[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Whelen Engineering",
            threatScore = 80,
            description = "Whelen Core lightbar system"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^whelen[_-]?(ion|legacy|liberty|freedom)[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Whelen Engineering",
            threatScore = 75,
            description = "Whelen lightbar series"
        ),

        // ==================== Axon Signal / Body Camera Trigger Patterns ====================

        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^signal[_-]?(sidearm|vehicle|performance)?.*",
            deviceType = DeviceType.AXON_POLICE_TECH,
            manufacturer = "Axon Enterprise",
            threatScore = 90,
            description = "Axon Signal - auto-activates body cameras on siren/gun draw"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^fleet[_-]?[23]?.*",
            deviceType = DeviceType.AXON_POLICE_TECH,
            manufacturer = "Axon Enterprise",
            threatScore = 85,
            description = "Axon Fleet in-car camera system"
        ),

        // ==================== Federal Signal Patterns ====================

        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^federal[_-]?signal.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Federal Signal",
            threatScore = 85,
            description = "Federal Signal emergency lighting"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(valor|integrity|allegiant)[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Federal Signal",
            threatScore = 80,
            description = "Federal Signal lightbar"
        ),

        // ==================== Code 3 / SoundOff Signal Patterns ====================

        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^code[_-]?3.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "Code 3",
            threatScore = 80,
            description = "Code 3 emergency lighting"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^soundoff[_-]?.*",
            deviceType = DeviceType.POLICE_VEHICLE,
            manufacturer = "SoundOff Signal",
            threatScore = 80,
            description = "SoundOff Signal emergency equipment"
        ),

        // ==================== Tracker/AirTag Detection Patterns ====================

        // Apple AirTag
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(airtag|find[_-]?my).*",
            deviceType = DeviceType.AIRTAG,
            manufacturer = "Apple",
            threatScore = 60,
            description = "Apple AirTag - potential tracking device"
        ),

        // Tile Trackers
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^tile[_-]?(mate|pro|slim|sticker)?.*",
            deviceType = DeviceType.TILE_TRACKER,
            manufacturer = "Tile",
            threatScore = 55,
            description = "Tile Bluetooth tracker"
        ),

        // Samsung SmartTag
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(smart[_-]?tag|galaxy[_-]?tag).*",
            deviceType = DeviceType.SAMSUNG_SMARTTAG,
            manufacturer = "Samsung",
            threatScore = 55,
            description = "Samsung SmartTag tracker"
        ),

        // Generic BLE Trackers
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(chipolo|nut[_-]?find|pebblebee|cube[_-]?tracker).*",
            deviceType = DeviceType.GENERIC_BLE_TRACKER,
            manufacturer = null,
            threatScore = 50,
            description = "Generic Bluetooth tracker device"
        ),

        // ==================== Smart Home IoT BLE Patterns ====================

        // Ring
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^ring[_-]?(doorbell|cam|chime|setup).*",
            deviceType = DeviceType.RING_DOORBELL,
            manufacturer = "Ring (Amazon)",
            threatScore = 40,
            description = "Ring doorbell/camera BLE setup"
        ),

        // Nest/Google
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(nest|google)[_-]?(cam|doorbell|hello|hub).*",
            deviceType = DeviceType.NEST_CAMERA,
            manufacturer = "Google/Nest",
            threatScore = 35,
            description = "Google Nest camera/doorbell"
        ),

        // Wyze
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^wyze[_-]?.*",
            deviceType = DeviceType.WYZE_CAMERA,
            manufacturer = "Wyze",
            threatScore = 35,
            description = "Wyze smart home device"
        ),

        // Arlo
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^arlo[_-]?.*",
            deviceType = DeviceType.ARLO_CAMERA,
            manufacturer = "Arlo",
            threatScore = 35,
            description = "Arlo security camera"
        ),

        // Eufy
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^eufy[_-]?.*",
            deviceType = DeviceType.EUFY_CAMERA,
            manufacturer = "Eufy/Anker",
            threatScore = 35,
            description = "Eufy security device"
        ),

        // Blink
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^blink[_-]?.*",
            deviceType = DeviceType.BLINK_CAMERA,
            manufacturer = "Blink (Amazon)",
            threatScore = 40,
            description = "Blink camera"
        ),

        // ==================== Retail Beacon Patterns ====================

        // iBeacon / Eddystone patterns
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(ibeacon|eddystone|estimote|kontakt).*",
            deviceType = DeviceType.BLUETOOTH_BEACON,
            manufacturer = null,
            threatScore = 45,
            description = "Retail/location Bluetooth beacon"
        ),

        // Retail analytics
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(retailnext|shoppertrak|sensoro).*",
            deviceType = DeviceType.RETAIL_TRACKER,
            manufacturer = null,
            threatScore = 50,
            description = "Retail foot traffic sensor"
        ),

        // ==================== Law Enforcement Specific BLE Patterns ====================

        // ShotSpotter/Acoustic
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(shotspot|soundthink|acoustic[_-]?sens).*",
            deviceType = DeviceType.SHOTSPOTTER,
            manufacturer = "SoundThinking",
            threatScore = 95,
            description = "ShotSpotter acoustic gunshot sensor"
        ),

        // GrayKey
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^graykey.*",
            deviceType = DeviceType.GRAYKEY_DEVICE,
            manufacturer = "Grayshift",
            threatScore = 95,
            description = "GrayKey mobile forensics device"
        ),

        // ==================== Flipper Zero and Hacking Tool Patterns ====================

        // Flipper Zero - Default and common device names
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^flipper[_\\- ]?(zero)?[_\\- ]?.*",
            deviceType = DeviceType.FLIPPER_ZERO,
            manufacturer = "Flipper Devices",
            threatScore = 65,
            description = "Flipper Zero multi-tool hacking device - Sub-GHz, RFID, NFC, IR, BLE capable"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^flip[_\\- ]?[0-9a-f]+.*",
            deviceType = DeviceType.FLIPPER_ZERO,
            manufacturer = "Flipper Devices",
            threatScore = 60,
            description = "Flipper Zero (serial number format)"
        ),
        // Flipper custom firmware naming patterns
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(unleashed|roguemaster|xtreme|momentum)[_\\- ]?.*",
            deviceType = DeviceType.FLIPPER_ZERO,
            manufacturer = "Flipper Devices (Custom FW)",
            threatScore = 75,
            description = "Flipper Zero with custom firmware (Unleashed/RogueMaster/Xtreme) - enhanced capabilities"
        ),
        // Flipper BadUSB/BLE mode patterns
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^badusb[_\\- ]?.*",
            deviceType = DeviceType.FLIPPER_ZERO,
            manufacturer = "Flipper Devices",
            threatScore = 85,
            description = "Flipper Zero in BadUSB mode - keystroke injection capable"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^badbt[_\\- ]?.*",
            deviceType = DeviceType.FLIPPER_ZERO,
            manufacturer = "Flipper Devices",
            threatScore = 85,
            description = "Flipper Zero in BadBT mode - Bluetooth keystroke injection"
        ),

        // Hak5 Devices
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(bash[_\\- ]?bunny|bashbunny).*",
            deviceType = DeviceType.BASH_BUNNY,
            manufacturer = "Hak5",
            threatScore = 80,
            description = "Hak5 Bash Bunny - USB attack platform"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(lan[_\\- ]?turtle|lanturtle).*",
            deviceType = DeviceType.LAN_TURTLE,
            manufacturer = "Hak5",
            threatScore = 80,
            description = "Hak5 LAN Turtle - covert network access device"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(rubber[_\\- ]?ducky|rubberducky).*",
            deviceType = DeviceType.USB_RUBBER_DUCKY,
            manufacturer = "Hak5",
            threatScore = 75,
            description = "Hak5 USB Rubber Ducky - keystroke injection device"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(key[_\\- ]?croc|keycroc).*",
            deviceType = DeviceType.KEYCROC,
            manufacturer = "Hak5",
            threatScore = 85,
            description = "Hak5 Key Croc - keylogger with WiFi exfiltration"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(shark[_\\- ]?jack|sharkjack).*",
            deviceType = DeviceType.SHARK_JACK,
            manufacturer = "Hak5",
            threatScore = 80,
            description = "Hak5 Shark Jack - portable network attack tool"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(screen[_\\- ]?crab|screencrab).*",
            deviceType = DeviceType.SCREEN_CRAB,
            manufacturer = "Hak5",
            threatScore = 85,
            description = "Hak5 Screen Crab - HDMI man-in-the-middle"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^hak5[_\\- ]?.*",
            deviceType = DeviceType.GENERIC_HACKING_TOOL,
            manufacturer = "Hak5",
            threatScore = 75,
            description = "Hak5 security testing device"
        ),

        // SDR/RF Tools
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(hackrf|portapack).*",
            deviceType = DeviceType.HACKRF_SDR,
            manufacturer = "Great Scott Gadgets",
            threatScore = 70,
            description = "HackRF/PortaPack SDR - RF analysis and transmission capable"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(sdr|rtl[_\\- ]?sdr).*",
            deviceType = DeviceType.HACKRF_SDR,
            manufacturer = null,
            threatScore = 50,
            description = "Software Defined Radio device - RF monitoring capable"
        ),

        // RFID/NFC Tools
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^proxmark.*",
            deviceType = DeviceType.PROXMARK,
            manufacturer = "Proxmark",
            threatScore = 80,
            description = "Proxmark RFID/NFC tool - can clone access cards"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(chameleon|chameleomini).*",
            deviceType = DeviceType.PROXMARK,
            manufacturer = null,
            threatScore = 75,
            description = "ChameleonMini RFID emulator - card cloning device"
        ),
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(icopy|icopy[_\\- ]?x).*",
            deviceType = DeviceType.PROXMARK,
            manufacturer = null,
            threatScore = 70,
            description = "iCopy-X RFID cloner"
        ),

        // Generic hacking/pentest patterns
        DetectionPattern(
            type = PatternType.BLE_NAME_REGEX,
            pattern = "(?i)^(pentest|hackbox|pwn|0wn|hack[_\\- ]?tool).*",
            deviceType = DeviceType.GENERIC_HACKING_TOOL,
            manufacturer = null,
            threatScore = 65,
            description = "Potential security testing/hacking device"
        )
    )
}
