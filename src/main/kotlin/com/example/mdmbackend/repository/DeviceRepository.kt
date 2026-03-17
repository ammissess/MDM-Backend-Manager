package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceEventsTable
import com.example.mdmbackend.model.DevicesTable
import com.example.mdmbackend.model.ProfilesTable
import com.example.mdmbackend.util.PasswordHasher
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
import com.example.mdmbackend.model.DeviceStatus
import org.jetbrains.exposed.sql.*
import com.example.mdmbackend.dto.DeviceResponse

data class DeviceRecord(
    val id: UUID,
    val deviceCode: String,
    val profileId: UUID?,
    val userCode: String?,

    // Device info
    val androidVersion: String,
    val manufacturer: String,
    val model: String,
    val imei: String,
    val serial: String,
    val sdkInt: Int,

    // Telemetry
    val batteryLevel: Int,
    val isCharging: Boolean,
    val wifiEnabled: Boolean,

    // Status & timestamps
    val status: String,
    val lastSeenAt: Instant,
)

data class EventRecord(
    val id: UUID,
    val deviceId: UUID,
    val type: String,
    val payload: String,
    val createdAt: java.time.Instant,
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

    /**
     * ✅ upsertRegister() - Sửa lại để insert/update đầy đủ thông tin thiết bị
     * + Set unlockPassHash mặc định nếu device mới
     */
    fun upsertRegister(
        deviceCode: String,
        androidVersion: String,
        sdkInt: Int,
        manufacturer: String,
        model: String,
        imei: String,
        serial: String,
        batteryLevel: Int,
        isCharging: Boolean,
        wifiEnabled: Boolean,
        defaultUnlockPassHash: String = "", // sẽ truyền từ service
    ): DeviceRecord = transaction {
        val existing = DevicesTable
            .selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .firstOrNull()

        val now = Instant.now()

        if (existing == null) {
            // Device mới: insert + set unlockPassHash mặc định
            val id = UUID.randomUUID()
            DevicesTable.insert {
                it[DevicesTable.id] = id
                it[DevicesTable.deviceCode] = deviceCode
                it[DevicesTable.androidVersion] = androidVersion
                it[DevicesTable.manufacturer] = manufacturer
                it[DevicesTable.model] = model
                it[DevicesTable.imei] = imei
                it[DevicesTable.serial] = serial
                it[DevicesTable.sdkInt] = sdkInt
                it[DevicesTable.batteryLevel] = batteryLevel
                it[DevicesTable.isCharging] = isCharging
                it[DevicesTable.wifiEnabled] = wifiEnabled
                it[DevicesTable.unlockPassHash] = defaultUnlockPassHash
                it[DevicesTable.status] = DeviceStatus.LOCKED
                it[DevicesTable.createdAt] = now
                it[DevicesTable.lastSeenAt] = now
            }
        } else {
            // Device tồn tại: update thông tin + lastSeenAt
            val id = existing[DevicesTable.id].value
            DevicesTable.update({ DevicesTable.id eq EntityID(id, DevicesTable) }) {
                it[DevicesTable.androidVersion] = androidVersion
                it[DevicesTable.manufacturer] = manufacturer
                it[DevicesTable.model] = model
                it[DevicesTable.imei] = imei
                it[DevicesTable.serial] = serial
                it[DevicesTable.sdkInt] = sdkInt
                it[DevicesTable.batteryLevel] = batteryLevel
                it[DevicesTable.isCharging] = isCharging
                it[DevicesTable.wifiEnabled] = wifiEnabled
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

    /**
     * ✅ mapJoined() - Sửa để map đầy đủ các field từ DevicesTable
     */
    private fun mapJoined(row: ResultRow): DeviceRecord {
        val profileEntityId = row.getOrNull(DevicesTable.profileId) // EntityID<UUID>?
        return DeviceRecord(
            id = row[DevicesTable.id].value,
            deviceCode = row[DevicesTable.deviceCode],
            profileId = profileEntityId?.value,
            userCode = row.getOrNull(ProfilesTable.userCode),

            // Device info
            androidVersion = row[DevicesTable.androidVersion],
            manufacturer = row[DevicesTable.manufacturer],
            model = row[DevicesTable.model],
            imei = row[DevicesTable.imei],
            serial = row[DevicesTable.serial],
            sdkInt = row[DevicesTable.sdkInt],

            // Telemetry
            batteryLevel = row[DevicesTable.batteryLevel],
            isCharging = row[DevicesTable.isCharging],
            wifiEnabled = row[DevicesTable.wifiEnabled],

            // Status & timestamps
            status = row[DevicesTable.status].name,
            lastSeenAt = row[DevicesTable.lastSeenAt],
        )
    }

    fun findStatus(deviceCode: String): String? = transaction {
        DevicesTable
            .selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .firstOrNull()
            ?.get(DevicesTable.status)
            ?.name
    }

    fun unlock(deviceCode: String, plainPass: String): Boolean = transaction {
        val row = DevicesTable
            .selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .firstOrNull()
            ?: return@transaction false

        val hash = row[DevicesTable.unlockPassHash]
        if (hash.isBlank()) return@transaction false

        val ok = PasswordHasher.verify(plainPass, hash)
        if (!ok) return@transaction false

        DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
            it[DevicesTable.status] = DeviceStatus.ACTIVE
            it[DevicesTable.lastSeenAt] = Instant.now()
        }
        true
    }

    fun listEvents(deviceId: UUID, limit: Int): List<EventRecord> = transaction {
        DeviceEventsTable.selectAll()
            .where { DeviceEventsTable.deviceId eq EntityID(deviceId, DevicesTable) }
            .orderBy(DeviceEventsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                EventRecord(
                    id = row[DeviceEventsTable.id].value,
                    deviceId = row[DeviceEventsTable.deviceId].value,
                    type = row[DeviceEventsTable.type],
                    payload = row[DeviceEventsTable.payload],
                    createdAt = row[DeviceEventsTable.createdAt],
                )
            }
    }

    fun lockDevice(id: UUID): Boolean = transaction {
        DevicesTable.update({ DevicesTable.id eq EntityID(id, DevicesTable) }) {
            it[DevicesTable.status] = DeviceStatus.LOCKED
            it[DevicesTable.lastSeenAt] = Instant.now()
        } > 0
    }

    fun resetUnlockPass(id: UUID, newPassHash: String): Boolean = transaction {
        DevicesTable.update({ DevicesTable.id eq EntityID(id, DevicesTable) }) {
            it[DevicesTable.unlockPassHash] = newPassHash
        } > 0
    }
}