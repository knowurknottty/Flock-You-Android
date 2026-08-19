package com.flockyou.testmode

internal data class SyntheticLocation(
    val latitude: Double,
    val longitude: Double
)

internal object SyntheticLocationPolicy {
    fun validated(latitude: Double?, longitude: Double?): SyntheticLocation? {
        if (latitude == null || longitude == null) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return SyntheticLocation(latitude, longitude)
    }
}
