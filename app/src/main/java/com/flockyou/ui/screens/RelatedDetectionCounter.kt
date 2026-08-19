package com.flockyou.ui.screens

import com.flockyou.data.model.Detection

/**
 * Computes the same "related detection" counts previously produced by MainViewModel's nested scan,
 * without comparing every detection to every other detection.
 *
 * Semantics are intentionally unchanged:
 * 1. any two detections with the same non-null MAC are related, regardless of time/type/protocol;
 * 2. otherwise, detections with the same device type + protocol are related when timestamps differ
 *    by strictly less than ten minutes;
 * 3. a pair satisfying both rules is counted once.
 *
 * Work is O(n log n) from grouping/sorting plus linear sliding-window passes, versus the former
 * O(n^2) nested loop on every repository emission.
 */
object RelatedDetectionCounter {
    private const val RELATED_WINDOW_MS = 600_000L

    fun compute(detections: List<Detection>): Map<String, Int> {
        if (detections.size < 2) return emptyMap()

        val macGroupSizes = detections
            .asSequence()
            .mapNotNull { detection -> detection.macAddress?.let { it to detection } }
            .groupingBy { it.first }
            .eachCount()

        val result = HashMap<String, Int>(detections.size)

        detections
            .groupBy { detection -> detection.deviceType to detection.protocol }
            .values
            .forEach { group ->
                val sorted = group.sortedBy { it.timestamp }
                val allWindowCounts = strictWindowCounts(sorted)

                // Same-MAC neighbors inside this type/protocol time window are already counted by
                // the global MAC rule, so subtract them from the time-rule contribution.
                val sameMacWindowCounts = HashMap<String, Int>()
                sorted
                    .filter { it.macAddress != null }
                    .groupBy { it.macAddress!! }
                    .values
                    .forEach { sameMacGroup ->
                        val sameMacSorted = sameMacGroup.sortedBy { it.timestamp }
                        val windowCounts = strictWindowCounts(sameMacSorted)
                        sameMacSorted.forEachIndexed { index, detection ->
                            sameMacWindowCounts[detection.id] = windowCounts[index]
                        }
                    }

                sorted.forEachIndexed { index, detection ->
                    val macRelated = detection.macAddress
                        ?.let { mac -> (macGroupSizes[mac] ?: 1) - 1 }
                        ?: 0
                    val timeRelated = allWindowCounts[index] -
                        (sameMacWindowCounts[detection.id] ?: 0)
                    val total = macRelated + timeRelated
                    if (total > 0) result[detection.id] = total
                }
            }

        return result
    }

    /**
     * For each sorted detection, count neighbors whose absolute timestamp distance is strictly less
     * than [RELATED_WINDOW_MS]. Two monotonic pointers make the pass linear.
     */
    private fun strictWindowCounts(sorted: List<Detection>): IntArray {
        val counts = IntArray(sorted.size)
        if (sorted.size < 2) return counts

        var left = 0
        var right = 0

        for (index in sorted.indices) {
            val timestamp = sorted[index].timestamp

            while (left < sorted.size && timestamp - sorted[left].timestamp >= RELATED_WINDOW_MS) {
                left++
            }

            if (right < index) right = index
            while (
                right + 1 < sorted.size &&
                sorted[right + 1].timestamp - timestamp < RELATED_WINDOW_MS
            ) {
                right++
            }

            counts[index] = (right - left).coerceAtLeast(0)
        }

        return counts
    }
}
