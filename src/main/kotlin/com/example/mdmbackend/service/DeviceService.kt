package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.DeviceEventRequest
import com.example.mdmbackend.dto.DevicePolicyStateReportRequest
import com.example.mdmbackend.dto.DevicePolicyStateResponse
import com.example.mdmbackend.dto.DeviceRegisterRequest
import com.example.mdmbackend.dto.DeviceRegisterResponse
import com.example.mdmbackend.dto.DeviceStateSnapshotRequest
import com.example.mdmbackend.dto.DeviceStateSnapshotResponse
import com.example.mdmbackend.dto.DeviceConfigResponse
import com.example.mdmbackend.dto.LocationUpdateRequest
import com.example.mdmbackend.dto.UsageBatchReportRequest
import com.example.mdmbackend.dto.UsageBatchReportResponse
import com.example.mdmbackend.dto.UsageReportRequest
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.model.DeviceStatus
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.PolicyStateUpsert
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.repository.StateSnapshotUpsert
import com.example.mdmbackend.util.PasswordHasher
import io.ktor.http.HttpStatusCode
import java.time.Instant
import java.util.UUID

data class UnlockResult(
    val ok: Boolean,
    val status: String,
    val message: String,
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

    fun register(
        req: DeviceRegisterRequest,
        actorType: String = "DEVICE",
        actorUserId: UUID? = null,
        actorDeviceCode: String? = null,
    ): DeviceRegisterResponse {
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
            message = null,
        )
    }

    fun addEvent(
        deviceCode: String,
        req: DeviceEventRequest,
        actorType: String = "DEVICE",
        actorUserId: UUID? = null,
    ): Boolean {
        val device = devices.findByDeviceCode(deviceCode) ?: return false
        devices.addStructuredEvent(
            deviceId = device.id,
            type = req.type,
            category = req.category,
            severity = req.severity,
            payload = req.payload,
            errorCode = req.errorCode,
            message = req.message,
        )

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

    fun upsertStateSnapshot(
        req: DeviceStateSnapshotRequest,
        actorType: String = "DEVICE",
        actorUserId: UUID? = null,
    ): DeviceStateSnapshotResponse? {
        val normalizedNetworkType = validateStateSnapshotRequest(req)

        val snapshot = StateSnapshotUpsert(
            reportedAt = Instant.ofEpochMilli(req.reportedAtEpochMillis),
            batteryLevel = req.batteryLevel,
            isCharging = req.isCharging,
            wifiEnabled = req.wifiEnabled,
            networkType = normalizedNetworkType,
            foregroundPackage = req.foregroundPackage,
            isDeviceOwner = req.isDeviceOwner,
            isLauncherDefault = req.isLauncherDefault,
            isKioskRunning = req.isKioskRunning,
            storageFreeBytes = req.storageFreeBytes,
            storageTotalBytes = req.storageTotalBytes,
            ramFreeMb = req.ramFreeMb,
            ramTotalMb = req.ramTotalMb,
            lastBootAt = req.lastBootAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        )

        val updated = devices.upsertStateSnapshot(req.deviceCode, snapshot) ?: return null

        if (!req.errorCode.isNullOrBlank() || !req.errorMessage.isNullOrBlank()) {
            devices.addStructuredEvent(
                deviceId = updated.id,
                type = "telemetry_state_report_error",
                category = "TELEMETRY",
                severity = "WARN",
                payload = """{"reportedAtEpochMillis":${req.reportedAtEpochMillis}}""",
                errorCode = req.errorCode,
                message = req.errorMessage,
            )
        }

        eventBus.publish(
            TelemetryReceivedEvent(
                telemetryType = "state",
                deviceCode = req.deviceCode,
                actorType = actorType,
                actorUserId = actorUserId,
            )
        )

        return DeviceStateSnapshotResponse(ok = true, updatedAtEpochMillis = Instant.now().toEpochMilli())
    }

    fun upsertPolicyState(
        req: DevicePolicyStateReportRequest,
        actorType: String = "DEVICE",
        actorUserId: UUID? = null,
    ): DevicePolicyStateResponse? {
        val normalizedReq = validatePolicyStateRequest(req)

        val policy = PolicyStateUpsert(
            appliedConfigVersionEpochMillis = normalizedReq.appliedConfigVersionEpochMillis,
            appliedConfigHash = normalizedReq.appliedConfigHash,
            policyApplyStatus = normalizedReq.policyApplyStatus,
            policyApplyError = normalizedReq.policyApplyError,
            policyApplyErrorCode = normalizedReq.policyApplyErrorCode,
            policyAppliedAt = normalizedReq.policyAppliedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        )

        val updated = devices.upsertPolicyState(normalizedReq.deviceCode, policy) ?: return null

        if (normalizedReq.policyApplyStatus == "FAILED" || !normalizedReq.policyApplyError.isNullOrBlank()) {
            devices.addStructuredEvent(
                deviceId = updated.id,
                type = "policy_apply_result",
                category = "POLICY",
                severity = if (normalizedReq.policyApplyStatus == "FAILED") "ERROR" else "WARN",
                payload = """{"status":"${normalizedReq.policyApplyStatus}","appliedConfigHash":${normalizedReq.appliedConfigHash?.let { "\"$it\"" } ?: "null"}}""",
                errorCode = normalizedReq.policyApplyErrorCode,
                message = normalizedReq.policyApplyError,
            )
        }

        eventBus.publish(
            TelemetryReceivedEvent(
                telemetryType = "policy_state",
                deviceCode = normalizedReq.deviceCode,
                actorType = actorType,
                actorUserId = actorUserId,
            )
        )

        if (normalizedReq.policyApplyStatus == "SUCCESS" || normalizedReq.policyApplyStatus == "FAILED") {
            eventBus.publish(
                TelemetryReceivedEvent(
                    telemetryType = "policy_apply_reported_${normalizedReq.policyApplyStatus.lowercase()}",
                    deviceCode = normalizedReq.deviceCode,
                    actorType = actorType,
                    actorUserId = actorUserId,
                )
            )
        }

        return DevicePolicyStateResponse(ok = true, status = normalizedReq.policyApplyStatus)
    }

    fun getConfigByUserCode(userCode: String): DeviceConfigResponse? =
        profiles.findByUserCode(userCode)?.let { profiles.toDeviceConfigResponse(it) }

    fun getCurrentConfigByDeviceCode(deviceCode: String): DeviceConfigResponse? {
        val device = devices.findByDeviceCode(deviceCode) ?: return null
        val profileId = device.profileId ?: return null
        val profile = profiles.findById(profileId) ?: return null
        return profiles.toDeviceConfigResponse(profile)
    }

    fun getDeviceStatus(deviceCode: String): String? = devices.findStatus(deviceCode)

    fun unlock(
        deviceCode: String,
        password: String,
        actorType: String = "DEVICE",
        actorUserId: UUID? = null,
        actorDeviceCode: String? = null,
    ): UnlockResult {
        val status = devices.findStatus(deviceCode) ?: return UnlockResult(false, "NOT_FOUND", "Device not found")
        if (status == DeviceStatus.ACTIVE.name) return UnlockResult(true, DeviceStatus.ACTIVE.name, "Already unlocked")

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
        privateInfo.upsertLocation(device.id, req.latitude, req.longitude, req.accuracyMeters)
        eventBus.publish(
            TelemetryReceivedEvent("location", req.deviceCode, actorType, actorUserId)
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
            durationMs = req.durationMs,
        )
        eventBus.publish(
            TelemetryReceivedEvent("usage", req.deviceCode, actorType, actorUserId)
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
                durationMs = it.durationMs,
            )
        }
        val inserted = usage.insertUsageBatch(device.id, rows)
        eventBus.publish(
            TelemetryReceivedEvent("usage_batch", req.deviceCode, actorType, actorUserId)
        )
        return UsageBatchReportResponse(ok = true, inserted = inserted)
    }
}

