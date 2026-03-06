package com.example.mdmbackend.service

import com.example.mdmbackend.dto.*
import com.example.mdmbackend.model.CommandStatus
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DeviceRepository
import java.time.Instant
import java.util.UUID

class DeviceCommandService(
    private val devices: DeviceRepository,
    private val commands: DeviceCommandRepository
) {
    fun adminCreate(deviceId: UUID, createdByUserId: UUID, req: AdminCreateCommandRequest): CommandView {
        // ensure device exists
        devices.findById(deviceId) ?: throw IllegalArgumentException("Device not found")
        val rec = commands.create(deviceId, req.type, req.payload, createdByUserId)
        return rec.toView()
    }

    fun adminList(deviceId: UUID, status: String?, limit: Int, offset: Long): AdminListCommandsResponse {
        devices.findById(deviceId) ?: throw IllegalArgumentException("Device not found")
        val st = status?.let { CommandStatus.valueOf(it.uppercase()) }
        val (items, total) = commands.list(deviceId, st, limit, offset)
        return AdminListCommandsResponse(items.map { it.toView() }, total)
    }

    fun devicePoll(deviceCode: String, sessionDeviceCode: String?, limit: Int, leaseSeconds: Long = 60): DevicePollCommandsResponse {
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

        val updated = commands.ack(device.id, cmdId, leaseToken, targetStatus, req.error, req.output)
            ?: throw IllegalStateException("Lease mismatch or command not found")

        return DeviceAckCommandResponse(ok = true, status = updated.status.name)
    }
}

// mapping helpers
private fun com.example.mdmbackend.repository.CommandRecord.toView(): CommandView = CommandView(
    id = id.toString(),
    deviceId = deviceId.toString(),
    type = type,
    payload = payload,
    status = status.name,
    createdByUserId = createdByUserId?.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    leasedAtEpochMillis = leasedAt?.toEpochMilli(),
    leaseToken = leaseToken?.toString(),
    leaseExpiresAtEpochMillis = leaseExpiresAt?.toEpochMilli(),
    ackedAtEpochMillis = ackedAt?.toEpochMilli(),
    error = error,
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
)