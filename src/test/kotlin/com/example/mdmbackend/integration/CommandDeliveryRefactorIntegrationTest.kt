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

class CommandDeliveryRefactorIntegrationTest {

    @Test
    fun testPollBehaviorUnchanged_AfterDeliveryAbstraction() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_DELIVERY_001"

        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)
        val cmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, cmdResp.status)

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","limit":5}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val body = pollResp.bodyAsText()
        assertTrue(body.contains("lock_screen"))
        assertTrue(body.contains("leaseToken"))
    }
}