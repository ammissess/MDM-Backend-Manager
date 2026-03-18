package com.example.mdmbackend.middleware

import com.example.mdmbackend.config.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors(cfg: AppConfig) {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        val allowedHosts = cfg.cors.allowedHosts
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (allowedHosts.any { it == "*" }) {
            anyHost()
            return@install
        }

        allowedHosts.forEach { raw ->
            val normalized = raw
                .removePrefix("http://")
                .removePrefix("https://")
                .trimEnd('/')

            if (normalized.isBlank()) return@forEach

            allowHost(
                host = normalized,
                schemes = listOf("http", "https")
            )
        }
    }
}