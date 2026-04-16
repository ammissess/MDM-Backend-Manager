package com.example.mdmbackend.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val profile: String,
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
        val defaultDeviceUnlockPass: String,
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
        private val supportedProfiles = setOf("local-dev", "integration-test", "demo")

        fun from(cfg: ApplicationConfig): AppConfig {
            val root = cfg.config("mdm")
            val auth = root.config("auth")
            val profile = root.propertyOrNull("profile")
                ?.getString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: detectProfile(root)

            require(profile in supportedProfiles) {
                "Unsupported mdm.profile '$profile'. Supported profiles: ${supportedProfiles.joinToString(", ")}"
            }

            val cors = CorsConfig(readOptionalList(root, "cors.allowedHosts") ?: listOf("*"))

            return AppConfig(
                profile = profile,
                auth = AuthConfig(
                    sessionTtlMinutes = auth.property("sessionTtlMinutes").getString().toLong(),
                ),
                seed = SeedConfig(
                    adminUser = readProfileString(root, profile, "seed", "adminUser"),
                    adminPass = readProfileString(root, profile, "seed", "adminPass"),
                    deviceUser = readProfileString(root, profile, "seed", "deviceUser"),
                    devicePass = readProfileString(root, profile, "seed", "devicePass"),
                    defaultDeviceUnlockPass = readProfileString(root, profile, "seed", "defaultDeviceUnlockPass"),
                    defaultUserCode = readProfileString(root, profile, "seed", "defaultUserCode"),
                    defaultAllowedApps = readProfileList(root, profile, "seed", "defaultAllowedApps"),
                ),
                db = DbConfig(
                    jdbcUrl = readProfileString(root, profile, "db", "jdbcUrl"),
                    driver = readProfileString(root, profile, "db", "driver"),
                    user = readProfileString(root, profile, "db", "user"),
                    password = readProfileString(root, profile, "db", "password"),
                ),
                cors = cors,
                telemetry = TelemetryConfig(
                    eventRetentionDays = root.propertyOrNull("telemetry.eventRetentionDays")?.getString()?.toLong() ?: 30L,
                    usageRetentionDays = root.propertyOrNull("telemetry.usageRetentionDays")?.getString()?.toLong() ?: 30L,
                ),
            )
        }

        private fun detectProfile(root: ApplicationConfig): String {
            val driver = root.propertyOrNull("db.driver")?.getString()?.trim()
            val jdbcUrl = root.propertyOrNull("db.jdbcUrl")?.getString()?.trim()
            val usesH2 = driver.equals("org.h2.Driver", ignoreCase = true) ||
                jdbcUrl?.startsWith("jdbc:h2:", ignoreCase = true) == true

            return if (usesH2) "integration-test" else "local-dev"
        }

        private fun readProfileString(
            root: ApplicationConfig,
            profile: String,
            section: String,
            key: String,
        ): String {
            val rootPath = "$section.$key"
            val profilePath = "profiles.$profile.$section.$key"

            return root.propertyOrNull(rootPath)?.getString()
                ?: root.propertyOrNull(profilePath)?.getString()
                ?: throw IllegalArgumentException(
                    "Missing config for mdm.$rootPath (active profile: $profile, expected fallback at mdm.$profilePath)"
                )
        }

        private fun readProfileList(
            root: ApplicationConfig,
            profile: String,
            section: String,
            key: String,
        ): List<String> {
            val rootPath = "$section.$key"
            val profilePath = "profiles.$profile.$section.$key"

            return readOptionalList(root, rootPath)
                ?: readOptionalList(root, profilePath)
                ?: throw IllegalArgumentException(
                    "Missing config for mdm.$rootPath (active profile: $profile, expected fallback at mdm.$profilePath)"
                )
        }

        private fun readOptionalList(root: ApplicationConfig, path: String): List<String>? {
            root.propertyOrNull(path)?.let { property ->
                runCatching { property.getList() }
                    .getOrNull()
                    ?.let { return it }
            }

            val indexedValues = buildList {
                var index = 0
                while (true) {
                    val value = root.propertyOrNull("$path.$index")?.getString() ?: break
                    add(value)
                    index++
                }
            }

            return indexedValues.takeIf { it.isNotEmpty() }
        }
    }
}
