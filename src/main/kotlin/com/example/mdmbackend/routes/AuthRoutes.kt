package com.example.mdmbackend.routes

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.LoginRequest
import com.example.mdmbackend.dto.LoginResponse
import com.example.mdmbackend.service.AuthService
import com.example.mdmbackend.repository.SessionRepository
import com.example.mdmbackend.repository.UserRepository
import com.example.mdmbackend.middleware.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(cfg: AppConfig) {
    val auth = AuthService(cfg, UserRepository(), SessionRepository())

    route("/auth") {
        post("/login") {
            val req = call.receive<LoginRequest>()
            val result = auth.login(req)
            if (result == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                return@post
            }
            call.respond(
                LoginResponse(
                    token = result.token.toString(),
                    expiresAtEpochMillis = result.expiresAt.toEpochMilli(),
                    role = result.role,
                )
            )
        }

        authenticate("session") {
            post("/logout") {
                val principal = call.principal<UserPrincipal>()!!
                val token = call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                if (token == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing token"))
                    return@post
                }
                val ok = runCatching { java.util.UUID.fromString(token) }.getOrNull()?.let { auth.logout(it) } ?: false
                call.respond(mapOf("ok" to ok, "user" to principal.username))
            }
        }
    }
}
