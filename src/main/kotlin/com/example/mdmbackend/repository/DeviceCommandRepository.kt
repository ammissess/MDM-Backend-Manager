package com.example.mdmbackend.repository

import com.example.mdmbackend.model.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class CommandRecord(
    val id: UUID,
    val deviceId: UUID,
    val type: String,
    val payload: String,
    val status: CommandStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    val leasedAt: Instant?,
    val leaseToken: UUID?,
    val leaseExpiresAt: Instant?,
    val ackedAt: Instant?,
    val error: String?,
    val output: String?,
)

class DeviceCommandRepository {

    fun create(deviceId: UUID, type: String, payload: String, createdByUserId: UUID?): CommandRecord = transaction {
        val id = UUID.randomUUID()
        val now = Instant.now()
        DeviceCommandsTable.insert {
            it[DeviceCommandsTable.id] = id
            it[DeviceCommandsTable.deviceId] = EntityID(deviceId, DevicesTable)
            it[DeviceCommandsTable.type] = type
            it[DeviceCommandsTable.payload] = payload
            it[DeviceCommandsTable.status] = CommandStatus.PENDING
            it[DeviceCommandsTable.createdAt] = now
            it[DeviceCommandsTable.createdByUserId] = createdByUserId?.let { uid -> EntityID(uid, UsersTable) }
        }
        getById(id)!!
    }

    fun getById(id: UUID): CommandRecord? = transaction {
        DeviceCommandsTable.selectAll()
            .where { DeviceCommandsTable.id eq EntityID(id, DeviceCommandsTable) }
            .limit(1)
            .map { mapRow(it) }
            .firstOrNull()
    }

    fun leaseNext(deviceId: UUID, leaseSeconds: Long, now: Instant = Instant.now()): CommandRecord? = transaction {
        val row = DeviceCommandsTable
            .selectAll()
            .where {
                (DeviceCommandsTable.deviceId eq EntityID(deviceId, DevicesTable)) and
                        (
                                (DeviceCommandsTable.status eq CommandStatus.PENDING) or
                                        ((DeviceCommandsTable.status eq CommandStatus.SENT) and (DeviceCommandsTable.leaseExpiresAt lessEq now))
                                )
            }
            .orderBy(DeviceCommandsTable.createdAt, SortOrder.ASC)
            .limit(1)
            .firstOrNull() ?: return@transaction null

        val id = row[DeviceCommandsTable.id].value
        val leaseToken = UUID.randomUUID()
        val leaseExpiresAt = now.plusSeconds(leaseSeconds)

        DeviceCommandsTable.update({ DeviceCommandsTable.id eq EntityID(id, DeviceCommandsTable) }) {
            it[status] = CommandStatus.SENT
            it[attempts] = row[DeviceCommandsTable.attempts] + 1
            it[leasedAt] = now
            it[DeviceCommandsTable.leaseToken] = leaseToken
            it[DeviceCommandsTable.leaseExpiresAt] = leaseExpiresAt
        }
        getById(id)!!
    }

    fun ack(
        deviceId: UUID,
        commandId: UUID,
        leaseToken: UUID,
        resultStatus: CommandStatus, // SUCCESS or FAILED
        error: String?,
        output: String?,
        now: Instant = Instant.now()
    ): CommandRecord? = transaction {
        val current = getById(commandId) ?: return@transaction null
        if (current.deviceId != deviceId) return@transaction null

        // idempotent: nếu đã final thì trả luôn
        if (current.status == CommandStatus.SUCCESS || current.status == CommandStatus.FAILED) return@transaction current

        // lease guard
        if (current.status != CommandStatus.SENT || current.leaseToken != leaseToken) return@transaction null

        DeviceCommandsTable.update({ DeviceCommandsTable.id eq EntityID(commandId, DeviceCommandsTable) }) {
            it[status] = resultStatus
            it[ackedAt] = now
            it[DeviceCommandsTable.error] = error
            it[DeviceCommandsTable.output] = output
        }
        getById(commandId)!!
    }

    fun list(deviceId: UUID, status: CommandStatus?, limit: Int, offset: Long): Pair<List<CommandRecord>, Long> = transaction {
        val base = DeviceCommandsTable.selectAll()
            .where { DeviceCommandsTable.deviceId eq EntityID(deviceId, DevicesTable) }
            .let { if (status != null) it.andWhere { DeviceCommandsTable.status eq status } else it }

        val total = base.count()
        val items = base.orderBy(DeviceCommandsTable.createdAt, SortOrder.DESC)
            // .limit(limit, offset) lỗi phiên bản mới của JetBrains Exposed, truyền 2 tham số bị loại bỏ
            .limit(limit)
            .offset(offset)
            .map { mapRow(it) }

        items to total
    }

    private fun mapRow(r: ResultRow): CommandRecord =
        CommandRecord(
            id = r[DeviceCommandsTable.id].value,
            deviceId = r[DeviceCommandsTable.deviceId].value,
            type = r[DeviceCommandsTable.type],
            //payload = r[DeviceCommandsTable.payload],
            payload = r[DeviceCommandsTable.payload] ?: "{}",   // ✅ FIX
            status = r[DeviceCommandsTable.status],
            createdByUserId = r[DeviceCommandsTable.createdByUserId]?.value,
            createdAt = r[DeviceCommandsTable.createdAt],
            leasedAt = r[DeviceCommandsTable.leasedAt],
            leaseToken = r[DeviceCommandsTable.leaseToken],
            leaseExpiresAt = r[DeviceCommandsTable.leaseExpiresAt],
            ackedAt = r[DeviceCommandsTable.ackedAt],
            error = r[DeviceCommandsTable.error],
            output = r[DeviceCommandsTable.output],
        )
}