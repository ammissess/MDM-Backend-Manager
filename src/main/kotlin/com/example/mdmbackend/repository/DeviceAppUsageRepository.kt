package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceAppUsageTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.sum


data class UsageSummaryItemRecord(
    val packageName: String,
    val totalDurationMs: Long,
    val sessions: Int,
)

class DeviceAppUsageRepository {

    data class UsageRow(
        val packageName: String,
        val startedAt: Instant,
        val endedAt: Instant,
        val durationMs: Long,
    )

    fun insertUsage(
        deviceId: UUID,
        packageName: String,
        startedAt: Instant,
        endedAt: Instant,
        durationMs: Long
    ) = transaction {
        DeviceAppUsageTable.insert {
            it[id] = UUID.randomUUID()
            it[this.deviceId] = deviceId
            it[this.packageName] = packageName
            it[this.startedAt] = startedAt
            it[this.endedAt] = endedAt
            it[this.durationMs] = durationMs
            it[createdAt] = Instant.now()
        }
    }

    fun insertUsageBatch(deviceId: UUID, items: List<UsageRow>): Int = transaction {
        if (items.isEmpty()) return@transaction 0
        val now = Instant.now()

        DeviceAppUsageTable.batchInsert(items, shouldReturnGeneratedValues = false) { item ->
            this[DeviceAppUsageTable.id] = UUID.randomUUID()
            this[DeviceAppUsageTable.deviceId] = deviceId
            this[DeviceAppUsageTable.packageName] = item.packageName
            this[DeviceAppUsageTable.startedAt] = item.startedAt
            this[DeviceAppUsageTable.endedAt] = item.endedAt
            this[DeviceAppUsageTable.durationMs] = item.durationMs
            this[DeviceAppUsageTable.createdAt] = now
        }.size
    }

    fun cleanupUsageOlderThan(cutoff: Instant): Int = transaction {
        DeviceAppUsageTable.deleteWhere { DeviceAppUsageTable.endedAt lessEq cutoff }
    }

    fun summaryByDeviceId(
        deviceId: UUID,
        from: java.time.Instant?,
        to: java.time.Instant?,
    ): List<UsageSummaryItemRecord> = transaction {
        val totalDuration = DeviceAppUsageTable.durationMs.sum()
        val sessionCount = DeviceAppUsageTable.id.count()

        var query = DeviceAppUsageTable
            .select(DeviceAppUsageTable.packageName, totalDuration, sessionCount)
            .where { DeviceAppUsageTable.deviceId eq deviceId }

        if (from != null) query = query.andWhere { DeviceAppUsageTable.startedAt greaterEq from }
        if (to != null) query = query.andWhere { DeviceAppUsageTable.endedAt lessEq to }

        query.groupBy(DeviceAppUsageTable.packageName)
            .orderBy(totalDuration, SortOrder.DESC)
            .map { row ->
                UsageSummaryItemRecord(
                    packageName = row[DeviceAppUsageTable.packageName],
                    totalDurationMs = row[totalDuration] ?: 0L,
                    sessions = row[sessionCount].toInt(),
                )
            }
    }
}
