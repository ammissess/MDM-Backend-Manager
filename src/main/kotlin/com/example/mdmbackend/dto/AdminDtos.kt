package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminLatestLocationResponse(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class AdminDeviceEventView(
    val id: String,
    val deviceId: String,
    val type: String,
    val category: String,
    val severity: String,
    val payload: String,
    val errorCode: String? = null,
    val message: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class AdminUsageSummaryItem(
    val packageName: String,
    val totalDurationMs: Long,
    val sessions: Int,
)

@Serializable
data class AdminUsageSummaryResponse(
    val deviceId: String,
    val fromEpochMillis: Long?,
    val toEpochMillis: Long?,
    val items: List<AdminUsageSummaryItem>,
)

@Serializable
data class AdminDeviceAppView(
    val packageName: String,
    val appName: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val isSystemApp: Boolean? = null,
    val hasLauncherActivity: Boolean? = null,
    val installed: Boolean,
    val disabled: Boolean? = null,
    val hidden: Boolean? = null,
    val suspended: Boolean? = null,
    val lastSeenAtEpochMillis: Long,
)

@Serializable
data class AdminDeviceAppsResponse(
    val deviceId: String,
    val items: List<AdminDeviceAppView>,
    val total: Long,
)

@Serializable
data class AggregateCountItem(
    val key: String,
    val count: Long,
)

@Serializable
data class AdminTelemetrySummaryResponse(
    val deviceId: String,
    val eventCountByType: List<AggregateCountItem>,
    val eventCountByCategory: List<AggregateCountItem>,
    val eventCountBySeverity: List<AggregateCountItem>,
    val policyApplyFailed24h: Long,
    val policyApplyFailed7d: Long,
    val generatedAtEpochMillis: Long,
)

@Serializable
data class AdminResetUnlockPassRequest(
    val newPassword: String,
)

@Serializable
data class HealthSummary(
    val isOnline: Boolean,
    val telemetryFreshness: String,
)

@Serializable
data class ComplianceSummary(
    val isCompliant: Boolean,
)

data class AdminDeviceEventsFilter(
    val category: String? = null,
    val severity: String? = null,
    val type: String? = null,
    val errorCode: String? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val limit: Int = 50,
)

data class AdminAuditFilter(
    val action: String? = null,
    val actorType: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val limit: Int = 50,
    val offset: Long = 0,
)

