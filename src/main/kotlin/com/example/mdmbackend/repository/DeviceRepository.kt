package com.example.mdmbackend.repository

import com.example.mdmbackend.model.DeviceEventsTable
import com.example.mdmbackend.model.DeviceInstalledAppsTable
import com.example.mdmbackend.model.DeviceStatus
import com.example.mdmbackend.model.DevicesTable
import com.example.mdmbackend.model.ProfilesTable
import com.example.mdmbackend.util.PasswordHasher
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
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
    val agentVersion: String?,
    val agentBuildCode: Int?,
    val ipAddress: String?,
    val currentLauncherPackage: String?,
    val uptimeMs: Long?,
    val abi: String?,
    val buildFingerprint: String?,
    val isDeviceOwner: Boolean,
    val isLauncherDefault: Boolean,
    val isKioskRunning: Boolean,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val ramFreeMb: Int,
    val ramTotalMb: Int,
    val lastBootAt: Instant?,
    val lastTelemetryAt: Instant?,
    val lastPollAt: Instant?,
    val lastCommandAckAt: Instant?,
    val desiredConfigVersionEpochMillis: Long?,
    val desiredConfigHash: String?,
    val appliedConfigVersionEpochMillis: Long?,
    val appliedConfigHash: String?,
    val policyApplyStatus: String,
    val policyApplyError: String?,
    val policyApplyErrorCode: String?,
    val lastPolicyAppliedAt: Instant?,
    val fcmToken: String?,
    val fcmTokenUpdatedAt: Instant?,
    val status: String,
    val lastSeenAt: Instant,
)

