package com.flockyou.service

import com.flockyou.data.model.DetectionProtocol

internal fun ScanStatistics.recordCandidate(
    protocol: DetectionProtocol,
    count: Int = 1
): ScanStatistics = when (protocol) {
    DetectionProtocol.BLUETOOTH_LE -> copy(bleCandidates = bleCandidates + count)
    DetectionProtocol.WIFI -> copy(wifiCandidates = wifiCandidates + count)
    else -> this
}

internal fun ScanStatistics.recordPersistenceOutcome(
    protocol: DetectionProtocol,
    isNew: Boolean
): ScanStatistics = when (protocol) {
    DetectionProtocol.BLUETOOTH_LE -> if (isNew) {
        copy(
            bleDetectionsCreated = bleDetectionsCreated + 1,
            detectionsCreated = detectionsCreated + 1
        )
    } else copy(bleDetectionsNotNew = bleDetectionsNotNew + 1)
    DetectionProtocol.WIFI -> if (isNew) {
        copy(
            wifiDetectionsCreated = wifiDetectionsCreated + 1,
            detectionsCreated = detectionsCreated + 1
        )
    } else copy(wifiDetectionsNotNew = wifiDetectionsNotNew + 1)
    else -> if (isNew) copy(detectionsCreated = detectionsCreated + 1) else this
}

internal fun ScanStatistics.recordExplicitWifiSuppressions(count: Int = 1): ScanStatistics =
    copy(wifiExplicitSuppressions = wifiExplicitSuppressions + count)
