package com.nothingx.protocol

enum class AncMode(val wireValue: Int) {
    OFF(Commands.ANC_WIRE_OFF),
    NOISE_CANCELLATION(Commands.ANC_WIRE_STRONG),
    TRANSPARENCY(Commands.ANC_WIRE_TRANSPARENCY);

    companion object {
        fun fromModeWireValue(value: Int): AncMode = when (value) {
            Commands.ANC_WIRE_TRANSPARENCY -> TRANSPARENCY
            Commands.ANC_WIRE_OFF, 0 -> OFF
            else -> NOISE_CANCELLATION
        }
    }
}

enum class EqPreset(val wireValue: Int) {
    BALANCED(0),
    MORE_BASS(1),
    MORE_TREBLE(2),
    VOICE(3);

    companion object {
        fun fromWireValue(value: Int): EqPreset = entries.firstOrNull { it.wireValue == value } ?: BALANCED
    }
}
