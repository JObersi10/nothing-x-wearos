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
)

/** A command the session wants sent to the device. Transport assigns the FSN at encode time. */
data class OutgoingCommand(val cmd: Int, val payload: ByteArray = ByteArray(0), val label: String = "")
