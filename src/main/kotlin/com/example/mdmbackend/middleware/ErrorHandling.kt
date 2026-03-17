package com.example.mdmbackend.middleware

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureErrorHandling() {
    install(StatusPages) {
        /**
         * ✅ HttpException - Intentional errors với status code rõ ràng
         */
        exception<HttpException> { call, cause ->
            call.respond(
                cause.status,
                mapOf(
                    "error" to cause.message,
                    "code" to (cause.code ?: "")
                ).filterValues { it.isNotEmpty() }
            )
        }

        /**
         * ✅ IllegalArgumentException - Input validation errors (400 Bad Request)
         */
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Bad request"))
            )
        }

        /**
         * ✅ Unhandled exceptions (500 Internal Server Error)
         */
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error")
            )
        }
    }
}