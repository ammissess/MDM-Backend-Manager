package com.example.mdmbackend.service

import com.example.mdmbackend.dto.AuditLogItem
import com.example.mdmbackend.dto.AuditLogListResponse
import com.example.mdmbackend.dto.AdminAuditFilter
import com.example.mdmbackend.repository.AuditRepository
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class AuditService(
    private val repo: AuditRepository = AuditRepository()
) {

    companion object {
        private val subscribersRegistered = AtomicBoolean(false)
        const val ACTION_POLICY_DESIRED_CHANGED = "POLICY_DESIRED_CHANGED"
        const val ACTION_POLICY_REFRESH_ENQUEUED = "POLICY_REFRESH_ENQUEUED"
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
                        payloadJson = """{"deviceCode":"${event.deviceCode}","status":"${event.status}"}"""
                    )
                }

                is DeviceUnlockedEvent -> {
                    log(
                        actorType = event.actorType,
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.actorDeviceCode,
                        action = "UNLOCK_DEVICE",
                        targetType = "DEVICE",
                        targetId = event.deviceCode,
                        payloadJson = """{"status":"${event.status}"}"""
                    )
                }

                is ProfileLinkedEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = "LINK_PROFILE",
                        targetType = "DEVICE",
                        targetId = event.deviceId.toString(),
                        payloadJson = """{"userCode":${event.userCode?.let { "\"$it\"" } ?: "null"}}"""
                    )
                }

                is CommandCreatedEvent -> {
                    log(
                        actorType = "ADMIN",
                        actorUserId = event.actorUserId,
                        action = "CREATE_COMMAND",
                        targetType = "COMMAND",
                        targetId = event.commandId.toString(),
                        payloadJson = """{"deviceId":"${event.deviceId}","type":"${event.type}","ttlSeconds":${event.ttlSeconds}}"""
                    )
                }

                is TelemetryReceivedEvent -> {
                    val action = when (event.telemetryType) {
                        "policy_apply_reported_success" -> "POLICY_APPLY_REPORTED_SUCCESS"
                        "policy_apply_reported_failed" -> "POLICY_APPLY_REPORTED_FAILED"
                        else -> "TELEMETRY_RECEIVED"
                    }

                    log(
                        actorType = event.actorType,
                        actorUserId = event.actorUserId,
                        actorDeviceCode = event.deviceCode,
                        action = action,
                        targetType = "DEVICE",
                        targetId = event.deviceCode,
                        payloadJson = """{"telemetryType":"${event.telemetryType}"}"""
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
    ) {
        repo.create(
            actorType = actorType,
            actorUserId = actorUserId,
            actorDeviceCode = actorDeviceCode,
            action = action,
            targetType = targetType,
            targetId = targetId,
            payloadJson = payloadJson
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