private fun validateStateSnapshotRequest(req: DeviceStateSnapshotRequest): String? {
    val nowMillis = System.currentTimeMillis()
    val maxAllowedEpochMillis = nowMillis + DeviceStateSnapshotRequest.MAX_FUTURE_DRIFT_MILLIS

    if (req.batteryLevel != null && req.batteryLevel !in 0..100) {
        throw invalidStateRequest(
            message = "Invalid field 'batteryLevel': must be between 0 and 100",
            errorCode = "INVALID_BATTERY_LEVEL"
        )
    }

    if (req.storageFreeBytes != null && req.storageFreeBytes < 0) {
        throw invalidStateRequest(
            message = "Invalid field 'storageFreeBytes': must be >= 0",
            errorCode = "INVALID_STORAGE_VALUES"
        )
    }

    if (req.storageTotalBytes != null && req.storageTotalBytes < 0) {
        throw invalidStateRequest(
            message = "Invalid field 'storageTotalBytes': must be >= 0",
            errorCode = "INVALID_STORAGE_VALUES"
        )
    }

    if (
        req.storageFreeBytes != null &&
        req.storageTotalBytes != null &&
        req.storageFreeBytes > req.storageTotalBytes
    ) {
        throw invalidStateRequest(
            message = "Invalid storage values: 'storageFreeBytes' must be <= 'storageTotalBytes'",
            errorCode = "INVALID_STORAGE_VALUES"
        )
    }

    if (req.ramFreeMb != null && req.ramFreeMb < 0) {
        throw invalidStateRequest(
            message = "Invalid field 'ramFreeMb': must be >= 0",
            errorCode = "INVALID_RAM_VALUES"
        )
    }

    if (req.ramTotalMb != null && req.ramTotalMb < 0) {
        throw invalidStateRequest(
            message = "Invalid field 'ramTotalMb': must be >= 0",
            errorCode = "INVALID_RAM_VALUES"
        )
    }

    if (req.ramFreeMb != null && req.ramTotalMb != null && req.ramFreeMb > req.ramTotalMb) {
        throw invalidStateRequest(
            message = "Invalid RAM values: 'ramFreeMb' must be <= 'ramTotalMb'",
            errorCode = "INVALID_RAM_VALUES"
        )
    }

    if (req.reportedAtEpochMillis > maxAllowedEpochMillis) {
        throw invalidStateRequest(
            message = "Invalid field 'reportedAtEpochMillis': timestamp is too far in the future",
            errorCode = "INVALID_REPORTED_AT"
        )
    }

    val normalizedNetworkType = req.networkType?.trim()?.uppercase()
    if (normalizedNetworkType != null) {
        if (normalizedNetworkType.isEmpty() || normalizedNetworkType !in DeviceStateSnapshotRequest.ALLOWED_NETWORK_TYPES) {
            throw invalidStateRequest(
                message = "Invalid field 'networkType': supported values are ${DeviceStateSnapshotRequest.ALLOWED_NETWORK_TYPES.joinToString(", ")}",
                errorCode = "INVALID_NETWORK_TYPE"
            )
        }
    }

    if (req.lastBootAtEpochMillis != null) {
        if (req.lastBootAtEpochMillis > req.reportedAtEpochMillis) {
            throw invalidStateRequest(
                message = "Invalid field 'lastBootAtEpochMillis': must be <= 'reportedAtEpochMillis'",
                errorCode = "INVALID_REPORTED_AT"
            )
        }

        if (req.lastBootAtEpochMillis > maxAllowedEpochMillis) {
            throw invalidStateRequest(
                message = "Invalid field 'lastBootAtEpochMillis': timestamp is too far in the future",
                errorCode = "INVALID_REPORTED_AT"
            )
        }
    }

    return normalizedNetworkType
}

