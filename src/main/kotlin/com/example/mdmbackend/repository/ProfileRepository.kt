package com.example.mdmbackend.repository

import com.example.mdmbackend.dto.DeviceConfigResponse
import com.example.mdmbackend.dto.CanonicalDesiredConfig
import com.example.mdmbackend.dto.ProfileCreateRequest
import com.example.mdmbackend.dto.ProfileUpdateRequest
import com.example.mdmbackend.dto.normalizeAllowedApps
import com.example.mdmbackend.model.ProfileAllowedAppsTable
import com.example.mdmbackend.model.ProfilesTable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

data class ProfileRecord(
    val id: UUID,
    val userCode: String,
    val name: String,
    val description: String,
    val allowedApps: List<String>,

    val disableWifi: Boolean,
    val disableBluetooth: Boolean,
    val disableCamera: Boolean,
    val disableStatusBar: Boolean,
    val kioskMode: Boolean,
    val blockUninstall: Boolean,

    val showWifi: Boolean,
    val showBluetooth: Boolean,

    val updatedAt: Instant,
)

data class DesiredConfigFingerprint(
    val canonicalConfigJson: String,
    val desiredConfigHash: String,
)

class ProfileRepository {

    fun list(): List<ProfileRecord> = transaction {
        ProfilesTable.selectAll().orderBy(ProfilesTable.updatedAt, SortOrder.DESC).map { row ->
            val id = row[ProfilesTable.id].value
            toRecord(row, allowedAppsFor(id))
        }
    }

    fun findById(id: UUID): ProfileRecord? = transaction {
        ProfilesTable.selectAll().where { ProfilesTable.id eq id }.limit(1).map { row ->
            toRecord(row, allowedAppsFor(id))
        }.firstOrNull()
    }

    fun findByUserCode(userCode: String): ProfileRecord? = transaction {
        ProfilesTable.selectAll().where { ProfilesTable.userCode eq userCode }.limit(1).map { row ->
            val id = row[ProfilesTable.id].value
            toRecord(row, allowedAppsFor(id))
        }.firstOrNull()
    }

    fun create(req: ProfileCreateRequest): ProfileRecord = transaction {
        val id = UUID.randomUUID()
        val normalizedAllowedApps = req.allowedApps.normalizeAllowedApps()
        ProfilesTable.insert {
            it[ProfilesTable.id] = id
            it[userCode] = req.userCode
            it[name] = req.name
            it[description] = req.description

            it[disableWifi] = req.disableWifi
            it[disableBluetooth] = req.disableBluetooth
            it[disableCamera] = req.disableCamera
            it[disableStatusBar] = req.disableStatusBar
            it[kioskMode] = req.kioskMode
            it[blockUninstall] = req.blockUninstall

            it[showWifi] = req.showWifi
            it[showBluetooth] = req.showBluetooth

            it[updatedAt] = Instant.now()
        }

        setAllowedAppsInternal(id, normalizedAllowedApps)
        findById(id)!!
    }

    fun update(id: UUID, req: ProfileUpdateRequest): ProfileRecord? = transaction {
        val updated = ProfilesTable.update({ ProfilesTable.id eq id }) {
            req.name?.let { v -> it[name] = v }
            req.description?.let { v -> it[description] = v }

            req.disableWifi?.let { v -> it[disableWifi] = v }
            req.disableBluetooth?.let { v -> it[disableBluetooth] = v }
            req.disableCamera?.let { v -> it[disableCamera] = v }
            req.disableStatusBar?.let { v -> it[disableStatusBar] = v }
            req.kioskMode?.let { v -> it[kioskMode] = v }
            req.blockUninstall?.let { v -> it[blockUninstall] = v }

            req.showWifi?.let { v -> it[showWifi] = v }
            req.showBluetooth?.let { v -> it[showBluetooth] = v }

            it[updatedAt] = Instant.now()
        }
        if (updated == 0) return@transaction null
        findById(id)
    }

    fun delete(id: UUID): Boolean = transaction {
        ProfilesTable.deleteWhere { ProfilesTable.id eq id } > 0
    }

    fun setAllowedApps(id: UUID, packages: List<String>): ProfileRecord? = transaction {
        //val exists = ProfilesTable.select { ProfilesTable.id eq id }.any()
        val exists = ProfilesTable.selectAll().where { ProfilesTable.id eq id }.any()
        if (!exists) return@transaction null
        setAllowedAppsInternal(id, packages)
        ProfilesTable.update({ ProfilesTable.id eq id }) { it[updatedAt] = Instant.now() }
        findById(id)
    }

