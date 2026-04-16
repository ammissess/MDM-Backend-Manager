package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterRequest(
    val deviceCode: String,
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val manufacturer: String = "",
    val model: String = "",
    val imei: String = "",
    val serial: String = "",
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val wifiEnabled: Boolean = false,
)

@Serializable
data class DeviceRegisterResponse(
    val deviceId: String,
    val deviceCode: String,
    val status: String,
    val message: String? = null,
)

@Serializable
data class DeviceResponse(
    val id: String,
    val deviceCode: String,
    val userCode: String? = null,
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val imei: String,
    val serial: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val wifiEnabled: Boolean,
    val status: String,
    val lastSeenAtEpochMillis: Long,
)

@Serializable
data class DeviceDetailResponse(
    val id: String,
    val deviceCode: String,
    val userCode: String? = null,
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val imei: String,
    val serial: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val wifiEnabled: Boolean,
    val networkType: String? = null,
    val foregroundPackage: String? = null,
    val agentVersion: String? = null,
    val agentBuildCode: Int? = null,
    val ipAddress: String? = null,
    val currentLauncherPackage: String? = null,
    val uptimeMs: Long? = null,
    val abi: String? = null,
    val buildFingerprint: String? = null,
    val isDeviceOwner: Boolean,
    val isLauncherDefault: Boolean,
    val isKioskRunning: Boolean,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val ramFreeMb: Int,
    val ramTotalMb: Int,
    val lastBootAtEpochMillis: Long? = null,
    val lastTelemetryAtEpochMillis: Long? = null,
    val lastPollAtEpochMillis: Long? = null,
    val lastCommandAckAtEpochMillis: Long? = null,
    val desiredConfigVersionEpochMillis: Long? = null,
    val desiredConfigHash: String? = null,
    val appliedConfigVersionEpochMillis: Long? = null,
    val appliedConfigHash: String? = null,
    val policyApplyStatus: String,
    val policyApplyError: String? = null,
    val policyApplyErrorCode: String? = null,
    val lastPolicyAppliedAtEpochMillis: Long? = null,
    val healthSummary: HealthSummary? = null,
    val complianceSummary: ComplianceSummary? = null,
    val status: String,
    val lastSeenAtEpochMillis: Long,
)

@Serializable
data class DeviceUnlockRequest(
    val deviceCode: String,
    val password: String,
)

@Serializable
data class DeviceUnlockResponse(
    val status: String,
    val message: String,
)

@Serializable
data class LocationUpdateRequest(
    val deviceCode: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double = 0.0,
)

@Serializable
data class UsageReportRequest(
    val deviceCode: String,
    val packageName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val durationMs: Long,
)

@Serializable
data class DeviceEventRequest(
    val type: String,
    val category: String = "GENERAL",
    val severity: String = "INFO",
    val payload: String = "{}",
    val errorCode: String? = null,
    val message: String? = null,
)

@Serializable
data class DeviceAppInventoryItem(
    val packageName: String,
    val appName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val isSystemApp: Boolean? = null,
    val hasLauncherActivity: Boolean? = null,
    val installed: Boolean = true,
    val disabled: Boolean? = null,
    val hidden: Boolean? = null,
    val suspended: Boolean? = null,
)

@Serializable
data class DeviceAppInventoryReportRequest(
    val deviceCode: String,
    val reportedAtEpochMillis: Long,
    val items: List<DeviceAppInventoryItem> = emptyList(),
) {
    companion object {
        const val MAX_FUTURE_DRIFT_MILLIS: Long = 5 * 60 * 1000
    }
}

@Serializable
data class DeviceAppInventoryReportResponse(
    val ok: Boolean,
    val upserted: Int,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class DeviceStateSnapshotRequest(
    val deviceCode: String,
    val reportedAtEpochMillis: Long,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val wifiEnabled: Boolean? = null,
    val networkType: String? = null,
    val foregroundPackage: String? = null,
    val agentVersion: String? = null,
    val agentBuildCode: Int? = null,
    val currentLauncherPackage: String? = null,
    val uptimeMs: Long? = null,
    val abi: String? = null,
    val buildFingerprint: String? = null,
    val isDeviceOwner: Boolean? = null,
    val isLauncherDefault: Boolean? = null,
    val isKioskRunning: Boolean? = null,
    val storageFreeBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val ramFreeMb: Int? = null,
    val ramTotalMb: Int? = null,
    val lastBootAtEpochMillis: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    companion object {
        val ALLOWED_NETWORK_TYPES: Set<String> = setOf("WIFI", "CELLULAR", "OFFLINE", "UNKNOWN")
        const val MAX_FUTURE_DRIFT_MILLIS: Long = 5 * 60 * 1000
    }
}

@Serializable
data class DeviceStateSnapshotResponse(
    val ok: Boolean,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class DevicePolicyStateReportRequest(
    val deviceCode: String,
    val desiredConfigVersionEpochMillis: Long? = null,
    val desiredConfigHash: String? = null,
    val appliedConfigVersionEpochMillis: Long? = null,
    val appliedConfigHash: String? = null,
    val policyApplyStatus: String,
    val policyApplyError: String? = null,
    val policyApplyErrorCode: String? = null,
    val policyAppliedAtEpochMillis: Long? = null,
) {
    companion object {
        val ALLOWED_STATUSES: Set<String> = setOf("PENDING", "SUCCESS", "FAILED", "PARTIAL")
        const val MAX_FUTURE_DRIFT_MILLIS: Long = 5 * 60 * 1000
    }
}

@Serializable
data class DevicePolicyStateResponse(
    val ok: Boolean,
    val status: String,
)

@Serializable
data class DeviceFcmTokenUpsertRequest(
    val deviceCode: String,
    val fcmToken: String,
    val appVersion: String? = null,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class DeviceFcmTokenUpsertResponse(
    val ok: Boolean,
    val updatedAtEpochMillis: Long,
)
