package com.example.mdmbackend.service

import com.example.mdmbackend.dto.DeviceDetailResponse
import com.example.mdmbackend.dto.DeviceResponse
import com.example.mdmbackend.dto.AdminCreateCommandRequest
import com.example.mdmbackend.dto.AdminDeviceEventView
import com.example.mdmbackend.dto.AdminDeviceEventsFilter
import com.example.mdmbackend.dto.ComplianceSummary
import com.example.mdmbackend.dto.HealthSummary
import com.example.mdmbackend.model.CommandType
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.util.PasswordHasher
import java.util.UUID

class AdminDeviceService(
    private val devices: DeviceRepository,
    private val profiles: ProfileRepository,
    private val commands: DeviceCommandService,
    private val audit: AuditService,
    private val eventBus: EventBus = EventBusHolder.bus,
) {
    companion object {
        private const val ONLINE_WINDOW_MILLIS: Long = 5 * 60 * 1000
        private const val TELEMETRY_FRESH_WINDOW_MILLIS: Long = 10 * 60 * 1000
    }

    fun list(): List<DeviceResponse> = devices.list().map { it.toDeviceResponse() }

    fun getById(id: UUID): DeviceResponse? = devices.findById(id)?.toDeviceResponse()

    fun getDetailById(id: UUID): DeviceDetailResponse? = devices.findDetailById(id)?.toDeviceDetailResponse()

    fun listEvents(deviceId: UUID, filter: AdminDeviceEventsFilter): List<AdminDeviceEventView> =
        devices.listEvents(
            deviceId = deviceId,
            limit = filter.limit,
            category = filter.category,
            severity = filter.severity,
            type = filter.type,
            errorCode = filter.errorCode,
            fromEpochMillis = filter.fromEpochMillis,
            toEpochMillis = filter.toEpochMillis,
        ).map { event ->
            AdminDeviceEventView(
                id = event.id.toString(),
                deviceId = event.deviceId.toString(),
                type = event.type,
                category = event.category,
                severity = event.severity,
                payload = event.payload,
                errorCode = event.errorCode,
                message = event.message,
                createdAtEpochMillis = event.createdAt.toEpochMilli(),
            )
        }

    fun linkDeviceToUserCode(deviceId: UUID, userCode: String?, actorUserId: UUID? = null): DeviceResponse? {
        val before = devices.findById(deviceId) ?: return null
        val profile = userCode?.let { profiles.findByUserCode(it) }
        val profileId = profile?.id
        devices.setProfile(deviceId, profileId) ?: return null

        val desiredVersionEpochMillis = System.currentTimeMillis()
        if (profile != null) {
            val fingerprint = profiles.buildDesiredConfigFingerprint(profile)
            devices.updateDesiredConfigForDevice(
                deviceId = deviceId,
                desiredConfigHash = fingerprint.desiredConfigHash,
                desiredConfigVersionEpochMillis = desiredVersionEpochMillis,
            )
        } else {
            devices.clearDesiredConfigForDevice(deviceId)
        }

        val after = devices.findById(deviceId) ?: return null
        val desiredChanged = before.desiredConfigHash != after.desiredConfigHash

        if (desiredChanged && actorUserId != null) {
            val effectiveVersion = after.desiredConfigVersionEpochMillis ?: desiredVersionEpochMillis

            audit.logPolicyDesiredChanged(
                actorUserId = actorUserId,
                deviceId = deviceId,
                profileId = after.profileId,
                userCode = after.userCode,
                oldDesiredHash = before.desiredConfigHash,
                newDesiredHash = after.desiredConfigHash,
                desiredConfigVersionEpochMillis = effectiveVersion,
            )

            val created = commands.adminCreate(
                deviceId = deviceId,
                createdByUserId = actorUserId,
                req = AdminCreateCommandRequest(
                    type = CommandType.REFRESH_CONFIG.wireValue,
                    payload = "{}",
                    ttlSeconds = 600,
                )
            )

            audit.logPolicyRefreshEnqueued(
                actorUserId = actorUserId,
                deviceId = deviceId,
                profileId = after.profileId,
                userCode = after.userCode,
                oldDesiredHash = before.desiredConfigHash,
                newDesiredHash = after.desiredConfigHash,
                desiredConfigVersionEpochMillis = effectiveVersion,
                commandId = UUID.fromString(created.id),
            )
        }

        if (actorUserId != null) {
            eventBus.publish(
                ProfileLinkedEvent(
                    deviceId = deviceId,
                    userCode = userCode,
                    actorUserId = actorUserId,
                )
            )
        }

        return after.toDeviceResponse()
    }

    fun lockDevice(id: UUID): Boolean = devices.lockDevice(id)

    fun resetUnlockPass(id: UUID, newPassword: String): Boolean {
        val hash = PasswordHasher.hash(newPassword)
        return devices.resetUnlockPass(id, hash)
    }

    private fun com.example.mdmbackend.repository.DeviceRecord.toDeviceResponse(): DeviceResponse =
        DeviceResponse(
            id = id.toString(),
            deviceCode = deviceCode,
            userCode = userCode,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            manufacturer = manufacturer,
            model = model,
            imei = imei,
            serial = serial,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            wifiEnabled = wifiEnabled,
            status = status,
            lastSeenAtEpochMillis = lastSeenAt.toEpochMilli(),
        )

    private fun com.example.mdmbackend.repository.DeviceRecord.toDeviceDetailResponse(): DeviceDetailResponse =
        DeviceDetailResponse(
            id = id.toString(),
            deviceCode = deviceCode,
            userCode = userCode,
            androidVersion = androidVersion,
            sdkInt = sdkInt,
            manufacturer = manufacturer,
            model = model,
            imei = imei,
            serial = serial,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            wifiEnabled = wifiEnabled,
            networkType = networkType,
            foregroundPackage = foregroundPackage,
            isDeviceOwner = isDeviceOwner,
            isLauncherDefault = isLauncherDefault,
            isKioskRunning = isKioskRunning,
            storageFreeBytes = storageFreeBytes,
            storageTotalBytes = storageTotalBytes,
            ramFreeMb = ramFreeMb,
            ramTotalMb = ramTotalMb,
            lastBootAtEpochMillis = lastBootAt?.toEpochMilli(),
            lastTelemetryAtEpochMillis = lastTelemetryAt?.toEpochMilli(),
            desiredConfigVersionEpochMillis = desiredConfigVersionEpochMillis,
            desiredConfigHash = desiredConfigHash,
            appliedConfigVersionEpochMillis = appliedConfigVersionEpochMillis,
            appliedConfigHash = appliedConfigHash,
            policyApplyStatus = policyApplyStatus,
            policyApplyError = policyApplyError,
            policyApplyErrorCode = policyApplyErrorCode,
            lastPolicyAppliedAtEpochMillis = lastPolicyAppliedAt?.toEpochMilli(),
            healthSummary = toHealthSummary(),
            complianceSummary = toComplianceSummary(),
            status = status,
            lastSeenAtEpochMillis = lastSeenAt.toEpochMilli(),
        )

    private fun com.example.mdmbackend.repository.DeviceRecord.toHealthSummary(): HealthSummary {
        val nowMillis = System.currentTimeMillis()
        val lastSeenMillis = lastSeenAt.toEpochMilli()
        val isOnline = nowMillis - lastSeenMillis <= ONLINE_WINDOW_MILLIS
        val telemetryFreshness = when (val telemetryMillis = lastTelemetryAt?.toEpochMilli()) {
            null -> "UNKNOWN"
            else -> if (nowMillis - telemetryMillis <= TELEMETRY_FRESH_WINDOW_MILLIS) "FRESH" else "STALE"
        }

        return HealthSummary(
            isOnline = isOnline,
            telemetryFreshness = telemetryFreshness,
        )
    }

    private fun com.example.mdmbackend.repository.DeviceRecord.toComplianceSummary(): ComplianceSummary {
        val isCompliant = desiredConfigHash != null &&
            desiredConfigHash == appliedConfigHash &&
            policyApplyStatus == "SUCCESS"

        return ComplianceSummary(isCompliant = isCompliant)
    }
}