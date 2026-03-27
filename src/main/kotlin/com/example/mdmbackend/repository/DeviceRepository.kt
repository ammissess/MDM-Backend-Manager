package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceEventsTable
import com.example.mdmbackend.model.DeviceStatus
import com.example.mdmbackend.model.DevicesTable
import com.example.mdmbackend.model.ProfilesTable
import com.example.mdmbackend.util.PasswordHasher
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class DeviceRecord(
    val id: UUID,
    val deviceCode: String,
    val profileId: UUID?,
    val userCode: String?,
    val androidVersion: String,
    val manufacturer: String,
    val model: String,
    val imei: String,
    val serial: String,
    val sdkInt: Int,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val wifiEnabled: Boolean,
    val networkType: String?,
    val foregroundPackage: String?,
    val isDeviceOwner: Boolean,
    val isLauncherDefault: Boolean,
    val isKioskRunning: Boolean,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val ramFreeMb: Int,
    val ramTotalMb: Int,
    val lastBootAt: Instant?,
    val lastTelemetryAt: Instant?,
    val desiredConfigVersionEpochMillis: Long?,
    val desiredConfigHash: String?,
    val appliedConfigVersionEpochMillis: Long?,
    val appliedConfigHash: String?,
    val policyApplyStatus: String,
    val policyApplyError: String?,
    val policyApplyErrorCode: String?,
    val lastPolicyAppliedAt: Instant?,
    val status: String,
    val lastSeenAt: Instant,
)

data class EventRecord(
    val id: UUID,
    val deviceId: UUID,
    val type: String,
    val category: String,
    val severity: String,
    val payload: String,
    val errorCode: String?,
    val message: String?,
    val createdAt: Instant,
)

data class StateSnapshotUpsert(
    val reportedAt: Instant,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val wifiEnabled: Boolean?,
    val networkType: String?,
    val foregroundPackage: String?,
    val isDeviceOwner: Boolean?,
    val isLauncherDefault: Boolean?,
    val isKioskRunning: Boolean?,
    val storageFreeBytes: Long?,
    val storageTotalBytes: Long?,
    val ramFreeMb: Int?,
    val ramTotalMb: Int?,
    val lastBootAt: Instant?,
)

data class PolicyStateUpsert(
    val appliedConfigVersionEpochMillis: Long?,
    val appliedConfigHash: String?,
    val policyApplyStatus: String,
    val policyApplyError: String?,
    val policyApplyErrorCode: String?,
    val policyAppliedAt: Instant?,
)

