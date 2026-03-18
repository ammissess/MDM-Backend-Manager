package com.example.mdmbackend.repository

import com.example.mdmbackend.model.AuditLogsTable
import com.example.mdmbackend.model.UsersTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class AuditRecord(
    val id: UUID,
    val actorType: String,
    val actorUserId: UUID?,
    val actorDeviceCode: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val payloadJson: String?,
    val createdAt: Instant,
)

class AuditRepository {

    fun create(
        actorType: String,
        actorUserId: UUID?,
        actorDeviceCode: String?,
        action: String,
        targetType: String?,
        targetId: String?,
        payloadJson: String?,
        createdAt: Instant = Instant.now(),
    ): AuditRecord = transaction {
        val id = UUID.randomUUID()
        AuditLogsTable.insert {
            it[AuditLogsTable.id] = id
            it[AuditLogsTable.actorType] = actorType
            it[AuditLogsTable.actorUserId] = actorUserId?.let { uid -> EntityID(uid, UsersTable) }
            it[AuditLogsTable.actorDeviceCode] = actorDeviceCode
            it[AuditLogsTable.action] = action
            it[AuditLogsTable.targetType] = targetType
            it[AuditLogsTable.targetId] = targetId
            it[AuditLogsTable.payloadJson] = payloadJson
            it[AuditLogsTable.createdAt] = createdAt
        }
        getById(id)!!
    }

    fun getById(id: UUID): AuditRecord? = transaction {
        AuditLogsTable.selectAll()
            .where { AuditLogsTable.id eq EntityID(id, AuditLogsTable) }
            .limit(1)
            .map { row ->
                AuditRecord(
                    id = row[AuditLogsTable.id].value,
                    actorType = row[AuditLogsTable.actorType],
                    actorUserId = row[AuditLogsTable.actorUserId]?.value,
                    actorDeviceCode = row[AuditLogsTable.actorDeviceCode],
                    action = row[AuditLogsTable.action],
                    targetType = row[AuditLogsTable.targetType],
                    targetId = row[AuditLogsTable.targetId],
                    payloadJson = row[AuditLogsTable.payloadJson],
                    createdAt = row[AuditLogsTable.createdAt],
                )
            }
            .firstOrNull()
    }

    fun list(
        limit: Int,
        offset: Long,
        action: String?,
        actorType: String?,
    ): Pair<List<AuditRecord>, Long> = transaction {
        var q = AuditLogsTable.selectAll()

        if (!action.isNullOrBlank()) {
            q = q.andWhere { AuditLogsTable.action eq action }
        }
        if (!actorType.isNullOrBlank()) {
            q = q.andWhere { AuditLogsTable.actorType eq actorType.uppercase() }
        }

        val total = q.count()

        val items = q.orderBy(AuditLogsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { row ->
                AuditRecord(
                    id = row[AuditLogsTable.id].value,
                    actorType = row[AuditLogsTable.actorType],
                    actorUserId = row[AuditLogsTable.actorUserId]?.value,
                    actorDeviceCode = row[AuditLogsTable.actorDeviceCode],
                    action = row[AuditLogsTable.action],
                    targetType = row[AuditLogsTable.targetType],
                    targetId = row[AuditLogsTable.targetId],
                    payloadJson = row[AuditLogsTable.payloadJson],
                    createdAt = row[AuditLogsTable.createdAt],
                )
            }

        items to total
    }
}