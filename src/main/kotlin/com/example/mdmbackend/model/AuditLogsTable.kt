package com.example.mdmbackend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object AuditLogsTable : UUIDTable("audit_logs") {
    val actorType = varchar("actor_type", 16) // ADMIN | DEVICE | SYSTEM
    val actorUserId = reference("actor_user_id", UsersTable).nullable()
    val actorDeviceCode = varchar("actor_device_code", 128).nullable()

    val action = varchar("action", 64)

    val targetType = varchar("target_type", 32).nullable() // DEVICE | PROFILE | COMMAND | SESSION...
    val targetId = varchar("target_id", 128).nullable()

    val payloadJson = text("payload_json").nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}