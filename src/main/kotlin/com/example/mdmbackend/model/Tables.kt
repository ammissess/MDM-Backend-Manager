package com.example.mdmbackend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object UsersTable : UUIDTable("users") {
    val username = varchar("username", 64).uniqueIndex()
    val passwordHash = varchar("password_hash", 120)
    val role = varchar("role", 32)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}

object SessionsTable : Table("sessions") {
    val token = uuid("token")

    // ✅ FK tới UUIDTable: dùng reference(...)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)

    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(token)
}

object ProfilesTable : UUIDTable("profiles") {
    val userCode = varchar("user_code", 64).uniqueIndex()
    val name = varchar("name", 128)
    //val description = text("description").default("")
    val description = varchar("description", length = 2048).default("")

    // Policy flags
    val disableWifi = bool("disable_wifi").default(false)
    val disableBluetooth = bool("disable_bluetooth").default(false)
    val disableCamera = bool("disable_camera").default(false)
    val disableStatusBar = bool("disable_status_bar").default(true)
    val kioskMode = bool("kiosk_mode").default(true)
    val blockUninstall = bool("block_uninstall").default(true)

    // UI flags
    val showWifi = bool("show_wifi").default(true)
    val showBluetooth = bool("show_bluetooth").default(true)

    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object ProfileAllowedAppsTable : Table("profile_allowed_apps") {
    // ✅ FK tới UUIDTable: dùng reference(...)
    val profileId = reference("profile_id", ProfilesTable, onDelete = ReferenceOption.CASCADE)

    val packageName = varchar("package_name", 255)

    override val primaryKey = PrimaryKey(profileId, packageName)
}

object DevicesTable : UUIDTable("devices") {
    val deviceCode = varchar("device_code", 128).uniqueIndex()

    // ✅ thêm 2 cột mới
    val status = enumerationByName("status", 16, DeviceStatus::class).default(DeviceStatus.LOCKED)
    val unlockPassHash = varchar("unlock_pass_hash", 120).default("")

    // ✅ FK nullable tới UUIDTable: reference(...).nullable()
    val profileId = reference("profile_id", ProfilesTable, onDelete = ReferenceOption.SET_NULL).nullable()

    val manufacturer = varchar("manufacturer", 128).default("")
    val model = varchar("model", 128).default("")
    val serial = varchar("serial", 128).default("")
    val sdkInt = integer("sdk_int").default(0)

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val lastSeenAt = timestamp("last_seen_at").clientDefault { Instant.now() }
}

object DeviceEventsTable : UUIDTable("device_events") {
    // ✅ FK tới UUIDTable: dùng reference(...)
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)

    val type = varchar("type", 64)
    //val payload = text("payload").default("{}")
    val payload = varchar("payload", length = 8192).default("{}")

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}
