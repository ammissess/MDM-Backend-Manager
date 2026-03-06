package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminCreateCommandRequest(
    val type: String,
    val payload: String = "{}",
    val ttlSeconds: Long = 600,
)

@Serializable
data class CommandView(
    val id: String,
    val deviceId: String,
    val type: String,
    val payload: String,
    val status: String,
    val createdByUserId: String? = null,
    val createdAtEpochMillis: Long,
    val leasedAtEpochMillis: Long? = null,
    val leaseToken: String? = null,
    val leaseExpiresAtEpochMillis: Long? = null,
    val ackedAtEpochMillis: Long? = null,
    val error: String? = null,
    val output: String? = null,
)

@Serializable
data class AdminListCommandsResponse(
    val items: List<CommandView>,
    val total: Long,
)

@Serializable
data class DevicePollCommandsRequest(
    val deviceCode: String,
    val limit: Int = 1,
)

@Serializable
data class DevicePollCommandsResponse(
    val commands: List<DeviceLeasedCommand>,
    val serverTimeEpochMillis: Long,
)

@Serializable
data class DeviceLeasedCommand(
    val id: String,
    val type: String,
    val payload: String,
    val status: String, // SENT
    val leaseToken: String,
    val leaseExpiresAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
)

@Serializable
data class DeviceAckCommandRequest(
    val deviceCode: String,
    val commandId: String,
    val leaseToken: String,
    val result: String, // SUCCESS|FAILED
    val error: String? = null,
    val output: String? = null,
)

@Serializable
data class DeviceAckCommandResponse(
    val ok: Boolean,
    val status: String,
)