package com.example.mdmbackend.config

import io.ktor.server.config.*

data class AppConfig(
    val auth: AuthConfig,
    val seed: SeedConfig,
    val db: DbConfig,
    val cors: CorsConfig,
    val telemetry: TelemetryConfig,
) {
    data class AuthConfig(
        val sessionTtlMinutes: Long,
    )

    data class SeedConfig(
        val adminUser: String,
        val adminPass: String,
        val deviceUser: String,
        val devicePass: String,
        val defaultDeviceUnlockPass: String, // ✅ thêm
        val defaultUserCode: String,
        val defaultAllowedApps: List<String>,
    )

    data class DbConfig(
        val jdbcUrl: String,
        val driver: String,
        val user: String,
        val password: String,
    )

    data class CorsConfig(
        val allowedHosts: List<String>,
    )

    data class TelemetryConfig(
        val eventRetentionDays: Long,
        val usageRetentionDays: Long,
    )

    companion object {
        fun from(cfg: ApplicationConfig): AppConfig {
            val root = cfg.config("mdm")

            val auth = root.config("auth")
            val seed = root.config("seed")
            val db = root.config("db")
            val telemetry = root.config("telemetry")

            // CORS: cho phép dashboard (dev) truy cập. Có thể chỉnh sau.
            val cors = if (root.propertyOrNull("cors.allowedHosts") != null) {
                CorsConfig(root.property("cors.allowedHosts").getList())
            } else {
                CorsConfig(listOf("*") )
            }

            return AppConfig(
                auth = AuthConfig(
                    sessionTtlMinutes = auth.property("sessionTtlMinutes").getString().toLong(),
                ),
                seed = SeedConfig(
                    adminUser = seed.property("adminUser").getString(),
                    adminPass = seed.property("adminPass").getString(),
                    deviceUser = seed.property("deviceUser").getString(),
                    devicePass = seed.property("devicePass").getString(),

                    // ✅ thêm dòng này
                    defaultDeviceUnlockPass = seed.property("defaultDeviceUnlockPass").getString(),

                    defaultUserCode = seed.property("defaultUserCode").getString(),
                    defaultAllowedApps = seed.property("defaultAllowedApps").getList(),
                ),

                db = DbConfig(
                    jdbcUrl = db.property("jdbcUrl").getString(),
                    driver = db.property("driver").getString(),
                    user = db.property("user").getString(),
                    password = db.property("password").getString(),
                ),
                cors = cors,
                telemetry = TelemetryConfig(
                    eventRetentionDays = telemetry.propertyOrNull("eventRetentionDays")?.getString()?.toLong() ?: 30L,
                    usageRetentionDays = telemetry.propertyOrNull("usageRetentionDays")?.getString()?.toLong() ?: 30L,
                ),
            )
        }
    }
}
