package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UnlockPasswordConfigIntegrationTest {

    @Test
    fun testDefaultUnlockPassword_FromConfig_NotHardcoded1111() = testApplication {
        environment {
            config = io.ktor.server.config.MapApplicationConfig(
                "ktor.deployment.port" to "8080",
                "ktor.application.modules.0" to "com.example.mdmbackend.ApplicationKt.module",

                "mdm.auth.sessionTtlMinutes" to "43200",

                "mdm.seed.adminUser" to "admin",
                "mdm.seed.adminPass" to "admin123",
                "mdm.seed.deviceUser" to "device",
                "mdm.seed.devicePass" to "device123",
                "mdm.seed.defaultDeviceUnlockPass" to "2468",
                "mdm.seed.defaultUserCode" to "TEST002",
                "mdm.seed.defaultAllowedApps.0" to "com.android.settings",
                "mdm.seed.defaultAllowedApps.1" to "com.android.chrome",

                "mdm.db.jdbcUrl" to "jdbc:mysql://localhost:3306/mdmappbasic?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                "mdm.db.driver" to "com.mysql.cj.jdbc.Driver",
                "mdm.db.user" to "mdm",
                "mdm.db.password" to "mdm123",
            )
        }

        application { module() }
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