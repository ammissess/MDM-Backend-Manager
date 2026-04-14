package com.example.mdmbackend.integration
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceSessionGuardIntegrationTest {

    @Test
    fun testRegister_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_REG_A")
        val resp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""{"deviceCode":"GUARD_REG_B"}""")
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testUnlock_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_UNLOCK_A")
        val resp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""{"deviceCode":"GUARD_UNLOCK_B","password":"1111"}""")
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testLocation_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_LOC_A")
        val resp = client.post("/api/device/location") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""{"deviceCode":"GUARD_LOC_B","latitude":1.0,"longitude":2.0,"accuracyMeters":3.0}""")
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testUsage_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_USAGE_A")
        val now = System.currentTimeMillis()

        val resp = client.post("/api/device/usage") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                """
                {
                  "deviceCode":"GUARD_USAGE_B",
                  "packageName":"com.android.chrome",
                  "startedAtEpochMillis":${now - 10000},
                  "endedAtEpochMillis":$now,
                  "durationMs":10000
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testUsageBatch_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_BATCH_A")
        val now = System.currentTimeMillis()

        val resp = client.post("/api/device/usage/batch") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody(
                """
                {
                  "deviceCode":"GUARD_BATCH_B",
                  "items":[
                    {
                      "packageName":"com.android.chrome",
                      "startedAtEpochMillis":${now - 10000},
                      "endedAtEpochMillis":$now,
                      "durationMs":10000
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testEvents_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_EVT_A")
        val resp = client.post("/api/device/GUARD_EVT_B/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            setBody("""{"type":"app_crash","payload":"{}"}""")
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testConfigCurrent_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_CFG_A")
        val resp = client.get("/api/device/config/current?deviceCode=GUARD_CFG_B") {
            header("Authorization", "Bearer $token")
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testDeprecatedConfigRoute_DeviceCodeMismatch_ShouldReturn409() = testApplication {
        configureDeviceSessionGuardTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val token = TestAuthHelper.loginDevice(client, "GUARD_OLDCFG_A")
        val resp = client.get("/api/device/config/ANY_USER?deviceCode=GUARD_OLDCFG_B") {
            header("Authorization", "Bearer $token")
        }

        assertEquals(HttpStatusCode.Conflict, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
        assertTrue(body.contains("DEVICE_CODE_MISMATCH"))
    }
}

private fun ApplicationTestBuilder.configureDeviceSessionGuardTestApplication() {
    environment {
        val dbName = "device_session_guard_${System.nanoTime()}"
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
