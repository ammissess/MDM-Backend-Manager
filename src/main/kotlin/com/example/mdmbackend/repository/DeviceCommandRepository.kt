package com.example.mdmbackend.repository

import com.example.mdmbackend.model.*
import com.example.mdmbackend.service.CommandExpiredEvent
import com.example.mdmbackend.service.EventBus
import com.example.mdmbackend.service.EventBusHolder
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
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
    val expiresAt: Instant?,
    val leasedAt: Instant?,
    val leaseToken: UUID?,
    val leaseExpiresAt: Instant?,
    val ackedAt: Instant?,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val cancelledByUserId: UUID?,
    val error: String?,
    val errorCode: String?,
    val output: String?,
)

class DeviceCommandRepository(
    private val eventBus: EventBus = EventBusHolder.bus,
) {

    private data class ExpiredCandidate(
        val id: UUID,
        val deviceId: UUID,
        val type: String,
        val expiresAt: Instant?,
    )

    private val expirableStatuses = listOf(CommandStatus.PENDING, CommandStatus.SENT)

    fun create(
        deviceId: UUID,
        type: String,
        payload: String,
        createdByUserId: UUID?,
        ttlSeconds: Long,
    ): CommandRecord = transaction {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val expiresAt = now.plusSeconds(ttlSeconds.coerceAtLeast(1))

        DeviceCommandsTable.insert {
            it[DeviceCommandsTable.id] = id
            it[DeviceCommandsTable.deviceId] = EntityID(deviceId, DevicesTable)
            it[DeviceCommandsTable.type] = type
            it[DeviceCommandsTable.payload] = payload
            it[DeviceCommandsTable.status] = CommandStatus.PENDING
            it[DeviceCommandsTable.createdAt] = now
            it[DeviceCommandsTable.expiresAt] = expiresAt
            it[DeviceCommandsTable.createdByUserId] = createdByUserId?.let { uid -> EntityID(uid, UsersTable) }
        }
        getById(id)!!
    }

    fun getById(id: UUID): CommandRecord? {
        markExpiredAndPublish(commandId = id)
        return transaction {
        DeviceCommandsTable.selectAll()
            .where { DeviceCommandsTable.id eq EntityID(id, DeviceCommandsTable) }
            .limit(1)
            .map { mapRow(it) }
            .firstOrNull()
        }
    }

    fun leaseNext(deviceId: UUID, leaseSeconds: Long, now: Instant = Instant.now()): CommandRecord? {
        markExpiredAndPublish(deviceId = deviceId, now = now)
        return transaction {
        val row = DeviceCommandsTable
            .selectAll()
            .where {
                (DeviceCommandsTable.deviceId eq EntityID(deviceId, DevicesTable)) and
                        (
                                (DeviceCommandsTable.status eq CommandStatus.PENDING) or
                                        ((DeviceCommandsTable.status eq CommandStatus.SENT) and (DeviceCommandsTable.leaseExpiresAt lessEq now))
                                )
            }
            .andWhere {
                (DeviceCommandsTable.expiresAt.isNull()) or (DeviceCommandsTable.expiresAt greater now)
            }
            .andWhere {
                DeviceCommandsTable.status neq CommandStatus.CANCELLED
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
    }

    fun ack(
        deviceId: UUID,
        commandId: UUID,
        leaseToken: UUID,
        resultStatus: CommandStatus, // SUCCESS or FAILED
        error: String?,
        errorCode: String?,
        output: String?,
        now: Instant = Instant.now()
    ): CommandRecord? {
        markExpiredAndPublish(deviceId = deviceId, commandId = commandId, now = now)
        return transaction {
        val current = getById(commandId) ?: return@transaction null
        if (current.deviceId != deviceId) return@transaction null

        if (current.status == CommandStatus.SUCCESS || current.status == CommandStatus.FAILED) return@transaction current
        if (current.status == CommandStatus.CANCELLED || current.status == CommandStatus.EXPIRED) return@transaction null
        if (current.expiresAt != null && !current.expiresAt.isAfter(now)) return@transaction null

        if (current.status != CommandStatus.SENT || current.leaseToken != leaseToken) return@transaction null

        DeviceCommandsTable.update({ DeviceCommandsTable.id eq EntityID(commandId, DeviceCommandsTable) }) {
            it[status] = resultStatus
            it[ackedAt] = now
            it[DeviceCommandsTable.error] = error
            it[DeviceCommandsTable.errorCode] = if (resultStatus == CommandStatus.FAILED) errorCode else null
            it[DeviceCommandsTable.output] = output
        }
        getById(commandId)!!
        }
    }

    fun cancel(
        deviceId: UUID,
        commandId: UUID,
        cancelledByUserId: UUID,
        reason: String,
        errorCode: String?,
        now: Instant = Instant.now(),
    ): CommandRecord? {
        markExpiredAndPublish(deviceId = deviceId, commandId = commandId, now = now)
        return transaction {
        val current = getById(commandId) ?: return@transaction null
        if (current.deviceId != deviceId) return@transaction null

        if (
            current.status == CommandStatus.SUCCESS ||
            current.status == CommandStatus.FAILED ||
            current.status == CommandStatus.CANCELLED ||
            current.status == CommandStatus.EXPIRED
        ) {
            return@transaction null
        }

        DeviceCommandsTable.update({ DeviceCommandsTable.id eq EntityID(commandId, DeviceCommandsTable) }) {
            it[DeviceCommandsTable.status] = CommandStatus.CANCELLED
            it[DeviceCommandsTable.cancelledAt] = now
            it[DeviceCommandsTable.cancelReason] = reason
            it[DeviceCommandsTable.cancelledByUserId] = EntityID(cancelledByUserId, UsersTable)
            it[DeviceCommandsTable.errorCode] = errorCode
        }

        getById(commandId)!!
        }
    }

    fun list(deviceId: UUID, status: CommandStatus?, limit: Int, offset: Long): Pair<List<CommandRecord>, Long> {
        markExpiredAndPublish(deviceId = deviceId)
        return transaction {
        val base = DeviceCommandsTable.selectAll()
            .where { DeviceCommandsTable.deviceId eq EntityID(deviceId, DevicesTable) }
            .let { if (status != null) it.andWhere { DeviceCommandsTable.status eq status } else it }

        val total = base.count()
        val items = base.orderBy(DeviceCommandsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { mapRow(it) }

        items to total
        }
    }

    private fun markExpiredAndPublish(
        deviceId: UUID? = null,
        commandId: UUID? = null,
        now: Instant = Instant.now(),
    ) {
        val expired = transaction {
            val candidates = DeviceCommandsTable.selectAll()
                .where {
                    (DeviceCommandsTable.status inList expirableStatuses) and
                        DeviceCommandsTable.expiresAt.isNotNull() and
                        (DeviceCommandsTable.expiresAt lessEq now)
                }
                .let { q ->
                    when {
                        commandId != null -> q.andWhere { DeviceCommandsTable.id eq EntityID(commandId, DeviceCommandsTable) }
                        deviceId != null -> q.andWhere { DeviceCommandsTable.deviceId eq EntityID(deviceId, DevicesTable) }
                        else -> q
                    }
                }
                .map {
                    ExpiredCandidate(
                        id = it[DeviceCommandsTable.id].value,
                        deviceId = it[DeviceCommandsTable.deviceId].value,
                        type = it[DeviceCommandsTable.type],
                        expiresAt = it[DeviceCommandsTable.expiresAt],
                    )
                }

            val transitioned = mutableListOf<ExpiredCandidate>()
            candidates.forEach { candidate ->
                val affected = DeviceCommandsTable.update({
                    (DeviceCommandsTable.id eq EntityID(candidate.id, DeviceCommandsTable)) and
                        (DeviceCommandsTable.status inList expirableStatuses) and
                        DeviceCommandsTable.expiresAt.isNotNull() and
                        (DeviceCommandsTable.expiresAt lessEq now)
                }) {
                    it[status] = CommandStatus.EXPIRED
                }
                if (affected > 0) {
                    transitioned += candidate
                }
            }

            transitioned
        }

        expired.forEach { candidate ->
            eventBus.publish(
                CommandExpiredEvent(
                    commandId = candidate.id,
                    deviceId = candidate.deviceId,
                    type = candidate.type,
                    expiresAtEpochMillis = candidate.expiresAt?.toEpochMilli(),
                )
            )
        }
    }

    private fun mapRow(r: ResultRow): CommandRecord =
        CommandRecord(
            id = r[DeviceCommandsTable.id].value,
            deviceId = r[DeviceCommandsTable.deviceId].value,
            type = r[DeviceCommandsTable.type],
            payload = r[DeviceCommandsTable.payload] ?: "{}",
            status = r[DeviceCommandsTable.status],
            createdByUserId = r[DeviceCommandsTable.createdByUserId]?.value,
            createdAt = r[DeviceCommandsTable.createdAt],
            expiresAt = r[DeviceCommandsTable.expiresAt],
            leasedAt = r[DeviceCommandsTable.leasedAt],
            leaseToken = r[DeviceCommandsTable.leaseToken],
            leaseExpiresAt = r[DeviceCommandsTable.leaseExpiresAt],
            ackedAt = r[DeviceCommandsTable.ackedAt],
            cancelledAt = r[DeviceCommandsTable.cancelledAt],
            cancelReason = r[DeviceCommandsTable.cancelReason],
            cancelledByUserId = r[DeviceCommandsTable.cancelledByUserId]?.value,
            error = r[DeviceCommandsTable.error],
            errorCode = r[DeviceCommandsTable.errorCode],
            output = r[DeviceCommandsTable.output],
        )
}