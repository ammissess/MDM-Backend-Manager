package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceAppUsageTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.batchInsert

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
}
