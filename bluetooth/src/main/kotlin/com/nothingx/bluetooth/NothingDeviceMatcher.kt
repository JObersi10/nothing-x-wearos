package com.nothingx.bluetooth

/**
 * Bonded-device name matching, ported from something-x's `NOTHING_PATTERNS`
 * (bluetooth.py) plus CMF names it doesn't list. This is a name heuristic
 * only — it does not confirm the device actually speaks the `0x55` protocol
 * or which command IDs it uses. CMF Buds protocol details are UNVERIFIED
 * (see protocol/Commands.kt doc comment); treat a CMF match as "try direct
 * connect, capture raw frames with debug logging on if it misbehaves."
 */
object NothingDeviceMatcher {
    private val PATTERNS = listOf(
        "nothing ear",
        "ear (1)",
        "ear (2)",
        "ear (a)",
        "ear (stick)",
        "cmf buds",
        "cmf earphone",
        "nothing phone",
        "nothing headphone",
    )

    fun isSupportedEarbuds(deviceName: String?): Boolean {
        if (deviceName.isNullOrBlank()) return false
        val lower = deviceName.lowercase()
        return PATTERNS.any { lower.contains(it) }
    }

    fun isUnverifiedCmf(deviceName: String?): Boolean {
        val lower = deviceName?.lowercase() ?: return false
        return lower.contains("cmf")
    }
}
