package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

// Nhóm B — admin telemetry read
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
    val payload: String,
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

// Nhóm C — admin device actions
@Serializable
data class AdminResetUnlockPassRequest(
    val newPassword: String,
)