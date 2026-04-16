package com.example.mdmbackend.integration
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventBusAuditIntegrationTest {

    @Test
    fun testCommandCreatedEvent_ProducesSingleAuditLog() = testApplication {
        configureEventBusAuditTestApplication()
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
        assertEquals(1, "\"action\"\\s*:\\s*\"CREATE_COMMAND\"".toRegex().findAll(body).count())
    }

    @Test
    fun testAuditSubscriberNotDuplicatedAcrossMultipleAppStartups() {
        repeat(3) {
            testApplication {
                configureEventBusAuditTestApplication()
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

private fun ApplicationTestBuilder.configureEventBusAuditTestApplication() {
    environment {
        val dbName = "event_bus_audit_${System.nanoTime()}"
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
