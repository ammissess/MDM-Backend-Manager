package com.example.mdmbackend.integration

import com.example.mdmbackend.model.DeviceEventsTable
import com.example.mdmbackend.model.DevicesTable
import com.example.mdmbackend.module
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.service.TelemetryRetentionPolicy
import com.example.mdmbackend.service.TelemetryRetentionService
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageIntegrationTest {

    @Test
    fun testPostUsageSingle() = testApplication {
        configureUsageTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_USAGE_001")
        client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_USAGE_001",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Samsung",
              "model": "Galaxy S24",
              "imei": "111111111",
              "serial": "RF001",
              "batteryLevel": 80,
              "isCharging": true,
              "wifiEnabled": true
            }
            """)
        }

        // Post single usage
        val now = Instant.now().toEpochMilli()
        val usageResp = client.post("/api/device/usage") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_USAGE_001",
              "packageName": "com.android.chrome",
              "startedAtEpochMillis": ${now - 60000},
              "endedAtEpochMillis": $now,
              "durationMs": 60000
            }
            """)
        }

        assertEquals(HttpStatusCode.OK, usageResp.status)
        assertTrue(usageResp.bodyAsText().contains("true"))
        println("✅ Single usage posted")
    }

    @Test
    fun testPostUsageBatch() = testApplication {
        configureUsageTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_USAGE_BATCH")
        client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_USAGE_BATCH",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "OnePlus",
              "model": "12",
              "imei": "222222222",
              "serial": "RF002",
              "batteryLevel": 50,
              "isCharging": false,
              "wifiEnabled": false
            }
            """)
        }

        // Post batch usage
        val now = Instant.now().toEpochMilli()
        val batchResp = client.post("/api/device/usage/batch") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_USAGE_BATCH",
              "items": [
                {
                  "packageName": "com.android.chrome",
                  "startedAtEpochMillis": ${now - 180000},
                  "endedAtEpochMillis": ${now - 120000},
                  "durationMs": 60000
                },
                {
                  "packageName": "com.android.settings",
                  "startedAtEpochMillis": ${now - 120000},
                  "endedAtEpochMillis": $now,
                  "durationMs": 120000
                }
              ]
            }
            """)
        }

        assertEquals(HttpStatusCode.OK, batchResp.status)
        val body = batchResp.bodyAsText()
        assertTrue("\\\"ok\\\"\\s*:\\s*true".toRegex().containsMatchIn(body))
        assertTrue("\\\"inserted\\\"\\s*:\\s*2".toRegex().containsMatchIn(body))
        println("✅ Batch usage posted (2 items)")
    }

    @Test
    fun testAdminUsageSummary() = testApplication {
        configureUsageTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device & post usage
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_USAGE_SUMMARY")
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_USAGE_SUMMARY",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Xiaomi",
              "model": "14",
              "imei": "333333333",
              "serial": "RF003",
              "batteryLevel": 100,
              "isCharging": false,
              "wifiEnabled": true
            }
            """)
        }
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        // Post batch usage
        val now = Instant.now().toEpochMilli()
        client.post("/api/device/usage/batch") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_USAGE_SUMMARY",
              "items": [
                {
                  "packageName": "com.android.chrome",
                  "startedAtEpochMillis": ${now - 300000},
                  "endedAtEpochMillis": ${now - 200000},
                  "durationMs": 100000
                },
                {
                  "packageName": "com.android.chrome",
                  "startedAtEpochMillis": ${now - 200000},
                  "endedAtEpochMillis": ${now - 100000},
                  "durationMs": 100000
                },
                {
                  "packageName": "com.android.settings",
                  "startedAtEpochMillis": ${now - 100000},
                  "endedAtEpochMillis": $now,
                  "durationMs": 100000
                }
              ]
            }
            """)
        }

        // Admin get usage summary
        val adminToken = TestAuthHelper.loginAdmin(client)
        val summaryResp = client.get("/api/admin/devices/$deviceId/usage/summary") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.OK, summaryResp.status)
        val body = summaryResp.bodyAsText()

        // Verify aggregate data
        assertTrue(body.contains("com.android.chrome"))
        assertTrue(body.contains("com.android.settings"))
        assertTrue("\\\"totalDurationMs\\\"\\s*:\\s*200000".toRegex().containsMatchIn(body)) // chrome: 100+100
        assertTrue("\\\"sessions\\\"\\s*:\\s*2".toRegex().containsMatchIn(body)) // chrome has 2 sessions
        println("✅ Usage summary aggregated correctly")
    }

    @Test
    fun testTelemetryRetentionCleanup_ShouldDeleteOldRawRowsAndKeepRecent() = testApplication {
        configureUsageTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_RETENTION_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = UUID.fromString(TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId"))

        val usageRepo = DeviceAppUsageRepository()
        val deviceRepo = DeviceRepository()

        val now = Instant.now()
        val old = now.minusSeconds(31L * 24 * 60 * 60)
        val recent = now.minusSeconds(60)

        usageRepo.insertUsage(
            deviceId = deviceId,
            packageName = "com.old.app",
            startedAt = old.minusSeconds(30),
            endedAt = old,
            durationMs = 10_000,
        )
        usageRepo.insertUsage(
            deviceId = deviceId,
            packageName = "com.new.app",
            startedAt = recent.minusSeconds(30),
            endedAt = recent,
            durationMs = 20_000,
        )

        transaction {
            DeviceEventsTable.insert {
                it[DeviceEventsTable.id] = UUID.randomUUID()
                it[DeviceEventsTable.deviceId] = EntityID(deviceId, DevicesTable)
                it[DeviceEventsTable.type] = "old_event"
                it[DeviceEventsTable.category] = "SYSTEM"
                it[DeviceEventsTable.severity] = "INFO"
                it[DeviceEventsTable.payload] = "{}"
                it[DeviceEventsTable.createdAt] = old
            }
            DeviceEventsTable.insert {
                it[DeviceEventsTable.id] = UUID.randomUUID()
                it[DeviceEventsTable.deviceId] = EntityID(deviceId, DevicesTable)
                it[DeviceEventsTable.type] = "new_event"
                it[DeviceEventsTable.category] = "SYSTEM"
                it[DeviceEventsTable.severity] = "INFO"
                it[DeviceEventsTable.payload] = "{}"
                it[DeviceEventsTable.createdAt] = recent
            }
        }

        val cleanup = TelemetryRetentionService(
            devices = deviceRepo,
            usage = usageRepo,
            policy = TelemetryRetentionPolicy(eventRetentionDays = 30, usageRetentionDays = 30),
        ).runCleanup(now)
        println("DAY52_RETENTION_CLEANUP_RESULT=eventRowsDeleted=${cleanup.eventRowsDeleted},usageRowsDeleted=${cleanup.usageRowsDeleted}")

        assertEquals(1, cleanup.eventRowsDeleted)
        assertEquals(1, cleanup.usageRowsDeleted)

        val adminToken = TestAuthHelper.loginAdmin(client)
        val eventsResp = client.get("/api/admin/devices/$deviceId/events?limit=50") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, eventsResp.status)
        val eventsBody = eventsResp.bodyAsText()
        println("DAY52_RETENTION_EVENTS_AFTER=$eventsBody")
        assertTrue(eventsBody.contains("new_event"))
        assertTrue(!eventsBody.contains("old_event"))

        val usageResp = client.get("/api/admin/devices/$deviceId/usage/summary") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, usageResp.status)
        val usageBody = usageResp.bodyAsText()
        println("DAY52_RETENTION_USAGE_AFTER=$usageBody")
        assertTrue(usageBody.contains("com.new.app"))
        assertTrue(!usageBody.contains("com.old.app"))
    }
}

private fun ApplicationTestBuilder.configureUsageTestApplication() {
    environment {
        val dbName = "usage_integration_${System.nanoTime()}"
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
        )
    }
}