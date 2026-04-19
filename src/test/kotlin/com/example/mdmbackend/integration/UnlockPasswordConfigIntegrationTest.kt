package com.example.mdmbackend.integration

import com.example.mdmbackend.model.DevicesTable
import com.example.mdmbackend.util.PasswordHasher
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.*
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class UnlockPasswordConfigIntegrationTest {

    @Test
    fun testDefaultUnlockPassword_FromConfig_NotHardcoded1111() = testApplication {
        configureUnlockPasswordTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceCode = "TEST_UNLOCK_CFG_001"
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
                  "imei": "123123123",
                  "serial": "SR-UNLOCK-001",
                  "batteryLevel": 80,
                  "isCharging": true,
                  "wifiEnabled": true
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)
        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode":"UNLOCK_CFG_001",
                  "name":"Unlock cfg profile",
                  "allowedApps":["com.android.settings"]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"UNLOCK_CFG_001"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        val unlockOldDefault = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"1111"}""")
        }
        assertEquals(HttpStatusCode.Locked, unlockOldDefault.status)

        val unlockConfigPass = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockConfigPass.status)
    }

    @Test
    fun testLegacyBackfill_DoesNotOverwriteExistingUnlockHash() = testApplication {
        configureUnlockPasswordTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val deviceCode = "TEST_UNLOCK_CFG_002"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)

        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)
        val deviceId = TestJsonHelper.extractField(registerResp.bodyAsText(), "deviceId")

        val adminToken = TestAuthHelper.loginAdmin(client)
        val profileResp = client.post("/api/admin/profiles") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody(
                """
                {
                  "userCode":"UNLOCK_CFG_002",
                  "name":"Unlock cfg profile 2",
                  "allowedApps":["com.android.settings"]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Created, profileResp.status)

        val linkResp = client.put("/api/admin/devices/$deviceId/link") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $adminToken")
            setBody("""{"userCode":"UNLOCK_CFG_002"}""")
        }
        assertEquals(HttpStatusCode.OK, linkResp.status)

        // Pre-seed a valid non-blank hash to ensure legacy backfill does not overwrite it.
        transaction {
            DevicesTable.update({ DevicesTable.deviceCode eq deviceCode }) {
                it[unlockPassHash] = PasswordHasher.hash("9999")
            }
        }

        val unlockWithDefault = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"2468"}""")
        }
        assertEquals(HttpStatusCode.Locked, unlockWithDefault.status)

        val unlockWithExisting = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"9999"}""")
        }
        assertEquals(HttpStatusCode.OK, unlockWithExisting.status)
    }
}

private fun ApplicationTestBuilder.configureUnlockPasswordTestApplication() {
    environment {
        val dbName = "unlock_password_${System.nanoTime()}"
        val baseConfig = ConfigFactory.load()
        config = HoconApplicationConfig(
            baseConfig
                .withValue("mdm.profile", ConfigValueFactory.fromAnyRef("integration-test"))
                .withValue("mdm.auth.sessionTtlMinutes", ConfigValueFactory.fromAnyRef("43200"))
                .withValue("mdm.profiles.integration-test.seed.defaultDeviceUnlockPass", ConfigValueFactory.fromAnyRef("2468"))
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
