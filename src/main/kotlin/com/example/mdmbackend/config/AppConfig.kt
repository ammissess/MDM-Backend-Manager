package com.example.mdmbackend.config

import io.ktor.server.config.*

data class AppConfig(
    val auth: AuthConfig,
    val seed: SeedConfig,
    val db: DbConfig,
    val cors: CorsConfig,
) {
    data class AuthConfig(
        val sessionTtlMinutes: Long,
    )

    data class SeedConfig(
        val adminUser: String,
        val adminPass: String,
        val deviceUser: String,
        val devicePass: String,
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

    companion object {
        fun from(cfg: ApplicationConfig): AppConfig {
            val root = cfg.config("mdm")

            val auth = root.config("auth")
            val seed = root.config("seed")
            val db = root.config("db")

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
            )
        }
    }
}
