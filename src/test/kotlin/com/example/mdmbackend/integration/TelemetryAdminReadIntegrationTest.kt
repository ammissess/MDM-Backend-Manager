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

class TelemetryAdminReadIntegrationTest {

    @Test
    fun testLocationTelemetry() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_LOC_001")
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_LOC_001",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Apple",
              "model": "iPhone 15",
              "imei": "444444444",
              "serial": "RF004",
              "batteryLevel": 70,
              "isCharging": false,
              "wifiEnabled": true
            }
            """)
        }
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        // Device post location
        val locResp = client.post("/api/device/location") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_LOC_001",
              "latitude": 21.0285,
              "longitude": 105.8542,
              "accuracyMeters": 50.0
            }
            """)
        }
        assertEquals(HttpStatusCode.OK, locResp.status)
        println("✅ Location posted")

        // Admin get location/latest
        val adminToken = TestAuthHelper.loginAdmin(client)
        val getLocResp = client.get("/api/admin/devices/$deviceId/location/latest") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.OK, getLocResp.status)
        val locBody = getLocResp.bodyAsText()
        assertTrue(locBody.contains("21.0285"))
        assertTrue(locBody.contains("105.8542"))
        assertTrue(locBody.contains("50"))
        println("✅ Location retrieved by admin")
    }

    @Test
    fun testEventTelemetry() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_EVT_001")
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_EVT_001",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Huawei",
              "model": "P60",
              "imei": "555555555",
              "serial": "RF005",
              "batteryLevel": 45,
              "isCharging": true,
              "wifiEnabled": false
            }
            """)
        }
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        // Device post event
        val eventResp = client.post("/api/device/TEST_EVT_001/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "type": "app_crash",
              "payload": "{\"app\":\"com.android.chrome\",\"error\":\"NullPointerException\"}"
            }
            """)
        }
        assertEquals(HttpStatusCode.OK, eventResp.status)
        println("✅ Event posted")

        // Admin get events
        val adminToken = TestAuthHelper.loginAdmin(client)
        val getEvtResp = client.get("/api/admin/devices/$deviceId/events?limit=10") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.OK, getEvtResp.status)
        val evtBody = getEvtResp.bodyAsText()
        assertTrue(evtBody.contains("app_crash"))
        assertTrue(evtBody.contains("NullPointerException"))
        println("✅ Events retrieved by admin")
    }

    @Test
    fun testNoLocationData_Returns404() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device nhưng không post location
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_LOC_EMPTY")
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_LOC_EMPTY",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Generic",
              "model": "Phone",
              "imei": "666666666",
              "serial": "RF006",
              "batteryLevel": 30,
              "isCharging": false,
              "wifiEnabled": true
            }
            """)
        }
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        // Admin try get location → 404
        val adminToken = TestAuthHelper.loginAdmin(client)
        val getLocResp = client.get("/api/admin/devices/$deviceId/location/latest") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.NotFound, getLocResp.status)
        assertTrue(getLocResp.bodyAsText().contains("No location data"))
        println("✅ No location data correctly returns 404")
    }

    @Test
    fun testDeviceStatus_ListedByAdmin() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Register device
        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_STATUS_001")
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""
            {
              "deviceCode": "TEST_STATUS_001",
              "androidVersion": "14",
              "sdkInt": 34,
              "manufacturer": "Google",
              "model": "Nexus",
              "imei": "777777777",
              "serial": "RF007",
              "batteryLevel": 20,
              "isCharging": false,
              "wifiEnabled": true
            }
            """)
        }
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        // Admin list devices
        val adminToken = TestAuthHelper.loginAdmin(client)
        val listResp = client.get("/api/admin/devices") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.OK, listResp.status)
        val body = listResp.bodyAsText()
        assertTrue(body.contains("TEST_STATUS_001"))
        assertTrue(body.contains("LOCKED")) // Default status khi register
        println("✅ Device status shown in admin list")
    }
}