    private fun setAllowedAppsInternal(id: UUID, packages: List<String>) {
        ProfileAllowedAppsTable.deleteWhere { ProfileAllowedAppsTable.profileId eq id }
        packages.normalizeAllowedApps().forEach { pkg ->
            ProfileAllowedAppsTable.insert {
                it[profileId] = id
                it[packageName] = pkg
            }
        }
    }

    private fun allowedAppsFor(id: UUID): List<String> = ProfileAllowedAppsTable
        .selectAll().where { ProfileAllowedAppsTable.profileId eq id }
        .orderBy(ProfileAllowedAppsTable.packageName)
        .map { it[ProfileAllowedAppsTable.packageName] }

    private fun toRecord(row: ResultRow, allowedApps: List<String>): ProfileRecord {
        return ProfileRecord(
            id = row[ProfilesTable.id].value,
            userCode = row[ProfilesTable.userCode],
            name = row[ProfilesTable.name],
            description = row[ProfilesTable.description],
            allowedApps = allowedApps,

            disableWifi = row[ProfilesTable.disableWifi],
            disableBluetooth = row[ProfilesTable.disableBluetooth],
            disableCamera = row[ProfilesTable.disableCamera],
            disableStatusBar = row[ProfilesTable.disableStatusBar],
            kioskMode = row[ProfilesTable.kioskMode],
            blockUninstall = row[ProfilesTable.blockUninstall],

            showWifi = row[ProfilesTable.showWifi],
            showBluetooth = row[ProfilesTable.showBluetooth],

            updatedAt = row[ProfilesTable.updatedAt],
        )
    }

    //ProfileRepository: thêm helper map sang DeviceConfigResponse
    fun toDeviceConfigResponse(p: ProfileRecord): DeviceConfigResponse =
        DeviceConfigResponse(
            userCode = p.userCode,
            allowedApps = p.allowedApps,
            disableWifi = p.disableWifi,
            disableBluetooth = p.disableBluetooth,
            disableCamera = p.disableCamera,
            disableStatusBar = p.disableStatusBar,
            kioskMode = p.kioskMode,
            blockUninstall = p.blockUninstall,
            showWifi = p.showWifi,
            showBluetooth = p.showBluetooth,
            configVersionEpochMillis = p.updatedAt.toEpochMilli(),
        )

    fun buildDesiredConfigFingerprint(profile: ProfileRecord): DesiredConfigFingerprint {
        val canonical = CanonicalDesiredConfig(
            userCode = profile.userCode.trim(),
            allowedApps = profile.allowedApps.normalizeAllowedApps(),
            disableWifi = profile.disableWifi,
            disableBluetooth = profile.disableBluetooth,
            disableCamera = profile.disableCamera,
            disableStatusBar = profile.disableStatusBar,
            kioskMode = profile.kioskMode,
            blockUninstall = profile.blockUninstall,
            showWifi = profile.showWifi,
            showBluetooth = profile.showBluetooth,
        )

        val canonicalJson = buildCanonicalJson(canonical)
        return DesiredConfigFingerprint(
            canonicalConfigJson = canonicalJson,
            desiredConfigHash = sha256Hex(canonicalJson),
        )
    }

    private fun buildCanonicalJson(config: CanonicalDesiredConfig): String {
        val appsJson = config.allowedApps.joinToString(",") { jsonString(it) }
        return "{" +
            "\"userCode\":${jsonString(config.userCode)}," +
            "\"allowedApps\":[${appsJson}]," +
            "\"disableWifi\":${config.disableWifi}," +
            "\"disableBluetooth\":${config.disableBluetooth}," +
            "\"disableCamera\":${config.disableCamera}," +
            "\"disableStatusBar\":${config.disableStatusBar}," +
            "\"kioskMode\":${config.kioskMode}," +
            "\"blockUninstall\":${config.blockUninstall}," +
            "\"showWifi\":${config.showWifi}," +
            "\"showBluetooth\":${config.showBluetooth}" +
            "}"
    }

    private fun jsonString(value: String): String = Json.encodeToString(String.serializer(), value)

    private fun sha256Hex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

}
