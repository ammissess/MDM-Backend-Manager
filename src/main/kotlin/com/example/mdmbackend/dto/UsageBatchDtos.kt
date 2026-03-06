package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class UsageBatchReportRequest(
    val deviceCode: String,
    val items: List<UsageItem>,
) {
    @Serializable
    data class UsageItem(
        val packageName: String,
        val startedAtEpochMillis: Long,
        val endedAtEpochMillis: Long,
        val durationMs: Long,
    )
}

@Serializable
data class UsageBatchReportResponse(
    val ok: Boolean,
    val inserted: Int,
)