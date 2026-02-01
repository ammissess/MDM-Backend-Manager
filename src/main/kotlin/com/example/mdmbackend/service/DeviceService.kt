package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DeviceConfigResponse
import com.example.mdmbackend.dto.DeviceEventRequest
import com.example.mdmbackend.dto.DeviceRegisterRequest
import com.example.mdmbackend.dto.DeviceResponse
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.util.toEpochMillis
import java.time.Instant
import java.util.UUID

class DeviceService(
    private val devices: DeviceRepository,
    private val profiles: ProfileRepository,
) {

    fun register(req: DeviceRegisterRequest): DeviceResponse {
        val profileId = req.userCode?.let { profiles.findByUserCode(it)?.id }
        val device = devices.upsert(
            deviceCode = req.deviceCode,
            profileId = profileId,
            manufacturer = req.manufacturer,
            model = req.model,
            serial = req.serial,
            sdkInt = req.sdkInt,
        )
        return device.toResponse()
    }

    fun list(): List<DeviceResponse> = devices.list().map { it.toResponse() }

    fun get(id: UUID): DeviceResponse? = devices.findById(id)?.toResponse()

    fun getByDeviceCode(deviceCode: String): DeviceResponse? = devices.findByDeviceCode(deviceCode)?.toResponse()

    /**
     * Device poll config theo userCode (mã người dùng) — giống concept profile ở mẫu backend.
     */
    fun getConfigByUserCode(userCode: String): DeviceConfigResponse? {
        val profile = profiles.findByUserCode(userCode) ?: return null
        return DeviceConfigResponse(
            userCode = profile.userCode,
            allowedApps = profile.allowedApps,
            disableWifi = profile.disableWifi,
            disableBluetooth = profile.disableBluetooth,
            disableCamera = profile.disableCamera,
            disableStatusBar = profile.disableStatusBar,
            kioskMode = profile.kioskMode,
            blockUninstall = profile.blockUninstall,
            showWifi = profile.showWifi,
            showBluetooth = profile.showBluetooth,
            configVersionEpochMillis = profile.updatedAt.toEpochMillis(),
        )
    }

    fun linkDeviceToUserCode(deviceId: UUID, userCode: String?): DeviceResponse? {
        val profileId = userCode?.let { profiles.findByUserCode(it)?.id }
        return devices.setProfile(deviceId, profileId)?.toResponse()
    }

    fun addEvent(deviceCode: String, req: DeviceEventRequest): Boolean {
        val device = devices.findByDeviceCode(deviceCode) ?: return false
        devices.addEvent(device.id, req.type, req.payload)
        return true
    }

    private fun com.example.mdmbackend.repository.DeviceRecord.toResponse(): DeviceResponse {
        return DeviceResponse(
            id = id.toString(),
            deviceCode = deviceCode,
            userCode = userCode,
            manufacturer = manufacturer,
            model = model,
            serial = serial,
            sdkInt = sdkInt,
            lastSeenAtEpochMillis = lastSeenAt.toEpochMillis(),
        )
    }
}
