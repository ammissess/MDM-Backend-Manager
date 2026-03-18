@file:JvmName("CommandDeliveryServiceKt")

package com.example.mdmbackend.service

import com.example.mdmbackend.dto.*
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.model.CommandStatus
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DeviceRepository
import io.ktor.http.*
import java.time.Instant
import java.util.UUID

class DeviceCommandService(
    private val devices: DeviceRepository,
    private val commands: DeviceCommandRepository,
    private val eventBus: EventBus = EventBusHolder.bus,
) {
    fun adminCreate(deviceId: UUID, createdByUserId: UUID, req: AdminCreateCommandRequest): CommandView {
        devices.findById(deviceId) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
        val rec = commands.create(deviceId, req.type, req.payload, createdByUserId, req.ttlSeconds)

        eventBus.publish(
            CommandCreatedEvent(
                commandId = rec.id,
                deviceId = deviceId,
                type = req.type,
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