package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DevicePollCommandsResponse

class PollingDeliveryStrategy(
    private val commandService: DeviceCommandService,
) : CommandDeliveryStrategy {

    override fun deliverPendingCommands(
        deviceCode: String,
        sessionDeviceCode: String?,
        limit: Int,
        ipAddress: String?,
    ): DevicePollCommandsResponse {
        return commandService.leasePendingForPolling(
            deviceCode = deviceCode,
            sessionDeviceCode = sessionDeviceCode,
            limit = limit,
            ipAddress = ipAddress,
        )
    }
}