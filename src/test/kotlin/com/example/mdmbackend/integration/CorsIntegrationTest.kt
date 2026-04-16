package com.example.mdmbackend.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CorsIntegrationTest {

    @Test
    fun testCors_AllowedOrigin_ShouldReturnAllowOriginHeader() = testApplication {
        configureCorsTestApplication()

        val resp = client.get("/health") {
            header(HttpHeaders.Origin, "http://localhost:5173")
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        val allowOrigin = resp.headers[HttpHeaders.AccessControlAllowOrigin]
        assertNotNull(allowOrigin)
        assertEquals("http://localhost:5173", allowOrigin)
    }

    @Test
    fun testCors_DisallowedOrigin_ShouldNotReturnAllowOriginHeader() = testApplication {
        configureCorsTestApplication()

        val resp = client.get("/health") {
            header(HttpHeaders.Origin, "http://evil.local:9999")
        }

        assertEquals(HttpStatusCode.Forbidden, resp.status)
        val allowOrigin = resp.headers[HttpHeaders.AccessControlAllowOrigin]
        assertNull(allowOrigin)
    }
}

private fun ApplicationTestBuilder.configureCorsTestApplication() {
    environment {
        val dbName = "cors_integration_${System.nanoTime()}"
        val baseConfig = ConfigFactory.load()
        config = HoconApplicationConfig(
            baseConfig
                .withValue("mdm.profile", ConfigValueFactory.fromAnyRef("integration-test"))
                .withValue("mdm.auth.sessionTtlMinutes", ConfigValueFactory.fromAnyRef("43200"))
                .withValue("mdm.cors.allowedOrigins", ConfigValueFactory.fromIterable(listOf("http://localhost:5173")))
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
