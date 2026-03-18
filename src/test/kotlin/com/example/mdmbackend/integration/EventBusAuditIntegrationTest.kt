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

class EventBusAuditIntegrationTest {

    @Test
    fun testCommandCreatedEvent_ProducesSingleAuditLog() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "TEST_EVTBUS_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val reg = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, reg.status)
        val deviceId = TestJsonHelper.extractField(reg.bodyAsText(), "deviceId")

        val create = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":120}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)

        val audit = client.get("/api/admin/audit?action=CREATE_COMMAND&limit=20&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, audit.status)

        val body = audit.bodyAsText()
        assertTrue(body.contains("CREATE_COMMAND"))
        assertTrue(body.contains(deviceId))
        assertEquals(1, body.split("\"action\":\"CREATE_COMMAND\"").size - 1)
    }

    @Test
    fun testAuditSubscriberNotDuplicatedAcrossMultipleAppStartups() {
        repeat(3) {
            testApplication {
                application { module() }
                val client = createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
                }

                val adminToken = TestAuthHelper.loginAdmin(client)
                val deviceCode = "TEST_EVTBUS_MULTI_$it"
                val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

                val reg = client.post("/api/device/register") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $deviceToken")
                    setBody("""{"deviceCode":"$deviceCode"}""")
                }
                assertEquals(HttpStatusCode.OK, reg.status)
                val deviceId = TestJsonHelper.extractField(reg.bodyAsText(), "deviceId")

                val create = client.post("/api/admin/devices/$deviceId/commands") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $adminToken")
                    setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":120}""")
                }
                assertEquals(HttpStatusCode.Created, create.status)

                val audit = client.get("/api/admin/audit?action=CREATE_COMMAND&limit=50&offset=0") {
                    header("Authorization", "Bearer $adminToken")
                }
                assertEquals(HttpStatusCode.OK, audit.status)
                val body = audit.bodyAsText()

                assertTrue(body.contains(deviceId))
                assertEquals(1, body.split(deviceId).size - 1)
            }
        }
    }
}