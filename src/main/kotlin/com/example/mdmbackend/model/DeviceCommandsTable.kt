package com.example.mdmbackend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object DeviceCommandsTable : UUIDTable("device_commands") {
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE).index()

    val type = varchar("type", 64)
    //val payload = varchar("payload", length = 8192).default("{}")
    //val payload = text("payload").default("{}")
    val payload = text("payload").nullable() // ✅ tránh default DB
    val status = enumerationByName("status", 16, CommandStatus::class).default(CommandStatus.PENDING)

    // Lease
    val attempts = integer("attempts").default(0)
    val leasedAt = timestamp("leased_at").nullable()
    val leaseToken = uuid("lease_token").nullable()
    val leaseExpiresAt = timestamp("lease_expires_at").nullable()

    // Audit
    val createdByUserId = reference("created_by_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    // Result
    val ackedAt = timestamp("acked_at").nullable()
    //val error = varchar("error", 2048).nullable()
    //val output = varchar("output", 8192).nullable()
    val error = text("error").nullable()
    val output = text("output").nullable()
}