package com.example.mdmbackend.middleware

import com.example.mdmbackend.config.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import java.net.URI

fun Application.configureCors(cfg: AppConfig) {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        val allowedOrigins = cfg.cors.allowedOrigins
            .map { it.trim() }
            .filter { it.isNotBlank() }

        require(allowedOrigins.isNotEmpty()) {
            "mdm.cors.allowedOrigins must contain at least one explicit origin"
        }

        require(allowedOrigins.none { it == "*" }) {
            "Wildcard CORS origin '*' is not allowed"
        }

        allowedOrigins.forEach { raw ->
            val normalized = raw.trim().removeSuffix("/")
            val uri = runCatching { URI.create(normalized) }
                .getOrElse { throw IllegalArgumentException("Invalid CORS origin '$raw'") }
            val scheme = uri.scheme?.lowercase()
            require(scheme == "http" || scheme == "https") {
                "CORS origin '$raw' must use http or https"
            }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "CORS origin '$raw' must not include credentials, query, or fragment"
            }

            val host = uri.host?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("CORS origin '$raw' must include a valid host")
            val path = uri.path.orEmpty()
            require(path.isEmpty() || path == "/") {
                "CORS origin '$raw' must not include a path"
            }

            val hostWithPort = if (uri.port == -1) host else "$host:${uri.port}"

            allowHost(
                host = hostWithPort,
                schemes = listOf(scheme)
            )
        }
    }
}
