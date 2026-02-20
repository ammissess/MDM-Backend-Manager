package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DeviceResponse
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import java.util.UUID

class AdminDeviceService(
    private val devices: DeviceRepository,
    private val profiles: ProfileRepository,
) {
    fun list(): List<DeviceResponse> =
        devices.list().map { d -> d.toDeviceResponse() }

    fun linkDeviceToUserCode(deviceId: UUID, userCode: String?): DeviceResponse? {
        val profileId = userCode?.let { profiles.findByUserCode(it)?.id }
        val updated = devices.setProfile(deviceId, profileId) ?: return null
        return updated.toDeviceResponse()
    }

    private fun com.example.mdmbackend.repository.DeviceRecord.toDeviceResponse(): DeviceResponse =
        DeviceResponse(
            id = id.toString(),
            deviceCode = deviceCode,
            userCode = userCode,

            androidVersion = "",        // nếu DeviceRecord chưa có thì để tạm
            sdkInt = sdkInt,
            manufacturer = manufacturer,
            model = model,
            imei = "",                  // nếu DeviceRecord chưa có thì để tạm
            serial = serial,

            batteryLevel = -1,          // nếu DeviceRecord chưa có thì để tạm
            isCharging = false,         // nếu DeviceRecord chưa có thì để tạm
            wifiEnabled = false,        // nếu DeviceRecord chưa có thì để tạm

            status = status,
            lastSeenAtEpochMillis = lastSeenAt.toEpochMilli()
        )
}
