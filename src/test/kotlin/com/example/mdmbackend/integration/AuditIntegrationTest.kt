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
import kotlin.test.assertNotEquals
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
    fun testLockResetUnlockAndCreateCommand_AuditGenerated() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)

        val deviceCode = "AUDIT_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val lockResp = client.post("/api/admin/devices/$deviceId/lock") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, lockResp.status)

        val resetResp = client.post("/api/admin/devices/$deviceId/reset-unlock-pass") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"newPassword":"4321"}""")
        }
        assertEquals(HttpStatusCode.OK, resetResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"4321"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockResp.status)

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
        assertTrue(body.contains("LOCK"))
        assertTrue(body.contains("RESET_UNLOCK_PASSWORD"))
        assertTrue(body.contains("UNLOCK"))
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

        val successAuditResp = client.get("/api/admin/audit?limit=100&offset=0&action=POLICY_APPLY_SUCCESS") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, successAuditResp.status)
        val successBody = successAuditResp.bodyAsText()
        assertTrue(successBody.contains("POLICY_APPLY_SUCCESS"))
        assertTrue(successBody.contains(deviceCode))

        val failedAuditResp = client.get("/api/admin/audit?limit=100&offset=0&action=POLICY_APPLY_FAILED") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, failedAuditResp.status)
        val failedBody = failedAuditResp.bodyAsText()
        assertTrue(failedBody.contains("POLICY_APPLY_FAILED"))
        assertTrue(failedBody.contains(deviceCode))
    }

    @Test
    fun testCommandCancelled_ShouldWriteAuditAction() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "AUDIT_CANCEL_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val createResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val commandId = TestJsonHelper.extractField(createResp.bodyAsText(), "id")

        val cancelResp = client.post("/api/admin/devices/$deviceId/commands/$commandId/cancel") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"reason":"Operator cancelled","errorCode":"ADMIN_CANCELLED"}""")
        }
        assertEquals(HttpStatusCode.OK, cancelResp.status)

        val auditResp = client.get("/api/admin/audit?action=COMMAND_CANCELLED&targetType=COMMAND&targetId=$commandId&limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val body = auditResp.bodyAsText()
        assertTrue(body.contains("COMMAND_CANCELLED"))
        assertTrue(body.contains(commandId))
        assertTrue(body.contains("Operator cancelled"))
    }

    @Test
    fun testCommandAckFailed_ShouldWriteAuditAction() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "AUDIT_ACK_FAILED_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val createResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","limit":1}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val pollBody = pollResp.bodyAsText()
        val commandId = TestJsonHelper.extractField(pollBody, "id")
        val leaseToken = TestJsonHelper.extractField(pollBody, "leaseToken")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "commandId":"$commandId",
                  "leaseToken":"$leaseToken",
                  "result":"FAILED",
                  "error":"Command execution failed",
                  "errorCode":"EXECUTION_FAILED",
                  "output":"stacktrace"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, ackResp.status)

        val auditResp = client.get("/api/admin/audit?action=COMMAND_ACK_FAILED&targetType=COMMAND&targetId=$commandId&limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val body = auditResp.bodyAsText()
        assertTrue(body.contains("COMMAND_ACK_FAILED"))
        assertTrue(body.contains(commandId))
        assertTrue(body.contains(deviceCode))
        assertTrue(body.contains("EXECUTION_FAILED"))
    }

    @Test
    fun testAdminAuditFilters_ShouldSupportTargetTimeAndPolicyLifecycleActions() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_FILTER_UC_001","name":"Audit Filter Profile","allowedApps":["com.example.app"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "AUDIT_FILTER_DEV_001"
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
            setBody("""{"userCode":"AUDIT_FILTER_UC_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

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

        val desiredUrl = "/api/admin/audit?action=POLICY_DESIRED_CHANGED&targetType=DEVICE&targetId=$deviceId&limit=50&offset=0"
        val desiredChanged = client.get(desiredUrl) {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, desiredChanged.status)
        val desiredBody = desiredChanged.bodyAsText()
        println("DAY34_AUDIT_FILTER_URL=$desiredUrl")
        println("DAY34_AUDIT_FILTER_RESPONSE=$desiredBody")
        assertTrue(desiredBody.contains("POLICY_DESIRED_CHANGED"))
        assertTrue(desiredBody.contains(deviceId))

        val refreshUrl = "/api/admin/audit?action=POLICY_REFRESH_ENQUEUED&targetType=COMMAND&limit=50&offset=0"
        val refreshEnqueued = client.get(refreshUrl) {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, refreshEnqueued.status)
        val refreshBody = refreshEnqueued.bodyAsText()
        println("DAY34_AUDIT_POLICY_REFRESH_URL=$refreshUrl")
        println("DAY34_AUDIT_POLICY_REFRESH_RESPONSE=$refreshBody")
        assertTrue(refreshBody.contains("POLICY_REFRESH_ENQUEUED"))
        assertTrue(refreshBody.contains("COMMAND"))

        val successUrl = "/api/admin/audit?action=POLICY_APPLY_SUCCESS&actorType=DEVICE&targetId=$deviceCode&limit=50&offset=0"
        val successFiltered = client.get(successUrl) {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, successFiltered.status)
        val successBody = successFiltered.bodyAsText()
        println("DAY34_AUDIT_POLICY_SUCCESS_URL=$successUrl")
        println("DAY34_AUDIT_POLICY_SUCCESS_RESPONSE=$successBody")
        assertTrue(successBody.contains("POLICY_APPLY_SUCCESS"))
        assertTrue(successBody.contains(deviceCode))

        val failedUrl = "/api/admin/audit?action=POLICY_APPLY_FAILED&actorType=DEVICE&targetId=$deviceCode&limit=50&offset=0"
        val failedFiltered = client.get(failedUrl) {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, failedFiltered.status)
        val failedBody = failedFiltered.bodyAsText()
        println("DAY34_AUDIT_POLICY_FAILED_URL=$failedUrl")
        println("DAY34_AUDIT_POLICY_FAILED_RESPONSE=$failedBody")
        assertTrue(failedBody.contains("POLICY_APPLY_FAILED"))
        assertTrue(failedBody.contains(deviceCode))

        val futureWindow = client.get("/api/admin/audit?fromEpochMillis=${now + 3_600_000}&limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, futureWindow.status)
        assertEquals(0, """\"id\"\s*:""".toRegex().findAll(futureWindow.bodyAsText()).count())

        val pagedLogin = client.get("/api/admin/audit?action=LOGIN&limit=1&offset=1") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, pagedLogin.status)
        val pagedBody = pagedLogin.bodyAsText()
        assertTrue("""\"limit\"\s*:\s*1""".toRegex().containsMatchIn(pagedBody))
        assertTrue("""\"offset\"\s*:\s*1""".toRegex().containsMatchIn(pagedBody))
    }

    @Test
    fun testProfileDesiredLifecycle_ChangedThenUnchangedUpdate_ShouldEnqueueOnceAndAudit() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode":"AUDIT_DESIRED_UC_001",
                  "name":"Desired lifecycle profile",
                  "allowedApps":["com.example.a"],
                  "disableWifi":false
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)
        val profileId = TestJsonHelper.extractField(profileResp.bodyAsText(), "id")

        val deviceCode = "AUDIT_DESIRED_DEV_001"
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
            setBody("""{"userCode":"AUDIT_DESIRED_UC_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        suspend fun refreshCount(): Int {
            val listResp = client.get("/api/admin/devices/$deviceId/commands?limit=100&offset=0") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, listResp.status)
            return "\\\"type\\\"\\s*:\\s*\\\"refresh_config\\\"".toRegex().findAll(listResp.bodyAsText()).count()
        }

        suspend fun actionCount(action: String): Int {
            val resp = client.get("/api/admin/audit?limit=500&offset=0&action=$action") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            return "\\\"action\\\"\\s*:\\s*\\\"$action\\\"".toRegex().findAll(resp.bodyAsText()).count()
        }

        val refreshBefore = refreshCount()
        val desiredAuditBefore = actionCount("POLICY_DESIRED_CHANGED")
        val refreshAuditBefore = actionCount("POLICY_REFRESH_ENQUEUED")

        val changedUpdateResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"disableWifi":true}""")
        }
        assertEquals(HttpStatusCode.OK, changedUpdateResp.status)

        val refreshAfterChanged = refreshCount()
        assertEquals(refreshBefore + 1, refreshAfterChanged)

        val desiredAuditAfterChanged = actionCount("POLICY_DESIRED_CHANGED")
        val refreshAuditAfterChanged = actionCount("POLICY_REFRESH_ENQUEUED")
        assertEquals(desiredAuditBefore + 1, desiredAuditAfterChanged)
        assertEquals(refreshAuditBefore + 1, refreshAuditAfterChanged)

        val noChangeUpdateResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"disableWifi":true}""")
        }
        assertEquals(HttpStatusCode.OK, noChangeUpdateResp.status)

        val refreshAfterNoChange = refreshCount()
        assertEquals(refreshAfterChanged, refreshAfterNoChange)
    }

    @Test
    fun testCommandExpired_ShouldWriteSingleAuditRecordOnFirstTransition() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "AUDIT_CMD_EXPIRED_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)
        val createResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":1}""")
        }
        assertEquals(HttpStatusCode.Created, createResp.status)
        val commandId = TestJsonHelper.extractField(createResp.bodyAsText(), "id")

        Thread.sleep(1500)

        // Trigger lazy-expire twice; audit must still contain a single COMMAND_EXPIRED for this command.
        repeat(2) {
            val listResp = client.get("/api/admin/devices/$deviceId/commands?limit=20&offset=0") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, listResp.status)
        }

        val auditResp = client.get("/api/admin/audit?action=COMMAND_EXPIRED&targetType=COMMAND&targetId=$commandId&limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, auditResp.status)
        val body = auditResp.bodyAsText()
        val count = "\\\"action\\\"\\s*:\\s*\\\"COMMAND_EXPIRED\\\"".toRegex().findAll(body).count()
        assertEquals(1, count)
        assertTrue(body.contains(commandId))
    }

    @Test
    fun testLinkUnlinkDesiredLifecycle_ShouldRecomputeAuditAndEnqueueRefresh() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_DESIRED_UC_002","name":"Profile link unlink","allowedApps":["com.example.app"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "AUDIT_DESIRED_DEV_002"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        suspend fun readDesiredHash(): String {
            val detailResp = client.get("/api/admin/devices/$deviceId") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, detailResp.status)
            return TestJsonHelper.extractField(detailResp.bodyAsText(), "desiredConfigHash")
        }

        suspend fun refreshCount(): Int {
            val listResp = client.get("/api/admin/devices/$deviceId/commands?limit=100&offset=0") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, listResp.status)
            return "\\\"type\\\"\\s*:\\s*\\\"refresh_config\\\"".toRegex().findAll(listResp.bodyAsText()).count()
        }

        suspend fun actionCount(action: String): Int {
            val resp = client.get("/api/admin/audit?limit=500&offset=0&action=$action") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            return "\\\"action\\\"\\s*:\\s*\\\"$action\\\"".toRegex().findAll(resp.bodyAsText()).count()
        }

        val desiredBeforeLink = readDesiredHash()
        assertTrue(desiredBeforeLink.isBlank())
        val refreshBeforeLink = refreshCount()
        val desiredAuditBeforeLink = actionCount("POLICY_DESIRED_CHANGED")
        val refreshAuditBeforeLink = actionCount("POLICY_REFRESH_ENQUEUED")

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"AUDIT_DESIRED_UC_002"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val desiredAfterLink = readDesiredHash()
        assertTrue(desiredAfterLink.isNotBlank())
        assertEquals(refreshBeforeLink + 1, refreshCount())
        assertEquals(desiredAuditBeforeLink + 1, actionCount("POLICY_DESIRED_CHANGED"))
        assertEquals(refreshAuditBeforeLink + 1, actionCount("POLICY_REFRESH_ENQUEUED"))

        val unlinkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":null}""")
        }
        assertEquals(HttpStatusCode.OK, unlinkResp.status)

        val desiredAfterUnlink = readDesiredHash()
        assertTrue(desiredAfterUnlink.isBlank())
        assertNotEquals(desiredAfterLink, desiredAfterUnlink)
        assertEquals(refreshBeforeLink + 2, refreshCount())
        assertEquals(desiredAuditBeforeLink + 2, actionCount("POLICY_DESIRED_CHANGED"))
        assertEquals(refreshAuditBeforeLink + 2, actionCount("POLICY_REFRESH_ENQUEUED"))
    }

    @Test
    fun testTelemetrySummary_ShouldCountPolicyApplyFailedFromAuditLifecycle() = testApplication {
        configureAuditTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "AUDIT_SUMMARY_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val now = System.currentTimeMillis()
        repeat(2) {
            val failedResp = client.post("/api/device/policy-state") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $deviceToken")
                setBody(
                    """
                    {
                      "deviceCode":"$deviceCode",
                      "policyApplyStatus":"FAILED",
                      "policyApplyErrorCode":"POLICY_APPLY_FAILED",
                      "policyApplyError":"apply failed $it",
                      "policyAppliedAtEpochMillis":$now
                    }
                    """.trimIndent()
                )
            }
            assertEquals(HttpStatusCode.OK, failedResp.status)
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val summaryResp = client.get("/api/admin/devices/$deviceId/telemetry/summary") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, summaryResp.status)
        val body = summaryResp.bodyAsText()

        val failed24h = TestJsonHelper.extractNumberField(body, "policyApplyFailed24h") ?: -1
        val failed7d = TestJsonHelper.extractNumberField(body, "policyApplyFailed7d") ?: -1
        assertEquals(2, failed24h)
        assertEquals(2, failed7d)
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
