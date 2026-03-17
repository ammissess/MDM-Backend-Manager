package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DeviceResponse
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.util.PasswordHasher
import java.util.UUID

class AdminDeviceService(
    private val devices: DeviceRepository,
    private val profiles: ProfileRepository,
) {
    fun list(): List<DeviceResponse> =
        devices.list().map { d -> d.toDeviceResponse() }

    fun getById(id: UUID): DeviceResponse? =
        devices.findById(id)?.toDeviceResponse()

    fun linkDeviceToUserCode(deviceId: UUID, userCode: String?): DeviceResponse? {
        val profileId = userCode?.let { profiles.findByUserCode(it)?.id }
        val updated = devices.setProfile(deviceId, profileId) ?: return null
        return updated.toDeviceResponse()
    }

    fun lockDevice(id: UUID): Boolean = devices.lockDevice(id)

    fun resetUnlockPass(id: UUID, newPassword: String): Boolean {
        val hash = PasswordHasher.hash(newPassword)
        return devices.resetUnlockPass(id, hash)
    }

    /**
     * ✅ toDeviceResponse() - Sửa để lấy giá trị thật từ DeviceRecord
     * Không còn hardcode "", -1, false nữa
     */
    private fun com.example.mdmbackend.repository.DeviceRecord.toDeviceResponse(): DeviceResponse =
        DeviceResponse(
            id = id.toString(),
            deviceCode = deviceCode,
            userCode = userCode,

            // Device info - lấy từ thật DeviceRecord
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            manufacturer = manufacturer,
            model = model,
            imei = imei,
            serial = serial,

            // Telemetry - lấy từ thật DeviceRecord
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            wifiEnabled = wifiEnabled,

            // Status
            status = status,
            lastSeenAtEpochMillis = lastSeenAt.toEpochMilli()
        )
}