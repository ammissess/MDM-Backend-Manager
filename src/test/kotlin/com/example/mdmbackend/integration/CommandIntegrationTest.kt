package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandIntegrationTest {

    @Test
    fun testCommandFlow_CreatePollAck() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // 1) Register device
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${TestAuthHelper.loginDevice(client, "TEST_CMD_001")}")
            setBody("""
            {
              "deviceCode": "TEST_CMD_001",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Google",
              "model": "Pixel 7",
              "imei": "123456789",
              "serial": "R5CRC",
              "batteryLevel": 85,
              "isCharging": false,
              "wifiEnabled": true
            }
            """)
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")
        println("✅ Device registered: $deviceId")

        // 2) Admin login
        val adminToken = TestAuthHelper.loginAdmin(client)
        println("✅ Admin logged in")

        // 3) Admin create command
        val createCmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""
            {
              "type": "lock_screen",
              "payload": "{\"duration_seconds\":60}",
              "ttlSeconds": 600
            }
            """)
        }
        assertEquals(HttpStatusCode.Created, createCmdResp.status)
        val commandId = TestJsonHelper.extractField(createCmdResp.bodyAsText(), "id")
        println("✅ Command created: $commandId")

        // 4) Device poll commands
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_001")
        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"TEST_CMD_001","limit":5}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val pollBody = pollResp.bodyAsText()
        assertTrue(pollBody.contains("lock_screen"))
        val leaseToken = TestJsonHelper.extractField(pollBody, "leaseToken")
        println("✅ Device polled, got leaseToken: $leaseToken")

        // 5) Device ack command
        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_CMD_001",
              "commandId": "$commandId",
              "leaseToken": "$leaseToken",
              "result": "SUCCESS",
              "output": "Screen locked"
            }
            """)
        }
        assertEquals(HttpStatusCode.OK, ackResp.status)
        assertTrue(ackResp.bodyAsText().contains("SUCCESS"))
        println("✅ Device acked command")

        // 6) Admin list commands → verify SUCCESS status
        val listResp = client.get("/api/admin/devices/$deviceId/commands?status=SUCCESS") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listResp.status)
        val listBody = listResp.bodyAsText()
        assertTrue(listBody.contains("SUCCESS"))
        assertTrue(listBody.contains(commandId))
        println("✅ Admin verified command SUCCESS")
    }

    @Test
    fun testPollWithMismatchedDeviceCode_ShouldFail() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Device token untuk TEST_CMD_002
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_002")

        // Poll dengan deviceCode berbeda → 409 Conflict
        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"DIFFERENT_CODE","limit":5}""")
        }

        assertEquals(HttpStatusCode.Conflict, pollResp.status)
        val body = pollResp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        println("✅ Poll mismatch correctly rejected (409)")
    }

    @Test
    fun testAckWithMismatchedDeviceCode_ShouldFail() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_003")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "DIFFERENT_CODE",
              "commandId": "550e8400-0000-0000-0000-000000000000",
              "leaseToken": "550e8400-0000-0000-0000-000000000001",
              "result": "SUCCESS"
            }
            """)
        }

        assertEquals(HttpStatusCode.Conflict, ackResp.status)
        assertTrue(ackResp.bodyAsText().contains("deviceCode mismatch"))
        println("✅ Ack mismatch correctly rejected (409)")
    }

    @Test
    fun testAckWithInvalidUuid_ShouldFail() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_004")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_CMD_004",
              "commandId": "invalid-uuid",
              "leaseToken": "550e8400-0000-0000-0000-000000000001",
              "result": "SUCCESS"
            }
            """)
        }

        assertEquals(HttpStatusCode.BadRequest, ackResp.status)
        assertTrue(ackResp.bodyAsText().contains("Invalid UUID format"))
        println("✅ Invalid UUID correctly rejected (400)")
    }
}