private fun invalidStateRequest(message: String, errorCode: String): HttpException =
    HttpException(HttpStatusCode.BadRequest, message, errorCode)

private fun validatePolicyStateRequest(req: DevicePolicyStateReportRequest): DevicePolicyStateReportRequest {
    val normalizedStatus = req.policyApplyStatus.trim().uppercase()
    if (normalizedStatus !in DevicePolicyStateReportRequest.ALLOWED_STATUSES) {
        throw invalidPolicyRequest(
            message = "Invalid field 'policyApplyStatus': supported values are ${DevicePolicyStateReportRequest.ALLOWED_STATUSES.joinToString(", ")}",
            errorCode = "INVALID_POLICY_STATUS"
        )
    }

    val normalizedAppliedHash = req.appliedConfigHash?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedPolicyError = req.policyApplyError?.trim()?.takeIf { it.isNotEmpty() }
    val normalizedPolicyErrorCode = req.policyApplyErrorCode?.trim()?.takeIf { it.isNotEmpty() }

    if ((normalizedAppliedHash == null) != (req.appliedConfigVersionEpochMillis == null)) {
        throw invalidPolicyRequest(
            message = "Invalid applied config shape: both 'appliedConfigHash' and 'appliedConfigVersionEpochMillis' must be provided together",
            errorCode = "MISSING_APPLIED_CONFIG"
        )
    }

    when (normalizedStatus) {
        "SUCCESS" -> {
            if (normalizedAppliedHash == null || req.appliedConfigVersionEpochMillis == null) {
                throw invalidPolicyRequest(
                    message = "Invalid policy result: 'appliedConfigHash' and 'appliedConfigVersionEpochMillis' are required when policyApplyStatus=SUCCESS",
                    errorCode = "MISSING_APPLIED_CONFIG"
                )
            }
            if (req.policyApplyError != null && normalizedPolicyError == null) {
                throw invalidPolicyRequest(
                    message = "Invalid field 'policyApplyError': must be null or non-blank when policyApplyStatus=SUCCESS",
                    errorCode = "INVALID_POLICY_STATUS"
                )
            }
        }

        "FAILED" -> {
            if (normalizedPolicyError == null && normalizedPolicyErrorCode == null) {
                throw invalidPolicyRequest(
                    message = "Invalid policy result: 'policyApplyError' or 'policyApplyErrorCode' is required when policyApplyStatus=FAILED",
                    errorCode = "MISSING_POLICY_ERROR"
                )
            }
        }

        "PARTIAL" -> {
            if ((normalizedAppliedHash == null) != (req.appliedConfigVersionEpochMillis == null)) {
                throw invalidPolicyRequest(
                    message = "Invalid applied config shape: both 'appliedConfigHash' and 'appliedConfigVersionEpochMillis' must be provided together when partially applied",
                    errorCode = "MISSING_APPLIED_CONFIG"
                )
            }
        }

        "PENDING" -> Unit
    }

    if (req.policyAppliedAtEpochMillis != null) {
        val nowMillis = System.currentTimeMillis()
        val maxAllowedEpochMillis = nowMillis + DevicePolicyStateReportRequest.MAX_FUTURE_DRIFT_MILLIS
        if (req.policyAppliedAtEpochMillis > maxAllowedEpochMillis) {
            throw invalidPolicyRequest(
                message = "Invalid field 'policyAppliedAtEpochMillis': timestamp is too far in the future",
                errorCode = "INVALID_POLICY_APPLIED_AT"
            )
        }
    }

    return req.copy(
        policyApplyStatus = normalizedStatus,
        appliedConfigHash = normalizedAppliedHash,
        policyApplyError = normalizedPolicyError,
        policyApplyErrorCode = normalizedPolicyErrorCode,
    )
}

private fun invalidPolicyRequest(message: String, errorCode: String): HttpException =
    HttpException(HttpStatusCode.BadRequest, message, errorCode)
