package com.example.mdmbackend.middleware

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.repository.SessionRepository
import com.example.mdmbackend.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import java.time.Instant
import java.util.UUID

data class UserPrincipal(
    val userId: UUID,
    val username: String,
    val role: Role,
) : Principal

fun Application.configureAuth(cfg: AppConfig) {
    val users = UserRepository()
    val sessions = SessionRepository()

    // cleanup expired sessions on startup
    sessions.deleteExpired(Instant.now())

    install(Authentication) {
        bearer("session") {
            authenticate { credentials ->
                val tokenStr = credentials.token
                val token = runCatching { UUID.fromString(tokenStr) }.getOrNull() ?: return@authenticate null

                val session = sessions.find(token) ?: return@authenticate null
                if (session.expiresAt.isBefore(Instant.now())) {
                    sessions.delete(token)
                    return@authenticate null
                }

                val user = users.findById(session.userId) ?: return@authenticate null
                UserPrincipal(user.id, user.username, user.role)
            }
            realm = "mdmappbasic"
        }
    }
}

suspend fun ApplicationCall.requireRole(vararg roles: Role) {
    val principal = principal<UserPrincipal>()
        ?: throw HttpException(HttpStatusCode.Unauthorized, "Unauthorized")
    if (principal.role !in roles) {
        throw HttpException(HttpStatusCode.Forbidden, "Forbidden")
    }
}
