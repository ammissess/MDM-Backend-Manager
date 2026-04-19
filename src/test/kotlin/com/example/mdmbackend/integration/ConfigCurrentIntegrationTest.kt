package com.example.mdmbackend.integration

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
import io.ktor.server.testing.testApplication
import junit.framework.TestCase.assertTrue
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import com.example.mdmbackend.model.DevicesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class ConfigCurrentIntegrationTest {

    @Test
    fun testConfigCurrent_MissingDeviceCode_ShouldReturn400() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CFG_MISSING_001")

        val resp = client.get("/api/device/config/current") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("Missing query param: deviceCode"))
    }

    @Test
    fun testConfigCurrent_DeviceNotFound_ShouldReturn404() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "TEST_CFG_NOTFOUND_001")

        val resp = client.get("/api/device/config/current?deviceCode=TEST_CFG_NOTFOUND_001") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("Device not found"))
    }

    @Test
    fun testConfigCurrent_LockedDevice_ShouldReturn423() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CFG_LOCKED_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Google",
                  "model": "Pixel 8",
                  "imei": "999001",
                  "serial": "SER001",
                  "batteryLevel": 80,
                  "isCharging": true,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)

        val resp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.Locked, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("Device profile not linked"))
        assertTrue(body.contains("DEVICE_PROFILE_NOT_LINKED"))
    }

    @Test
    fun testConfigCurrent_DeviceWithoutProfile_ShouldReturn404() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "TEST_CFG_NOPROFILE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Samsung",
                  "model": "S24",
                  "imei": "999002",
                  "serial": "SER002",
                  "batteryLevel": 65,
                  "isCharging": false,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.Companion.Locked, unlockResp.status)
        val unlockBody = unlockResp.bodyAsText()
        assertTrue(unlockBody.contains("Device profile not linked"))
        assertTrue(unlockBody.contains("DEVICE_PROFILE_NOT_LINKED"))

        val resp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }

        assertEquals(HttpStatusCode.Companion.Locked, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("Device profile not linked"))
        assertTrue(body.contains("DEVICE_PROFILE_NOT_LINKED"))
    }

    @Test
    fun testUnlinkProfile_ShouldForceLocked_AndRequireRelinkThenUnlock() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "TEST_CFG_UNLINK_LOCK_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode": "CFG_UNLINK_LOCK_001",
                  "name": "Unlink lock profile",
                  "allowedApps": ["com.example.alpha"]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

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
            setBody("""{"userCode":"CFG_UNLINK_LOCK_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockResp.status)

        val unlinkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":null}""")
        }
        assertEquals(HttpStatusCode.OK, unlinkResp.status)
        val unlinkBody = unlinkResp.bodyAsText()
        assertEquals("LOCKED", TestJsonHelper.extractField(unlinkBody, "status"))

        val detailAfterUnlinkResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, detailAfterUnlinkResp.status)
        val detailAfterUnlinkBody = detailAfterUnlinkResp.bodyAsText()
        assertEquals("LOCKED", TestJsonHelper.extractField(detailAfterUnlinkBody, "status"))
        val detailAfterUnlinkJson = Json.parseToJsonElement(detailAfterUnlinkBody) as JsonObject
        assertTrue(
            detailAfterUnlinkJson["userCode"] == null || detailAfterUnlinkJson["userCode"] == JsonNull
        )

        val unlockWhenUnlinkedResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.Locked, unlockWhenUnlinkedResp.status)
        val unlockWhenUnlinkedBody = unlockWhenUnlinkedResp.bodyAsText()
        assertTrue(unlockWhenUnlinkedBody.contains("Device profile not linked"))
        assertTrue(unlockWhenUnlinkedBody.contains("DEVICE_PROFILE_NOT_LINKED"))

        val relinkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"CFG_UNLINK_LOCK_001"}""")
        }
        assertEquals(HttpStatusCode.OK, relinkResp.status)
        assertEquals("LOCKED", TestJsonHelper.extractField(relinkResp.bodyAsText(), "status"))

        val unlockAfterRelinkResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockAfterRelinkResp.status)
        assertEquals("ACTIVE", TestJsonHelper.extractField(unlockAfterRelinkResp.bodyAsText(), "status"))
    }

    @Test
    fun testConfigCurrent_Success_ResolveByDeviceProfileId_NotByUserCodePath() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "TEST_CFG_SUCCESS_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode": "CFG_USER_001",
                  "name": "CFG Profile",
                  "description": "for config/current test",
                  "allowedApps": ["com.example.alpha", "com.example.beta"],
                  "disableWifi": true,
                  "disableBluetooth": false,
                  "disableCamera": true,
                  "disableStatusBar": true,
                  "kioskMode": true,
                  "blockUninstall": true,
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
        assertEquals(HttpStatusCode.Companion.Created, profileResp.status)
        val profileBody = profileResp.bodyAsText()
        assertFalse(profileBody.contains("\"showWifi\""))
        assertFalse(profileBody.contains("\"showBluetooth\""))
        val profileId = TestJsonHelper.extractField(profileBody, "id")

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Xiaomi",
                  "model": "14",
                  "imei": "999003",
                  "serial": "SER003",
                  "batteryLevel": 90,
                  "isCharging": true,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"CFG_USER_001"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, linkResp.status)

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, unlockResp.status)

        val currentResp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, currentResp.status)
        val currentBody = currentResp.bodyAsText()
        assertTrue(currentBody.contains("CFG_USER_001"))
        assertTrue(currentBody.contains("com.example.alpha"))
        assertTrue(currentBody.contains("disableWifi"))
        assertTrue(currentBody.contains("true"))
        assertEquals(true, TestJsonHelper.extractBooleanField(currentBody, "lockPrivateDnsConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(currentBody, "lockVpnConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(currentBody, "blockDebuggingFeatures"))
        assertEquals(true, TestJsonHelper.extractBooleanField(currentBody, "disableUsbDataSignaling"))
        assertEquals(true, TestJsonHelper.extractBooleanField(currentBody, "disallowSafeBoot"))
        assertEquals(true, TestJsonHelper.extractBooleanField(currentBody, "disallowFactoryReset"))
        assertFalse(currentBody.contains("\"showWifi\""))
        assertFalse(currentBody.contains("\"showBluetooth\""))

        val deprecatedResp = client.get("/api/device/config/WRONG_USER_CODE?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.Companion.NotFound, deprecatedResp.status)
        assertEquals("true", deprecatedResp.headers["X-Deprecated"])
        assertTrue((deprecatedResp.headers["Warning"] ?: "").contains("Deprecated endpoint"))

        assertTrue(profileId.isNotBlank()) // giữ biến sử dụng để tránh warning/unused
    }

    @Test
    fun testProfileSave_ShouldRecomputeDesiredConfig_AndExposeUpdatedConfigCurrent() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "TEST_CFG_RECOMPUTE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val createProfileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode": "CFG_RECOMPUTE_001",
                  "name": "Recompute profile",
                  "description": "verify desired recompute",
                  "allowedApps": ["com.android.settings"],
                  "disableWifi": false,
                  "disableBluetooth": false,
                  "disableCamera": false,
                  "disableStatusBar": true,
                  "kioskMode": true,
                  "blockUninstall": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.Created, createProfileResp.status)
        val profileId = TestJsonHelper.extractField(createProfileResp.bodyAsText(), "id")
        assertTrue(profileId.isNotBlank())

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode": "$deviceCode",
                  "androidVersion": "14",
                  "sdkInt": 34,
                  "manufacturer": "Google",
                  "model": "Pixel 8",
                  "imei": "998001",
                  "serial": "SER998001",
                  "batteryLevel": 88,
                  "isCharging": true,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Companion.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")
        assertTrue(deviceId.isNotBlank())

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"CFG_RECOMPUTE_001"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, linkResp.status)

        val beforeDeviceDetailResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, beforeDeviceDetailResp.status)
        val beforeDeviceDetailBody = beforeDeviceDetailResp.bodyAsText()
        val beforeDesiredHash = TestJsonHelper.extractField(beforeDeviceDetailBody, "desiredConfigHash")
        val beforeDesiredVersion = TestJsonHelper.extractNumberField(beforeDeviceDetailBody, "desiredConfigVersionEpochMillis")
        assertTrue(beforeDesiredHash.isNotBlank())
        assertNotNull(beforeDesiredVersion)
        println("EVIDENCE before desiredConfigHash=$beforeDesiredHash desiredConfigVersion=$beforeDesiredVersion")

        val commandsBeforeResp = client.get("/api/admin/devices/$deviceId/commands?limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, commandsBeforeResp.status)
        val commandsBeforeTotal = TestJsonHelper.extractNumberField(commandsBeforeResp.bodyAsText(), "total") ?: 0L

        Thread.sleep(5)

        val updateProfileResp = client.put("/api/admin/profiles/$profileId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "disableWifi": true,
                  "disableBluetooth": true,
                  "disableCamera": true,
                  "disableStatusBar": true,
                  "kioskMode": true,
                  "blockUninstall": true,
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
        assertEquals(HttpStatusCode.Companion.OK, updateProfileResp.status)

        val updateAllowedAppsResp = client.put("/api/admin/profiles/$profileId/allowed-apps") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(listOf("com.android.settings", "com.android.chrome", "com.google.android.apps.maps"))
        }
        assertEquals(HttpStatusCode.Companion.OK, updateAllowedAppsResp.status)

        val profileReadbackResp = client.get("/api/admin/profiles/$profileId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, profileReadbackResp.status)
        val profileReadbackBody = profileReadbackResp.bodyAsText()
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "disableWifi"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "disableBluetooth"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "disableCamera"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "lockPrivateDnsConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "lockVpnConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "blockDebuggingFeatures"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "disableUsbDataSignaling"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "disallowSafeBoot"))
        assertEquals(true, TestJsonHelper.extractBooleanField(profileReadbackBody, "disallowFactoryReset"))
        assertTrue(profileReadbackBody.contains("com.android.settings"))
        assertTrue(profileReadbackBody.contains("com.android.chrome"))
        assertTrue(profileReadbackBody.contains("com.google.android.apps.maps"))

        val afterDeviceDetailResp = client.get("/api/admin/devices/$deviceId") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, afterDeviceDetailResp.status)
        val afterDeviceDetailBody = afterDeviceDetailResp.bodyAsText()
        val afterDesiredHash = TestJsonHelper.extractField(afterDeviceDetailBody, "desiredConfigHash")
        val afterDesiredVersion = TestJsonHelper.extractNumberField(afterDeviceDetailBody, "desiredConfigVersionEpochMillis")
        assertTrue(afterDesiredHash.isNotBlank())
        assertNotNull(afterDesiredVersion)
        assertTrue(afterDesiredHash != beforeDesiredHash)
        assertTrue(afterDesiredVersion != beforeDesiredVersion)
        println("EVIDENCE after desiredConfigHash=$afterDesiredHash desiredConfigVersion=$afterDesiredVersion")

        val unlockResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.Companion.OK, unlockResp.status)

        val configCurrentResp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, configCurrentResp.status)
        val configCurrentBody = configCurrentResp.bodyAsText()
        assertEquals(true, TestJsonHelper.extractBooleanField(configCurrentBody, "lockPrivateDnsConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(configCurrentBody, "lockVpnConfig"))
        assertEquals(true, TestJsonHelper.extractBooleanField(configCurrentBody, "blockDebuggingFeatures"))
        assertEquals(true, TestJsonHelper.extractBooleanField(configCurrentBody, "disableUsbDataSignaling"))
        assertEquals(true, TestJsonHelper.extractBooleanField(configCurrentBody, "disallowSafeBoot"))
        assertEquals(true, TestJsonHelper.extractBooleanField(configCurrentBody, "disallowFactoryReset"))
        assertTrue(configCurrentBody.contains("com.google.android.apps.maps"))
        println("EVIDENCE configCurrent allowedApps hasMaps=${configCurrentBody.contains("com.google.android.apps.maps")}")
        println(
            "EVIDENCE configCurrent hardening lockPrivateDnsConfig=${TestJsonHelper.extractBooleanField(configCurrentBody, "lockPrivateDnsConfig")}" +
                " lockVpnConfig=${TestJsonHelper.extractBooleanField(configCurrentBody, "lockVpnConfig")}" +
                " blockDebuggingFeatures=${TestJsonHelper.extractBooleanField(configCurrentBody, "blockDebuggingFeatures")}" +
                " disableUsbDataSignaling=${TestJsonHelper.extractBooleanField(configCurrentBody, "disableUsbDataSignaling")}" +
                " disallowSafeBoot=${TestJsonHelper.extractBooleanField(configCurrentBody, "disallowSafeBoot")}" +
                " disallowFactoryReset=${TestJsonHelper.extractBooleanField(configCurrentBody, "disallowFactoryReset")}"
        )

        val commandsAfterResp = client.get("/api/admin/devices/$deviceId/commands?limit=50&offset=0") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Companion.OK, commandsAfterResp.status)
        val commandsAfterBody = commandsAfterResp.bodyAsText()
        val commandsAfterTotal = TestJsonHelper.extractNumberField(commandsAfterBody, "total") ?: 0L
        assertTrue(commandsAfterTotal > commandsBeforeTotal)
        assertTrue(commandsAfterBody.lowercase().contains("refresh_config"))
        println("EVIDENCE commandTimeline refresh_config_present=${commandsAfterBody.lowercase().contains("refresh_config")}")
    }

    @Test
    fun unlinkProfile_thenUnlockBlockedUntilProfileRelinked_thenUnlockActive_withLegacyBlankHash() = testApplication {
        configureConfigCurrentTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val deviceCode = "TEST_CFG_RELINK_RECOVER_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"CFG_RELINK_RECOVER_001","name":"Relink recover profile","allowedApps":["com.android.settings"]}""")
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

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
            setBody("""{"userCode":"CFG_RELINK_RECOVER_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val unlockActiveResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockActiveResp.status)

        val unlinkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":null}""")
        }
        assertEquals(HttpStatusCode.OK, unlinkResp.status)
        assertEquals("LOCKED", TestJsonHelper.extractField(unlinkResp.bodyAsText(), "status"))

        val unlockNoProfileResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.Locked, unlockNoProfileResp.status)
        assertTrue(unlockNoProfileResp.bodyAsText().contains("DEVICE_PROFILE_NOT_LINKED"))

        val relinkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"CFG_RELINK_RECOVER_001"}""")
        }
        assertEquals(HttpStatusCode.OK, relinkResp.status)
        assertEquals("LOCKED", TestJsonHelper.extractField(relinkResp.bodyAsText(), "status"))

        // Simulate legacy records with missing unlock hash; unlock flow must self-heal via configured default pass.
        transaction {
            DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
                it[DevicesTable.unlockPassHash] = ""
            }
        }

        val unlockAfterRelinkResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockAfterRelinkResp.status)
        assertEquals("ACTIVE", TestJsonHelper.extractField(unlockAfterRelinkResp.bodyAsText(), "status"))

        val configResp = client.get("/api/device/config/current?deviceCode=$deviceCode") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.OK, configResp.status)
        assertTrue(configResp.bodyAsText().contains("CFG_RELINK_RECOVER_001"))
    }
}

private fun ApplicationTestBuilder.configureConfigCurrentTestApplication() {
    environment {
        val dbName = "config_current_integration_${System.nanoTime()}"
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
                .withValue(
                    "mdm.profiles.integration-test.seed.defaultDeviceUnlockPass",
                    ConfigValueFactory.fromAnyRef("2468")
                )
        )
    }
}

