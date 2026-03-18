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

class AuditIntegrationTest {

    @Test
    fun testAdminLoginAndReadAudit() = testApplication {
        application { module() }
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
        assertTrue(body.contains("\"action\":\"LOGIN\""))
        assertTrue(body.contains("\"actorType\":\"ADMIN\""))
    }

    @Test
    fun testLinkProfileCreateCommand_AuditShouldNotDuplicate() = testApplication {
        application { module() }
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

        assertEquals(1, linkBody.split("\"action\":\"LINK_PROFILE\"").size - 1)
        assertEquals(1, createBody.split("\"action\":\"CREATE_COMMAND\"").size - 1)

        assertTrue(linkBody.contains(deviceId))
        assertTrue(createBody.contains(commandId))
    }

    @Test
    fun testLinkProfileResetUnlockCreateCommand_AuditGenerated() = testApplication {
        application { module() }
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
}