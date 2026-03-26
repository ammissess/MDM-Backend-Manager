package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryAdminReadIntegrationTest {

    @Test
    fun testStateSnapshotAndPolicyState_AdminCanReadDetail() = testApplication {
        application { module() }
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
        assertTrue(body.contains("networkType"))
        assertTrue(body.contains("WIFI"))
        assertTrue(body.contains("policyApplyStatus"))
        assertTrue(body.contains("SUCCESS"))
        assertTrue(body.contains("desired_hash_001"))
        assertTrue(body.contains("applied_hash_001"))
    }

    @Test
    fun testDeviceCodeMismatchForStateAndPolicy_ShouldReturn409() = testApplication {
        application { module() }
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
        application { module() }
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
}