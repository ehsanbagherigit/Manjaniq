package com.manjaniq.core

data class AetherRange(val min: Int, val max: Int) {

    fun format(): String = if (min == max) "$min" else "$min-$max"

    companion object {
        private const val MIN_FRAGMENT_SIZE = 1
        private const val MAX_FRAGMENT_SIZE = 4096
        private const val MIN_FRAGMENT_DELAY = 0
        private const val MAX_FRAGMENT_DELAY = 1000

        fun parse(value: String?, allowedMin: Int, allowedMax: Int): AetherRange? {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val parts = trimmed.split('-')
            val (min, max) = when (parts.size) {
                1 -> {
                    val n = parts[0].trim().toIntOrNull() ?: return null
                    n to n
                }
                2 -> {
                    val low = parts[0].trim().toIntOrNull() ?: return null
                    val high = parts[1].trim().toIntOrNull() ?: return null
                    low to high
                }
                else -> return null
            }
            if (min < allowedMin || max > allowedMax || min > max) return null
            return AetherRange(min, max)
        }

        fun parseFragmentSize(value: String?): AetherRange? =
            parse(value, MIN_FRAGMENT_SIZE, MAX_FRAGMENT_SIZE)

        fun parseFragmentDelay(value: String?): AetherRange? =
            parse(value, MIN_FRAGMENT_DELAY, MAX_FRAGMENT_DELAY)
    }
}
