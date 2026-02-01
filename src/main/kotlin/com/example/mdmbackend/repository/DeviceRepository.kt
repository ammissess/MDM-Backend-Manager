package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceEventsTable
import com.example.mdmbackend.model.DevicesTable
import com.example.mdmbackend.model.ProfilesTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

data class DeviceRecord(
    val id: UUID,
    val deviceCode: String,
    val profileId: UUID?,
    val userCode: String?,
    val manufacturer: String,
    val model: String,
    val serial: String,
    val sdkInt: Int,
    val lastSeenAt: Instant,
)

class DeviceRepository {

    fun upsert(
        deviceCode: String,
        profileId: UUID?,
        manufacturer: String,
        model: String,
        serial: String,
        sdkInt: Int,
    ): DeviceRecord = transaction {
        val existing = DevicesTable
            .selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .firstOrNull()

        val now = Instant.now()
        val profileEntityId: EntityID<UUID>? =
            profileId?.let { EntityID(it, ProfilesTable) }

        if (existing == null) {
            val id = UUID.randomUUID()
            DevicesTable.insert {
                it[DevicesTable.id] = id
                it[DevicesTable.deviceCode] = deviceCode
                it[DevicesTable.profileId] = profileEntityId
                it[DevicesTable.manufacturer] = manufacturer
                it[DevicesTable.model] = model
                it[DevicesTable.serial] = serial
                it[DevicesTable.sdkInt] = sdkInt
                it[DevicesTable.createdAt] = now
                it[DevicesTable.lastSeenAt] = now
            }
        } else {
            val id = existing[DevicesTable.id].value
            DevicesTable.update({ DevicesTable.id eq EntityID(id, DevicesTable) }) {
                it[DevicesTable.profileId] = profileEntityId
                it[DevicesTable.manufacturer] = manufacturer
                it[DevicesTable.model] = model
                it[DevicesTable.serial] = serial
                it[DevicesTable.sdkInt] = sdkInt
                it[DevicesTable.lastSeenAt] = now
            }
        }

        findByDeviceCode(deviceCode)!!
    }

    fun list(): List<DeviceRecord> = transaction {
        val join = DevicesTable.leftJoin(ProfilesTable, { DevicesTable.profileId }, { ProfilesTable.id })
        join
            .selectAll()
            .orderBy(DevicesTable.lastSeenAt, SortOrder.DESC)
            .map { row -> mapJoined(row) }
    }

    fun findById(id: UUID): DeviceRecord? = transaction {
        val join = DevicesTable.leftJoin(ProfilesTable, { DevicesTable.profileId }, { ProfilesTable.id })
        join
            .selectAll()
            .where { DevicesTable.id eq EntityID(id, DevicesTable) }
            .limit(1)
            .map { mapJoined(it) }
            .firstOrNull()
    }

    fun findByDeviceCode(deviceCode: String): DeviceRecord? = transaction {
        val join = DevicesTable.leftJoin(ProfilesTable, { DevicesTable.profileId }, { ProfilesTable.id })
        join
            .selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .map { mapJoined(it) }
            .firstOrNull()
    }

    fun setProfile(deviceId: UUID, profileId: UUID?): DeviceRecord? = transaction {
        val profileEntityId: EntityID<UUID>? =
            profileId?.let { EntityID(it, ProfilesTable) }

        val updated = DevicesTable.update({ DevicesTable.id eq EntityID(deviceId, DevicesTable) }) {
            it[DevicesTable.profileId] = profileEntityId
            it[DevicesTable.lastSeenAt] = Instant.now()
        }
        if (updated == 0) return@transaction null
        findById(deviceId)
    }

    fun addEvent(deviceId: UUID, type: String, payload: String) {
        transaction {
            DeviceEventsTable.insert {
                it[id] = UUID.randomUUID()
                it[DeviceEventsTable.deviceId] = EntityID(deviceId, DevicesTable)
                it[DeviceEventsTable.type] = type
                it[DeviceEventsTable.payload] = payload
                it[createdAt] = Instant.now()
            }
        }
    }

    private fun mapJoined(row: ResultRow): DeviceRecord {
        val profileEntityId = row.getOrNull(DevicesTable.profileId) // EntityID<UUID>?
        return DeviceRecord(
            id = row[DevicesTable.id].value,
            deviceCode = row[DevicesTable.deviceCode],
            profileId = profileEntityId?.value,
            userCode = row.getOrNull(ProfilesTable.userCode),
            manufacturer = row[DevicesTable.manufacturer],
            model = row[DevicesTable.model],
            serial = row[DevicesTable.serial],
            sdkInt = row[DevicesTable.sdkInt],
            lastSeenAt = row[DevicesTable.lastSeenAt],
        )
    }
}
