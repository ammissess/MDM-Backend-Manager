package com.example.mdmbackend.integration

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.config.DatabaseFactory
import com.example.mdmbackend.config.Seeder
import com.example.mdmbackend.dto.ProfileUpdateRequest
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.repository.UserRepository
import com.example.mdmbackend.service.CommandCreatedEvent
import com.example.mdmbackend.service.DeviceCommandService
import com.example.mdmbackend.service.DeviceWakeupRequest
import com.example.mdmbackend.service.DeviceWakeupSendResult
import com.example.mdmbackend.service.DeviceWakeupSender
import com.example.mdmbackend.service.EventBus
import com.example.mdmbackend.service.FcmWakeupService
import com.example.mdmbackend.service.ProfileService
import com.example.mdmbackend.service.AuditService
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FcmWakeupIntegrationTest {

    @AfterTest
    fun cleanupDatabase() {
        DatabaseFactory.close()
    }

    @Test
    fun testFcmTokenUpsert_ShouldPersistLatestToken() = testApplication {
        configureFcmTokenTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "FCM_TOKEN_OK_001"
        val token = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)

        val updatedAt = System.currentTimeMillis()
        val upsertResp = client.post("/api/device/fcm-token") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "fcmToken":"fcm-token-value-001",
                  "appVersion":"1.0.0",
                  "updatedAtEpochMillis":$updatedAt
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, upsertResp.status)
        assertTrue(upsertResp.bodyAsText().contains("\"ok\""))

        val stored = DeviceRepository().findByDeviceCode(deviceCode)
        assertNotNull(stored)
        assertEquals("fcm-token-value-001", stored.fcmToken)
        assertEquals(updatedAt, stored.fcmTokenUpdatedAt?.toEpochMilli())
    }

    @Test
    fun testCommandCreated_ShouldTriggerWakeupWhenTokenExists() {
        val cfg = initStandaloneBackendConfig("fcm_wakeup_command_${System.nanoTime()}")
        Seeder.seed(cfg)

        val devices = DeviceRepository()
        val commands = DeviceCommandRepository()
        val bus = EventBus()
        val sender = RecordingWakeupSender()
        val wakeups = FcmWakeupService(devices = devices, sender = sender)
        wakeups.register(bus)

        val registered = devices.upsertRegister(
            deviceCode = "FCM_WAKE_DEVICE_001",
            androidVersion = "14",
            sdkInt = 34,
            manufacturer = "Google",
            model = "Pixel 8",
            imei = "",
            serial = "",
            batteryLevel = 80,
            isCharging = true,
            wifiEnabled = true,
            defaultUnlockPassHash = "hash",
        )
        devices.upsertFcmToken(
            deviceCode = registered.deviceCode,
            fcmToken = "device-token-001",
            updatedAt = java.time.Instant.ofEpochMilli(1_710_000_000_000),
        )
        val adminUser = UserRepository().findByUsername(cfg.seed.adminUser)
        assertNotNull(adminUser)

        val service = DeviceCommandService(devices, commands, bus)
        service.adminCreate(
            deviceId = registered.id,
            createdByUserId = adminUser.id,
            req = com.example.mdmbackend.dto.AdminCreateCommandRequest(
                type = "lock_screen",
                payload = "{}",
                ttlSeconds = 600,
            )
        )

        assertEquals(1, sender.requests.size)
        assertEquals("command_created", sender.requests.single().request.triggerSource)
        assertEquals("pending_command", sender.requests.single().request.reason)
        assertEquals("lock_screen", sender.requests.single().request.commandType)
        assertEquals("FCM_WAKE_DEVICE_001", sender.requests.single().deviceCode)
    }

    @Test
    fun testDesiredChangeRefreshEnqueue_ShouldTriggerWakeup() {
        val cfg = initStandaloneBackendConfig("fcm_wakeup_refresh_${System.nanoTime()}")
        Seeder.seed(cfg)

        val devices = DeviceRepository()
        val profiles = ProfileRepository()
        val commands = DeviceCommandRepository()
        val bus = EventBus()
        val sender = RecordingWakeupSender()
        val wakeups = FcmWakeupService(devices = devices, sender = sender)
        wakeups.register(bus)

        val commandService = DeviceCommandService(devices, commands, bus)
        val profileService = ProfileService(
            repo = profiles,
            devices = devices,
            commands = commandService,
            audit = AuditService(),
        )

        val registered = devices.upsertRegister(
            deviceCode = "FCM_WAKE_DEVICE_002",
            androidVersion = "14",
            sdkInt = 34,
            manufacturer = "Google",
            model = "Pixel 8",
            imei = "",
            serial = "",
            batteryLevel = 80,
            isCharging = true,
            wifiEnabled = true,
            defaultUnlockPassHash = "hash",
        )
        devices.upsertFcmToken(
            deviceCode = registered.deviceCode,
            fcmToken = "device-token-002",
            updatedAt = java.time.Instant.ofEpochMilli(1_710_000_100_000),
        )

        val profile = profiles.findByUserCode(cfg.seed.defaultUserCode)
        assertNotNull(profile)
        devices.setProfile(registered.id, profile.id)

        val adminUser = UserRepository().findByUsername(cfg.seed.adminUser)
        assertNotNull(adminUser)

        val updated = profileService.update(
            id = profile.id,
            req = ProfileUpdateRequest(disableWifi = true),
            actorUserId = adminUser.id,
        )

        assertNotNull(updated)
        assertEquals(1, sender.requests.size)
        assertEquals("refresh_config_enqueued", sender.requests.single().request.triggerSource)
        assertEquals("refresh_config", sender.requests.single().request.commandType)
        assertEquals("FCM_WAKE_DEVICE_002", sender.requests.single().deviceCode)
    }

    @Test
    fun testFcmTokenUpsert_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureFcmTokenTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "FCM_GUARD_A")
        val resp = client.post("/api/device/fcm-token") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                """
                {
                  "deviceCode":"FCM_GUARD_B",
                  "fcmToken":"guard-token",
                  "updatedAtEpochMillis":123
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }
}

private class RecordingWakeupSender : DeviceWakeupSender {
    data class RecordedRequest(
        val deviceCode: String,
        val request: DeviceWakeupRequest,
    )

    val requests = mutableListOf<RecordedRequest>()

    override fun send(
        target: com.example.mdmbackend.repository.DeviceWakeupTarget,
        request: DeviceWakeupRequest,
    ): DeviceWakeupSendResult {
        requests += RecordedRequest(
            deviceCode = target.deviceCode,
            request = request,
        )
        return DeviceWakeupSendResult(
            attempted = true,
            delivered = true,
            detail = "mock_delivered",
        )
    }
}

private fun ApplicationTestBuilder.configureFcmTokenTestApplication() {
    environment {
        val dbName = "fcm_token_integration_${System.nanoTime()}"
        val baseConfig = ConfigFactory.load()
        config = HoconApplicationConfig(
            baseConfig
                .withValue("mdm.auth.sessionTtlMinutes", ConfigValueFactory.fromAnyRef("43200"))
                .withValue(
                    "mdm.db.jdbcUrl",
                    ConfigValueFactory.fromAnyRef(
                        "jdbc:h2:mem:$dbName;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
                    )
                )
                .withValue("mdm.db.driver", ConfigValueFactory.fromAnyRef("org.h2.Driver"))
                .withValue("mdm.db.user", ConfigValueFactory.fromAnyRef("sa"))
                .withValue("mdm.db.password", ConfigValueFactory.fromAnyRef(""))
                .withValue("mdm.fcm.enabled", ConfigValueFactory.fromAnyRef(false))
                .withValue("mdm.fcm.credentialsFile", ConfigValueFactory.fromAnyRef(""))
        )
    }
}

private fun initStandaloneBackendConfig(dbName: String): AppConfig {
    val cfg = AppConfig(
        profile = "integration-test",
        auth = AppConfig.AuthConfig(sessionTtlMinutes = 43200),
        seed = AppConfig.SeedConfig(
            adminUser = "admin",
            adminPass = "admin123",
            deviceUser = "device",
            devicePass = "device123",
            defaultDeviceUnlockPass = "1111",
            defaultUserCode = "TEST005",
            defaultAllowedApps = listOf("com.android.settings", "com.android.chrome"),
        ),
        db = AppConfig.DbConfig(
            jdbcUrl = "jdbc:h2:mem:$dbName;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            driver = "org.h2.Driver",
            user = "sa",
            password = "",
        ),
        cors = AppConfig.CorsConfig(allowedOrigins = listOf("http://localhost:5173")),
        rateLimit = AppConfig.RateLimitConfig(
            login = AppConfig.RouteRateLimitConfig(enabled = true, maxRequests = 20, windowSeconds = 60),
            unlock = AppConfig.RouteRateLimitConfig(enabled = true, maxRequests = 20, windowSeconds = 60),
        ),
        telemetry = AppConfig.TelemetryConfig(
            eventRetentionDays = 30,
            usageRetentionDays = 30,
        ),
        fcm = AppConfig.FcmConfig(
            enabled = false,
            credentialsFile = "",
        ),
    )
    DatabaseFactory.init(cfg)
    return cfg
}
