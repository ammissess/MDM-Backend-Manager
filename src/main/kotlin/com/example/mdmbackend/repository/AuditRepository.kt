package com.example.mdmbackend.repository

import com.example.mdmbackend.model.AuditLogsTable
import com.example.mdmbackend.model.UsersTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
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

    fun countByActionAndTarget(
        action: String,
        targetType: String,
        targetId: String,
        from: Instant,
    ): Long = transaction {
        AuditLogsTable
            .selectAll()
            .where { AuditLogsTable.action eq action }
            .andWhere { AuditLogsTable.targetType eq targetType }
            .andWhere { AuditLogsTable.targetId eq targetId }
            .andWhere { AuditLogsTable.createdAt greaterEq from }
            .count()
    }

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
        targetType: String?,
        targetId: String?,
        fromEpochMillis: Long?,
        toEpochMillis: Long?,
    ): Pair<List<AuditRecord>, Long> = transaction {
        val predicates = listOfNotNull<Op<Boolean>>(
            action?.takeIf { it.isNotBlank() }?.let { v -> Op.build { AuditLogsTable.action eq v } },
            actorType?.takeIf { it.isNotBlank() }?.let { v -> Op.build { AuditLogsTable.actorType eq v.uppercase() } },
            targetType?.takeIf { it.isNotBlank() }?.let { v -> Op.build { AuditLogsTable.targetType eq v.uppercase() } },
            targetId?.takeIf { it.isNotBlank() }?.let { v -> Op.build { AuditLogsTable.targetId eq v } },
            fromEpochMillis?.let { v -> Op.build { AuditLogsTable.createdAt greaterEq Instant.ofEpochMilli(v) } },
            toEpochMillis?.let { v -> Op.build { AuditLogsTable.createdAt lessEq Instant.ofEpochMilli(v) } },
        )

        var q = AuditLogsTable.selectAll()
        predicates.forEach { predicate ->
            q = q.andWhere { predicate }
        }

        val total = q.count()

        val items = q.orderBy(AuditLogsTable.createdAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 200))
            .offset(offset.coerceAtLeast(0))
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