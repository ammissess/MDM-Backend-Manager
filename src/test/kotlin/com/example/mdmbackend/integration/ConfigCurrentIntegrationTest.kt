package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import junit.framework.TestCase.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigCurrentIntegrationTest {

    @Test
    fun testConfigCurrent_MissingDeviceCode_ShouldReturn400() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CFG_MISSING_001")

        val resp = client.get("/api/device/config/current") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("Missing query param: deviceCode"))
    }

    @Test
    fun testConfigCurrent_DeviceNotFound_ShouldReturn404() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CFG_NOTFOUND_001")

        val resp = client.get("/api/device/config/current?deviceCode=TEST_CFG_NOTFOUND_001") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("Device not found"))
    }

    @Test
    fun testConfigCurrent_LockedDevice_ShouldReturn423() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CFG_LOCKED_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Google",
                  "model": "Pixel 8",
                  "imei": "999001",
                  "serial": "SER001",
                  "batteryLevel": 80,
                  "isCharging": true,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)

        val resp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.Locked, resp.status)
        assertTrue(resp.bodyAsText().contains("Device is locked"))
    }

    @Test
    fun testConfigCurrent_DeviceWithoutProfile_ShouldReturn404() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CFG_NOPROFILE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Samsung",
                  "model": "S24",
                  "imei": "999002",
                  "serial": "SER002",
                  "batteryLevel": 65,
                  "isCharging": false,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"1111"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, unlockResp.status)

        val resp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("Device profile not linked"))
    }

    @Test
    fun testConfigCurrent_Success_ResolveByDeviceProfileId_NotByUserCodePath() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "TEST_CFG_SUCCESS_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode": "CFG_USER_001",
                  "name": "CFG Profile",
                  "description": "for config/current test",
                  "allowedApps": ["com.example.alpha", "com.example.beta"],
                  "disableWifi": true,
                  "disableBluetooth": false,
                  "disableCamera": true,
                  "disableStatusBar": true,
                  "kioskMode": true,
                  "blockUninstall": true,
                  "showWifi": false,
                  "showBluetooth": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.Created, profileResp.status)
        val profileId = TestJsonHelper.extractField(profileResp.bodyAsText(), "id")

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Xiaomi",
                  "model": "14",
                  "imei": "999003",
                  "serial": "SER003",
                  "batteryLevel": 90,
                  "isCharging": true,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"CFG_USER_001"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, linkResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"1111"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, unlockResp.status)

        val currentResp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, currentResp.status)
        val currentBody = currentResp.bodyAsText()
        assertTrue(currentBody.contains("CFG_USER_001"))
        assertTrue(currentBody.contains("com.example.alpha"))
        assertTrue(currentBody.contains("disableWifi"))
        assertTrue(currentBody.contains("true"))

        val deprecatedResp = client.get("/api/device/config/WRONG_USER_CODE?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.Companion.NotFound, deprecatedResp.status)
        assertEquals("true", deprecatedResp.headers["X-Deprecated"])
        assertTrue((deprecatedResp.headers["Warning"] ?: "").contains("Deprecated endpoint"))

        assertTrue(profileId.isNotBlank()) // giữ biến sử dụng để tránh warning/unused
    }
}