package com.example.mdmbackend.integration

import com.example.mdmbackend.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthIntegrationTest {

    @Test
    fun testAdminLoginSuccess() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()

        assertTrue(body.contains("\"token\":"))
        assertTrue(body.contains("\"role\":\"ADMIN\""))
        assertTrue(body.contains("\"expiresAtEpochMillis\":"))
    }

    @Test
    fun testDeviceLoginWithoutDeviceCode_ShouldFail() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // DEVICE login mà không có deviceCode → 400 Bad Request
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"device","password":"device123"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("DEVICE role requires deviceCode"))
    }

    @Test
    fun testDeviceLoginWithDeviceCode_Success() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"device","password":"device123","deviceCode":"TEST_DEVICE_001"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()

        assertTrue(body.contains("\"token\":"))
        assertTrue(body.contains("\"role\":\"DEVICE\""))
    }

    @Test
    fun testLoginInvalidCredentials() = testApplication {
        application { module() }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"wrongpassword"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Invalid credentials"))
    }
}