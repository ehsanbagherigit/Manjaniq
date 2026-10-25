package com.manjaniq.core

data class AetherRange(val min: Int, val max: Int) {

    override fun toString(): String = if (min == max) "$min" else "$min-$max"

    fun format(): String = toString()

    companion object {

        val FRAGMENT_SIZE = 1..4096
        val FRAGMENT_DELAY = 0..1000

        private fun decimal(text: String): Int? {
            if (text.isEmpty() || text.length > 5) return null
            return text.fold(0) { value, ch -> value * 10 + (ch.digitToIntOrNull() ?: return null) }
        }

        fun parse(text: String?, bounds: IntRange): AetherRange? {
            val parts = text?.trim()?.split('-') ?: return null
            if (parts.size !in 1..2) return null
            val values = parts.map { part -> decimal(part.trim())?.takeIf { it in bounds } ?: return null }
            return AetherRange(values.min(), values.max())
        }

        fun parse(value: String?, allowedMin: Int, allowedMax: Int): AetherRange? =
            parse(value, allowedMin..allowedMax)

        fun parseFragmentSize(value: String?): AetherRange? =
            parse(value, FRAGMENT_SIZE)

        fun parseFragmentDelay(value: String?): AetherRange? =
            parse(value, FRAGMENT_DELAY)
    }
}
