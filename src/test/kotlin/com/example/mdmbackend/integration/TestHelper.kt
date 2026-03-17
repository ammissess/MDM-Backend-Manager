package com.example.mdmbackend.integration

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helper để login admin/device và extract token
 */
object TestAuthHelper {

    suspend fun loginAdmin(client: HttpClient): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }
        val body = response.bodyAsText()
        // Simple JSON parsing để lấy token
        val token = body.substringAfter("\"token\":\"").substringBefore("\"")
        return token
    }

    suspend fun loginDevice(client: HttpClient, deviceCode: String): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"device","password":"device123","deviceCode":"$deviceCode"}""")
        }
        val body = response.bodyAsText()
        val token = body.substringAfter("\"token\":\"").substringBefore("\"")
        return token
    }
}

/**
 * Helper để parse JSON response
 */
object TestJsonHelper {
    fun extractField(jsonString: String, fieldName: String): String {
        return jsonString.substringAfter("\"$fieldName\":\"").substringBefore("\"")
    }

    fun extractNumberField(jsonString: String, fieldName: String): Long? {
        val pattern = "\"$fieldName\":(\\d+)".toRegex()
        return pattern.find(jsonString)?.groupValues?.get(1)?.toLongOrNull()
    }

    fun extractBooleanField(jsonString: String, fieldName: String): Boolean? {
        val pattern = "\"$fieldName\":(true|false)".toRegex()
        return pattern.find(jsonString)?.groupValues?.get(1)?.toBoolean()
    }
}