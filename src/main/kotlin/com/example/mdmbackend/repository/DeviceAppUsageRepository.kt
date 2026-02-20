package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceAppUsageTable
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class DeviceAppUsageRepository {

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
}
