package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogItem(
    val id: String,
    val actorType: String,
    val actorUserId: String? = null,
    val actorDeviceCode: String? = null,
    val action: String,
    val targetType: String? = null,
    val targetId: String? = null,
    val payloadJson: String? = null,
    val createdAtEpochMillis: Long,
)

@Serializable
data class AuditLogListResponse(
    val items: List<AuditLogItem>,
    val total: Long,
    val limit: Int,
    val offset: Long,
)