@file:JvmName("CommandDeliveryServiceKt")

package com.example.mdmbackend.service

import com.example.mdmbackend.dto.*
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.model.CommandStatus
import com.example.mdmbackend.model.CommandType
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DeviceRepository
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.UUID

class DeviceCommandService(
    private val devices: DeviceRepository,
    private val commands: DeviceCommandRepository,
    private val eventBus: EventBus = EventBusHolder.bus,
) {
    fun adminCreate(deviceId: UUID, createdByUserId: UUID, req: AdminCreateCommandRequest): CommandView {
        devices.findById(deviceId) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")

        val commandType = req.requireCommandType()
        validateCreatePayload(commandType, req.payload)

        val rec = commands.create(deviceId, commandType.wireValue, req.payload, createdByUserId, req.ttlSeconds)

        eventBus.publish(
            CommandCreatedEvent(
                commandId = rec.id,
                deviceId = deviceId,
                type = commandType.wireValue,
                ttlSeconds = req.ttlSeconds,
                actorUserId = createdByUserId
            )
        )

        return rec.toView()
    }

    fun adminList(deviceId: UUID, status: String?, limit: Int, offset: Long): AdminListCommandsResponse {
        devices.findById(deviceId) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
        val st = status?.let { CommandStatus.valueOf(it.uppercase()) }
        val (items, total) = commands.list(deviceId, st, limit, offset)
        return AdminListCommandsResponse(items.map { it.toView() }, total)
    }

    fun adminCancel(
        deviceId: UUID,
        commandId: UUID,
        cancelledByUserId: UUID,
        req: AdminCancelCommandRequest,
    ): AdminCancelCommandResponse {
        devices.findById(deviceId) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")

        val updated = commands.cancel(
            deviceId = deviceId,
            commandId = commandId,
            cancelledByUserId = cancelledByUserId,
            reason = req.reason,
            errorCode = req.errorCode,
            now = Instant.now(),
        ) ?: throw HttpException(HttpStatusCode.Conflict, "Command cannot be cancelled in current state")

        return AdminCancelCommandResponse(
            ok = true,
            status = updated.status.name,
            cancelledAtEpochMillis = updated.cancelledAt!!.toEpochMilli(),
        )
    }

    /**
     * NEW: delivery-layer helper cho PollingDeliveryStrategy.
     * Giữ behavior cũ của /poll.
     */
    fun leasePendingForPolling(
        deviceCode: String,
        sessionDeviceCode: String?,
        limit: Int,
        leaseSeconds: Long = 60
    ): DevicePollCommandsResponse {
        if (sessionDeviceCode == null || sessionDeviceCode != deviceCode) {
            throw IllegalStateException("DeviceCode mismatch with session")
        }
        val device = devices.findByDeviceCode(deviceCode) ?: throw IllegalArgumentException("Device not found")

        val leased = (1..limit).mapNotNull {
            commands.leaseNext(device.id, leaseSeconds, Instant.now())
        }.map { it.toLeasedDto() }

        return DevicePollCommandsResponse(commands = leased, serverTimeEpochMillis = System.currentTimeMillis())
    }

    fun deviceAck(req: DeviceAckCommandRequest, sessionDeviceCode: String?): DeviceAckCommandResponse {
        if (sessionDeviceCode == null || sessionDeviceCode != req.deviceCode) {
            throw IllegalStateException("DeviceCode mismatch with session")
        }
        val device = devices.findByDeviceCode(req.deviceCode) ?: throw IllegalArgumentException("Device not found")

        val cmdId = UUID.fromString(req.commandId)
        val leaseToken = UUID.fromString(req.leaseToken)
        val targetStatus = when (req.result.uppercase()) {
            "SUCCESS" -> CommandStatus.SUCCESS
            "FAILED" -> CommandStatus.FAILED
            else -> throw IllegalArgumentException("Invalid result")
        }

        val updated = commands.ack(
            deviceId = device.id,
            commandId = cmdId,
            leaseToken = leaseToken,
            resultStatus = targetStatus,
            error = req.error,
            errorCode = req.errorCode,
            output = req.output,
        ) ?: throw IllegalStateException("Lease mismatch or command not found")

        return DeviceAckCommandResponse(ok = true, status = updated.status.name)
    }
}

private fun validateCreatePayload(commandType: CommandType, rawPayload: String) {
    val payload = parsePayloadObject(commandType, rawPayload)

    when (commandType) {
        CommandType.LOCK_SCREEN -> {
            val durationElement = payload["duration_seconds"] ?: return
            val durationSeconds = durationElement.jsonPrimitive.intOrNull
                ?: throw invalidPayload(commandType, "'duration_seconds' must be a positive integer")
            if (durationSeconds <= 0) {
                throw invalidPayload(commandType, "'duration_seconds' must be greater than 0")
            }
        }

        CommandType.REFRESH_CONFIG,
        CommandType.SYNC_CONFIG,
            -> Unit
    }
}

private fun parsePayloadObject(commandType: CommandType, rawPayload: String): JsonObject {
    val trimmed = rawPayload.trim()
    if (trimmed.isEmpty()) {
        throw invalidPayload(commandType, "Payload must be a valid JSON object")
    }

    val parsed = runCatching { Json.parseToJsonElement(trimmed) }
        .getOrElse { throw invalidPayload(commandType, "Payload must be a valid JSON object") }

    return parsed as? JsonObject
        ?: throw invalidPayload(commandType, "Payload must be a JSON object")
}

private fun invalidPayload(commandType: CommandType, reason: String): HttpException =
    HttpException(
        status = HttpStatusCode.BadRequest,
        message = "Invalid payload for '${commandType.wireValue}': $reason",
        code = "INVALID_COMMAND_PAYLOAD"
    )

private fun com.example.mdmbackend.repository.CommandRecord.toView(): CommandView = CommandView(
    id = id.toString(),
    deviceId = deviceId.toString(),
    type = type,
    payload = payload,
    status = status.name,
    createdByUserId = createdByUserId?.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    expiresAtEpochMillis = expiresAt?.toEpochMilli(),
    leasedAtEpochMillis = leasedAt?.toEpochMilli(),
    leaseToken = leaseToken?.toString(),
    leaseExpiresAtEpochMillis = leaseExpiresAt?.toEpochMilli(),
    ackedAtEpochMillis = ackedAt?.toEpochMilli(),
    cancelledAtEpochMillis = cancelledAt?.toEpochMilli(),
    cancelledByUserId = cancelledByUserId?.toString(),
    cancelReason = cancelReason,
    error = error,
    errorCode = errorCode,
    output = output,
)

private fun com.example.mdmbackend.repository.CommandRecord.toLeasedDto(): DeviceLeasedCommand = DeviceLeasedCommand(
    id = id.toString(),
    type = type,
    payload = payload,
    status = "SENT",
    leaseToken = leaseToken!!.toString(),
    leaseExpiresAtEpochMillis = leaseExpiresAt!!.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    expiresAtEpochMillis = expiresAt?.toEpochMilli(),
)