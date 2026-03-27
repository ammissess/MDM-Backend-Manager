package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CorsIntegrationTest {

    @Test
    fun testCors_AllowedOrigin_ShouldReturnAllowOriginHeader() = testApplication {
        environment {
            config = MapApplicationConfig(
                "ktor.deployment.port" to "8080",
                "ktor.application.modules.0" to "com.example.mdmbackend.ApplicationKt.module",

                "mdm.auth.sessionTtlMinutes" to "43200",

                "mdm.seed.adminUser" to "admin",
                "mdm.seed.adminPass" to "admin123",
                "mdm.seed.deviceUser" to "device",
                "mdm.seed.devicePass" to "device123",
                "mdm.seed.defaultDeviceUnlockPass" to "1111",
                "mdm.seed.defaultUserCode" to "TEST002",
                "mdm.seed.defaultAllowedApps.0" to "com.android.settings",
                "mdm.seed.defaultAllowedApps.1" to "com.android.chrome",

                "mdm.db.jdbcUrl" to "jdbc:mysql://localhost:3306/mdmappbasic?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                "mdm.db.driver" to "com.mysql.cj.jdbc.Driver",
                "mdm.db.user" to "mdm",
                "mdm.db.password" to "mdm123",

                "mdm.cors.allowedHosts.0" to "localhost:3000"
            )
        }

        application { module() }

        val resp = client.get("/health") {
            header(HttpHeaders.Origin, "http://localhost:3000")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val allowOrigin = resp.headers[HttpHeaders.AccessControlAllowOrigin]
        assertNotNull(allowOrigin)
        assertEquals("http://localhost:3000", allowOrigin)
    }

    @Test
    fun testCors_DisallowedOrigin_ShouldNotReturnAllowOriginHeader() = testApplication {
        environment {
            config = MapApplicationConfig(
                "ktor.deployment.port" to "8080",
                "ktor.application.modules.0" to "com.example.mdmbackend.ApplicationKt.module",

                "mdm.auth.sessionTtlMinutes" to "43200",

                "mdm.seed.adminUser" to "admin",
                "mdm.seed.adminPass" to "admin123",
                "mdm.seed.deviceUser" to "device",
                "mdm.seed.devicePass" to "device123",
                "mdm.seed.defaultDeviceUnlockPass" to "1111",
                "mdm.seed.defaultUserCode" to "TEST002",
                "mdm.seed.defaultAllowedApps.0" to "com.android.settings",
                "mdm.seed.defaultAllowedApps.1" to "com.android.chrome",

                "mdm.db.jdbcUrl" to "jdbc:mysql://localhost:3306/mdmappbasic?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                "mdm.db.driver" to "com.mysql.cj.jdbc.Driver",
                "mdm.db.user" to "mdm",
                "mdm.db.password" to "mdm123",

                "mdm.cors.allowedHosts.0" to "localhost:3000"
            )
        }

        application { module() }

        val resp = client.get("/health") {
            header(HttpHeaders.Origin, "http://evil.local:9999")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val allowOrigin = resp.headers[HttpHeaders.AccessControlAllowOrigin]
        assertNull(allowOrigin)
    }
}