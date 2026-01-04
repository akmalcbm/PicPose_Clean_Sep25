package com.picpose.bestphotographyapp.presentation.ads

object AdsFrequencyManager {

    private val lastShownMap = mutableMapOf<String, Long>()

    fun canShow(placementKey: String, frequencyPerHour: Int): Boolean {
        val now = System.currentTimeMillis()
        val last = lastShownMap[placementKey] ?: return true

        val minInterval = 60 * 60 * 1000L / frequencyPerHour
        return now - last >= minInterval
    }

    fun markShown(placementKey: String) {
        lastShownMap[placementKey] = System.currentTimeMillis()
    }
}
