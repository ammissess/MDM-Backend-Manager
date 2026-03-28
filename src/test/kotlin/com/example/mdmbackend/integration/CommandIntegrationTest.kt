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

class CommandIntegrationTest {

    @Test
    fun testCommandFlow_CreatePollAck() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${TestAuthHelper.loginDevice(client, "TEST_CMD_001")}")
            setBody(
                """
                {
                  "deviceCode": "TEST_CMD_001",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Google",
                  "model": "Pixel 7",
                  "imei": "123456789",
                  "serial": "R5CRC",
                  "batteryLevel": 85,
                  "isCharging": false,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)

        val createCmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "type": "lock_screen",
                  "payload": "{\"duration_seconds\":60}",
                  "ttlSeconds": 600
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, createCmdResp.status)
        val createCmdBody = createCmdResp.bodyAsText()
        assertTrue(hasCommandType(createCmdBody, "lock_screen"))
        val commandId = TestJsonHelper.extractField(createCmdBody, "id")

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_001")
        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"TEST_CMD_001","limit":5}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val pollBody = pollResp.bodyAsText()
        assertTrue(pollBody.contains("lock_screen"))
        val leaseToken = TestJsonHelper.extractField(pollBody, "leaseToken")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "TEST_CMD_001",
                  "commandId": "$commandId",
                  "leaseToken": "$leaseToken",
                  "result": "SUCCESS",
                  "output": "Screen locked"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, ackResp.status)
        assertTrue(ackResp.bodyAsText().contains("SUCCESS"))

        val listResp = client.get("/api/admin/devices/$deviceId/commands?status=SUCCESS") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listResp.status)
        val listBody = listResp.bodyAsText()
        assertTrue(listBody.contains("SUCCESS"))
        assertTrue(listBody.contains(commandId))
    }

    @Test
    fun testCreateCommandWithUppercaseType_ShouldNormalizeAndPass() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CMD_NORM_001"
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
            setBody("""{"type":"LOCK_SCREEN","payload":"{}","ttlSeconds":600}""")
        }

        assertEquals(HttpStatusCode.Created, createResp.status)
        assertTrue(hasCommandType(createResp.bodyAsText(), "lock_screen"))

        // Backward-compatible input: legacy hyphen form should still map to canonical wire value.
        val createLegacyResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"LOCK-SCREEN","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, createLegacyResp.status)
        assertTrue(hasCommandType(createLegacyResp.bodyAsText(), "lock_screen"))
    }

    @Test
    fun testCreateCommandWithInvalidType_ShouldReturn400() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CMD_INVALID_TYPE_001"
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
            setBody("""{"type":"unknown_type","payload":"{}","ttlSeconds":600}""")
        }

        assertEquals(HttpStatusCode.BadRequest, createResp.status)
        val body = createResp.bodyAsText()
        assertTrue(body.contains("INVALID_COMMAND_TYPE"))
    }

    @Test
    fun testCreateCommandWithInvalidPayload_ShouldReturn400() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CMD_INVALID_PAYLOAD_001"
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
            setBody("""{"type":"lock_screen","payload":"not-json","ttlSeconds":600}""")
        }

        assertEquals(HttpStatusCode.BadRequest, createResp.status)
        val body = createResp.bodyAsText()
        assertTrue(body.contains("INVALID_COMMAND_PAYLOAD"))
    }

    @Test
    fun testPollAndAck_ShouldUpdateInventoryTimestampsAndIpAddress() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }
        val evidenceFile = java.io.File("build/reports/day5-3-direct-evidence.log").apply {
            parentFile.mkdirs()
        }
        fun evidence(line: String) {
            evidenceFile.appendText(line + "\n")
        }

        val deviceCode = "DAY53_CMD_INV_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)
        val createCmdResp = client.post("/api/admin/devices/$deviceId/commands") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"type":"lock_screen","payload":"{}","ttlSeconds":600}""")
        }
        assertEquals(HttpStatusCode.Created, createCmdResp.status)
        val commandId = TestJsonHelper.extractField(createCmdResp.bodyAsText(), "id")

        val detailBeforePollResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailBeforePollResp.status)
        val detailBeforePollBody = detailBeforePollResp.bodyAsText()
        val lastPollBefore = TestJsonHelper.extractNumberField(detailBeforePollBody, "lastPollAtEpochMillis")
        println("DAY53_DETAIL_BEFORE_POLL_URL=/api/admin/devices/$deviceId")
        println("DAY53_DETAIL_BEFORE_POLL=$detailBeforePollBody")
        println("DAY53_LAST_POLL_BEFORE=$lastPollBefore")
        evidence("DAY53_DETAIL_BEFORE_POLL_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_DETAIL_BEFORE_POLL=$detailBeforePollBody")
        evidence("DAY53_LAST_POLL_BEFORE=$lastPollBefore")

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            header("X-Forwarded-For", "203.0.113.9")
            setBody("""{"deviceCode":"$deviceCode","limit":1}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val leaseToken = TestJsonHelper.extractField(pollResp.bodyAsText(), "leaseToken")

        val detailAfterPollResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailAfterPollResp.status)
        val detailAfterPollBody = detailAfterPollResp.bodyAsText()
        val lastPollAfter = TestJsonHelper.extractNumberField(detailAfterPollBody, "lastPollAtEpochMillis")
        println("DAY53_DETAIL_AFTER_POLL_URL=/api/admin/devices/$deviceId")
        println("DAY53_DETAIL_AFTER_POLL=$detailAfterPollBody")
        println("DAY53_LAST_POLL_AFTER=$lastPollAfter")
        evidence("DAY53_DETAIL_AFTER_POLL_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_DETAIL_AFTER_POLL=$detailAfterPollBody")
        evidence("DAY53_LAST_POLL_AFTER=$lastPollAfter")

        val detailBeforeAckResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailBeforeAckResp.status)
        val detailBeforeAckBody = detailBeforeAckResp.bodyAsText()
        val lastAckBefore = TestJsonHelper.extractNumberField(detailBeforeAckBody, "lastCommandAckAtEpochMillis")
        println("DAY53_DETAIL_BEFORE_ACK_URL=/api/admin/devices/$deviceId")
        println("DAY53_DETAIL_BEFORE_ACK=$detailBeforeAckBody")
        println("DAY53_LAST_ACK_BEFORE=$lastAckBefore")
        evidence("DAY53_DETAIL_BEFORE_ACK_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_DETAIL_BEFORE_ACK=$detailBeforeAckBody")
        evidence("DAY53_LAST_ACK_BEFORE=$lastAckBefore")

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
        val lastAckAfter = TestJsonHelper.extractNumberField(detailBody, "lastCommandAckAtEpochMillis")
        println("DAY53_DETAIL_AFTER_ACK_URL=/api/admin/devices/$deviceId")
        println("DAY53_DETAIL_AFTER_ACK=$detailBody")
        println("DAY53_LAST_ACK_AFTER=$lastAckAfter")
        evidence("DAY53_DETAIL_AFTER_ACK_URL=/api/admin/devices/$deviceId")
        evidence("DAY53_DETAIL_AFTER_ACK=$detailBody")
        evidence("DAY53_LAST_ACK_AFTER=$lastAckAfter")
        assertTrue("\"lastPollAtEpochMillis\"\\s*:\\s*\\d+".toRegex().containsMatchIn(detailBody))
        assertTrue("\"lastCommandAckAtEpochMillis\"\\s*:\\s*\\d+".toRegex().containsMatchIn(detailBody))
        assertTrue(detailBody.contains("\"ipAddress\""))
        assertTrue(detailBody.contains("198.51.100.10"))
    }

    @Test
    fun testExpiredCommand_ShouldNotBeLeased_AndShouldAppearAsExpiredInAdminList() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CMD_EXPIRED_001"
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

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","limit":5}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val pollBody = pollResp.bodyAsText()
        assertTrue(!pollBody.contains(commandId), "Expired command must not be leased")

        val listExpiredResp = client.get("/api/admin/devices/$deviceId/commands?status=EXPIRED&limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listExpiredResp.status)
        val expiredBody = listExpiredResp.bodyAsText()
        assertTrue(expiredBody.contains(commandId))
        assertTrue("\\\"status\\\"\\s*:\\s*\\\"EXPIRED\\\"".toRegex().containsMatchIn(expiredBody))
    }

    @Test
    fun testAckAfterExpiry_ShouldReturnConflictWithStableErrorCode() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CMD_EXPIRED_ACK_001"
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

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","limit":1}""")
        }
        assertEquals(HttpStatusCode.OK, pollResp.status)
        val leaseToken = TestJsonHelper.extractField(pollResp.bodyAsText(), "leaseToken")

        Thread.sleep(1500)

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
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
        assertEquals(HttpStatusCode.Conflict, ackResp.status)
        val ackBody = ackResp.bodyAsText()
        assertTrue(ackBody.contains("COMMAND_EXPIRED"))
        assertTrue(ackBody.contains("expired", ignoreCase = true))
    }

    @Test
    fun testCancelAfterExpiry_ShouldNotChangeExpiredToCancelled() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CMD_EXPIRED_CANCEL_001"
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

        val cancelResp = client.post("/api/admin/devices/$deviceId/commands/$commandId/cancel") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"reason":"late_cancel"}""")
        }
        assertEquals(HttpStatusCode.Conflict, cancelResp.status)
        assertTrue(cancelResp.bodyAsText().contains("COMMAND_EXPIRED"))

        val listExpiredResp = client.get("/api/admin/devices/$deviceId/commands?status=EXPIRED&limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listExpiredResp.status)
        val expiredBody = listExpiredResp.bodyAsText()
        assertTrue(expiredBody.contains(commandId))
        assertTrue("\\\"status\\\"\\s*:\\s*\\\"EXPIRED\\\"".toRegex().containsMatchIn(expiredBody))
    }

    @Test
    fun testLinkAndProfileUpdate_ShouldEnqueueRefreshConfigCommands() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"TEST_REFRESH_PROFILE_001","name":"Refresh profile","allowedApps":["com.android.settings"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)
        val profileId = TestJsonHelper.extractField(profileResp.bodyAsText(), "id")

        val deviceCode = "TEST_REFRESH_DEVICE_001"
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
            setBody("""{"userCode":"TEST_REFRESH_PROFILE_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val listAfterLink = client.get("/api/admin/devices/$deviceId/commands?limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listAfterLink.status)
        val bodyAfterLink = listAfterLink.bodyAsText()
        assertEquals(1, countCommandType(bodyAfterLink, "refresh_config"))

        val updateResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"name":"Refresh profile updated"}""")
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)

        val listAfterUpdate = client.get("/api/admin/devices/$deviceId/commands?limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listAfterUpdate.status)
        val bodyAfterUpdate = listAfterUpdate.bodyAsText()
        assertEquals(1, countCommandType(bodyAfterUpdate, "refresh_config"))

        val updatePolicyResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"disableWifi":true}""")
        }
        assertEquals(HttpStatusCode.OK, updatePolicyResp.status)

        val listAfterPolicyUpdate = client.get("/api/admin/devices/$deviceId/commands?limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, listAfterPolicyUpdate.status)
        val bodyAfterPolicyUpdate = listAfterPolicyUpdate.bodyAsText()
        assertEquals(2, countCommandType(bodyAfterPolicyUpdate, "refresh_config"))
    }

    @Test
    fun testPollWithMismatchedDeviceCode_ShouldFail() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_002")

        val pollResp = client.post("/api/device/poll") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"DIFFERENT_CODE","limit":5}""")
        }

        assertEquals(HttpStatusCode.Conflict, pollResp.status)
        val body = pollResp.bodyAsText()
        assertTrue(body.contains("deviceCode mismatch"))
    }

    @Test
    fun testAckWithMismatchedDeviceCode_ShouldFail() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_003")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "DIFFERENT_CODE",
                  "commandId": "550e8400-0000-0000-0000-000000000000",
                  "leaseToken": "550e8400-0000-0000-0000-000000000001",
                  "result": "SUCCESS"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Conflict, ackResp.status)
        assertTrue(ackResp.bodyAsText().contains("deviceCode mismatch"))
    }

    @Test
    fun testAckWithInvalidUuid_ShouldFail() = testApplication {
        configureCommandTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CMD_004")

        val ackResp = client.post("/api/device/ack") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "TEST_CMD_004",
                  "commandId": "invalid-uuid",
                  "leaseToken": "550e8400-0000-0000-0000-000000000001",
                  "result": "SUCCESS"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, ackResp.status)
        assertTrue(ackResp.bodyAsText().contains("Invalid UUID format"))
    }
}

private fun ApplicationTestBuilder.configureCommandTestApplication() {
    environment {
        val dbName = "command_integration_${System.nanoTime()}"
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

private fun hasCommandType(body: String, type: String): Boolean =
    "\"type\"\\s*:\\s*\"$type\"".toRegex().containsMatchIn(body)

private fun countCommandType(body: String, type: String): Int =
    "\"type\"\\s*:\\s*\"$type\"".toRegex().findAll(body).count()

