package com.example.mdmbackend.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RateLimitIntegrationTest {

    @Test
    fun testLoginRateLimit_ShouldReturn429AfterConfiguredAttempts() = testApplication {
        configureRateLimitTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        repeat(2) {
            val resp = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"admin","password":"wrong-password"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }

        val limitedResp = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"wrong-password"}""")
        }

        assertEquals(HttpStatusCode.TooManyRequests, limitedResp.status)
        assertEquals("RATE_LIMITED", TestJsonHelper.extractField(limitedResp.bodyAsText(), "code"))
        assertEquals("60", limitedResp.headers[HttpHeaders.RetryAfter])
        assertTrue(limitedResp.bodyAsText().contains("Too many login attempts"))
    }

    @Test
    fun testUnlockRateLimit_ShouldReturn429AfterConfiguredAttempts() = testApplication {
        configureRateLimitTestApplication()
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val deviceCode = "RATE_LIMIT_UNLOCK_001"
        val deviceToken = TestAuthHelper.loginDevice(client, deviceCode)
        val registerResp = client.post("/api/device/register") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode"}""")
        }
        assertEquals(HttpStatusCode.OK, registerResp.status)

        repeat(2) {
            val resp = client.post("/api/device/unlock") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $deviceToken")
                setBody("""{"deviceCode":"$deviceCode","password":"bad-pass"}""")
            }
            assertEquals(HttpStatusCode.Locked, resp.status)
        }

        val limitedResp = client.post("/api/device/unlock") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            setBody("""{"deviceCode":"$deviceCode","password":"bad-pass"}""")
        }

        assertEquals(HttpStatusCode.TooManyRequests, limitedResp.status)
        assertEquals("RATE_LIMITED", TestJsonHelper.extractField(limitedResp.bodyAsText(), "code"))
        assertEquals("60", limitedResp.headers[HttpHeaders.RetryAfter])
        assertTrue(limitedResp.bodyAsText().contains("Too many unlock attempts"))
    }
}

private fun ApplicationTestBuilder.configureRateLimitTestApplication() {
    environment {
        val dbName = "rate_limit_integration_${System.nanoTime()}"
        val baseConfig = ConfigFactory.load()
        config = HoconApplicationConfig(
            baseConfig
                .withValue("mdm.profile", ConfigValueFactory.fromAnyRef("integration-test"))
                .withValue("mdm.profiles.integration-test.rateLimit.login.maxRequests", ConfigValueFactory.fromAnyRef("2"))
                .withValue("mdm.profiles.integration-test.rateLimit.login.windowSeconds", ConfigValueFactory.fromAnyRef("60"))
                .withValue("mdm.profiles.integration-test.rateLimit.unlock.maxRequests", ConfigValueFactory.fromAnyRef("2"))
                .withValue("mdm.profiles.integration-test.rateLimit.unlock.windowSeconds", ConfigValueFactory.fromAnyRef("60"))
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
