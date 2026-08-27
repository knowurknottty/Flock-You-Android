package com.flockyou.data.model

/**
 * Device type information entries for surveillance device descriptions.
 * Extracted from DetectionPatterns for maintainability.
 */
internal object DeviceTypeInfoEntries {
    fun getInfo(deviceType: DeviceType): DetectionPatterns.DeviceTypeInfo {
        return when (deviceType) {
            DeviceType.FLOCK_SAFETY_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Flock Safety ALPR Camera",
                shortDescription = "Automated License Plate Reader",
                fullDescription = "Flock Safety cameras capture images of vehicles and license plates. " +
                    "They use 'Vehicle Fingerprint' technology to identify make, model, color, and " +
                    "distinguishing features. Data is stored for 30 days and shared with law enforcement. " +
                    "Over 5,000 communities use Flock with 20+ billion monthly plate scans.",
                capabilities = listOf(
                    "License plate capture (up to 100 mph)",
                    "Vehicle make/model/color identification",
                    "Vehicle 'fingerprinting' (dents, stickers, etc.)",
                    "Real-time hotlist alerts",
                    "Integration with NCIC database",
                    "Cross-jurisdiction data sharing"
                ),
                privacyConcerns = listOf(
                    "Mass surveillance of vehicle movements",
                    "30-day data retention (may vary by jurisdiction)",
                    "Shared across law enforcement network",
                    "No warrant required for access",
                    "Can be integrated with Palantir"
                )
            )
            DeviceType.RAVEN_GUNSHOT_DETECTOR -> DetectionPatterns.DeviceTypeInfo(
                name = "Raven Acoustic Gunshot Detector",
                shortDescription = "Audio Surveillance Device",
                fullDescription = "Raven devices (by Flock Safety, similar to ShotSpotter) continuously " +
                    "record audio in 5-second clips, using AI to detect gunfire. As of October 2025, " +
                    "Flock announced Ravens will also listen for 'human distress' (screaming). " +
                    "Solar-powered with cellular connectivity.",
                capabilities = listOf(
                    "Continuous audio monitoring",
                    "Gunshot detection and location",
                    "Human distress/scream detection (new)",
                    "GPS location tracking",
                    "Instant alerts to law enforcement",
                    "AI-powered audio analysis"
                ),
                privacyConcerns = listOf(
                    "Constant audio surveillance",
                    "'Human distress' detection is vague",
                    "Audio recordings may capture conversations",
                    "No consent from recorded individuals",
                    "Potential for false positives"
                )
            )
            DeviceType.PENGUIN_SURVEILLANCE -> DetectionPatterns.DeviceTypeInfo(
                name = "Penguin Surveillance Device",
                shortDescription = "Mobile ALPR System",
                fullDescription = "Penguin devices are mobile surveillance systems often mounted on vehicles.",
                capabilities = listOf("Mobile license plate reading", "Vehicle tracking"),
                privacyConcerns = listOf("Mobile mass surveillance", "Covert operation")
            )
            DeviceType.PIGVISION_SYSTEM -> DetectionPatterns.DeviceTypeInfo(
                name = "Pigvision System",
                shortDescription = "Surveillance Camera System",
                fullDescription = "Pigvision surveillance camera network.",
                capabilities = listOf("Video surveillance", "License plate capture"),
                privacyConcerns = listOf("Mass surveillance", "Data retention unknown")
            )
            DeviceType.MOTOROLA_POLICE_TECH -> DetectionPatterns.DeviceTypeInfo(
                name = "Motorola Police Technology",
                shortDescription = "Law Enforcement Equipment",
                fullDescription = "Motorola Solutions provides extensive police technology including " +
                    "body cameras (V300/V500), in-car video systems, APX radios, and the Vigilant ALPR platform. " +
                    "Evidence is typically stored in their CommandCentral platform.",
                capabilities = listOf(
                    "Body-worn camera recording",
                    "In-car video systems",
                    "Two-way radio communications",
                    "ALPR (Vigilant platform)",
                    "Real-time video streaming",
                    "GPS location tracking"
                ),
                privacyConcerns = listOf(
                    "Continuous recording capability",
                    "Cloud evidence storage",
                    "Cross-agency data sharing",
                    "Facial recognition integration potential"
                )
            )
            DeviceType.AXON_POLICE_TECH -> DetectionPatterns.DeviceTypeInfo(
                name = "Axon Police Technology",
                shortDescription = "Body Cameras & TASERs",
                fullDescription = "Axon (formerly TASER International) is the dominant body camera provider " +
                    "for US law enforcement. They also make TASERs, in-car cameras, and the Evidence.com " +
                    "cloud storage platform. Axon has been expanding into AI-powered features.",
                capabilities = listOf(
                    "Body camera recording (Body 2/3/4)",
                    "TASER deployment logging",
                    "Automatic recording triggers",
                    "Evidence.com cloud storage",
                    "Real-time streaming (Axon Respond)",
                    "AI-powered redaction and transcription"
                ),
                privacyConcerns = listOf(
                    "Massive video evidence database",
                    "AI/facial recognition features",
                    "Third-party cloud storage",
                    "Potential for covert recording",
                    "Data retention policies vary by agency"
                )
            )
            DeviceType.L3HARRIS_SURVEILLANCE -> DetectionPatterns.DeviceTypeInfo(
                name = "L3Harris Surveillance",
                shortDescription = "Advanced Surveillance Systems",
                fullDescription = "L3Harris Technologies manufactures advanced surveillance equipment " +
                    "including cell site simulators (StingRay/Hailstorm), radio systems, and ISR " +
                    "(Intelligence, Surveillance, and Reconnaissance) equipment.",
                capabilities = listOf(
                    "Radio communications systems",
                    "Electronic surveillance",
                    "SIGINT capabilities",
                    "Tactical communications"
                ),
                privacyConcerns = listOf(
                    "Military-grade surveillance tech",
                    "Cell site simulator manufacturer",
                    "Little public accountability"
                )
            )
            DeviceType.CELLEBRITE_FORENSICS -> DetectionPatterns.DeviceTypeInfo(
                name = "Cellebrite Mobile Forensics",
                shortDescription = "Phone Data Extraction Device",
                fullDescription = "Cellebrite UFED (Universal Forensic Extraction Device) is the most widely " +
                    "used mobile forensics tool by law enforcement worldwide. It can extract data from " +
                    "locked phones, including deleted content, app data, passwords, and encrypted messages.\n\n" +
                    "COST: $15,000-$30,000+ per unit (law enforcement, border agents, corporate security)\n\n" +
                    "IF DETECTED NEARBY: This may indicate an active forensic examination. Could be at a " +
                    "police station, border crossing, or mobile forensics unit. Proximity to you suggests " +
                    "potential device seizure risk.",
                capabilities = listOf(
                    "Bypass screen locks on most phones (even newer iPhones with some models)",
                    "Extract ALL data: messages, photos, videos, documents",
                    "Recover DELETED content (messages, photos, call logs)",
                    "Extract app data from: Signal, WhatsApp, Telegram, Instagram, etc.",
                    "Capture passwords, authentication tokens, browser history",
                    "Access encrypted app databases",
                    "Extract cloud account credentials for remote extraction",
                    "Full physical image of device storage",
                    "Geolocation history reconstruction"
                ),
                privacyConcerns = listOf(
                    "Complete phone data extraction - nothing is private",
                    "Recovers content you thought was deleted",
                    "Can access encrypted messaging apps via device extraction",
                    "Border agents can use WITHOUT a warrant",
                    "Some jurisdictions allow at traffic stops",
                    "Data may be retained indefinitely",
                    "Can extract cloud passwords to access online accounts",
                    "Creates complete forensic image for later analysis"
                ),
                recommendations = listOf(
                    "Know your rights: You can refuse consent (5th Amendment) but device may be seized",
                    "At borders: Different rules apply, consent may be compelled",
                    "Strong alphanumeric passwords are harder to crack than PINs",
                    "Enable full-disk encryption",
                    "Consider 'travel mode' devices for sensitive border crossings",
                    "Signal's disappearing messages are harder to recover",
                    "iPhone's USB Restricted Mode helps prevent extraction",
                    "Regular phone reboots help (data protection is stronger after reboot)"
                )
            )
            DeviceType.BODY_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Body-Worn Camera",
                shortDescription = "Police Body Camera",
                fullDescription = "Body-worn cameras record officer interactions with the public. " +
                    "While intended for accountability, they also create extensive surveillance footage " +
                    "of everyone officers encounter.",
                capabilities = listOf(
                    "Video and audio recording",
                    "GPS location logging",
                    "Automatic activation triggers",
                    "Real-time streaming capability",
                    "Night vision/low-light recording"
                ),
                privacyConcerns = listOf(
                    "Records bystanders without consent",
                    "Footage retention varies (30 days to years)",
                    "Can be used for facial recognition",
                    "Officers can review before writing reports",
                    "Release policies often favor police"
                )
            )
            DeviceType.POLICE_RADIO -> DetectionPatterns.DeviceTypeInfo(
                name = "Police Radio System",
                shortDescription = "Law Enforcement Communications",
                fullDescription = "Modern police radios use encrypted digital protocols and often " +
                    "include GPS tracking, emergency alerts, and data transmission capabilities.",
                capabilities = listOf(
                    "Encrypted voice communications",
                    "GPS location tracking",
                    "Data transmission",
                    "Emergency signaling",
                    "Inter-agency communication"
                ),
                privacyConcerns = listOf(
                    "Encryption prevents public monitoring",
                    "Location tracking of officers/suspects",
                    "Interoperability with surveillance systems"
                )
            )
            DeviceType.STINGRAY_IMSI -> DetectionPatterns.DeviceTypeInfo(
                name = "Cell Site Simulator (StingRay)",
                shortDescription = "IMSI Catcher / Fake Cell Tower",
                fullDescription = "Cell site simulators (StingRay, Hailstorm, Kingfish, Crossbow, etc.) are " +
                    "portable devices that impersonate legitimate cell towers to intercept mobile communications. " +
                    "When your phone connects to one, it captures your IMSI (unique SIM identifier), IMEI (device ID), " +
                    "and can intercept calls, texts, and data. These devices affect ALL phones in range (typically 1-2 km), " +
                    "not just the target, making them a mass surveillance tool.\n\n" +
                    "🔍 THIS DETECTION was triggered by anomalous cellular behavior on your device - " +
                    "your phone may have experienced an encryption downgrade, unexpected tower switch, " +
                    "or connected to a suspicious network identifier.",
                capabilities = listOf(
                    "Capture IMSI/IMEI from all phones in range (~1-2 km radius)",
                    "Track phone locations to within a few meters",
                    "Intercept calls, SMS, and data traffic",
                    "Force 4G/5G phones to downgrade to 2G (weak/no encryption)",
                    "Perform man-in-the-middle attacks on communications",
                    "Deny cell service selectively or entirely",
                    "Identify phone make, model, and installed apps",
                    "Clone phone identifiers for impersonation"
                ),
                privacyConcerns = listOf(
                    "Mass surveillance - captures data from EVERYONE nearby, not just targets",
                    "Used under NDA - police often hide usage from courts and defense attorneys",
                    "Can intercept encrypted app traffic via downgrade attacks",
                    "No warrant required in many jurisdictions (pen register theory)",
                    "Disrupts legitimate cell service for entire area",
                    "Data retention policies are opaque or nonexistent",
                    "Often deployed at protests, political events, and public gatherings",
                    "FBI requires local police to drop cases rather than reveal usage"
                ),
                recommendations = listOf(
                    "🛡️ IMMEDIATE: Enable airplane mode, then re-enable only WiFi if needed",
                    "📱 Use Signal, WhatsApp, or other E2E encrypted apps for sensitive communications",
                    "📍 Note your location and time - document for potential legal challenges",
                    "🚶 Leave the area if possible - StingRays have limited range (~1-2 km)",
                    "⚙️ Disable 2G on your phone if supported (Settings → Network → Preferred type → LTE/5G only)",
                    "🔒 Avoid making regular phone calls or SMS - use encrypted messaging instead",
                    "📸 Look for suspicious vehicles (vans, SUVs) with antennas or running generators",
                    "⚠️ FALSE POSITIVE? This could also be triggered by poor coverage, moving between towers, or network maintenance"
                )
            )
            DeviceType.ROGUE_AP -> DetectionPatterns.DeviceTypeInfo(
                name = "Rogue Access Point",
                shortDescription = "Unauthorized WiFi Access Point",
                fullDescription = "A rogue access point is an unauthorized wireless device that may be " +
                    "attempting to intercept network traffic. This could be an 'evil twin' attack, " +
                    "karma attack, or surveillance equipment designed to capture communications.",
                capabilities = listOf(
                    "Intercept wireless traffic",
                    "Capture login credentials",
                    "Man-in-the-middle attacks",
                    "Session hijacking",
                    "SSL stripping"
                ),
                privacyConcerns = listOf(
                    "All network traffic may be captured",
                    "Credentials and sensitive data at risk",
                    "Location tracking through WiFi",
                    "Device fingerprinting"
                ),
                recommendations = listOf(
                    "🛡️ Disconnect from this network immediately",
                    "🔒 Use a VPN for all internet activity",
                    "📱 Verify network authenticity before connecting",
                    "⚠️ Avoid entering sensitive information"
                )
            )
            DeviceType.HIDDEN_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Hidden Camera / Spy Camera",
                shortDescription = "Covert Video Surveillance Device",
                fullDescription = "A WiFi-enabled hidden camera has been detected through its network " +
                    "signature. These devices are often disguised as everyday objects and can stream " +
                    "video to remote viewers or cloud storage.\n\n" +
                    "COMMON DISGUISES:\n" +
                    "- Smoke detectors, carbon monoxide detectors\n" +
                    "- Clocks (alarm clocks, wall clocks)\n" +
                    "- USB chargers and power adapters\n" +
                    "- Electrical outlets and light switches\n" +
                    "- Picture frames and mirrors\n" +
                    "- Tissue boxes, plants, stuffed animals\n" +
                    "- Air purifiers, speakers, routers\n\n" +
                    "WHERE TO CHECK:\n" +
                    "- Airbnbs, hotels, vacation rentals\n" +
                    "- Changing rooms, bathrooms\n" +
                    "- Areas facing beds, showers, toilets\n" +
                    "- Any object with direct line of sight to private areas",
                capabilities = listOf(
                    "HD video recording (720p to 4K)",
                    "Live streaming over WiFi/4G",
                    "Night vision / IR recording",
                    "Motion-activated recording",
                    "Cloud storage upload",
                    "Remote viewing via app",
                    "Audio recording (some models)",
                    "Long battery life or wall-powered"
                ),
                privacyConcerns = listOf(
                    "Illegal in private spaces without consent",
                    "May be recording intimate moments",
                    "Footage can be sold, shared, or used for blackmail",
                    "Common in Airbnb/rental horror stories",
                    "Remote viewer may be watching in real-time",
                    "Cloud storage means footage persists even if camera removed"
                ),
                recommendations = listOf(
                    "IR DETECTION: Use your phone camera (front camera works better) to scan for IR LEDs - they appear as purple/white glow in dark",
                    "PHYSICAL INSPECTION: Check smoke detectors, clocks, outlets, and objects facing bed/bathroom",
                    "LENS REFLECTION: Use flashlight - camera lenses reflect light distinctively",
                    "RF DETECTOR: Dedicated RF detectors can find wireless cameras",
                    "SIGNAL STRENGTH: Move around room - strongest signal indicates camera location",
                    "NETWORK SCAN: Note MAC address to identify manufacturer",
                    "IF FOUND: Document with photos, DO NOT touch, contact police",
                    "LEGAL: Recording in private spaces without consent is illegal - report to authorities"
                )
            )
            DeviceType.SURVEILLANCE_VAN -> DetectionPatterns.DeviceTypeInfo(
                name = "Surveillance Van / Mobile Surveillance",
                shortDescription = "Mobile Surveillance Platform",
                fullDescription = "A mobile hotspot has been detected matching patterns associated with " +
                    "surveillance vehicles. This could be law enforcement, federal agencies, private " +
                    "investigators, or corporate security.\n\n" +
                    "IMPORTANT: Real surveillance operations use BLAND, generic SSIDs - not 'FBI_Van' " +
                    "(that's a joke). Look for: generic fleet names, manufacturer defaults (Sierra Wireless, " +
                    "Cradlepoint), or suspiciously plain hotspot names.\n\n" +
                    "KEY INDICATOR: Same SSID appearing at multiple of YOUR locations (home, work, gym) " +
                    "is a strong signal of targeted surveillance.\n\n" +
                    "WHO MIGHT OPERATE:\n" +
                    "- FBI, DEA, ATF, ICE, USMS\n" +
                    "- State and local police\n" +
                    "- Private investigators\n" +
                    "- Corporate security/counterintelligence",
                capabilities = listOf(
                    "Video/photo surveillance (telephoto, night vision)",
                    "Audio surveillance (parabolic mics, laser mics)",
                    "Cell site simulator (StingRay) operation",
                    "WiFi/Bluetooth interception",
                    "License plate readers (mobile ALPR)",
                    "GPS tracking coordination",
                    "Mobile command and control",
                    "Extended duration stakeout capability"
                ),
                privacyConcerns = listOf(
                    "Targeted surveillance of specific person/location",
                    "May deploy multiple surveillance technologies",
                    "Can follow subjects across jurisdictions",
                    "Often operate in unmarked vehicles (vans, SUVs, work trucks)",
                    "May include covert entry teams",
                    "Video/audio recording of activities",
                    "Cell phone interception capability"
                ),
                recommendations = listOf(
                    "CONFIRM: Does this SSID appear at multiple of YOUR locations?",
                    "LOCATE: Walk around - signal strength helps identify source vehicle",
                    "DOCUMENT: Note vehicle description (make, model, plate, location, time)",
                    "LOOK FOR: Vans/SUVs with running engines, unusual antennas, tinted windows",
                    "PATTERN: Track appearances over multiple days",
                    "COMMUNICATIONS: Use encrypted messaging (Signal) if concerned",
                    "LEGAL: Consult attorney if you believe you're under surveillance",
                    "DO NOT: Approach or confront suspected surveillance vehicle"
                )
            )
            DeviceType.TRACKING_DEVICE -> DetectionPatterns.DeviceTypeInfo(
                name = "Tracking Device / Following Network",
                shortDescription = "Location Tracking via WiFi",
                fullDescription = "A WiFi network has been detected that appears to be following your location. " +
                    "This is determined by the same network appearing at multiple distinct locations you visit.\n\n" +
                    "THIS IS A STRONG INDICATOR OF SURVEILLANCE if:\n" +
                    "- Same BSSID (MAC address) seen at 3+ of your locations\n" +
                    "- Network appears at both home AND work\n" +
                    "- Signal strength varies but network persists\n" +
                    "- Pattern correlates with your movement\n\n" +
                    "POSSIBLE EXPLANATIONS:\n" +
                    "1. Surveillance team using mobile hotspot\n" +
                    "2. GPS/WiFi tracker planted on your vehicle\n" +
                    "3. Tracking device in belongings\n" +
                    "4. Coincidental: neighbor/coworker with same commute (check timing patterns)\n" +
                    "5. Public transit WiFi (bus, train - if pattern matches routes)",
                capabilities = listOf(
                    "Continuous GPS/cellular location tracking",
                    "Movement pattern analysis",
                    "Real-time location updates to monitor",
                    "Geofence alerts (notify when entering/leaving areas)",
                    "Historical location logging",
                    "Long battery life (weeks to months)",
                    "Magnetic mounting for vehicles"
                ),
                privacyConcerns = listOf(
                    "Complete location history being logged",
                    "Daily routine and patterns exposed",
                    "Home, work, and frequent locations known",
                    "Relationships inferred from location data",
                    "May be part of larger surveillance operation",
                    "Could indicate stalking or harassment",
                    "Data may be shared with multiple parties"
                ),
                recommendations = listOf(
                    "VERIFY: Check if network appears at 3+ distinct locations you visit",
                    "FALSE POSITIVE CHECK: Is this a neighbor/coworker with same commute?",
                    "VEHICLE CHECK: Inspect wheel wells, undercarriage, bumpers, trunk",
                    "BELONGINGS: Check bags, briefcase, gifts you received",
                    "OBD PORT: Check for device plugged into car's OBD-II port",
                    "VARY ROUTINE: Take different route - does network still follow?",
                    "DOCUMENT: Log all sighting locations, times, and signal strengths",
                    "LEGAL: Police generally need warrant for GPS tracking (US v. Jones)",
                    "STALKING: If unauthorized, this is criminal in all states",
                    "DO NOT REMOVE: If found, document first - may be evidence"
                )
            )
            DeviceType.RF_JAMMER -> DetectionPatterns.DeviceTypeInfo(
                name = "RF Jammer",
                shortDescription = "Radio Frequency Jammer",
                fullDescription = "A device that disrupts wireless communications by emitting interference. " +
                    "RF jammers are illegal in most jurisdictions but may be used by law enforcement or criminals.",
                capabilities = listOf(
                    "Block cellular signals",
                    "Disrupt WiFi communications",
                    "Prevent GPS tracking",
                    "Interfere with Bluetooth"
                ),
                privacyConcerns = listOf(
                    "Prevents emergency calls",
                    "Blocks legitimate communications",
                    "May indicate criminal activity nearby",
                    "Illegal to operate in most places"
                ),
                recommendations = listOf(
                    "🚨 Leave area - may indicate robbery or attack",
                    "📍 Note location and time",
                    "📞 Report to authorities (from outside range)",
                    "⚠️ Do not attempt to locate the device"
                )
            )
            DeviceType.DRONE -> DetectionPatterns.DeviceTypeInfo(
                name = "Drone/UAV",
                shortDescription = "Unmanned Aerial Vehicle",
                fullDescription = "A drone detected via its WiFi control signal. Drones can carry cameras " +
                    "and other sensors for aerial surveillance.",
                capabilities = listOf(
                    "Aerial video/photo capture",
                    "Thermal imaging",
                    "GPS tracking",
                    "Extended range surveillance",
                    "Face recognition capability"
                ),
                privacyConcerns = listOf(
                    "Surveillance from above",
                    "Difficult to detect visually",
                    "Can follow subjects",
                    "May record private property"
                ),
                recommendations = listOf(
                    "🔍 Look up to locate the drone",
                    "📱 Use the signal strength to find operator",
                    "🚨 Report if over private property",
                    "🏠 Move indoors if concerned"
                )
            )
            DeviceType.SURVEILLANCE_INFRASTRUCTURE -> DetectionPatterns.DeviceTypeInfo(
                name = "Surveillance Infrastructure",
                shortDescription = "Fixed Surveillance System",
                fullDescription = "A concentration of surveillance devices indicating organized monitoring " +
                    "infrastructure such as camera networks or sensor arrays.",
                capabilities = listOf(
                    "Multiple camera coverage",
                    "License plate recognition",
                    "Face recognition",
                    "Behavior analysis",
                    "Cross-camera tracking"
                ),
                privacyConcerns = listOf(
                    "Comprehensive area monitoring",
                    "Long-term data retention",
                    "Automated tracking capabilities",
                    "Integration with law enforcement databases"
                )
            )
            DeviceType.ULTRASONIC_BEACON -> DetectionPatterns.DeviceTypeInfo(
                name = "Ultrasonic Beacon",
                shortDescription = "Ultrasonic Tracking Device",
                fullDescription = "An ultrasonic beacon emitting inaudible sounds for cross-device tracking. " +
                    "These are used by advertisers to link your devices and track you across locations.",
                capabilities = listOf(
                    "Cross-device tracking",
                    "Location tracking in stores",
                    "Ad targeting coordination",
                    "Linking anonymous browsing to identity"
                ),
                privacyConcerns = listOf(
                    "Tracks without visual indication",
                    "Links all your devices together",
                    "Retail location tracking",
                    "Advertising profile building",
                    "Works even with WiFi/Bluetooth off"
                ),
                recommendations = listOf(
                    "🔇 These signals are inaudible to humans",
                    "📱 Some apps can block ultrasonic tracking",
                    "🏪 Common in retail stores and TV ads",
                    "🔒 Consider ultrasonic firewall apps"
                )
            )
            DeviceType.POLICE_VEHICLE -> DetectionPatterns.DeviceTypeInfo(
                name = "Police/Emergency Vehicle",
                shortDescription = "Emergency Vehicle Detected",
                fullDescription = "A police car, ambulance, or fire truck has been detected nearby via its " +
                    "emergency lighting system's Bluetooth connection. Modern emergency vehicles use BLE " +
                    "to synchronize their lightbars (Whelen CenCom, Federal Signal, etc.) and may also " +
                    "have body camera triggers (Axon Signal) that activate when sirens are turned on.",
                capabilities = listOf(
                    "Emergency lightbar synchronization",
                    "Automatic body camera activation",
                    "Siren/PA system integration",
                    "In-car video triggering",
                    "GPS/AVL location reporting"
                ),
                privacyConcerns = listOf(
                    "Officers may be actively recording",
                    "Vehicle likely has dash cameras",
                    "ALPR systems often installed",
                    "Location data transmitted to dispatch"
                ),
                recommendations = listOf(
                    "🚔 Police or emergency vehicle is nearby",
                    "📹 Assume you are being recorded",
                    "📍 Note time and location if relevant",
                    "🚗 May be stationary or moving through area"
                )
            )
            DeviceType.FLEET_VEHICLE -> DetectionPatterns.DeviceTypeInfo(
                name = "Fleet Vehicle",
                shortDescription = "Commercial/Government Fleet Vehicle",
                fullDescription = "A vehicle with fleet management hardware has been detected. This could be " +
                    "a police vehicle, government car, utility truck, or commercial fleet vehicle. These " +
                    "vehicles often have WiFi hotspots using Sierra Wireless or Cradlepoint routers, which " +
                    "are commonly used by law enforcement for mobile data terminals and surveillance equipment.",
                capabilities = listOf(
                    "Mobile WiFi hotspot for in-vehicle systems",
                    "GPS tracking and route logging",
                    "Mobile data terminal connectivity",
                    "Potential ALPR/camera data uplink",
                    "Fleet management and dispatch integration"
                ),
                privacyConcerns = listOf(
                    "May be an unmarked police vehicle",
                    "Could have surveillance equipment",
                    "Vehicle movements are tracked",
                    "Hidden SSID networks are common"
                ),
                recommendations = listOf(
                    "🚐 Fleet vehicle detected via mobile router",
                    "👀 Could be police, utility, or commercial",
                    "📶 Strong signal = vehicle is close",
                    "🔍 Hidden SSIDs are suspicious"
                )
            )
            DeviceType.TESLA_VEHICLE -> DetectionPatterns.DeviceTypeInfo(
                name = "Tesla Vehicle",
                shortDescription = "Nearby Tesla Bluetooth Vehicle Interface",
                fullDescription = "A Tesla vehicle-command Bluetooth interface was observed nearby. " +
                    "This identifies a vehicle radio interface; it does not establish that cameras are recording, " +
                    "that the vehicle is tracking you, or who owns or operates it.",
                capabilities = listOf("Phone-key / vehicle-command Bluetooth connectivity", "Nearby vehicle presence signal"),
                privacyConcerns = listOf("Radio identifiers may be observable nearby", "Advertisement names can be spoofed"),
                recommendations = listOf("Treat as informational vehicle presence", "Correlate with repeated sightings before drawing movement conclusions")
            )
            DeviceType.WAYMO_VEHICLE -> DetectionPatterns.DeviceTypeInfo(
                name = "Waymo Vehicle",
                shortDescription = "Self-Identifying Waymo Bluetooth Candidate",
                fullDescription = "A bounded BLE name self-identifying as Waymo was observed. Waymo uses Bluetooth/Nearby Devices " +
                    "for rider-to-vehicle proximity, but no stable public service UUID is used here. The name is spoofable candidate evidence only.",
                capabilities = listOf("Rider-to-vehicle Bluetooth proximity functions", "Nearby vehicle radio presence"),
                privacyConcerns = listOf("Self-reported BLE names can be spoofed", "No camera or tracking activity is inferred from this signal"),
                recommendations = listOf("Treat as INFO-level candidate evidence", "Use repeated independent observations for any movement analysis")
            )
            DeviceType.SATELLITE_NTN -> DetectionPatterns.DeviceTypeInfo(
                name = "Satellite NTN Device",
                shortDescription = "Non-Terrestrial Network Connection",
                fullDescription = "Connection detected via satellite-based Non-Terrestrial Network (NTN). " +
                    "This could be legitimate D2D service (T-Mobile Starlink, Skylo) or potentially " +
                    "suspicious if unexpected in an area with good terrestrial coverage.",
                capabilities = listOf(
                    "SMS/MMS messaging (provider dependent)",
                    "Emergency SOS services",
                    "Location sharing",
                    "Limited data for select apps"
                ),
                privacyConcerns = listOf(
                    "Satellite connections can be spoofed by ground stations",
                    "NTN parameters can reveal user location",
                    "Unknown satellites may intercept communications"
                ),
                recommendations = listOf(
                    "Verify expected satellite provider",
                    "Check if terrestrial coverage is available",
                    "Monitor for timing anomalies"
                )
            )
            DeviceType.RF_INTERFERENCE -> DetectionPatterns.DeviceTypeInfo(
                name = "RF Interference",
                shortDescription = "Radio Frequency Interference Detected",
                fullDescription = "Significant change in the RF environment detected. This could indicate " +
                    "natural interference, new equipment nearby, or intentional signal manipulation.",
                capabilities = listOf(
                    "Disrupts wireless communications",
                    "May affect WiFi, Bluetooth, cellular",
                    "Can mask other surveillance activity"
                ),
                privacyConcerns = listOf(
                    "May be used to disrupt security systems",
                    "Could be part of surveillance operation",
                    "May precede other attacks"
                )
            )
            DeviceType.RF_ANOMALY -> DetectionPatterns.DeviceTypeInfo(
                name = "RF Environment Anomaly",
                shortDescription = "Unusual RF Activity Pattern",
                fullDescription = "Unusual patterns detected in the local RF environment. This is a " +
                    "low-confidence detection that may indicate surveillance equipment or environmental changes.",
                capabilities = listOf("Varies depending on source"),
                privacyConcerns = listOf("May indicate covert RF equipment nearby")
            )
            DeviceType.HIDDEN_TRANSMITTER -> DetectionPatterns.DeviceTypeInfo(
                name = "Hidden Transmitter",
                shortDescription = "Possible Covert RF Transmission",
                fullDescription = "A potential hidden RF transmitter has been detected. This could be " +
                    "a bug, tracker, or other covert transmission device.",
                capabilities = listOf(
                    "Continuous RF transmission",
                    "May transmit audio/video/data",
                    "Could be a tracking device"
                ),
                privacyConcerns = listOf(
                    "Covert audio/video surveillance",
                    "Location tracking",
                    "Data exfiltration"
                )
            )
            DeviceType.GNSS_SPOOFER -> DetectionPatterns.DeviceTypeInfo(
                name = "GNSS Spoofer",
                shortDescription = "GPS/GNSS Signal Spoofing Detected",
                fullDescription = "Fake GPS/GNSS signals detected. Someone may be attempting to " +
                    "manipulate your location data or deceive GPS-dependent systems.",
                capabilities = listOf(
                    "Broadcast fake GPS coordinates",
                    "Manipulate location-based services",
                    "Deceive navigation systems"
                ),
                privacyConcerns = listOf(
                    "Location data manipulation",
                    "May be used for tracking or misdirection",
                    "Can affect geofencing and location logs"
                )
            )
            DeviceType.GNSS_JAMMER -> DetectionPatterns.DeviceTypeInfo(
                name = "GNSS Jammer",
                shortDescription = "GPS/GNSS Signal Jamming Detected",
                fullDescription = "GPS/GNSS signals are being blocked or degraded. This prevents " +
                    "accurate location determination and may indicate a surveillance operation.",
                capabilities = listOf(
                    "Block GPS/GNSS signals",
                    "Prevent location tracking",
                    "Disable GPS-dependent security"
                ),
                privacyConcerns = listOf(
                    "May be used to avoid location tracking",
                    "Can indicate criminal activity nearby",
                    "Affects emergency services"
                )
            )
            DeviceType.UNKNOWN_SURVEILLANCE -> DetectionPatterns.DeviceTypeInfo(
                name = "Unknown Surveillance Device",
                shortDescription = "Unidentified Surveillance Equipment",
                fullDescription = "This device matches patterns associated with surveillance equipment " +
                    "but the specific manufacturer/model is unknown.",
                capabilities = listOf("Unknown - potentially ALPR or audio surveillance"),
                privacyConcerns = listOf("Unknown data collection practices")
            )
            // Smart Home / IoT Surveillance Devices
            DeviceType.RING_DOORBELL -> DetectionPatterns.DeviceTypeInfo(
                name = "Ring Doorbell/Camera",
                shortDescription = "Amazon Smart Doorbell",
                fullDescription = "Ring doorbells and cameras are connected to Amazon's Neighbors network. " +
                    "Ring has partnerships with over 2,500 police departments allowing law enforcement to " +
                    "request video footage, sometimes without a warrant.",
                capabilities = listOf(
                    "Video recording (1080p-4K)",
                    "Two-way audio",
                    "Motion detection",
                    "Police footage sharing via Neighbors"
                ),
                privacyConcerns = listOf(
                    "Footage shared with 2,500+ police agencies",
                    "Neighbors app creates surveillance network",
                    "Cloud storage on Amazon servers"
                )
            )
            DeviceType.NEST_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Nest/Google Camera",
                shortDescription = "Google Smart Camera",
                fullDescription = "Nest cameras are connected to Google's cloud infrastructure.",
                capabilities = listOf("HD/4K video", "AI-powered detection", "Google Home integration"),
                privacyConcerns = listOf("Data processed by Google AI", "Cloud storage", "May be subpoenaed")
            )
            DeviceType.AMAZON_SIDEWALK -> DetectionPatterns.DeviceTypeInfo(
                name = "Amazon Sidewalk Device",
                shortDescription = "Amazon Mesh Network",
                fullDescription = "Amazon Sidewalk creates a shared mesh network using Echo and Ring devices.",
                capabilities = listOf("Mesh network connectivity", "Extends smart home range"),
                privacyConcerns = listOf("Shares your bandwidth", "Creates neighborhood tracking network")
            )
            DeviceType.WYZE_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Wyze Camera",
                shortDescription = "Budget Smart Camera",
                fullDescription = "Wyze cameras are budget-friendly smart home cameras with cloud connectivity.",
                capabilities = listOf("HD video", "Motion detection", "Cloud storage"),
                privacyConcerns = listOf("Cloud-dependent", "Data breach history")
            )
            DeviceType.ARLO_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Arlo Camera",
                shortDescription = "Wireless Security Camera",
                fullDescription = "Arlo wireless security cameras with cloud storage and AI features.",
                capabilities = listOf("4K video", "Wire-free operation", "AI detection"),
                privacyConcerns = listOf("Cloud storage required", "Subscription model")
            )
            DeviceType.EUFY_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Eufy Camera",
                shortDescription = "Anker Security Camera",
                fullDescription = "Eufy security cameras advertise local storage but have been found to send data to cloud.",
                capabilities = listOf("Local storage option", "AI detection"),
                privacyConcerns = listOf("Misleading local storage claims", "Unencrypted cloud uploads discovered")
            )
            DeviceType.BLINK_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Blink Camera",
                shortDescription = "Amazon Blink Camera",
                fullDescription = "Blink cameras are Amazon-owned, similar privacy concerns to Ring.",
                capabilities = listOf("HD video", "Battery powered", "Cloud storage"),
                privacyConcerns = listOf("Amazon ecosystem", "Cloud storage", "Police partnership potential")
            )
            DeviceType.SIMPLISAFE_DEVICE -> DetectionPatterns.DeviceTypeInfo(
                name = "SimpliSafe Device",
                shortDescription = "SimpliSafe Security",
                fullDescription = "SimpliSafe home security system with professional monitoring option.",
                capabilities = listOf("Intrusion detection", "Video doorbells"),
                privacyConcerns = listOf("Professional monitoring access", "Cloud connectivity")
            )
            DeviceType.ADT_DEVICE -> DetectionPatterns.DeviceTypeInfo(
                name = "ADT Security Device",
                shortDescription = "ADT Security System",
                fullDescription = "ADT is a major security provider with deep law enforcement relationships.",
                capabilities = listOf("Professional monitoring", "Video surveillance"),
                privacyConcerns = listOf("Law enforcement partnerships", "Central monitoring station")
            )
            DeviceType.VIVINT_DEVICE -> DetectionPatterns.DeviceTypeInfo(
                name = "Vivint Smart Home",
                shortDescription = "Vivint Security",
                fullDescription = "Vivint smart home security with AI-powered cameras and monitoring.",
                capabilities = listOf("AI camera analytics", "Professional monitoring"),
                privacyConcerns = listOf("Professional monitoring", "AI video analysis")
            )
            // Retail/Commercial Tracking
            DeviceType.BLUETOOTH_BEACON -> DetectionPatterns.DeviceTypeInfo(
                name = "Bluetooth Beacon",
                shortDescription = "Location Beacon",
                fullDescription = "Bluetooth beacons are used for indoor positioning and retail tracking.",
                capabilities = listOf("Indoor positioning", "Proximity detection"),
                privacyConcerns = listOf("Tracks movement in stores", "Links to mobile apps")
            )
            DeviceType.RETAIL_TRACKER -> DetectionPatterns.DeviceTypeInfo(
                name = "Retail Tracker",
                shortDescription = "Store Tracking System",
                fullDescription = "Retail tracking systems monitor customer movement and dwell time in stores.",
                capabilities = listOf("Foot traffic analysis", "Dwell time tracking"),
                privacyConcerns = listOf("Tracks without consent", "Links to purchase data")
            )
            DeviceType.CROWD_ANALYTICS -> DetectionPatterns.DeviceTypeInfo(
                name = "Crowd Analytics Sensor",
                shortDescription = "People Counting System",
                fullDescription = "Sensors that count people and analyze crowd movement patterns.",
                capabilities = listOf("People counting", "Flow analysis"),
                privacyConcerns = listOf("Mass tracking", "Behavior analysis")
            )
            DeviceType.FACIAL_RECOGNITION -> DetectionPatterns.DeviceTypeInfo(
                name = "Facial Recognition System",
                shortDescription = "Face Detection Camera",
                fullDescription = "Camera system with facial recognition capabilities for identification.",
                capabilities = listOf("Face detection", "Identity matching"),
                privacyConcerns = listOf("Biometric data collection", "Identity tracking")
            )
            // Tracker Devices
            DeviceType.AIRTAG -> DetectionPatterns.DeviceTypeInfo(
                name = "Apple AirTag",
                shortDescription = "Apple Tracker",
                fullDescription = "Apple AirTag Bluetooth tracker. Can be misused for stalking.",
                capabilities = listOf("Precision Finding", "Find My network"),
                privacyConcerns = listOf("Stalking potential", "Movement tracking"),
                recommendations = listOf("Check if you're being tracked unexpectedly", "iOS alerts to unknown AirTags")
            )
            DeviceType.TILE_TRACKER -> DetectionPatterns.DeviceTypeInfo(
                name = "Tile Tracker",
                shortDescription = "Tile Bluetooth Tracker",
                fullDescription = "Tile Bluetooth trackers for finding lost items. Now owned by Life360.",
                capabilities = listOf("Bluetooth tracking", "Community finding"),
                privacyConcerns = listOf("Life360 data practices", "Stalking potential")
            )
            DeviceType.SAMSUNG_SMARTTAG -> DetectionPatterns.DeviceTypeInfo(
                name = "Samsung SmartTag",
                shortDescription = "Samsung Tracker",
                fullDescription = "Samsung's Bluetooth tracker using Galaxy Find Network.",
                capabilities = listOf("UWB precision finding", "Galaxy network"),
                privacyConcerns = listOf("Movement tracking", "Stalking potential")
            )
            DeviceType.GENERIC_BLE_TRACKER -> DetectionPatterns.DeviceTypeInfo(
                name = "BLE Tracker",
                shortDescription = "Bluetooth Tracker Device",
                fullDescription = "Generic Bluetooth tracker device detected.",
                capabilities = listOf("Bluetooth tracking"),
                privacyConcerns = listOf("Movement tracking", "Stalking potential")
            )
            // Traffic Enforcement
            DeviceType.SPEED_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Speed Camera",
                shortDescription = "Speed Enforcement Camera",
                fullDescription = "Automated speed enforcement camera that photographs speeding vehicles.",
                capabilities = listOf("Speed measurement", "License plate capture"),
                privacyConcerns = listOf("Vehicle tracking", "Photo evidence stored")
            )
            DeviceType.RED_LIGHT_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Red Light Camera",
                shortDescription = "Intersection Enforcement Camera",
                fullDescription = "Camera that captures vehicles running red lights.",
                capabilities = listOf("Intersection monitoring", "License plate capture"),
                privacyConcerns = listOf("Intersection surveillance", "Data retention")
            )
            DeviceType.TOLL_READER -> DetectionPatterns.DeviceTypeInfo(
                name = "Toll/E-ZPass Reader",
                shortDescription = "Electronic Toll Collection",
                fullDescription = "Electronic toll collection system that reads transponders and plates.",
                capabilities = listOf("Transponder reading", "License plate capture"),
                privacyConcerns = listOf("Travel pattern tracking", "Government data access")
            )
            DeviceType.TRAFFIC_SENSOR -> DetectionPatterns.DeviceTypeInfo(
                name = "Traffic Sensor",
                shortDescription = "Traffic Monitoring Sensor",
                fullDescription = "Sensor for monitoring traffic flow and conditions.",
                capabilities = listOf("Vehicle counting", "Speed estimation"),
                privacyConcerns = listOf("Movement pattern data")
            )
            // Law Enforcement Specific
            DeviceType.SHOTSPOTTER -> DetectionPatterns.DeviceTypeInfo(
                name = "ShotSpotter Sensor",
                shortDescription = "Gunshot Detection System",
                fullDescription = "ShotSpotter acoustic sensors detect gunfire and continuously monitor audio.",
                capabilities = listOf("Gunshot detection", "Audio triangulation"),
                privacyConcerns = listOf("Continuous audio surveillance", "May capture conversations", "False positive issues")
            )
            DeviceType.CLEARVIEW_AI -> DetectionPatterns.DeviceTypeInfo(
                name = "Clearview AI System",
                shortDescription = "Facial Recognition Database",
                fullDescription = "Clearview AI scraped billions of photos to create a massive facial recognition database.",
                capabilities = listOf("Face matching from any photo", "Social media linking"),
                privacyConcerns = listOf("Scraped photos without consent", "Used by thousands of agencies", "No opt-out")
            )
            DeviceType.PALANTIR_DEVICE -> DetectionPatterns.DeviceTypeInfo(
                name = "Palantir Device",
                shortDescription = "Data Analytics Platform",
                fullDescription = "Palantir provides data integration and analytics to law enforcement.",
                capabilities = listOf("Cross-database linking", "Pattern analysis"),
                privacyConcerns = listOf("Aggregates multiple data sources", "Predictive policing")
            )
            DeviceType.GRAYKEY_DEVICE -> DetectionPatterns.DeviceTypeInfo(
                name = "GrayKey iPhone Forensics",
                shortDescription = "iPhone Passcode Cracking Device",
                fullDescription = "GrayKey (by Grayshift, founded by ex-Apple engineers) is specifically designed " +
                    "to bypass iPhone passcodes and extract data. It's one of the most powerful iPhone " +
                    "forensics tools available, capable of cracking even recent iOS versions.\n\n" +
                    "COST: $15,000-$30,000 per unit (exclusively sold to law enforcement)\n\n" +
                    "IF DETECTED NEARBY: This is HIGHLY UNUSUAL. GrayKey devices are expensive, rare, and " +
                    "typically only used in police forensics labs. Detection suggests active iPhone examination.",
                capabilities = listOf(
                    "Crack iPhone passcodes (4-digit to complex alphanumeric)",
                    "Works on recent iOS versions (with some delays)",
                    "BFU (Before First Unlock) extraction on some models",
                    "AFU (After First Unlock) full extraction",
                    "Extract: Messages, photos, call logs, app data",
                    "Access encrypted keychain data",
                    "Recover some deleted content",
                    "Faster than Cellebrite for iPhones in many cases"
                ),
                privacyConcerns = listOf(
                    "Can crack most iPhone passcodes given enough time",
                    "Law enforcement exclusive - indicates serious investigation",
                    "Newer iPhones with USB Restricted Mode are more resistant",
                    "Alphanumeric passwords take much longer to crack",
                    "Data extraction is comprehensive once unlocked"
                ),
                recommendations = listOf(
                    "Use long alphanumeric passcode (not 4/6 digit PIN)",
                    "Enable USB Restricted Mode (Settings > Face/Touch ID > Accessories)",
                    "Reboot phone before any law enforcement encounter",
                    "iPhone locked + BFU state is most secure",
                    "Consider device legal protections (5th Amendment)",
                    "Know that refusing to unlock may result in device seizure"
                )
            )
            // Network Surveillance
            DeviceType.WIFI_PINEAPPLE -> DetectionPatterns.DeviceTypeInfo(
                name = "WiFi Pineapple",
                shortDescription = "Network Auditing/Attack Tool",
                fullDescription = "Hak5 WiFi Pineapple is used for network security testing but can be misused.",
                capabilities = listOf("Evil twin attacks", "Credential capture"),
                privacyConcerns = listOf("MITM attacks", "Password theft"),
                recommendations = listOf("Don't connect to unknown WiFi", "Use VPN")
            )
            DeviceType.PACKET_SNIFFER -> DetectionPatterns.DeviceTypeInfo(
                name = "Packet Sniffer",
                shortDescription = "Network Traffic Analyzer",
                fullDescription = "Device capturing network traffic for analysis.",
                capabilities = listOf("Traffic capture", "Protocol analysis"),
                privacyConcerns = listOf("Captures unencrypted data", "Password interception")
            )
            DeviceType.MAN_IN_MIDDLE -> DetectionPatterns.DeviceTypeInfo(
                name = "MITM Device",
                shortDescription = "Man-in-the-Middle Attack Device",
                fullDescription = "Device positioned between user and network to intercept communications.",
                capabilities = listOf("Traffic interception", "SSL stripping"),
                privacyConcerns = listOf("All traffic exposed", "Identity theft risk")
            )
            // ==================== Flipper Zero and Hacking Tools ====================
            DeviceType.FLIPPER_ZERO -> DetectionPatterns.DeviceTypeInfo(
                name = "Flipper Zero",
                shortDescription = "Multi-Tool Hacking Device",
                fullDescription = "Flipper Zero is a portable multi-tool device designed for hardware hacking, " +
                    "pentesting, and interacting with access control systems. It combines multiple radio protocols " +
                    "and can read, clone, and emulate various types of wireless signals. While it has many legitimate " +
                    "uses for security research and education, it can also be misused for malicious purposes.\n\n" +
                    "FIRMWARE VARIANTS:\n" +
                    "- Official: Standard features with regional restrictions\n" +
                    "- Unleashed: Removes region locks on Sub-GHz\n" +
                    "- RogueMaster: More aggressive features\n" +
                    "- Xtreme/Momentum: Feature-packed custom firmware",
                capabilities = listOf(
                    "Sub-GHz (300-928 MHz): Garage doors, car key fobs, wireless sensors",
                    "RFID (125 kHz): EM4100, HID Prox access cards",
                    "NFC (13.56 MHz): Mifare, NTAG, EMV payment cards (read-only)",
                    "Infrared: TV remotes, AC units, appliances",
                    "iButton: 1-Wire devices, building access",
                    "GPIO: Hardware debugging and hacking",
                    "BadUSB: Keystroke injection via USB",
                    "BadBT: Bluetooth keystroke injection",
                    "BLE: Device impersonation, spam attacks"
                ),
                privacyConcerns = listOf(
                    "Can clone access cards (RFID/NFC)",
                    "Can capture and replay garage door signals",
                    "BadUSB can execute malicious scripts on unlocked computers",
                    "BLE spam can disrupt iOS/Android devices",
                    "Can be used for stalking via car key relay attacks",
                    "Presence may indicate targeted hacking attempt"
                ),
                recommendations = listOf(
                    "Context matters: Security conferences = expected, random public place = concerning",
                    "If experiencing device popups/crashes, check if a Flipper is nearby",
                    "Look for small orange/black device with LCD screen and D-pad",
                    "If your garage/car is affected, consider rolling code upgrades",
                    "Document detection time/location for pattern analysis",
                    "Flipper has limited range (~10-50m depending on attack)"
                )
            )
            DeviceType.FLIPPER_ZERO_SPAM -> DetectionPatterns.DeviceTypeInfo(
                name = "Flipper Zero BLE Spam Attack",
                shortDescription = "Active BLE Spam Detected",
                fullDescription = "An active Bluetooth Low Energy spam attack has been detected, likely from a " +
                    "Flipper Zero device. This attack floods the BLE spectrum with fake device advertisements, " +
                    "causing popup floods on iPhones (Apple device pairing requests) or notification spam on " +
                    "Android (Fast Pair requests).\n\n" +
                    "This is MALICIOUS use of a Flipper Zero - there is no legitimate reason to spam BLE.",
                capabilities = listOf(
                    "iOS Popup Attack: Floods with fake AirPods/Apple device broadcasts",
                    "Android Fast Pair Spam: Floods with fake Google Fast Pair advertisements",
                    "Can crash older iOS versions",
                    "Can make devices unusable due to constant popups",
                    "Used for harassment or as distraction for other attacks"
                ),
                privacyConcerns = listOf(
                    "Active attack in progress",
                    "May be cover for other malicious activity",
                    "Indicates hostile intent",
                    "Person may be targeting you specifically"
                ),
                recommendations = listOf(
                    "IMMEDIATE: Turn off Bluetooth to stop popups",
                    "Look for person with small device (orange/black, LCD screen)",
                    "Move away from the area",
                    "If attack follows you, document and report to authorities",
                    "Note time/location for pattern analysis",
                    "Check if attacks stop when specific person leaves"
                )
            )
            DeviceType.HACKRF_SDR -> DetectionPatterns.DeviceTypeInfo(
                name = "Software Defined Radio (HackRF/SDR)",
                shortDescription = "RF Analysis Device",
                fullDescription = "A Software Defined Radio (SDR) device capable of receiving and transmitting " +
                    "across a wide range of radio frequencies. HackRF One can cover 1 MHz to 6 GHz. " +
                    "Used for RF research, amateur radio, and security testing.",
                capabilities = listOf(
                    "Wide frequency range reception (1 MHz - 6 GHz)",
                    "Transmit capability on HackRF",
                    "Spectrum analysis",
                    "Protocol decoding",
                    "GPS spoofing (illegal)",
                    "Cellular signal analysis"
                ),
                privacyConcerns = listOf(
                    "Can intercept unencrypted RF signals",
                    "Can analyze your wireless transmissions",
                    "Transmit mode can jam/spoof signals",
                    "May be recording RF environment"
                ),
                recommendations = listOf(
                    "SDRs are common among radio hobbyists",
                    "Presence alone is not concerning",
                    "Be cautious if combined with other suspicious behavior",
                    "If experiencing GPS issues, SDR spoofing is possible"
                )
            )
            DeviceType.PROXMARK -> DetectionPatterns.DeviceTypeInfo(
                name = "Proxmark RFID/NFC Tool",
                shortDescription = "RFID/NFC Cloning Device",
                fullDescription = "Proxmark is a powerful RFID/NFC research tool that can read, write, " +
                    "and emulate various card types. It's the gold standard for RFID security research " +
                    "but can be misused to clone access cards.",
                capabilities = listOf(
                    "Read/write 125 kHz RFID cards (EM4100, HID Prox)",
                    "Read/write 13.56 MHz NFC cards (Mifare, iClass)",
                    "Emulate cards in real-time",
                    "Sniff card-reader communications",
                    "Brute force weak card encryption"
                ),
                privacyConcerns = listOf(
                    "Can clone building access cards",
                    "Can read cards in your wallet/pocket",
                    "May be attempting unauthorized access",
                    "Often used by physical pentesters"
                ),
                recommendations = listOf(
                    "Common at security conferences",
                    "Unusual in random public places",
                    "Use RFID-blocking wallet if concerned",
                    "Report if seen near secure facilities"
                )
            )
            DeviceType.USB_RUBBER_DUCKY -> DetectionPatterns.DeviceTypeInfo(
                name = "USB Rubber Ducky",
                shortDescription = "Keystroke Injection Device",
                fullDescription = "The USB Rubber Ducky looks like a USB flash drive but acts as a keyboard, " +
                    "typing pre-programmed keystrokes at superhuman speed. It can execute complex attacks " +
                    "in seconds on an unlocked computer.",
                capabilities = listOf(
                    "Keystroke injection at 1000+ characters/second",
                    "Can open shells, download malware, exfiltrate data",
                    "Works on any OS that accepts USB keyboards",
                    "New versions have WiFi and storage"
                ),
                privacyConcerns = listOf(
                    "Requires physical access to computer",
                    "Attack happens in seconds",
                    "Difficult to detect during execution"
                ),
                recommendations = listOf(
                    "Never leave computer unlocked",
                    "Be suspicious of 'found' USB drives",
                    "USB port locks can prevent attacks",
                    "Group Policy can restrict USB devices"
                )
            )
            DeviceType.BASH_BUNNY -> DetectionPatterns.DeviceTypeInfo(
                name = "Bash Bunny",
                shortDescription = "USB Attack Platform",
                fullDescription = "Hak5 Bash Bunny is an advanced USB attack platform that can emulate " +
                    "multiple device types (keyboard, storage, ethernet) and run complex payloads.",
                capabilities = listOf(
                    "Multi-device emulation (keyboard, storage, ethernet)",
                    "Runs Debian Linux internally",
                    "Can exfiltrate files to internal storage",
                    "Network attacks via ethernet emulation",
                    "Credential harvesting"
                ),
                privacyConcerns = listOf(
                    "More powerful than Rubber Ducky",
                    "Can steal files and credentials",
                    "Network man-in-the-middle capability"
                ),
                recommendations = listOf(
                    "Same protections as USB Rubber Ducky",
                    "Monitor for new network adapters",
                    "Physical security is key"
                )
            )
            DeviceType.LAN_TURTLE -> DetectionPatterns.DeviceTypeInfo(
                name = "LAN Turtle",
                shortDescription = "Covert Network Access Device",
                fullDescription = "Hak5 LAN Turtle is a covert network access device disguised as a USB ethernet adapter. " +
                    "It provides persistent remote access to networks.",
                capabilities = listOf(
                    "Appears as normal USB ethernet adapter",
                    "Provides remote shell access",
                    "Man-in-the-middle network position",
                    "DNS spoofing and credential capture",
                    "VPN tunneling out of network"
                ),
                privacyConcerns = listOf(
                    "Hard to detect (looks like normal adapter)",
                    "Provides persistent access",
                    "All your network traffic may be monitored"
                ),
                recommendations = listOf(
                    "Check for unknown USB devices on computers",
                    "Monitor network for unauthorized devices",
                    "Use encrypted connections (HTTPS, VPN)"
                )
            )
            DeviceType.KEYCROC -> DetectionPatterns.DeviceTypeInfo(
                name = "Key Croc",
                shortDescription = "Keylogger with Exfiltration",
                fullDescription = "Hak5 Key Croc is an inline keylogger that sits between keyboard and computer, " +
                    "capturing all keystrokes and exfiltrating them over WiFi.",
                capabilities = listOf(
                    "Captures all keystrokes",
                    "WiFi exfiltration of captured data",
                    "Trigger-based payload execution",
                    "Pattern matching for credentials"
                ),
                privacyConcerns = listOf(
                    "Captures all passwords typed",
                    "Hard to detect (inline device)",
                    "Real-time exfiltration capability"
                ),
                recommendations = listOf(
                    "Visually inspect keyboard connection",
                    "Use password managers (paste instead of type)",
                    "Check for inline devices regularly"
                )
            )
            DeviceType.SHARK_JACK -> DetectionPatterns.DeviceTypeInfo(
                name = "Shark Jack",
                shortDescription = "Portable Network Attack Tool",
                fullDescription = "Hak5 Shark Jack is a portable network attack and reconnaissance tool " +
                    "that fits in your pocket.",
                capabilities = listOf(
                    "Network reconnaissance",
                    "Automated attack payloads",
                    "Nmap scanning",
                    "Data exfiltration"
                ),
                privacyConcerns = listOf(
                    "Can scan and attack networks quickly",
                    "Automated reconnaissance",
                    "Portable and concealable"
                ),
                recommendations = listOf(
                    "Monitor for port scanning",
                    "Network access control",
                    "802.1X authentication"
                )
            )
            DeviceType.SCREEN_CRAB -> DetectionPatterns.DeviceTypeInfo(
                name = "Screen Crab",
                shortDescription = "HDMI Man-in-the-Middle",
                fullDescription = "Hak5 Screen Crab intercepts HDMI video streams, capturing screenshots " +
                    "and exfiltrating them over WiFi.",
                capabilities = listOf(
                    "HDMI video interception",
                    "Screenshot capture",
                    "WiFi exfiltration",
                    "Remote viewing capability"
                ),
                privacyConcerns = listOf(
                    "Captures everything on screen",
                    "Passwords visible when typed",
                    "Sensitive documents exposed"
                ),
                recommendations = listOf(
                    "Check HDMI connections",
                    "Look for inline devices",
                    "Use encrypted screen content where possible"
                )
            )
            DeviceType.GENERIC_HACKING_TOOL -> DetectionPatterns.DeviceTypeInfo(
                name = "Security Testing Tool",
                shortDescription = "Potential Hacking Device",
                fullDescription = "A device matching patterns associated with security testing and hacking tools " +
                    "has been detected. This could be legitimate security research or potentially malicious activity.",
                capabilities = listOf(
                    "Varies by specific device",
                    "May include wireless attacks",
                    "May include physical access attacks"
                ),
                privacyConcerns = listOf(
                    "Purpose unknown without context",
                    "Could be legitimate or malicious",
                    "Monitor for suspicious behavior"
                ),
                recommendations = listOf(
                    "Consider the context (security conference vs random location)",
                    "Watch for correlated suspicious activity",
                    "Document if concerned"
                )
            )
            // Misc Surveillance
            DeviceType.LICENSE_PLATE_READER -> DetectionPatterns.DeviceTypeInfo(
                name = "License Plate Reader",
                shortDescription = "ALPR Camera",
                fullDescription = "Automated License Plate Reader camera system.",
                capabilities = listOf("Plate capture", "Vehicle identification"),
                privacyConcerns = listOf("Mass vehicle tracking", "Location history")
            )
            DeviceType.CCTV_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "CCTV Camera",
                shortDescription = "Closed-Circuit Camera",
                fullDescription = "Standard surveillance camera system.",
                capabilities = listOf("Video recording", "Motion detection"),
                privacyConcerns = listOf("Continuous monitoring", "Recording retention")
            )
            DeviceType.PTZ_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "PTZ Camera",
                shortDescription = "Pan-Tilt-Zoom Camera",
                fullDescription = "Camera with remote pan, tilt, and zoom capabilities for active surveillance.",
                capabilities = listOf("Remote directional control", "Optical zoom"),
                privacyConcerns = listOf("Active tracking capability")
            )
            DeviceType.THERMAL_CAMERA -> DetectionPatterns.DeviceTypeInfo(
                name = "Thermal Camera",
                shortDescription = "Infrared/Thermal Imaging",
                fullDescription = "Camera using thermal imaging to detect heat signatures.",
                capabilities = listOf("Heat detection", "Night operation"),
                privacyConcerns = listOf("Sees through concealment", "Activity detection in private spaces")
            )
            DeviceType.NIGHT_VISION -> DetectionPatterns.DeviceTypeInfo(
                name = "Night Vision Device",
                shortDescription = "Low-Light Imaging",
                fullDescription = "Device using infrared or image intensification for night surveillance.",
                capabilities = listOf("Low-light operation"),
                privacyConcerns = listOf("Surveillance in darkness")
            )
        }
    }
}
