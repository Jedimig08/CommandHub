package com.commandhub.hardware

import kotlinx.coroutines.flow.SharedFlow

enum class ChannelType {
    UART, BLUETOOTH, TCP, NONE
}

interface HardwareDeviceChannel {
    val type: ChannelType
    val isConnected: Boolean
    val dataFlow: SharedFlow<String>

    fun connect(target: String): String
    fun send(data: String)
    fun disconnect()
}
