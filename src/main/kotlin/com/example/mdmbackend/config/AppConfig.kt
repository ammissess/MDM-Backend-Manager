package com.example.mdmbackend.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val profile: String,
    val auth: AuthConfig,
    val seed: SeedConfig,
    val db: DbConfig,
    val cors: CorsConfig,
    val rateLimit: RateLimitConfig,
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
        val allowedOrigins: List<String>,
    )

    data class RateLimitConfig(
        val login: RouteRateLimitConfig,
        val unlock: RouteRateLimitConfig,
    )

    data class RouteRateLimitConfig(
        val enabled: Boolean,
        val maxRequests: Int,
        val windowSeconds: Long,
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

            val cors = CorsConfig(
                allowedOrigins = readProfileList(root, profile, "cors", "allowedOrigins")
            )
            val rateLimit = RateLimitConfig(
                login = readRateLimitRule(
                    root = root,
                    profile = profile,
                    path = "rateLimit.login",
                    defaultEnabled = true,
                    defaultMaxRequests = 10,
                    defaultWindowSeconds = 60,
                ),
                unlock = readRateLimitRule(
                    root = root,
                    profile = profile,
                    path = "rateLimit.unlock",
                    defaultEnabled = true,
                    defaultMaxRequests = 5,
                    defaultWindowSeconds = 60,
                ),
            )

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
                rateLimit = rateLimit,
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

                runCatching { property.getString() }
                    .getOrNull()
                    ?.let { raw ->
                        parseStringList(raw)?.let { return it }
                    }
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

        private fun parseStringList(raw: String): List<String>? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val content = trimmed
                .removePrefix("[")
                .removeSuffix("]")
                .trim()

            val values = if (content.isEmpty()) {
                emptyList()
            } else {
                content.split(',')
                    .map { it.trim().trim('"') }
                    .filter { it.isNotBlank() }
            }

            return values.takeIf { it.isNotEmpty() }
        }

        private fun readRateLimitRule(
            root: ApplicationConfig,
            profile: String,
            path: String,
            defaultEnabled: Boolean,
            defaultMaxRequests: Int,
            defaultWindowSeconds: Long,
        ): RouteRateLimitConfig {
            val enabled = readProfileBoolean(root, profile, "$path.enabled") ?: defaultEnabled
            val maxRequests = readProfileLong(root, profile, "$path.maxRequests")?.toInt() ?: defaultMaxRequests
            val windowSeconds = readProfileLong(root, profile, "$path.windowSeconds") ?: defaultWindowSeconds

            require(maxRequests > 0) { "mdm.$path.maxRequests must be > 0" }
            require(windowSeconds > 0) { "mdm.$path.windowSeconds must be > 0" }

            return RouteRateLimitConfig(
                enabled = enabled,
                maxRequests = maxRequests,
                windowSeconds = windowSeconds,
            )
        }

        private fun readProfileBoolean(
            root: ApplicationConfig,
            profile: String,
            path: String,
        ): Boolean? =
            readProfileRaw(root, profile, path)?.toBooleanStrictOrNull()

        private fun readProfileLong(
            root: ApplicationConfig,
            profile: String,
            path: String,
        ): Long? =
            readProfileRaw(root, profile, path)?.toLongOrNull()

        private fun readProfileRaw(
            root: ApplicationConfig,
            profile: String,
            path: String,
        ): String? =
            root.propertyOrNull(path)?.getString()
                ?: root.propertyOrNull("profiles.$profile.$path")?.getString()
    }
}
