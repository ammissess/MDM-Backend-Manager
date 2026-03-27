package com.example.mdmbackend.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditIntegrationTest {

    @Test
    fun testAdminLoginAndReadAudit() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val loginResp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResp.status)
        val token = TestJsonHelper.extractField(loginResp.bodyAsText(), "token")

        val auditResp = client.get("/api/admin/audit?limit=20&offset=0&action=LOGIN&actorType=ADMIN") {
            header("Authorization", "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val body = auditResp.bodyAsText()
        assertTrue(body.contains("LOGIN"))
        assertTrue(body.contains("ADMIN"))
    }

    @Test
    fun testLinkProfileCreateCommand_AuditShouldNotDuplicate() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_UC_SINGLE_001","name":"Audit profile","allowedApps":["com.android.settings"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "AUDIT_DEV_SINGLE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_UC_SINGLE_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val cmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, cmdResp.status)
        val commandId = TestJsonHelper.extractField(cmdResp.bodyAsText(), "id")

        val linkAudit = client.get("/api/admin/audit?limit=100&offset=0&action=LINK_PROFILE") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, linkAudit.status)
        val linkBody = linkAudit.bodyAsText()

        val createAudit = client.get("/api/admin/audit?limit=100&offset=0&action=CREATE_COMMAND") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, createAudit.status)
        val createBody = createAudit.bodyAsText()

        val linkCount = "\\\"action\\\"\\s*:\\s*\\\"LINK_PROFILE\\\"".toRegex().findAll(linkBody).count()
        val createCount = "\\\"action\\\"\\s*:\\s*\\\"CREATE_COMMAND\\\"".toRegex().findAll(createBody).count()

        assertTrue(linkCount >= 1)
        assertTrue(createCount >= 1)

        assertTrue(linkBody.contains(deviceId))
        assertTrue(createBody.contains(commandId))
    }

    @Test
    fun testLinkProfileResetUnlockCreateCommand_AuditGenerated() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_UC_001","name":"Audit profile","allowedApps":["com.android.settings"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "AUDIT_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_UC_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val resetResp = client.post("/api/admin/devices/$deviceId/reset-unlock-pass") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"newPassword":"4321"}""")
        }
        assertEquals(HttpStatusCode.OK, resetResp.status)

        val cmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, cmdResp.status)

        val auditResp = client.get("/api/admin/audit?limit=100&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val body = auditResp.bodyAsText()
        assertTrue(body.contains("LINK_PROFILE"))
        assertTrue(body.contains("RESET_UNLOCK_PASS"))
        assertTrue(body.contains("CREATE_COMMAND"))
    }

    @Test
    fun testPolicyStateSuccessAndFailed_ShouldWriteAuditActions() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "AUDIT_POLICY_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)

        val now = System.currentTimeMillis()

        val successResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "policyApplyStatus":"SUCCESS",
                  "appliedConfigVersionEpochMillis":$now,
                  "appliedConfigHash":"policy_hash_success",
                  "policyAppliedAtEpochMillis":$now
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, successResp.status)

        val failedResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "policyApplyStatus":"FAILED",
                  "policyApplyErrorCode":"POLICY_APPLY_FAILED",
                  "policyApplyError":"Apply failed"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, failedResp.status)

        val adminToken = TestAuthHelper.loginAdmin(client)

        val successAuditResp = client.get("/api/admin/audit?limit=100&offset=0&action=POLICY_APPLY_REPORTED_SUCCESS") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, successAuditResp.status)
        val successBody = successAuditResp.bodyAsText()
        assertTrue(successBody.contains("POLICY_APPLY_REPORTED_SUCCESS"))
        assertTrue(successBody.contains(deviceCode))

        val failedAuditResp = client.get("/api/admin/audit?limit=100&offset=0&action=POLICY_APPLY_REPORTED_FAILED") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, failedAuditResp.status)
        val failedBody = failedAuditResp.bodyAsText()
        assertTrue(failedBody.contains("POLICY_APPLY_REPORTED_FAILED"))
        assertTrue(failedBody.contains(deviceCode))
    }
}

private fun ApplicationTestBuilder.configureAuditTestApplication() {
    environment {
        val dbName = "audit_integration_${System.nanoTime()}"
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