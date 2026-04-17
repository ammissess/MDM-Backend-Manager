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

class ProfileContractIntegrationTest {

    @Test
    fun testProfileCreateGetUpdate_ShouldRoundTripAllHardeningFlags_AndDefaultFalseOnCreate() = testApplication {
        configureProfileContractTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val createResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode": "PROFILE_HARDEN_001",
                  "name": "Hardening profile",
                  "description": "contract round-trip",
                  "allowedApps": ["com.example.alpha"]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val createBody = createResp.bodyAsText()
        val profileId = TestJsonHelper.extractField(createBody, "id")
        assertEquals(false, TestJsonHelper.extractBooleanField(createBody, "lockPrivateDnsConfig"))
        assertEquals(false, TestJsonHelper.extractBooleanField(createBody, "lockVpnConfig"))
        assertEquals(false, TestJsonHelper.extractBooleanField(createBody, "blockDebuggingFeatures"))
        assertEquals(false, TestJsonHelper.extractBooleanField(createBody, "disableUsbDataSignaling"))
        assertEquals(false, TestJsonHelper.extractBooleanField(createBody, "disallowSafeBoot"))
        assertEquals(false, TestJsonHelper.extractBooleanField(createBody, "disallowFactoryReset"))

        val getCreatedResp = client.get("/api/admin/profiles/$profileId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, getCreatedResp.status)
        val getCreatedBody = getCreatedResp.bodyAsText()
        assertEquals(false, TestJsonHelper.extractBooleanField(getCreatedBody, "lockPrivateDnsConfig"))
        assertEquals(false, TestJsonHelper.extractBooleanField(getCreatedBody, "lockVpnConfig"))
        assertEquals(false, TestJsonHelper.extractBooleanField(getCreatedBody, "blockDebuggingFeatures"))
        assertEquals(false, TestJsonHelper.extractBooleanField(getCreatedBody, "disableUsbDataSignaling"))
        assertEquals(false, TestJsonHelper.extractBooleanField(getCreatedBody, "disallowSafeBoot"))
        assertEquals(false, TestJsonHelper.extractBooleanField(getCreatedBody, "disallowFactoryReset"))

        val updateResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "lockPrivateDnsConfig": true,
                  "lockVpnConfig": true,
                  "blockDebuggingFeatures": true,
                  "disableUsbDataSignaling": true,
                  "disallowSafeBoot": true,
                  "disallowFactoryReset": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)
        val updateBody = updateResp.bodyAsText()
        assertEquals(true, TestJsonHelper.extractBooleanField(updateBody, "lockPrivateDnsConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(updateBody, "lockVpnConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(updateBody, "blockDebuggingFeatures"))
        assertEquals(true, TestJsonHelper.extractBooleanField(updateBody, "disableUsbDataSignaling"))
        assertEquals(true, TestJsonHelper.extractBooleanField(updateBody, "disallowSafeBoot"))
        assertEquals(true, TestJsonHelper.extractBooleanField(updateBody, "disallowFactoryReset"))

        val getUpdatedResp = client.get("/api/admin/profiles/$profileId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, getUpdatedResp.status)
        val getUpdatedBody = getUpdatedResp.bodyAsText()
        assertEquals(true, TestJsonHelper.extractBooleanField(getUpdatedBody, "lockPrivateDnsConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(getUpdatedBody, "lockVpnConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(getUpdatedBody, "blockDebuggingFeatures"))
        assertEquals(true, TestJsonHelper.extractBooleanField(getUpdatedBody, "disableUsbDataSignaling"))
        assertEquals(true, TestJsonHelper.extractBooleanField(getUpdatedBody, "disallowSafeBoot"))
        assertEquals(true, TestJsonHelper.extractBooleanField(getUpdatedBody, "disallowFactoryReset"))
    }
}

private fun ApplicationTestBuilder.configureProfileContractTestApplication() {
    environment {
        val dbName = "profile_contract_integration_${System.nanoTime()}"
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
