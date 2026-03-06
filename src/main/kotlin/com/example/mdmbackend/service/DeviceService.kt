package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.DeviceRegisterRequest
import com.example.mdmbackend.dto.DeviceRegisterResponse
import com.example.mdmbackend.dto.DeviceEventRequest
import com.example.mdmbackend.dto.LocationUpdateRequest
import com.example.mdmbackend.dto.UsageBatchReportRequest
import com.example.mdmbackend.dto.UsageBatchReportResponse
import com.example.mdmbackend.dto.UsageReportRequest
import com.example.mdmbackend.model.DeviceStatus
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import java.time.Instant

data class UnlockResult(
    val ok: Boolean,
    val status: String,
    val message: String
)

class DeviceService(
    private val devices: DeviceRepository,
    private val profiles: ProfileRepository,
    private val privateInfo: DevicePrivateInfoRepository,
    private val usage: DeviceAppUsageRepository,
) {

    fun register(req: DeviceRegisterRequest): DeviceRegisterResponse {
        // upsert device basic + telemetry (bạn sẽ map thêm fields trong repo)
        val record = devices.upsertRegister(
            deviceCode = req.deviceCode,
            androidVersion = req.androidVersion,
            sdkInt = req.sdkInt,
            manufacturer = req.manufacturer,
            model = req.model,
            imei = req.imei,
            serial = req.serial,
            batteryLevel = req.batteryLevel,
            isCharging = req.isCharging,
            wifiEnabled = req.wifiEnabled,
        )

        return DeviceRegisterResponse(
            deviceId = record.id.toString(),
            deviceCode = record.deviceCode,
            status = record.status,
            message = null
        )
    }

    fun addEvent(deviceCode: String, req: DeviceEventRequest): Boolean {
        val device = devices.findByDeviceCode(deviceCode) ?: return false
        devices.addEvent(device.id, req.type, req.payload)
        return true
    }

    fun getConfigByUserCode(userCode: String) =
        profiles.findByUserCode(userCode)?.let { p ->
            // map sang DeviceConfigResponse như bạn đang làm
            profiles.toDeviceConfigResponse(p)
        }

    fun getDeviceStatus(deviceCode: String): String? = devices.findStatus(deviceCode)

    fun unlock(deviceCode: String, password: String): UnlockResult {
        val status = devices.findStatus(deviceCode) ?: return UnlockResult(false, "NOT_FOUND", "Device not found")

        if (status == DeviceStatus.ACTIVE.name) {
            return UnlockResult(true, DeviceStatus.ACTIVE.name, "Already unlocked")
        }

        val ok = devices.unlock(deviceCode, password)
        return if (ok) UnlockResult(true, DeviceStatus.ACTIVE.name, "Unlocked")
        else UnlockResult(false, DeviceStatus.LOCKED.name, "Invalid password or device not initialized")
    }

    fun updateLocation(req: LocationUpdateRequest): Boolean {
        val device = devices.findByDeviceCode(req.deviceCode) ?: return false
        privateInfo.upsertLocation(
            deviceId = device.id,
            lat = req.latitude,
            lon = req.longitude,
            acc = req.accuracyMeters
            // nếu repo có at thì thêm: , at = Instant.now()
        )
        return true
    }

    fun insertUsage(req: UsageReportRequest): Boolean {
        val device = devices.findByDeviceCode(req.deviceCode) ?: return false
        usage.insertUsage(
            deviceId = device.id,
            packageName = req.packageName,
            startedAt = Instant.ofEpochMilli(req.startedAtEpochMillis),
            endedAt = Instant.ofEpochMilli(req.endedAtEpochMillis),
            durationMs = req.durationMs
        )
        return true
    }

    fun insertUsageBatch(req: UsageBatchReportRequest): UsageBatchReportResponse {
        val device = devices.findByDeviceCode(req.deviceCode) ?: return UsageBatchReportResponse(false, 0)

        val rows = req.items.map {
            DeviceAppUsageRepository.UsageRow(
            packageName = it.packageName,
            startedAt = Instant.ofEpochMilli(it.startedAtEpochMillis),
            endedAt = Instant.ofEpochMilli(it.endedAtEpochMillis),
            durationMs = it.durationMs
        )
        }

        val inserted = usage.insertUsageBatch(device.id, rows)
        return UsageBatchReportResponse(ok = true, inserted = inserted)
    }

}
