package com.example.mdmbackend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object DevicePrivateInfoTable : UUIDTable("device_private_info") {
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()

    val latitude = double("latitude").default(0.0)
    val longitude = double("longitude").default(0.0)
    val accuracyMeters = double("accuracy_meters").default(0.0)

    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}
