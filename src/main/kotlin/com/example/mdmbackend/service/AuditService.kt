package com.example.mdmbackend.service

import com.example.mdmbackend.dto.AuditLogItem
import com.example.mdmbackend.dto.AuditLogListResponse
import com.example.mdmbackend.dto.AdminAuditFilter
import com.example.mdmbackend.repository.AuditRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AuditService(
    private val repo: AuditRepository = AuditRepository()
) {

    companion object {
        private val subscribersRegistered = AtomicBoolean(false)
        const val ACTION_LOGIN = "LOGIN"
        const val ACTION_LOGOUT = "LOGOUT"
        const val ACTION_LOCK = "LOCK"
        const val ACTION_UNLOCK = "UNLOCK"
        const val ACTION_RESET_UNLOCK_PASSWORD = "RESET_UNLOCK_PASSWORD"
        const val ACTION_CREATE_COMMAND = "CREATE_COMMAND"
        const val ACTION_COMMAND_CANCELLED = "COMMAND_CANCELLED"
        const val ACTION_POLICY_DESIRED_CHANGED = "POLICY_DESIRED_CHANGED"
        const val ACTION_POLICY_REFRESH_ENQUEUED = "POLICY_REFRESH_ENQUEUED"
        const val ACTION_COMMAND_EXPIRED = "COMMAND_EXPIRED"
        const val ACTION_COMMAND_ACK_FAILED = "COMMAND_ACK_FAILED"
        const val ACTION_POLICY_APPLY_SUCCESS = "POLICY_APPLY_SUCCESS"
        const val ACTION_POLICY_APPLY_FAILED = "POLICY_APPLY_FAILED"
    }

    fun registerEventSubscribers(bus: EventBus) {
        if (!subscribersRegistered.compareAndSet(false, true)) return

        bus.subscribe { event ->
            when (event) {
                is DeviceRegisteredEvent -> {
                    log(
                        actorType = event.actorType,
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.actorDeviceCode,
                        action = "DEVICE_REGISTERED",
                        targetType = "DEVICE",
                        targetId = event.deviceId.toString(),
                        payloadJson = """{"deviceCode":"${event.deviceCode}","status":"${event.status}","outcome":"SUCCESS"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is DeviceUnlockedEvent -> {
                    log(
                        actorType = event.actorType,
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.actorDeviceCode,
                        action = ACTION_UNLOCK,
                        targetType = "DEVICE",
                        targetId = event.deviceCode,
                        payloadJson = """{"status":"${event.status}","outcome":"SUCCESS"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is DeviceLockedEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = ACTION_LOCK,
                        targetType = "DEVICE",
                        targetId = event.deviceId.toString(),
                        payloadJson = """{"deviceCode":"${event.deviceCode}","previousStatus":"${event.previousStatus}","outcome":"SUCCESS"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is DeviceUnlockPasswordResetEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = ACTION_RESET_UNLOCK_PASSWORD,
                        targetType = "DEVICE",
                        targetId = event.deviceId.toString(),
                        payloadJson = """{"deviceCode":"${event.deviceCode}","outcome":"SUCCESS"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is ProfileLinkedEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = "LINK_PROFILE",
                        targetType = "DEVICE",
                        targetId = event.deviceId.toString(),
                        payloadJson = """{"userCode":${event.userCode?.let { "\"$it\"" } ?: "null"},"outcome":"SUCCESS"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is CommandCreatedEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = ACTION_CREATE_COMMAND,
                        targetType = "COMMAND",
                        targetId = event.commandId.toString(),
                        payloadJson = """{"deviceId":"${event.deviceId}","type":"${event.type}","ttlSeconds":${event.ttlSeconds},"status":"PENDING","outcome":"SUCCESS"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is CommandExpiredEvent -> {
                    log(
                        actorType = "SYSTEM",
                        action = ACTION_COMMAND_EXPIRED,
                        targetType = "COMMAND",
                        targetId = event.commandId.toString(),
                        payloadJson = """{"deviceId":"${event.deviceId}","type":"${event.type}","status":"EXPIRED","outcome":"EXPIRED","expiresAtEpochMillis":${event.expiresAtEpochMillis?.toString() ?: "null"}}""",
                        createdAt = event.occurredAt,
                    )
                }

                is CommandCancelledEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = ACTION_COMMAND_CANCELLED,
                        targetType = "COMMAND",
                        targetId = event.commandId.toString(),
                        payloadJson = """{"deviceId":"${event.deviceId}","type":"${event.type}","reason":"${event.reason}","errorCode":${event.errorCode?.let { "\"$it\"" } ?: "null"},"status":"CANCELLED","outcome":"CANCELLED"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is CommandAckFailedEvent -> {
                    log(
                        actorType = "DEVICE",
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.deviceCode,
                        action = ACTION_COMMAND_ACK_FAILED,
                        targetType = "COMMAND",
                        targetId = event.commandId.toString(),
                        payloadJson = """{"deviceId":"${event.deviceId}","deviceCode":"${event.deviceCode}","type":"${event.type}","error":${event.error?.let { "\"$it\"" } ?: "null"},"errorCode":${event.errorCode?.let { "\"$it\"" } ?: "null"},"output":${event.output?.let { "\"$it\"" } ?: "null"},"status":"FAILED","outcome":"FAILED"}""",
                        createdAt = event.occurredAt,
                    )
                }

                is PolicyAppliedEvent -> {
                    log(
                        actorType = event.actorType,
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.deviceCode,
                        action = if (event.status == "SUCCESS") ACTION_POLICY_APPLY_SUCCESS else ACTION_POLICY_APPLY_FAILED,
                        targetType = "DEVICE",
                        targetId = event.deviceCode,
                        payloadJson = "{" +
                            "\"status\":\"${event.status}\"," +
                            "\"outcome\":\"${event.status}\"," +
                            "\"appliedConfigVersionEpochMillis\":${event.appliedConfigVersionEpochMillis?.toString() ?: "null"}," +
                            "\"appliedConfigHash\":${event.appliedConfigHash?.let { "\"$it\"" } ?: "null"}," +
                            "\"policyAppliedAtEpochMillis\":${event.policyAppliedAtEpochMillis?.toString() ?: "null"}," +
                            "\"error\":${event.error?.let { "\"$it\"" } ?: "null"}," +
                            "\"errorCode\":${event.errorCode?.let { "\"$it\"" } ?: "null"}" +
                            "}",
                        createdAt = event.occurredAt,
                    )
                }

                is TelemetryReceivedEvent -> {
                    log(
                        actorType = event.actorType,
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.deviceCode,
                        action = "TELEMETRY_RECEIVED",
                        targetType = "DEVICE",
                        targetId = event.deviceCode,
                        payloadJson = """{"telemetryType":"${event.telemetryType}","outcome":"RECEIVED"}""",
                        createdAt = event.occurredAt,
                    )
                }
            }
        }
    }

    fun log(
        actorType: String,
        actorUserId: UUID? = null,
        actorDeviceCode: String? = null,
        action: String,
        targetType: String? = null,
        targetId: String? = null,
        payloadJson: String? = null,
        createdAt: Instant = Instant.now(),
    ) {
        repo.create(
            actorType = actorType,
            actorUserId = actorUserId,
            actorDeviceCode = actorDeviceCode,
            action = action,
            targetType = targetType,
            targetId = targetId,
            payloadJson = payloadJson,
            createdAt = createdAt,
        )
    }

    fun logPolicyDesiredChanged(
        actorUserId: UUID,
        deviceId: UUID,
        profileId: UUID?,
        userCode: String?,
        oldDesiredHash: String?,
        newDesiredHash: String?,
        desiredConfigVersionEpochMillis: Long,
    ) {
        log(
            actorType = "ADMIN",
            actorUserId = actorUserId,
            action = ACTION_POLICY_DESIRED_CHANGED,
            targetType = "DEVICE",
            targetId = deviceId.toString(),
            payloadJson = buildDesiredPayload(
                deviceId = deviceId,
                profileId = profileId,
                userCode = userCode,
                oldDesiredHash = oldDesiredHash,
                newDesiredHash = newDesiredHash,
                desiredConfigVersionEpochMillis = desiredConfigVersionEpochMillis,
                actorUserId = actorUserId,
                commandId = null,
            )
        )
    }

    fun logPolicyRefreshEnqueued(
        actorUserId: UUID,
        deviceId: UUID,
        profileId: UUID?,
        userCode: String?,
        oldDesiredHash: String?,
        newDesiredHash: String?,
        desiredConfigVersionEpochMillis: Long,
        commandId: UUID,
    ) {
        log(
            actorType = "ADMIN",
            actorUserId = actorUserId,
            action = ACTION_POLICY_REFRESH_ENQUEUED,
            targetType = "COMMAND",
            targetId = commandId.toString(),
            payloadJson = buildDesiredPayload(
                deviceId = deviceId,
                profileId = profileId,
                userCode = userCode,
                oldDesiredHash = oldDesiredHash,
                newDesiredHash = newDesiredHash,
                desiredConfigVersionEpochMillis = desiredConfigVersionEpochMillis,
                actorUserId = actorUserId,
                commandId = commandId,
            )
        )
    }

    private fun buildDesiredPayload(
        deviceId: UUID,
        profileId: UUID?,
        userCode: String?,
        oldDesiredHash: String?,
        newDesiredHash: String?,
        desiredConfigVersionEpochMillis: Long,
        actorUserId: UUID,
        commandId: UUID?,
    ): String =
        "{" +
            "\"deviceId\":\"$deviceId\"," +
            "\"profileId\":${profileId?.let { "\"$it\"" } ?: "null"}," +
            "\"userCode\":${userCode?.let { "\"$it\"" } ?: "null"}," +
            "\"oldDesiredHash\":${oldDesiredHash?.let { "\"$it\"" } ?: "null"}," +
            "\"newDesiredHash\":${newDesiredHash?.let { "\"$it\"" } ?: "null"}," +
            "\"desiredConfigVersionEpochMillis\":$desiredConfigVersionEpochMillis," +
            "\"actorUserId\":\"$actorUserId\"," +
            "\"commandId\":${commandId?.let { "\"$it\"" } ?: "null"}" +
            "}"

    fun list(filter: AdminAuditFilter): AuditLogListResponse {
        val (items, total) = repo.list(
            limit = filter.limit,
            offset = filter.offset,
            action = filter.action,
            actorType = filter.actorType,
            targetType = filter.targetType,
            targetId = filter.targetId,
            fromEpochMillis = filter.fromEpochMillis,
            toEpochMillis = filter.toEpochMillis,
        )
        return AuditLogListResponse(
            items = items.map {
                AuditLogItem(
                    id = it.id.toString(),
                    actorType = it.actorType,
                    actorUserId = it.actorUserId?.toString(),
                    actorDeviceCode = it.actorDeviceCode,
                    action = it.action,
                    targetType = it.targetType,
                    targetId = it.targetId,
                    payloadJson = it.payloadJson,
                    createdAtEpochMillis = it.createdAt.toEpochMilli(),
                )
            },
            total = total,
            limit = filter.limit,
            offset = filter.offset,
        )
    }
}