class DeviceRepository {

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
        defaultUnlockPassHash: String = "",
    ): DeviceRecord = transaction {
        val existing = DevicesTable
            .selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .firstOrNull()

        val now = Instant.now()

        if (existing == null) {
            val id = UUID.randomUUID()
            DevicesTable.insert {
                it[DevicesTable.id] = id
                it[DevicesTable.deviceCode] = deviceCode
                it[DevicesTable.androidVersion] = androidVersion
                it[DevicesTable.sdkInt] = sdkInt
                it[DevicesTable.manufacturer] = manufacturer
                it[DevicesTable.model] = model
                it[DevicesTable.imei] = imei
                it[DevicesTable.serial] = serial
                it[DevicesTable.batteryLevel] = batteryLevel
                it[DevicesTable.isCharging] = isCharging
                it[DevicesTable.wifiEnabled] = wifiEnabled
                it[DevicesTable.unlockPassHash] = defaultUnlockPassHash
                it[DevicesTable.status] = DeviceStatus.LOCKED
                it[DevicesTable.createdAt] = now
                it[DevicesTable.lastSeenAt] = now
            }
        } else {
            val id = existing[DevicesTable.id].value
            DevicesTable.update({ DevicesTable.id eq EntityID(id, DevicesTable) }) {
                it[DevicesTable.androidVersion] = androidVersion
                it[DevicesTable.sdkInt] = sdkInt
                it[DevicesTable.manufacturer] = manufacturer
                it[DevicesTable.model] = model
                it[DevicesTable.imei] = imei
                it[DevicesTable.serial] = serial
                it[DevicesTable.batteryLevel] = batteryLevel
                it[DevicesTable.isCharging] = isCharging
                it[DevicesTable.wifiEnabled] = wifiEnabled
                it[DevicesTable.lastSeenAt] = now
            }
        }

        findByDeviceCode(deviceCode)!!
    }

    fun upsertStateSnapshot(deviceCode: String, snapshot: StateSnapshotUpsert): DeviceRecord? = transaction {
        val updated = DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
            snapshot.batteryLevel?.let { v -> it[DevicesTable.batteryLevel] = v }
            snapshot.isCharging?.let { v -> it[DevicesTable.isCharging] = v }
            snapshot.wifiEnabled?.let { v -> it[DevicesTable.wifiEnabled] = v }

            if (snapshot.networkType != null) it[DevicesTable.networkType] = snapshot.networkType
            if (snapshot.foregroundPackage != null) it[DevicesTable.foregroundPackage] = snapshot.foregroundPackage
            snapshot.isDeviceOwner?.let { v -> it[DevicesTable.isDeviceOwner] = v }
            snapshot.isLauncherDefault?.let { v -> it[DevicesTable.isLauncherDefault] = v }
            snapshot.isKioskRunning?.let { v -> it[DevicesTable.isKioskRunning] = v }
            snapshot.storageFreeBytes?.let { v -> it[DevicesTable.storageFreeBytes] = v }
            snapshot.storageTotalBytes?.let { v -> it[DevicesTable.storageTotalBytes] = v }
            snapshot.ramFreeMb?.let { v -> it[DevicesTable.ramFreeMb] = v }
            snapshot.ramTotalMb?.let { v -> it[DevicesTable.ramTotalMb] = v }
            snapshot.lastBootAt?.let { v -> it[DevicesTable.lastBootAt] = v }

            it[DevicesTable.lastTelemetryAt] = snapshot.reportedAt
            it[DevicesTable.lastSeenAt] = Instant.now()
        }
        if (updated == 0) return@transaction null
        findByDeviceCode(deviceCode)
    }

    fun upsertPolicyState(deviceCode: String, policy: PolicyStateUpsert): DeviceRecord? = transaction {
        val now = Instant.now()
        val updated = DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
            it[DevicesTable.appliedConfigVersionEpochMillis] = policy.appliedConfigVersionEpochMillis
            it[DevicesTable.appliedConfigHash] = policy.appliedConfigHash
            it[DevicesTable.policyApplyStatus] = policy.policyApplyStatus
            it[DevicesTable.policyApplyError] = policy.policyApplyError
            it[DevicesTable.policyApplyErrorCode] = policy.policyApplyErrorCode
            it[DevicesTable.lastPolicyAppliedAt] = policy.policyAppliedAt
            it[DevicesTable.lastSeenAt] = now
        }
        if (updated == 0) return@transaction null
        findByDeviceCode(deviceCode)
    }

    fun updateDesiredConfigForProfileDevices(
        profileId: UUID,
        desiredConfigHash: String,
        desiredConfigVersionEpochMillis: Long,
    ): Int = transaction {
        val rows = DevicesTable
            .select(
                DevicesTable.id,
                DevicesTable.desiredConfigHash,
            )
            .where { DevicesTable.profileId eq EntityID(profileId, ProfilesTable) }
            .toList()

        var changed = 0
        rows.forEach { row ->
            val existingHash = row.getOrNull(DevicesTable.desiredConfigHash)
            if (existingHash != desiredConfigHash) {
                DevicesTable.update({ DevicesTable.id eq row[DevicesTable.id] }) {
                    it[DevicesTable.desiredConfigHash] = desiredConfigHash
                    it[DevicesTable.desiredConfigVersionEpochMillis] = desiredConfigVersionEpochMillis
                }
                changed++
            }
        }

        changed
    }

    fun updateDesiredConfigForDevice(
        deviceId: UUID,
        desiredConfigHash: String,
        desiredConfigVersionEpochMillis: Long,
    ): Boolean = transaction {
        val row = DevicesTable
            .select(DevicesTable.desiredConfigHash)
            .where { DevicesTable.id eq EntityID(deviceId, DevicesTable) }
            .limit(1)
            .firstOrNull()
            ?: return@transaction false

        val existingHash = row.getOrNull(DevicesTable.desiredConfigHash)
        if (existingHash == desiredConfigHash) {
            return@transaction false
        }

        DevicesTable.update({ DevicesTable.id eq EntityID(deviceId, DevicesTable) }) {
            it[DevicesTable.desiredConfigHash] = desiredConfigHash
            it[DevicesTable.desiredConfigVersionEpochMillis] = desiredConfigVersionEpochMillis
        }
        true
    }

    fun clearDesiredConfigForDevice(deviceId: UUID): Boolean = transaction {
        val row = DevicesTable
            .select(
                DevicesTable.desiredConfigHash,
                DevicesTable.desiredConfigVersionEpochMillis,
            )
            .where { DevicesTable.id eq EntityID(deviceId, DevicesTable) }
            .limit(1)
            .firstOrNull()
            ?: return@transaction false

        val hasDesired = row.getOrNull(DevicesTable.desiredConfigHash) != null ||
            row.getOrNull(DevicesTable.desiredConfigVersionEpochMillis) != null
        if (!hasDesired) {
            return@transaction false
        }

        DevicesTable.update({ DevicesTable.id eq EntityID(deviceId, DevicesTable) }) {
            it[DevicesTable.desiredConfigHash] = null
            it[DevicesTable.desiredConfigVersionEpochMillis] = null
        }
        true
    }

    fun addStructuredEvent(
        deviceId: UUID,
        type: String,
        category: String,
        severity: String,
        payload: String,
        errorCode: String?,
        message: String?,
    ) {
        transaction {
            DeviceEventsTable.insert {
                it[DeviceEventsTable.id] = UUID.randomUUID()
                it[DeviceEventsTable.deviceId] = EntityID(deviceId, DevicesTable)
                it[DeviceEventsTable.type] = type
                it[DeviceEventsTable.category] = category
                it[DeviceEventsTable.severity] = severity
                it[DeviceEventsTable.payload] = payload
                it[DeviceEventsTable.errorCode] = errorCode
                it[DeviceEventsTable.message] = message
                it[DeviceEventsTable.createdAt] = Instant.now()
            }
            DevicesTable.update({ DevicesTable.id eq EntityID(deviceId, DevicesTable) }) {
                it[DevicesTable.lastSeenAt] = Instant.now()
            }
        }
    }

    fun list(): List<DeviceRecord> = transaction {
        val join = DevicesTable.leftJoin(ProfilesTable, { DevicesTable.profileId }, { ProfilesTable.id })
        join.selectAll()
            .orderBy(DevicesTable.lastSeenAt, SortOrder.DESC)
            .map { mapJoined(it) }
    }

    fun findById(id: UUID): DeviceRecord? = transaction {
        val join = DevicesTable.leftJoin(ProfilesTable, { DevicesTable.profileId }, { ProfilesTable.id })
        join.selectAll()
            .where { DevicesTable.id eq EntityID(id, DevicesTable) }
            .limit(1)
            .map { mapJoined(it) }
            .firstOrNull()
    }

    fun findDetailById(id: UUID): DeviceRecord? = findById(id)

    fun findByDeviceCode(deviceCode: String): DeviceRecord? = transaction {
        val join = DevicesTable.leftJoin(ProfilesTable, { DevicesTable.profileId }, { ProfilesTable.id })
        join.selectAll()
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .map { mapJoined(it) }
            .firstOrNull()
    }

    fun setProfile(deviceId: UUID, profileId: UUID?): DeviceRecord? = transaction {
        val profileEntityId = profileId?.let { EntityID(it, ProfilesTable) }
        val updated = DevicesTable.update({ DevicesTable.id eq EntityID(deviceId, DevicesTable) }) {
            it[DevicesTable.profileId] = profileEntityId
            it[DevicesTable.lastSeenAt] = Instant.now()
        }
        if (updated == 0) return@transaction null
        findById(deviceId)
    }

    fun findStatus(deviceCode: String): String? = transaction {
        DevicesTable.selectAll()
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

    fun listEvents(
        deviceId: UUID,
        limit: Int,
        category: String? = null,
        severity: String? = null,
        type: String? = null,
        errorCode: String? = null,
        fromEpochMillis: Long? = null,
        toEpochMillis: Long? = null,
    ): List<EventRecord> = transaction {
        val deviceRef = EntityID(deviceId, DevicesTable)
        val predicates = listOfNotNull<Op<Boolean>>(
            Op.build { DeviceEventsTable.deviceId eq deviceRef },
            category?.takeIf { it.isNotBlank() }?.let { v -> Op.build { DeviceEventsTable.category eq v } },
            severity?.takeIf { it.isNotBlank() }?.let { v -> Op.build { DeviceEventsTable.severity eq v } },
            type?.takeIf { it.isNotBlank() }?.let { v -> Op.build { DeviceEventsTable.type eq v } },
            errorCode?.takeIf { it.isNotBlank() }?.let { v -> Op.build { DeviceEventsTable.errorCode eq v } },
            fromEpochMillis?.let { v -> Op.build { DeviceEventsTable.createdAt greaterEq Instant.ofEpochMilli(v) } },
            toEpochMillis?.let { v -> Op.build { DeviceEventsTable.createdAt lessEq Instant.ofEpochMilli(v) } },
        )

        var query = DeviceEventsTable.selectAll()
        predicates.forEach { predicate ->
            query = query.andWhere { predicate }
        }

        query
            .orderBy(DeviceEventsTable.createdAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { row ->
                EventRecord(
                    id = row[DeviceEventsTable.id].value,
                    deviceId = row[DeviceEventsTable.deviceId].value,
                    type = row[DeviceEventsTable.type],
                    category = row[DeviceEventsTable.category],
                    severity = row[DeviceEventsTable.severity],
                    payload = row[DeviceEventsTable.payload],
                    errorCode = row[DeviceEventsTable.errorCode],
                    message = row[DeviceEventsTable.message],
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

    private fun mapJoined(row: ResultRow): DeviceRecord {
        val profileEntityId = row.getOrNull(DevicesTable.profileId)
        return DeviceRecord(
            id = row[DevicesTable.id].value,
            deviceCode = row[DevicesTable.deviceCode],
            profileId = profileEntityId?.value,
            userCode = row.getOrNull(ProfilesTable.userCode),
            androidVersion = row[DevicesTable.androidVersion],
            manufacturer = row[DevicesTable.manufacturer],
            model = row[DevicesTable.model],
            imei = row[DevicesTable.imei],
            serial = row[DevicesTable.serial],
            sdkInt = row[DevicesTable.sdkInt],
            batteryLevel = row[DevicesTable.batteryLevel],
            isCharging = row[DevicesTable.isCharging],
            wifiEnabled = row[DevicesTable.wifiEnabled],
            networkType = row.getOrNull(DevicesTable.networkType),
            foregroundPackage = row.getOrNull(DevicesTable.foregroundPackage),
            isDeviceOwner = row[DevicesTable.isDeviceOwner],
            isLauncherDefault = row[DevicesTable.isLauncherDefault],
            isKioskRunning = row[DevicesTable.isKioskRunning],
            storageFreeBytes = row[DevicesTable.storageFreeBytes],
            storageTotalBytes = row[DevicesTable.storageTotalBytes],
            ramFreeMb = row[DevicesTable.ramFreeMb],
            ramTotalMb = row[DevicesTable.ramTotalMb],
            lastBootAt = row.getOrNull(DevicesTable.lastBootAt),
            lastTelemetryAt = row.getOrNull(DevicesTable.lastTelemetryAt),
            desiredConfigVersionEpochMillis = row.getOrNull(DevicesTable.desiredConfigVersionEpochMillis),
            desiredConfigHash = row.getOrNull(DevicesTable.desiredConfigHash),
            appliedConfigVersionEpochMillis = row.getOrNull(DevicesTable.appliedConfigVersionEpochMillis),
            appliedConfigHash = row.getOrNull(DevicesTable.appliedConfigHash),
            policyApplyStatus = row[DevicesTable.policyApplyStatus],
            policyApplyError = row.getOrNull(DevicesTable.policyApplyError),
            policyApplyErrorCode = row.getOrNull(DevicesTable.policyApplyErrorCode),
            lastPolicyAppliedAt = row.getOrNull(DevicesTable.lastPolicyAppliedAt),
            status = row[DevicesTable.status].name,
            lastSeenAt = row[DevicesTable.lastSeenAt],
        )
    }
}