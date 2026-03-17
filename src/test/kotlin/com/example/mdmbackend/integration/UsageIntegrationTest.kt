package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageIntegrationTest {

    @Test
    fun testPostUsageSingle() = testApplication {
        application { module() }
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
        application { module() }
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
        assertTrue(body.contains("\"ok\":true"))
        assertTrue(body.contains("\"inserted\":2"))
        println("✅ Batch usage posted (2 items)")
    }

    @Test
    fun testAdminUsageSummary() = testApplication {
        application { module() }
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
        assertTrue(body.contains("\"totalDurationMs\":200000")) // chrome: 100+100
        assertTrue(body.contains("\"sessions\":2")) // chrome has 2 sessions
        println("✅ Usage summary aggregated correctly")
    }
}