package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DevicePrivateInfoTable
import com.example.mdmbackend.model.DevicesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class DevicePrivateInfoRepository {

    fun upsertLocation(deviceId: UUID, lat: Double, lon: Double, acc: Double) = transaction {
        val exists = DevicePrivateInfoTable.selectAll().where { DevicePrivateInfoTable.deviceId eq deviceId }.any()
        val now = Instant.now()

        if (!exists) {
            DevicePrivateInfoTable.insert {
                it[id] = UUID.randomUUID()
                it[this.deviceId] = deviceId
                it[latitude] = lat
                it[longitude] = lon
                it[accuracyMeters] = acc
                it[updatedAt] = now
            }
        } else {
            DevicePrivateInfoTable.update({ DevicePrivateInfoTable.deviceId eq deviceId }) {
                it[latitude] = lat
                it[longitude] = lon
                it[accuracyMeters] = acc
                it[updatedAt] = now
            }
        }

        // update lastSeenAt luôn
        DevicesTable.update({ DevicesTable.id eq deviceId }) { it[lastSeenAt] = now }
    }

    fun getLatestByDeviceId(deviceId: UUID): LocationRecord? = transaction {
        DevicePrivateInfoTable.selectAll()
            .where { DevicePrivateInfoTable.deviceId eq deviceId }
            .limit(1)
            .map { row ->
                LocationRecord(
                    deviceId = deviceId,
                    latitude = row[DevicePrivateInfoTable.latitude],
                    longitude = row[DevicePrivateInfoTable.longitude],
                    accuracyMeters = row[DevicePrivateInfoTable.accuracyMeters],
                    updatedAt = row[DevicePrivateInfoTable.updatedAt],
                )
            }
            .firstOrNull()
    }

    data class LocationRecord(
        val deviceId: UUID,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double,
        val updatedAt: java.time.Instant,
    )
}
