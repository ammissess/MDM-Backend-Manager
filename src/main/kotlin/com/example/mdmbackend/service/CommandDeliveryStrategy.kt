package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DevicePollCommandsResponse

interface CommandDeliveryStrategy {
    fun deliverPendingCommands(
        deviceCode: String,
        sessionDeviceCode: String?,
        limit: Int,
    ): DevicePollCommandsResponse
}