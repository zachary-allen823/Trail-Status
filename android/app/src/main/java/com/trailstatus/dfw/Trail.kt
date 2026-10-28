package com.trailstatus.dfw

enum class StatusLevel(val code: Int, val label: String, val argb: Long) {
    OPEN(1, "Open", 0xFF2E7D32),
    MINOR(2, "Minor Issues", 0xFFF9A825),
    CAUTION(3, "Caution", 0xFFEF6C00),
    CLOSED(4, "Closed", 0xFFC62828),
    UNKNOWN(0, "Unknown", 0xFF757575);

    companion object {
        fun fromCode(code: Int?): StatusLevel? = entries.firstOrNull { it.code == code && it != UNKNOWN }

        fun fromColorClass(color: String?): StatusLevel = when (color) {
            "green" -> OPEN
            "yellow" -> MINOR
            "amber", "orange" -> CAUTION
            "red" -> CLOSED
            else -> UNKNOWN
        }
    }
}

data class Trail(
    val name: String,
    val url: String?,
    val level: StatusLevel,
    val statusText: String,
    /** Epoch seconds of the last trail report, if known. */
    val updatedEpochSec: Long?,
)

data class TrailCache(
    val fetchedAtMillis: Long,
    val trails: List<Trail>,
    /** True when the most recent refresh failed and these results are from an earlier one. */
    val stale: Boolean,
)

object TrailMatcher {
    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Loose match: "North Shore" -> "Northshore Trails @ Grapevine Lake".
     * When several trails match ("Erwin Park" vs "Erwin Park Skill Park"), the shortest name wins.
     */
    fun find(trails: List<Trail>, query: String): Trail? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        return trails.filter { normalize(it.name).contains(q) }.minByOrNull { it.name.length }
    }

    fun shortName(name: String): String = name
        .replace(Regex("""\s*@.*$"""), "")
        .replace(Regex("""\s+Nature Preserve.*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+(DORBA\s+)?Trails?$""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifEmpty { name }
}
