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
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val deviceCode = varchar("device_code", 128).nullable()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(token)
}

object ProfilesTable : UUIDTable("profiles") {
    val userCode = varchar("user_code", 64).uniqueIndex()
    val name = varchar("name", 128)
    val description = varchar("description", length = 2048).default("")

    val disableWifi = bool("disable_wifi").default(false)
    val disableBluetooth = bool("disable_bluetooth").default(false)
    val disableCamera = bool("disable_camera").default(false)
    val disableStatusBar = bool("disable_status_bar").default(true)
    val kioskMode = bool("kiosk_mode").default(true)
    val blockUninstall = bool("block_uninstall").default(true)

    val showWifi = bool("show_wifi").default(true)
    val showBluetooth = bool("show_bluetooth").default(true)

    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }
}

object ProfileAllowedAppsTable : Table("profile_allowed_apps") {
    val profileId = reference("profile_id", ProfilesTable, onDelete = ReferenceOption.CASCADE)
    val packageName = varchar("package_name", 255)
    override val primaryKey = PrimaryKey(profileId, packageName)
}

object DevicesTable : UUIDTable("devices") {
    val deviceCode = varchar("device_code", 128).uniqueIndex()

    val status = enumerationByName("status", 16, DeviceStatus::class).default(DeviceStatus.LOCKED)
    val unlockPassHash = varchar("unlock_pass_hash", 120).default("")

    val profileId = reference("profile_id", ProfilesTable, onDelete = ReferenceOption.SET_NULL).nullable()

    val androidVersion = varchar("android_version", 64).default("")
    val manufacturer = varchar("manufacturer", 128).default("")
    val model = varchar("model", 128).default("")
    val imei = varchar("imei", 64).default("")
    val serial = varchar("serial", 128).default("")
    val sdkInt = integer("sdk_int").default(0)

    val batteryLevel = integer("battery_level").default(-1)
    val isCharging = bool("is_charging").default(false)
    val wifiEnabled = bool("wifi_enabled").default(false)

    // NEW telemetry snapshot fields
    val networkType = varchar("network_type", 32).nullable()
    val foregroundPackage = varchar("foreground_package", 255).nullable()
    val agentVersion = varchar("agent_version", 64).nullable()
    val agentBuildCode = integer("agent_build_code").nullable()
    val ipAddress = varchar("ip_address", 128).nullable()
    val currentLauncherPackage = varchar("current_launcher_package", 255).nullable()
    val uptimeMs = long("uptime_ms").nullable()
    val abi = varchar("abi", 128).nullable()
    val buildFingerprint = varchar("build_fingerprint", 512).nullable()
    val isDeviceOwner = bool("is_device_owner").default(false)
    val isLauncherDefault = bool("is_launcher_default").default(false)
    val isKioskRunning = bool("is_kiosk_running").default(false)
    val storageFreeBytes = long("storage_free_bytes").default(0)
    val storageTotalBytes = long("storage_total_bytes").default(0)
    val ramFreeMb = integer("ram_free_mb").default(0)
    val ramTotalMb = integer("ram_total_mb").default(0)
    val lastBootAt = timestamp("last_boot_at").nullable()
    val lastTelemetryAt = timestamp("last_telemetry_at").nullable()
    val lastPollAt = timestamp("last_poll_at").nullable()
    val lastCommandAckAt = timestamp("last_command_ack_at").nullable()

    // NEW desired vs applied policy fields
    val desiredConfigVersionEpochMillis = long("desired_config_version_epoch_millis").nullable()
    val desiredConfigHash = varchar("desired_config_hash", 128).nullable()
    val appliedConfigVersionEpochMillis = long("applied_config_version_epoch_millis").nullable()
    val appliedConfigHash = varchar("applied_config_hash", 128).nullable()
    val policyApplyStatus = varchar("policy_apply_status", 32).default("UNKNOWN")
    val policyApplyError = varchar("policy_apply_error", 2048).nullable()
    val policyApplyErrorCode = varchar("policy_apply_error_code", 64).nullable()
    val lastPolicyAppliedAt = timestamp("last_policy_applied_at").nullable()
    val fcmToken = varchar("fcm_token", 4096).nullable()
    val fcmTokenUpdatedAt = timestamp("fcm_token_updated_at").nullable()

    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val lastSeenAt = timestamp("last_seen_at").clientDefault { Instant.now() }
}

object DeviceInstalledAppsTable : Table("device_installed_apps") {
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val packageName = varchar("package_name", 255)
    val appName = varchar("app_name", 255).nullable()
    val versionName = varchar("version_name", 128).nullable()
    val versionCode = long("version_code").nullable()
    val isSystemApp = bool("is_system_app").nullable()
    val hasLauncherActivity = bool("has_launcher_activity").nullable()
    val installed = bool("installed").default(true)
    val disabled = bool("disabled").nullable()
    val hidden = bool("hidden").nullable()
    val suspended = bool("suspended").nullable()
    val firstSeenAt = timestamp("first_seen_at")
    val lastSeenAt = timestamp("last_seen_at")

    override val primaryKey = PrimaryKey(deviceId, packageName)
}

object DeviceEventsTable : UUIDTable("device_events") {
    val deviceId = reference("device_id", DevicesTable, onDelete = ReferenceOption.CASCADE)
    val type = varchar("type", 64)
    val category = varchar("category", 32).default("GENERAL")
    val severity = varchar("severity", 16).default("INFO")
    val payload = varchar("payload", length = 8192).default("{}")
    val errorCode = varchar("error_code", 64).nullable()
    val message = varchar("message", 1024).nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
}
