package com.example.mdmbackend.integration

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Helper để login admin/device và extract token
 */
object TestAuthHelper {

    suspend fun loginAdmin(client: HttpClient): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"admin","password":"admin123"}""")
        }
        return extractToken(response.bodyAsText())
    }

    suspend fun loginDevice(client: HttpClient, deviceCode: String): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"device","password":"device123","deviceCode":"$deviceCode"}""")
        }
        return extractToken(response.bodyAsText())
    }

    private fun extractToken(body: String): String {
        val token = runCatching {
            val json = Json.parseToJsonElement(body) as JsonObject
            json["token"]?.jsonPrimitive?.content
        }.getOrNull()

        require(!token.isNullOrBlank()) {
            "Cannot extract auth token from login response: $body"
        }
        return token
    }
}

/**
 * Helper để parse JSON response
 */
object TestJsonHelper {
    fun extractField(jsonString: String, fieldName: String): String {
        val parsed = parseJsonObject(jsonString)
        return parsed?.let { findStringField(it, fieldName) }
            ?: "\"$fieldName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                .find(jsonString)
                ?.groupValues
                ?.get(1)
            ?: ""
    }

    fun extractNumberField(jsonString: String, fieldName: String): Long? {
        val parsed = parseJsonObject(jsonString)
        return parsed?.get(fieldName)?.jsonPrimitive?.longOrNull
            ?: "\"$fieldName\":(\\d+)".toRegex().find(jsonString)?.groupValues?.get(1)?.toLongOrNull()
    }

    fun extractBooleanField(jsonString: String, fieldName: String): Boolean? {
        val parsed = parseJsonObject(jsonString)
        return parsed?.get(fieldName)?.jsonPrimitive?.booleanOrNull
            ?: "\"$fieldName\":(true|false)".toRegex().find(jsonString)?.groupValues?.get(1)?.toBoolean()
    }

    private fun parseJsonObject(jsonString: String): JsonObject? =
        runCatching { Json.parseToJsonElement(jsonString) as? JsonObject }.getOrNull()

    private fun findStringField(element: JsonElement, fieldName: String): String? = when (element) {
        is JsonObject -> {
            runCatching { element[fieldName]?.jsonPrimitive?.content }.getOrNull()
                ?: element.values.asSequence().mapNotNull { findStringField(it, fieldName) }.firstOrNull()
        }

        is JsonArray -> element.firstNotNullOfOrNull { findStringField(it, fieldName) }
        else -> null
    }
}