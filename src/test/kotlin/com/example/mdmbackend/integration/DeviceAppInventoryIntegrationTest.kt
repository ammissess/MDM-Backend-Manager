package com.example.mdmbackend.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceAppInventoryIntegrationTest {

    @Test
    fun testHappyPathIngestAndAdminRead() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "APP_INV_HP_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val deviceId = registerDevice(client, deviceCode, deviceToken)
        val adminToken = TestAuthHelper.loginAdmin(client)

        val reportedAt = System.currentTimeMillis()
        val ingestResp = client.post("/api/device/apps/inventory") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":$reportedAt,
                  "items":[
                    {
                      "packageName":"com.android.chrome",
                      "appName":"Chrome",
                      "versionName":"124.0.1",
                      "versionCode":124001,
                      "isSystemApp":false,
                      "hasLauncherActivity":true,
                      "installed":true,
                      "disabled":false,
                      "hidden":false,
                      "suspended":false
                    },
                    {
                      "packageName":"com.android.settings",
                      "appName":"Settings",
                      "versionName":"14",
                      "versionCode":34,
                      "isSystemApp":true,
                      "hasLauncherActivity":true,
                      "installed":true
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, ingestResp.status)
        val ingestJson = parseObject(ingestResp.bodyAsText())
        assertEquals(true, ingestJson.booleanField("ok"))
        assertEquals(2L, ingestJson.longField("upserted"))
        assertTrue(ingestJson.longField("updatedAtEpochMillis") > 0L)

        val adminReadResp = client.get("/api/admin/devices/$deviceId/apps") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.OK, adminReadResp.status)
        val adminReadJson = parseObject(adminReadResp.bodyAsText())
        assertEquals(deviceId, adminReadJson.stringField("deviceId"))
        assertEquals(2L, adminReadJson.longField("total"))

        val chrome = findInventoryItem(adminReadJson, "com.android.chrome")
        assertEquals("Chrome", chrome.stringField("appName"))
        assertEquals("124.0.1", chrome.stringField("versionName"))
        assertEquals(124001L, chrome.longField("versionCode"))
        assertEquals(false, chrome.booleanField("isSystemApp"))
        assertEquals(true, chrome.booleanField("hasLauncherActivity"))
        assertEquals(true, chrome.booleanField("installed"))
        assertEquals(false, chrome.booleanField("disabled"))
        assertEquals(false, chrome.booleanField("hidden"))
        assertEquals(false, chrome.booleanField("suspended"))
        assertTrue(chrome.longField("lastSeenAtEpochMillis") >= reportedAt)
    }

    @Test
    fun testDeviceCodeMismatchReturns409() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceToken = TestAuthHelper.loginDevice(client, "APP_INV_MISMATCH_A")
        val resp = client.post("/api/device/apps/inventory") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"APP_INV_MISMATCH_B",
                  "reportedAtEpochMillis":${System.currentTimeMillis()},
                  "items":[{"packageName":"com.example.app"}]
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
    fun testBlankPackageNameReturns400() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "APP_INV_BLANK_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        registerDevice(client, deviceCode, deviceToken)

        val resp = client.post("/api/device/apps/inventory") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":${System.currentTimeMillis()},
                  "items":[{"packageName":"   ","installed":true}]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("INVALID_PACKAGE_NAME"))
    }

    @Test
    fun testDuplicatePackageNameSameRequestKeepsLastItem() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "APP_INV_DUP_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val deviceId = registerDevice(client, deviceCode, deviceToken)
        val adminToken = TestAuthHelper.loginAdmin(client)

        val ingestResp = client.post("/api/device/apps/inventory") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":${System.currentTimeMillis()},
                  "items":[
                    {
                      "packageName":"com.example.dup",
                      "appName":"Old Name",
                      "versionName":"1.0",
                      "versionCode":1,
                      "installed":true
                    },
                    {
                      "packageName":"com.example.other",
                      "appName":"Other App",
                      "versionCode":3,
                      "installed":true
                    },
                    {
                      "packageName":"com.example.dup",
                      "appName":"New Name",
                      "versionName":"2.0",
                      "versionCode":2,
                      "installed":false,
                      "disabled":true,
                      "hidden":true,
                      "suspended":false
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, ingestResp.status)
        val ingestJson = parseObject(ingestResp.bodyAsText())
        assertEquals(2L, ingestJson.longField("upserted"))

        val adminReadResp = client.get("/api/admin/devices/$deviceId/apps") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, adminReadResp.status)

        val adminReadJson = parseObject(adminReadResp.bodyAsText())
        assertEquals(2L, adminReadJson.longField("total"))
        val deduped = findInventoryItem(adminReadJson, "com.example.dup")
        assertEquals("New Name", deduped.stringField("appName"))
        assertEquals("2.0", deduped.stringField("versionName"))
        assertEquals(2L, deduped.longField("versionCode"))
        assertEquals(false, deduped.booleanField("installed"))
        assertEquals(true, deduped.booleanField("disabled"))
        assertEquals(true, deduped.booleanField("hidden"))
        assertEquals(false, deduped.booleanField("suspended"))
    }

    @Test
    fun testAdminReadInvalidUuidReturns400() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val resp = client.get("/api/admin/devices/not-a-uuid/apps") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertTrue(resp.bodyAsText().contains("Invalid UUID format"))
    }

    @Test
    fun testAdminReadNotFoundReturns404() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val adminToken = TestAuthHelper.loginAdmin(client)
        val resp = client.get("/api/admin/devices/${UUID.randomUUID()}/apps") {
            header("Authorization", "Bearer $adminToken")
        }

        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertTrue(resp.bodyAsText().contains("Device not found"))
    }

    @Test
    fun testNullableFirstItemFieldsStillReadable() = testApplication {
        configureDeviceAppInventoryTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "APP_INV_NULLABLE_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val deviceId = registerDevice(client, deviceCode, deviceToken)
        val adminToken = TestAuthHelper.loginAdmin(client)

        val ingestResp = client.post("/api/device/apps/inventory") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody(
                """
                {
                  "deviceCode":"$deviceCode",
                  "reportedAtEpochMillis":${System.currentTimeMillis()},
                  "items":[{"packageName":"com.example.nullable"}]
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, ingestResp.status)
        val adminReadResp = client.get("/api/admin/devices/$deviceId/apps") {
            header("Authorization", "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, adminReadResp.status)

        val item = findInventoryItem(parseObject(adminReadResp.bodyAsText()), "com.example.nullable")
        assertEquals(true, item.booleanField("installed"))
        assertTrue(item.longField("lastSeenAtEpochMillis") > 0L)
        assertTrue(isNullOrMissing(item, "appName"))
        assertTrue(isNullOrMissing(item, "versionName"))
        assertTrue(isNullOrMissing(item, "versionCode"))
        assertTrue(isNullOrMissing(item, "isSystemApp"))
        assertTrue(isNullOrMissing(item, "hasLauncherActivity"))
        assertTrue(isNullOrMissing(item, "disabled"))
        assertTrue(isNullOrMissing(item, "hidden"))
        assertTrue(isNullOrMissing(item, "suspended"))
    }
}

private suspend fun registerDevice(client: HttpClient, deviceCode: String, deviceToken: String): String {
    val response = client.post("/api/device/register") {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $deviceToken")
        setBody("""{"deviceCode":"$deviceCode"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    return TestJsonHelper.extractField(response.bodyAsText(), "deviceId")
}

private fun parseObject(body: String): JsonObject =
    Json.parseToJsonElement(body).jsonObject

private fun findInventoryItem(payload: JsonObject, packageName: String): JsonObject =
    payload.getValue("items").jsonArray
        .map { it.jsonObject }
        .first { it.stringField("packageName") == packageName }

private fun JsonObject.stringField(fieldName: String): String =
    getValue(fieldName).jsonPrimitive.content

private fun JsonObject.longField(fieldName: String): Long =
    getValue(fieldName).jsonPrimitive.long

private fun JsonObject.booleanField(fieldName: String): Boolean =
    getValue(fieldName).jsonPrimitive.boolean

private fun isNullOrMissing(payload: JsonObject, fieldName: String): Boolean {
    val value = payload[fieldName] ?: return true
    return value is JsonNull
}

private fun ApplicationTestBuilder.configureDeviceAppInventoryTestApplication() {
    environment {
        val dbName = "device_app_inventory_${System.nanoTime()}"
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
