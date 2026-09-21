package com.nothingx.bluetooth

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val channelsTried: Int) : ConnectionState
    data object Connected : ConnectionState
    data object Disconnected : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}
