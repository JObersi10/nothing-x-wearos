package com.nothingx.protocol

data class DeviceState(
    val leftBattery: Int = -1,
    val rightBattery: Int = -1,
    val caseBattery: Int = -1,
    val ancMode: AncMode = AncMode.OFF,
    val eqPreset: EqPreset = EqPreset.BALANCED,
    val leftWearing: Boolean = false,
    val rightWearing: Boolean = false,
    val firmwareVersion: String? = null,
    val serialNumber: String? = null,
    val activated: Boolean = false,
    // Settings-screen fields (mined from ear-web, see Commands.kt doc comment).
    // null = not yet queried/unknown; the device may also just not support a
    // given field, which this app can't distinguish from "not queried yet"
    // since it doesn't do ear-web's model/SKU gating.
    val inEarDetectionEnabled: Boolean? = null,
    val lowLatencyEnabled: Boolean? = null,
    val personalizedAncEnabled: Boolean? = null,
    val bassEnhanceEnabled: Boolean? = null,
    val bassLevel: Int? = null, // 0-4, already halved from the doubled wire value
    val gestureCount: Int? = null,
    val earFitTestResult: EarFitTestResult? = null,
)

data class EarFitTestResult(val left: Int, val right: Int)

/** A command the session wants sent to the device. Transport assigns the FSN at encode time. */
data class OutgoingCommand(val cmd: Int, val payload: ByteArray = ByteArray(0), val label: String = "")
