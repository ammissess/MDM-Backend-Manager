package com.example.mdmbackend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object DeviceAppUsageTable : UUIDTable("device_app_usage") {
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE).index()
    val packageName = varchar("package_name", 255).index()

    // một record usage (session) do device gửi lên
    val startedAt = timestamp("started_at")
    val endedAt = timestamp("ended_at")
    val durationMs = long("duration_ms")

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}
