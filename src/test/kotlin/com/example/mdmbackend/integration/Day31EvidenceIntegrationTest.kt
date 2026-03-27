package com.example.mdmbackend.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
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
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Day31EvidenceIntegrationTest {

    @Test
    fun testPrintRawProfileAndConfigCurrentResponses() = testApplication {
        configureEvidenceTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "EVIDENCE_DAY31_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode":"EVIDENCE_DAY31_USER_001",
                  "name":"Evidence Day3-1",
                  "description":"evidence",
                  "allowedApps":["com.example.alpha","com.example.beta"],
                  "disableWifi":true,
                  "disableBluetooth":false,
                  "disableCamera":true,
                  "disableStatusBar":true,
                  "kioskMode":true,
                  "blockUninstall":true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)
        val profileBody = profileResp.bodyAsText()
        println("PROFILE_RESPONSE_RAW=$profileBody")
        assertFalse(profileBody.contains("\"showWifi\""))
        assertFalse(profileBody.contains("\"showBluetooth\""))

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "androidVersion":"14",
                  "sdkInt":34,
                  "manufacturer":"Evidence",
                  "model":"Device"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"EVIDENCE_DAY31_USER_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"1111"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockResp.status)

        val configResp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.OK, configResp.status)
        val configBody = configResp.bodyAsText()
        println("CONFIG_CURRENT_RESPONSE_RAW=$configBody")
        assertFalse(configBody.contains("\"showWifi\""))
        assertFalse(configBody.contains("\"showBluetooth\""))
    }
}

private fun ApplicationTestBuilder.configureEvidenceTestApplication() {
    environment {
        val dbName = "day31_evidence_${System.nanoTime()}"
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

