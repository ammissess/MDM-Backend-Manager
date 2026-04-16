package com.example.mdmbackend.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.*
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
}

private fun ApplicationTestBuilder.configureUnlockPasswordTestApplication() {
    environment {
        val dbName = "unlock_password_${System.nanoTime()}"
        val baseConfig = ConfigFactory.load()
        config = HoconApplicationConfig(
            baseConfig
                .withValue("mdm.profile", ConfigValueFactory.fromAnyRef("integration-test"))
                .withValue("mdm.auth.sessionTtlMinutes", ConfigValueFactory.fromAnyRef("43200"))
                .withValue("mdm.seed.defaultDeviceUnlockPass", ConfigValueFactory.fromAnyRef("2468"))
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