data class DeviceWakeupTarget(
    val deviceId: UUID,
    val deviceCode: String,
    val fcmToken: String,
    val fcmTokenUpdatedAt: Instant?,
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

data class EventCountRecord(
    val key: String,
    val count: Long,
)

data class AppInventoryItemUpsert(
    val packageName: String,
    val appName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val isSystemApp: Boolean?,
    val hasLauncherActivity: Boolean?,
    val installed: Boolean,
    val disabled: Boolean?,
    val hidden: Boolean?,
    val suspended: Boolean?,
)

data class DeviceInstalledAppRecord(
    val packageName: String,
    val appName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val isSystemApp: Boolean?,
    val hasLauncherActivity: Boolean?,
    val installed: Boolean,
    val disabled: Boolean?,
    val hidden: Boolean?,
    val suspended: Boolean?,
    val lastSeenAt: Instant,
)

data class DeviceInstalledAppsSnapshotRecord(
    val deviceId: UUID,
    val items: List<DeviceInstalledAppRecord>,
)

data class StateSnapshotUpsert(
    val reportedAt: Instant,
    val batteryLevel: Int?,
    val isCharging: Boolean?,
    val wifiEnabled: Boolean?,
    val networkType: String?,
    val foregroundPackage: String?,
    val agentVersion: String?,
    val agentBuildCode: Int?,
    val currentLauncherPackage: String?,
    val uptimeMs: Long?,
    val abi: String?,
    val buildFingerprint: String?,
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

    fun cleanupEventsOlderThan(cutoff: Instant): Int = transaction {
        DeviceEventsTable.deleteWhere { DeviceEventsTable.createdAt lessEq cutoff }
    }

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
            if (snapshot.agentVersion != null) it[DevicesTable.agentVersion] = snapshot.agentVersion
            snapshot.agentBuildCode?.let { v -> it[DevicesTable.agentBuildCode] = v }
            if (snapshot.currentLauncherPackage != null) it[DevicesTable.currentLauncherPackage] = snapshot.currentLauncherPackage
            snapshot.uptimeMs?.let { v -> it[DevicesTable.uptimeMs] = v }
            if (snapshot.abi != null) it[DevicesTable.abi] = snapshot.abi
            if (snapshot.buildFingerprint != null) it[DevicesTable.buildFingerprint] = snapshot.buildFingerprint
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

    fun touchPollSuccess(deviceCode: String, ipAddress: String?): DeviceRecord? = transaction {
        val now = Instant.now()
        val updated = DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
            it[DevicesTable.lastPollAt] = now
            it[DevicesTable.lastSeenAt] = now
            if (!ipAddress.isNullOrBlank()) {
                it[DevicesTable.ipAddress] = ipAddress
            }
        }
        if (updated == 0) return@transaction null
        findByDeviceCode(deviceCode)
    }

    fun touchAckSuccess(deviceCode: String, ipAddress: String?): DeviceRecord? = transaction {
        val now = Instant.now()
        val updated = DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
            it[DevicesTable.lastCommandAckAt] = now
            it[DevicesTable.lastSeenAt] = now
            if (!ipAddress.isNullOrBlank()) {
                it[DevicesTable.ipAddress] = ipAddress
            }
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

    fun upsertFcmToken(
        deviceCode: String,
        fcmToken: String,
        updatedAt: Instant,
    ): DeviceRecord? = transaction {
        val updated = DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
            it[DevicesTable.fcmToken] = fcmToken
            it[DevicesTable.fcmTokenUpdatedAt] = updatedAt
            it[DevicesTable.lastSeenAt] = Instant.now()
        }
        if (updated == 0) return@transaction null
        findByDeviceCode(deviceCode)
    }

    fun findWakeupTargetByDeviceId(deviceId: UUID): DeviceWakeupTarget? = transaction {
        DevicesTable
            .select(
                DevicesTable.id,
                DevicesTable.deviceCode,
                DevicesTable.fcmToken,
                DevicesTable.fcmTokenUpdatedAt,
            )
            .where { DevicesTable.id eq EntityID(deviceId, DevicesTable) }
            .limit(1)
            .mapNotNull { row ->
                val token = row.getOrNull(DevicesTable.fcmToken)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                DeviceWakeupTarget(
                    deviceId = row[DevicesTable.id].value,
                    deviceCode = row[DevicesTable.deviceCode],
                    fcmToken = token,
                    fcmTokenUpdatedAt = row.getOrNull(DevicesTable.fcmTokenUpdatedAt),
                )
            }
            .firstOrNull()
    }

    fun upsertInstalledAppsInventory(
        deviceCode: String,
        reportedAt: Instant,
        apps: List<AppInventoryItemUpsert>,
    ): Int? = transaction {
        val deviceRow = DevicesTable
            .select(DevicesTable.id)
            .where { DevicesTable.deviceCode eq deviceCode }
            .limit(1)
            .firstOrNull()
            ?: return@transaction null

        val deviceId = deviceRow[DevicesTable.id].value
        val deviceRef = EntityID(deviceId, DevicesTable)
        var upserted = 0

        apps.forEach { app ->
            val devicePackagePredicate: Op<Boolean> = Op.build {
                (DeviceInstalledAppsTable.deviceId eq deviceRef) and
                    (DeviceInstalledAppsTable.packageName eq app.packageName)
            }

            val existing = DeviceInstalledAppsTable
                .select(DeviceInstalledAppsTable.firstSeenAt)
                .where { devicePackagePredicate }
                .limit(1)
                .firstOrNull()

            if (existing == null) {
                DeviceInstalledAppsTable.insert {
                    it[DeviceInstalledAppsTable.deviceId] = deviceRef
                    it[DeviceInstalledAppsTable.packageName] = app.packageName
                    it[DeviceInstalledAppsTable.appName] = app.appName
                    it[DeviceInstalledAppsTable.versionName] = app.versionName
                    it[DeviceInstalledAppsTable.versionCode] = app.versionCode
                    it[DeviceInstalledAppsTable.isSystemApp] = app.isSystemApp
                    it[DeviceInstalledAppsTable.hasLauncherActivity] = app.hasLauncherActivity
                    it[DeviceInstalledAppsTable.installed] = app.installed
                    it[DeviceInstalledAppsTable.disabled] = app.disabled
                    it[DeviceInstalledAppsTable.hidden] = app.hidden
                    it[DeviceInstalledAppsTable.suspended] = app.suspended
                    it[DeviceInstalledAppsTable.firstSeenAt] = reportedAt
                    it[DeviceInstalledAppsTable.lastSeenAt] = reportedAt
                }
            } else {
                DeviceInstalledAppsTable.update({ devicePackagePredicate }) {
                    it[DeviceInstalledAppsTable.appName] = app.appName
                    it[DeviceInstalledAppsTable.versionName] = app.versionName
                    it[DeviceInstalledAppsTable.versionCode] = app.versionCode
                    it[DeviceInstalledAppsTable.isSystemApp] = app.isSystemApp
                    it[DeviceInstalledAppsTable.hasLauncherActivity] = app.hasLauncherActivity
                    it[DeviceInstalledAppsTable.installed] = app.installed
                    it[DeviceInstalledAppsTable.disabled] = app.disabled
                    it[DeviceInstalledAppsTable.hidden] = app.hidden
                    it[DeviceInstalledAppsTable.suspended] = app.suspended
                    it[DeviceInstalledAppsTable.lastSeenAt] = reportedAt
                }
            }
            upserted++
        }

        DevicesTable.update({ DevicesTable.id eq deviceRef }) {
            it[DevicesTable.lastSeenAt] = Instant.now()
        }

        upserted
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

    fun getInstalledAppsByDeviceId(deviceId: UUID): DeviceInstalledAppsSnapshotRecord? = transaction {
        val deviceRow = DevicesTable
            .select(DevicesTable.id)
            .where { DevicesTable.id eq EntityID(deviceId, DevicesTable) }
            .limit(1)
            .firstOrNull()
            ?: return@transaction null

        val deviceRef = EntityID(deviceId, DevicesTable)
        val apps = DeviceInstalledAppsTable
            .selectAll()
            .where { DeviceInstalledAppsTable.deviceId eq deviceRef }
            .orderBy(DeviceInstalledAppsTable.packageName)
            .map { row ->
                DeviceInstalledAppRecord(
                    packageName = row[DeviceInstalledAppsTable.packageName],
                    appName = row.getOrNull(DeviceInstalledAppsTable.appName),
                    versionName = row.getOrNull(DeviceInstalledAppsTable.versionName),
                    versionCode = row.getOrNull(DeviceInstalledAppsTable.versionCode),
                    isSystemApp = row.getOrNull(DeviceInstalledAppsTable.isSystemApp),
                    hasLauncherActivity = row.getOrNull(DeviceInstalledAppsTable.hasLauncherActivity),
                    installed = row[DeviceInstalledAppsTable.installed],
                    disabled = row.getOrNull(DeviceInstalledAppsTable.disabled),
                    hidden = row.getOrNull(DeviceInstalledAppsTable.hidden),
                    suspended = row.getOrNull(DeviceInstalledAppsTable.suspended),
                    lastSeenAt = row[DeviceInstalledAppsTable.lastSeenAt],
                )
            }

        DeviceInstalledAppsSnapshotRecord(
            deviceId = deviceId,
            items = apps,
        )
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

    fun countEventsByType(deviceId: UUID): List<EventCountRecord> = countEventsGroupedBy(deviceId, DeviceEventsTable.type)

    fun countEventsByCategory(deviceId: UUID): List<EventCountRecord> = countEventsGroupedBy(deviceId, DeviceEventsTable.category)

    fun countEventsBySeverity(deviceId: UUID): List<EventCountRecord> = countEventsGroupedBy(deviceId, DeviceEventsTable.severity)

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
            agentVersion = row.getOrNull(DevicesTable.agentVersion),
            agentBuildCode = row.getOrNull(DevicesTable.agentBuildCode),
            ipAddress = row.getOrNull(DevicesTable.ipAddress),
            currentLauncherPackage = row.getOrNull(DevicesTable.currentLauncherPackage),
            uptimeMs = row.getOrNull(DevicesTable.uptimeMs),
            abi = row.getOrNull(DevicesTable.abi),
            buildFingerprint = row.getOrNull(DevicesTable.buildFingerprint),
            isDeviceOwner = row[DevicesTable.isDeviceOwner],
            isLauncherDefault = row[DevicesTable.isLauncherDefault],
            isKioskRunning = row[DevicesTable.isKioskRunning],
            storageFreeBytes = row[DevicesTable.storageFreeBytes],
            storageTotalBytes = row[DevicesTable.storageTotalBytes],
            ramFreeMb = row[DevicesTable.ramFreeMb],
            ramTotalMb = row[DevicesTable.ramTotalMb],
            lastBootAt = row.getOrNull(DevicesTable.lastBootAt),
            lastTelemetryAt = row.getOrNull(DevicesTable.lastTelemetryAt),
            lastPollAt = row.getOrNull(DevicesTable.lastPollAt),
            lastCommandAckAt = row.getOrNull(DevicesTable.lastCommandAckAt),
            desiredConfigVersionEpochMillis = row.getOrNull(DevicesTable.desiredConfigVersionEpochMillis),
            desiredConfigHash = row.getOrNull(DevicesTable.desiredConfigHash),
            appliedConfigVersionEpochMillis = row.getOrNull(DevicesTable.appliedConfigVersionEpochMillis),
            appliedConfigHash = row.getOrNull(DevicesTable.appliedConfigHash),
            policyApplyStatus = row[DevicesTable.policyApplyStatus],
            policyApplyError = row.getOrNull(DevicesTable.policyApplyError),
            policyApplyErrorCode = row.getOrNull(DevicesTable.policyApplyErrorCode),
            lastPolicyAppliedAt = row.getOrNull(DevicesTable.lastPolicyAppliedAt),
            fcmToken = row.getOrNull(DevicesTable.fcmToken),
            fcmTokenUpdatedAt = row.getOrNull(DevicesTable.fcmTokenUpdatedAt),
            status = row[DevicesTable.status].name,
            lastSeenAt = row[DevicesTable.lastSeenAt],
        )
    }

    private fun countEventsGroupedBy(
        deviceId: UUID,
        dimension: org.jetbrains.exposed.sql.Expression<String>,
    ): List<EventCountRecord> = transaction {
        val totalCount = DeviceEventsTable.id.count()

        DeviceEventsTable
            .select(dimension, totalCount)
            .where { DeviceEventsTable.deviceId eq EntityID(deviceId, DevicesTable) }
            .groupBy(dimension)
            .orderBy(totalCount, SortOrder.DESC)
            .map { row ->
                EventCountRecord(
                    key = row[dimension],
                    count = row[totalCount],
                )
            }
    }
}
