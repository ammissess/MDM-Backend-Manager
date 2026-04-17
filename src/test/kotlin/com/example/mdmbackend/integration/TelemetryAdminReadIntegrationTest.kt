package com.example.mdmbackend.integration

import com.example.mdmbackend.module
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TelemetryAdminReadIntegrationTest {

    @Test
    fun testExtendedInventorySnapshot_AdminDetailShouldExposeNewFields_NullableFirstSafe() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }
        val evidenceFile = java.io.File("build/reports/day5-3-direct-evidence.log").apply {
            parentFile.mkdirs()
        }
        fun evidence(line: String) {
            evidenceFile.appendText(line + "\n")
        }

        val deviceCode = "DAY53_INV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)

        // Nullable-first: detail must still be readable before Android sends extended inventory fields.
        val detailBeforeResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailBeforeResp.status)
        val detailBeforeBody = detailBeforeResp.bodyAsText()
        println("DAY53_ADMIN_DETAIL_BEFORE_URL=/api/admin/devices/$deviceId")
        println("DAY53_ADMIN_DETAIL_BEFORE=$detailBeforeBody")
        evidence("DAY53_ADMIN_DETAIL_BEFORE_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_ADMIN_DETAIL_BEFORE=$detailBeforeBody")

        val now = System.currentTimeMillis()
        val stateResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":$now,
                  "agentVersion":"1.2.3",
                  "agentBuildCode":123,
                  "currentLauncherPackage":"com.example.launcher",
                  "uptimeMs":456789,
                  "abi":"arm64-v8a",
                  "buildFingerprint":"google/panther/panther:14/UP1A/test:user/release-keys"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, stateResp.status)

        val detailAfterResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailAfterResp.status)
        val body = detailAfterResp.bodyAsText()
        println("DAY53_ADMIN_DETAIL_AFTER_URL=/api/admin/devices/$deviceId")
        println("DAY53_ADMIN_DETAIL_AFTER=$body")
        evidence("DAY53_ADMIN_DETAIL_AFTER_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_ADMIN_DETAIL_AFTER=$body")
        assertTrue(body.contains("agentVersion"))
        assertTrue(body.contains("1.2.3"))
        assertTrue(body.contains("agentBuildCode"))
        assertTrue(body.contains("123"))
        assertTrue(body.contains("currentLauncherPackage"))
        assertTrue(body.contains("com.example.launcher"))
        assertTrue(body.contains("uptimeMs"))
        assertTrue(body.contains("456789"))
        assertTrue(body.contains("abi"))
        assertTrue(body.contains("arm64-v8a"))
        assertTrue(body.contains("buildFingerprint"))
        assertTrue(body.contains("google/panther/panther:14/UP1A/test:user/release-keys"))
    }

    @Test
    fun testExtendedInventorySnapshot_AdminDetailShouldExposeInventoryAndPollAckTogether() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }
        val evidenceFile = java.io.File("build/reports/day5-3-direct-evidence.log").apply {
            parentFile.mkdirs()
        }
        fun evidence(line: String) {
            evidenceFile.appendText(line + "\n")
        }

        val deviceCode = "DAY53_INV_FULL_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)

        val now = System.currentTimeMillis()
        val stateResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":$now,
                  "agentVersion":"1.2.3",
                  "agentBuildCode":123,
                  "currentLauncherPackage":"com.example.launcher",
                  "uptimeMs":456789,
                  "abi":"arm64-v8a",
                  "buildFingerprint":"google/panther/panther:14/UP1A/test:user/release-keys"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, stateResp.status)

        val createCmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, createCmdResp.status)
        val commandId = TestJsonHelper.extractField(createCmdResp.bodyAsText(), "id")

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            header("X-Forwarded-For", "203.0.113.9")
            setBody("""{"deviceCode":"$deviceCode","limit":1}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val leaseToken = TestJsonHelper.extractField(pollResp.bodyAsText(), "leaseToken")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            header("X-Forwarded-For", "198.51.100.10")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "commandId":"$commandId",
                  "leaseToken":"$leaseToken",
                  "result":"SUCCESS"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, ackResp.status)

        val detailResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailResp.status)
        val detailBody = detailResp.bodyAsText()
        println("DAY53_ADMIN_DETAIL_FULL_URL=/api/admin/devices/$deviceId")
        println("DAY53_ADMIN_DETAIL_FULL=$detailBody")
        evidence("DAY53_ADMIN_DETAIL_FULL_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_ADMIN_DETAIL_FULL=$detailBody")

        assertTrue(detailBody.contains("agentVersion"))
        assertTrue(detailBody.contains("agentBuildCode"))
        assertTrue(detailBody.contains("ipAddress"))
        assertTrue(detailBody.contains("currentLauncherPackage"))
        assertTrue(detailBody.contains("uptimeMs"))
        assertTrue(detailBody.contains("abi"))
        assertTrue(detailBody.contains("buildFingerprint"))
        assertTrue(detailBody.contains("lastPollAtEpochMillis"))
        assertTrue(detailBody.contains("lastCommandAckAtEpochMillis"))
    }

    @Test
    fun testStateSnapshotAndPolicyState_AdminCanReadDetail() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_STATE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","androidVersion":"14","sdkInt":34}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val now = System.currentTimeMillis()
        val stateResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":$now,
                  "batteryLevel":88,
                  "isCharging":true,
                  "wifiEnabled":true,
                  "networkType":"WIFI",
                  "foregroundPackage":"com.android.settings",
                  "isDeviceOwner":true,
                  "isLauncherDefault":true,
                  "isKioskRunning":true,
                  "storageFreeBytes":123456,
                  "storageTotalBytes":987654,
                  "ramFreeMb":512,
                  "ramTotalMb":2048,
                  "lastBootAtEpochMillis":${now - 3600000}
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, stateResp.status)

        val policyResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "desiredConfigVersionEpochMillis":$now,
                  "desiredConfigHash":"desired_hash_001",
                  "appliedConfigVersionEpochMillis":$now,
                  "appliedConfigHash":"applied_hash_001",
                  "policyApplyStatus":"SUCCESS",
                  "policyApplyError":null,
                  "policyApplyErrorCode":null,
                  "policyAppliedAtEpochMillis":$now
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, policyResp.status)

        val adminToken = TestAuthHelper.loginAdmin(client)
        val detailResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailResp.status)

        val body = detailResp.bodyAsText()
        assertTrue(body.contains("healthSummary"))
        assertTrue(body.contains("complianceSummary"))
        assertTrue(body.contains("networkType"))
        assertTrue(body.contains("WIFI"))
        assertTrue(body.contains("policyApplyStatus"))
        assertTrue(body.contains("SUCCESS"))
        assertTrue(body.contains("lastSeenAtEpochMillis"))
        assertTrue(body.contains("appliedConfigHash"))
        assertTrue(!body.contains("desired_hash_001"))
        assertTrue(body.contains("applied_hash_001"))
    }

    @Test
    fun testDetailComplianceSummary_ShouldBeTrueWhenDesiredEqualsAppliedAndPolicySuccess() = testApplication {
        configureTelemetryTestApplication()
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
                  "userCode":"DAY3_3_USER_001",
                  "name":"Day3-3 Profile",
                  "allowedApps":["com.example.app"]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "DAY3_3_DEV_001"
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
            setBody("""{"userCode":"DAY3_3_USER_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val detailAfterLinkResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailAfterLinkResp.status)
        val desiredHash = TestJsonHelper.extractField(detailAfterLinkResp.bodyAsText(), "desiredConfigHash")
        assertTrue(desiredHash.isNotBlank())

        val now = System.currentTimeMillis()
        val policyResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "appliedConfigVersionEpochMillis":$now,
                  "appliedConfigHash":"$desiredHash",
                  "policyApplyStatus":"SUCCESS",
                  "policyAppliedAtEpochMillis":$now
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, policyResp.status)

        val detailResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailResp.status)

        val body = detailResp.bodyAsText()
        assertTrue(body.contains("healthSummary"))
        assertTrue(body.contains("complianceSummary"))
        val isCompliant = "\"isCompliant\"\\s*:\\s*(true|false)".toRegex()
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toBooleanStrictOrNull()
        assertEquals(true, isCompliant)
        assertTrue(body.contains("desiredConfigHash"))
        assertTrue(body.contains("appliedConfigHash"))
        assertTrue(body.contains("policyApplyStatus"))
    }

    @Test
    fun testStateValidationRules_ShouldRejectInvalidInputsAndAcceptValidRequest() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_STATE_VALIDATE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","androidVersion":"14","sdkInt":34}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)

        val now = System.currentTimeMillis()

        fun stateBody(extraFields: String, reportedAtEpochMillis: Long = now): String =
            """
            {
              "deviceCode":"$deviceCode",
              "reportedAtEpochMillis":$reportedAtEpochMillis,
              $extraFields
            }
            """.trimIndent()

        val batteryBelowZeroResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(stateBody("\"batteryLevel\":-1"))
        }
        assertEquals(HttpStatusCode.BadRequest, batteryBelowZeroResp.status)
        assertTrue(batteryBelowZeroResp.bodyAsText().contains("INVALID_BATTERY_LEVEL"))

        val batteryAboveHundredResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(stateBody("\"batteryLevel\":101"))
        }
        assertEquals(HttpStatusCode.BadRequest, batteryAboveHundredResp.status)
        assertTrue(batteryAboveHundredResp.bodyAsText().contains("INVALID_BATTERY_LEVEL"))

        val storageInvalidResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(stateBody("\"storageFreeBytes\":999,\"storageTotalBytes\":100"))
        }
        assertEquals(HttpStatusCode.BadRequest, storageInvalidResp.status)
        assertTrue(storageInvalidResp.bodyAsText().contains("INVALID_STORAGE_VALUES"))

        val ramInvalidResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(stateBody("\"ramFreeMb\":4096,\"ramTotalMb\":1024"))
        }
        assertEquals(HttpStatusCode.BadRequest, ramInvalidResp.status)
        assertTrue(ramInvalidResp.bodyAsText().contains("INVALID_RAM_VALUES"))

        val networkInvalidResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(stateBody("\"networkType\":\"SATELLITE\""))
        }
        assertEquals(HttpStatusCode.BadRequest, networkInvalidResp.status)
        assertTrue(networkInvalidResp.bodyAsText().contains("INVALID_NETWORK_TYPE"))

        val reportedAtTooFarFutureResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(stateBody("\"batteryLevel\":80", reportedAtEpochMillis = now + (6 * 60 * 1000)))
        }
        assertEquals(HttpStatusCode.BadRequest, reportedAtTooFarFutureResp.status)
        assertTrue(reportedAtTooFarFutureResp.bodyAsText().contains("INVALID_REPORTED_AT"))

        val validResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                stateBody(
                    """
                    "batteryLevel":50,
                    "storageFreeBytes":100,
                    "storageTotalBytes":1000,
                    "ramFreeMb":256,
                    "ramTotalMb":1024,
                    "networkType":"WIFI",
                    "lastBootAtEpochMillis":${now - 60000}
                    """.trimIndent()
                )
            )
        }
        assertEquals(HttpStatusCode.OK, validResp.status)
    }

    @Test
    fun testDeviceCodeMismatchForStateAndPolicy_ShouldReturn409() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "MISMATCH_DEV_A")
        val now = System.currentTimeMillis()

        val stateResp = client.post("/api/device/state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"MISMATCH_DEV_B","reportedAtEpochMillis":$now}""")
        }
        assertEquals(HttpStatusCode.Conflict, stateResp.status)
        assertTrue(stateResp.bodyAsText().contains("DEVICE_CODE_MISMATCH"))

        val policyResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"MISMATCH_DEV_B","policyApplyStatus":"SUCCESS"}""")
        }
        assertEquals(HttpStatusCode.Conflict, policyResp.status)
        assertTrue(policyResp.bodyAsText().contains("DEVICE_CODE_MISMATCH"))
    }

    @Test
    fun testStructuredEventSavedAndReturnedToAdmin() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_EVT_STRUCT_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val eventResp = client.post("/api/device/$deviceCode/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "type":"policy_apply",
                  "category":"POLICY",
                  "severity":"ERROR",
                  "payload":"{\"step\":\"setLockTaskPackages\"}",
                  "errorCode":"POLICY_APPLY_FAILED",
                  "message":"Failed to apply lock task packages"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, eventResp.status)

        val adminToken = TestAuthHelper.loginAdmin(client)
        val listResp = client.get("/api/admin/devices/$deviceId/events?limit=10") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listResp.status)

        val body = listResp.bodyAsText()
        assertTrue(body.contains("POLICY"))
        assertTrue(body.contains("ERROR"))
        assertTrue(body.contains("POLICY_APPLY_FAILED"))
        assertTrue(body.contains("Failed to apply lock task packages"))
    }

    @Test
    fun testStructuredEvents_AdminCanFilterByCategorySeverityTypeErrorAndTime() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_EVT_FILTER_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val oldEventResp = client.post("/api/device/$deviceCode/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "type":"policy_apply",
                  "category":"POLICY",
                  "severity":"ERROR",
                  "payload":"{}",
                  "errorCode":"POLICY_OLD",
                  "message":"older policy error"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, oldEventResp.status)
        Thread.sleep(5)

        val newestEventResp = client.post("/api/device/$deviceCode/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "type":"policy_apply",
                  "category":"POLICY",
                  "severity":"ERROR",
                  "payload":"{}",
                  "errorCode":"POLICY_NEW",
                  "message":"newest policy error"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, newestEventResp.status)

        val adminToken = TestAuthHelper.loginAdmin(client)

        val newestOnlyResp = client.get("/api/admin/devices/$deviceId/events?limit=1") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, newestOnlyResp.status)
        val newestOnlyBody = newestOnlyResp.bodyAsText()
        assertTrue(newestOnlyBody.contains("newest policy error"))
        assertFalse(newestOnlyBody.contains("older policy error"))

        val now = System.currentTimeMillis()
        val filteredUrl = "/api/admin/devices/$deviceId/events" +
            "?category=policy&severity=error&type=policy_apply&errorCode=POLICY_NEW" +
            "&fromEpochMillis=${now - 60_000}&toEpochMillis=${now + 60_000}&limit=10"
        val filteredResp = client.get(filteredUrl) {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, filteredResp.status)
        val filteredBody = filteredResp.bodyAsText()
        println("DAY34_EVENTS_FILTER_URL=$filteredUrl")
        println("DAY34_EVENTS_FILTER_RESPONSE=$filteredBody")
        assertTrue(filteredBody.contains("POLICY_NEW"))
        assertTrue(filteredBody.contains("newest policy error"))
        assertFalse(filteredBody.contains("POLICY_OLD"))

        val futureWindowResp = client.get(
            "/api/admin/devices/$deviceId/events?fromEpochMillis=${now + 3_600_000}&limit=10"
        ) {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, futureWindowResp.status)
        val futureWindowBody = futureWindowResp.bodyAsText()
        assertEquals(0, """\"id\"\s*:""".toRegex().findAll(futureWindowBody).count())
    }

    @Test
    fun testPolicyStateValidationRules_ShouldEnforceContract() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_POLICY_VALIDATE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","androidVersion":"14","sdkInt":34}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)

        val now = System.currentTimeMillis()

        fun policyBody(extraFields: String): String =
            """
            {
              "deviceCode":"$deviceCode",
              $extraFields
            }
            """.trimIndent()

        val invalidStatusResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(policyBody("\"policyApplyStatus\":\"DONE\""))
        }
        assertEquals(HttpStatusCode.BadRequest, invalidStatusResp.status)
        assertTrue(invalidStatusResp.bodyAsText().contains("INVALID_POLICY_STATUS"))

        val successMissingAppliedResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(policyBody("\"policyApplyStatus\":\"SUCCESS\""))
        }
        assertEquals(HttpStatusCode.BadRequest, successMissingAppliedResp.status)
        assertTrue(successMissingAppliedResp.bodyAsText().contains("MISSING_APPLIED_CONFIG"))

        val failedMissingErrorResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                policyBody(
                    """
                    "policyApplyStatus":"FAILED",
                    "appliedConfigVersionEpochMillis":$now,
                    "appliedConfigHash":"hash_failed_no_reason"
                    """.trimIndent()
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, failedMissingErrorResp.status)
        assertTrue(failedMissingErrorResp.bodyAsText().contains("MISSING_POLICY_ERROR"))

        val successValidResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                policyBody(
                    """
                    "policyApplyStatus":"SUCCESS",
                    "appliedConfigVersionEpochMillis":$now,
                    "appliedConfigHash":"applied_hash_ok",
                    "policyAppliedAtEpochMillis":$now
                    """.trimIndent()
                )
            )
        }
        assertEquals(HttpStatusCode.OK, successValidResp.status)

        val failedValidResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                policyBody(
                    """
                    "policyApplyStatus":"FAILED",
                    "policyApplyErrorCode":"POLICY_APPLY_FAILED",
                    "policyApplyError":"Agent failed to apply policy",
                    "policyAppliedAtEpochMillis":$now
                    """.trimIndent()
                )
            )
        }
        assertEquals(HttpStatusCode.OK, failedValidResp.status)
    }

    @Test
    fun testDesiredConfigOwnership_ReorderShouldKeepHash_UpdateShouldChangeHash() = testApplication {
        configureTelemetryTestApplication()
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
                  "userCode":"DESIRED_USER_001",
                  "name":"Desired profile",
                  "allowedApps":["com.example.z","com.example.a"],
                  "disableWifi":false,
                  "disableBluetooth":false,
                  "disableCamera":false,
                  "disableStatusBar":true,
                  "kioskMode":true,
                  "blockUninstall":true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)
        val profileBody = profileResp.bodyAsText()
        assertFalse(profileBody.contains("\"showWifi\""))
        assertFalse(profileBody.contains("\"showBluetooth\""))
        val profileId = TestJsonHelper.extractField(profileBody, "id")

        val deviceCode = "DESIRED_DEV_001"
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
            setBody("""{"userCode":"DESIRED_USER_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        suspend fun readDesired(deviceId: String): Pair<String, Long?> {
            val detailResp = client.get("/api/admin/devices/$deviceId") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, detailResp.status)
            val body = detailResp.bodyAsText()
            return TestJsonHelper.extractField(body, "desiredConfigHash") to
                TestJsonHelper.extractNumberField(body, "desiredConfigVersionEpochMillis")
        }

        val (initialHash, initialVersion) = readDesired(deviceId)
        assertTrue(initialHash.isNotBlank())
        assertTrue(initialVersion != null)

        val reorderResp = client.put("/api/admin/profiles/$profileId/allowed-apps") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""["com.example.a","com.example.z"]""")
        }
        assertEquals(HttpStatusCode.OK, reorderResp.status)

        val (reorderedHash, reorderedVersion) = readDesired(deviceId)
        assertEquals(initialHash, reorderedHash)
        assertEquals(initialVersion, reorderedVersion)

        val updateResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"disableWifi":true}""")
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)

        val (updatedHash, updatedVersion) = readDesired(deviceId)
        assertNotEquals(reorderedHash, updatedHash)
        assertTrue(updatedVersion != null)
        assertTrue(reorderedVersion == null || updatedVersion >= reorderedVersion)
    }

    @Test
    fun testDesiredConfigOwnership_LinkAndUnlinkShouldSetAndClearDesired() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"DESIRED_USER_002","name":"Profile 2","allowedApps":["com.example.app"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "DESIRED_DEV_002"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        suspend fun desiredFromDetail(): Pair<String, Long?> {
            val detailResp = client.get("/api/admin/devices/$deviceId") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, detailResp.status)
            val body = detailResp.bodyAsText()
            return TestJsonHelper.extractField(body, "desiredConfigHash") to
                TestJsonHelper.extractNumberField(body, "desiredConfigVersionEpochMillis")
        }

        val (beforeLinkHash, beforeLinkVersion) = desiredFromDetail()
        assertTrue(beforeLinkHash.isBlank())
        assertEquals(null, beforeLinkVersion)

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"DESIRED_USER_002"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val (linkedHash, linkedVersion) = desiredFromDetail()
        assertTrue(linkedHash.isNotBlank())
        assertTrue(linkedVersion != null)

        val unlinkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":null}""")
        }
        assertEquals(HttpStatusCode.OK, unlinkResp.status)

        val (unlinkedHash, unlinkedVersion) = desiredFromDetail()
        assertTrue(unlinkedHash.isBlank())
        assertEquals(null, unlinkedVersion)
    }

    @Test
    fun testPolicyState_ShouldNotOverrideBackendOwnedDesired() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"DESIRED_USER_003","name":"Profile 3","allowedApps":["com.example.app"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val deviceCode = "DESIRED_DEV_003"
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
            setBody("""{"userCode":"DESIRED_USER_003"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        suspend fun readDesired(deviceId: String): Pair<String, Long?> {
            val detailResp = client.get("/api/admin/devices/$deviceId") {
                header("Authorization", "Bearer $adminToken")
            }
            assertEquals(HttpStatusCode.OK, detailResp.status)
            val body = detailResp.bodyAsText()
            return TestJsonHelper.extractField(body, "desiredConfigHash") to
                TestJsonHelper.extractNumberField(body, "desiredConfigVersionEpochMillis")
        }

        val (baselineHash, baselineVersion) = readDesired(deviceId)
        assertTrue(baselineHash.isNotBlank())
        assertTrue(baselineVersion != null)

        val now = System.currentTimeMillis()
        val policyResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "desiredConfigVersionEpochMillis":1,
                  "desiredConfigHash":"agent_owned_should_be_ignored",
                  "appliedConfigVersionEpochMillis":$now,
                  "appliedConfigHash":"applied_hash",
                  "policyApplyStatus":"SUCCESS",
                  "policyAppliedAtEpochMillis":$now
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, policyResp.status)

        val (afterPolicyHash, afterPolicyVersion) = readDesired(deviceId)
        assertEquals(baselineHash, afterPolicyHash)
        assertEquals(baselineVersion, afterPolicyVersion)
    }

    @Test
    fun testTelemetrySummaryEndpoint_ShouldReturnEventAggregatesAndPolicyFailedCounts() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TELEMETRY_SUMMARY_DEV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        client.post("/api/device/$deviceCode/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"type":"policy_apply","category":"POLICY","severity":"ERROR","payload":"{}","errorCode":"POLICY_APPLY_FAILED"}""")
        }
        client.post("/api/device/$deviceCode/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"type":"heartbeat","category":"SYSTEM","severity":"INFO","payload":"{}"}""")
        }

        val now = System.currentTimeMillis()
        val failedPolicyResp = client.post("/api/device/policy-state") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "policyApplyStatus":"FAILED",
                  "policyApplyErrorCode":"POLICY_APPLY_FAILED",
                  "policyApplyError":"apply failed",
                  "policyAppliedAtEpochMillis":$now
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, failedPolicyResp.status)

        val adminToken = TestAuthHelper.loginAdmin(client)
        val summaryResp = client.get("/api/admin/devices/$deviceId/telemetry/summary") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, summaryResp.status)

        val body = summaryResp.bodyAsText()
        println("DAY52_TELEMETRY_SUMMARY_URL=/api/admin/devices/$deviceId/telemetry/summary")
        println("DAY52_TELEMETRY_SUMMARY_RESPONSE=$body")
        assertTrue(body.contains("eventCountByType"))
        assertTrue(body.contains("eventCountByCategory"))
        assertTrue(body.contains("eventCountBySeverity"))
        assertTrue(body.contains("policy_apply"))
        assertTrue(body.contains("POLICY"))
        assertTrue(body.contains("ERROR"))

        val failed24h = TestJsonHelper.extractNumberField(body, "policyApplyFailed24h") ?: -1
        val failed7d = TestJsonHelper.extractNumberField(body, "policyApplyFailed7d") ?: -1
        assertEquals(1, failed24h)
        assertEquals(1, failed7d)
    }

    @Test
    fun testAdminDetail_ShouldExposeSafeFcmTransportHealthWithoutRawToken() = testApplication {
        configureTelemetryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "FCM_OBS_001"
        val rawToken = "raw-fcm-token-should-not-leak"
        val updatedAt = System.currentTimeMillis()

        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val upsertResp = client.post("/api/device/fcm-token") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "fcmToken":"$rawToken",
                  "appVersion":"1.0.0",
                  "updatedAtEpochMillis":$updatedAt
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, upsertResp.status)

        val adminToken = TestAuthHelper.loginAdmin(client)
        val createCommandResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, createCommandResp.status)

        val detailResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailResp.status)

        val body = detailResp.bodyAsText()
        assertTrue(body.contains("hasFcmToken"))
        assertTrue(body.contains("fcmTokenUpdatedAtEpochMillis"))
        assertTrue(body.contains("lastWakeupAttemptAtEpochMillis"))
        assertTrue(body.contains("lastWakeupReason"))
        assertTrue(body.contains("command_created:pending_command"))
        assertTrue(body.contains("lastWakeupResult"))
        assertTrue(body.contains("disabled"))
        assertFalse(body.contains(rawToken))
    }
}

private fun ApplicationTestBuilder.configureTelemetryTestApplication() {
    environment {
        val dbName = "telemetry_admin_read_${System.nanoTime()}"
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
