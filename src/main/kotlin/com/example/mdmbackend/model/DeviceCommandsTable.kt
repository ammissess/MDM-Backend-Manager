package com.example.mdmbackend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object DeviceCommandsTable : UUIDTable("device_commands") {
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE).index()

    val type = varchar("type", 64)
    val payload = text("payload").nullable()
    val status = enumerationByName("status", 16, CommandStatus::class).default(CommandStatus.PENDING)

    // TTL / expiry
    val expiresAt = timestamp("expires_at").nullable()

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
    val error = text("error").nullable()
    val errorCode = varchar("error_code", 128).nullable()
    val output = text("output").nullable()

    // Cancel metadata
    val cancelledAt = timestamp("cancelled_at").nullable()
    val cancelReason = text("cancel_reason").nullable()
    val cancelledByUserId = reference("cancelled_by_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
}