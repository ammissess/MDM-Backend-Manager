package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DevicePollCommandsResponse

class CommandDeliveryService(
    private val strategy: CommandDeliveryStrategy,
) {
    fun deliverPendingCommands(
        deviceCode: String,
        sessionDeviceCode: String?,
        limit: Int,
        ipAddress: String? = null,
    ): DevicePollCommandsResponse {
        return strategy.deliverPendingCommands(
            deviceCode = deviceCode,
            sessionDeviceCode = sessionDeviceCode,
            limit = limit,
            ipAddress = ipAddress,
        )
    }
}