package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.*
import com.example.mdmbackend.model.DeviceStatus
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.util.PasswordHasher
import java.time.Instant
import java.util.UUID

data class UnlockResult(
    val ok: Boolean,
    val status: String,
    val message: String
)

class DeviceService(
    private val cfg: AppConfig,
    private val devices: DeviceRepository,
    private val profiles: ProfileRepository,
    private val privateInfo: DevicePrivateInfoRepository,
    private val usage: DeviceAppUsageRepository,
    private val eventBus: EventBus = EventBusHolder.bus,
) {
    init {
        require(cfg.seed.defaultDeviceUnlockPass.isNotBlank()) {
            "mdm.seed.defaultDeviceUnlockPass must not be blank"
        }
    }

    fun register(req: DeviceRegisterRequest, actorType: String = "DEVICE", actorUserId: UUID? = null, actorDeviceCode: String? = null): DeviceRegisterResponse {
        val defaultPassHash = PasswordHasher.hash(cfg.seed.defaultDeviceUnlockPass)

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
            defaultUnlockPassHash = defaultPassHash,
        )

        eventBus.publish(
            DeviceRegisteredEvent(
                deviceId = record.id,
                deviceCode = record.deviceCode,
                status = record.status,
                actorType = actorType,
                actorUserId = actorUserId,
                actorDeviceCode = actorDeviceCode ?: req.deviceCode,
            )
        )

        return DeviceRegisterResponse(
            deviceId = record.id.toString(),
            deviceCode = record.deviceCode,
            status = record.status,
            message = null
        )
    }

    fun addEvent(deviceCode: String, req: DeviceEventRequest, actorType: String = "DEVICE", actorUserId: UUID? = null): Boolean {
        val device = devices.findByDeviceCode(deviceCode) ?: return false
        devices.addEvent(device.id, req.type, req.payload)

        eventBus.publish(
            TelemetryReceivedEvent(
                telemetryType = "event",
                deviceCode = deviceCode,
                actorType = actorType,
                actorUserId = actorUserId,
            )
        )
        return true
    }

    fun getConfigByUserCode(userCode: String) =
        profiles.findByUserCode(userCode)?.let { p ->
            profiles.toDeviceConfigResponse(p)
        }

    fun getCurrentConfigByDeviceCode(deviceCode: String): DeviceConfigResponse? {
        val device = devices.findByDeviceCode(deviceCode) ?: return null
        val profileId = device.profileId ?: return null
        val profile = profiles.findById(profileId) ?: return null
        return profiles.toDeviceConfigResponse(profile)
    }

    fun getDeviceStatus(deviceCode: String): String? = devices.findStatus(deviceCode)

    fun unlock(deviceCode: String, password: String, actorType: String = "DEVICE", actorUserId: UUID? = null, actorDeviceCode: String? = null): UnlockResult {
        val status = devices.findStatus(deviceCode) ?: return UnlockResult(false, "NOT_FOUND", "Device not found")

        if (status == DeviceStatus.ACTIVE.name) {
            return UnlockResult(true, DeviceStatus.ACTIVE.name, "Already unlocked")
        }

        val ok = devices.unlock(deviceCode, password)
        val result = if (ok) {
            UnlockResult(true, DeviceStatus.ACTIVE.name, "Unlocked")
        } else {
            UnlockResult(false, DeviceStatus.LOCKED.name, "Invalid password or device not initialized")
        }

        if (result.ok) {
            eventBus.publish(
                DeviceUnlockedEvent(
                    deviceCode = deviceCode,
                    status = result.status,
                    actorType = actorType,
                    actorUserId = actorUserId,
                    actorDeviceCode = actorDeviceCode ?: deviceCode,
                )
            )
        }

        return result
    }

    fun updateLocation(req: LocationUpdateRequest, actorType: String = "DEVICE", actorUserId: UUID? = null): Boolean {
        val device = devices.findByDeviceCode(req.deviceCode) ?: return false
        privateInfo.upsertLocation(
            deviceId = device.id,
            lat = req.latitude,
            lon = req.longitude,
            acc = req.accuracyMeters
        )

        eventBus.publish(
            TelemetryReceivedEvent(
                telemetryType = "location",
                deviceCode = req.deviceCode,
                actorType = actorType,
                actorUserId = actorUserId,
            )
        )
        return true
    }

    fun insertUsage(req: UsageReportRequest, actorType: String = "DEVICE", actorUserId: UUID? = null): Boolean {
        val device = devices.findByDeviceCode(req.deviceCode) ?: return false
        usage.insertUsage(
            deviceId = device.id,
            packageName = req.packageName,
            startedAt = Instant.ofEpochMilli(req.startedAtEpochMillis),
            endedAt = Instant.ofEpochMilli(req.endedAtEpochMillis),
            durationMs = req.durationMs
        )

        eventBus.publish(
            TelemetryReceivedEvent(
                telemetryType = "usage",
                deviceCode = req.deviceCode,
                actorType = actorType,
                actorUserId = actorUserId,
            )
        )
        return true
    }

    fun insertUsageBatch(req: UsageBatchReportRequest, actorType: String = "DEVICE", actorUserId: UUID? = null): UsageBatchReportResponse {
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

        eventBus.publish(
            TelemetryReceivedEvent(
                telemetryType = "usage_batch",
                deviceCode = req.deviceCode,
                actorType = actorType,
                actorUserId = actorUserId,
            )
        )

        return UsageBatchReportResponse(ok = true, inserted = inserted)
    }
}