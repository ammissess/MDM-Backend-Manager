package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.LoginRequest
import com.example.mdmbackend.repository.SessionRepository
import com.example.mdmbackend.repository.UserRepository
import com.example.mdmbackend.util.PasswordHasher
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class AuthService(
    private val cfg: AppConfig,
    private val users: UserRepository,
    private val sessions: SessionRepository,
) {
    data class LoginResult(
        val token: UUID,
        val expiresAt: Instant,
        val role: String,
    )

    fun login(req: LoginRequest): LoginResult? {
        val user = users.findByUsername(req.username) ?: return null
        val ok = PasswordHasher.verify(req.password, user.passwordHash)
        if (!ok) return null

        val expiresAt = Instant.now().plus(cfg.auth.sessionTtlMinutes, ChronoUnit.MINUTES)
        val session = sessions.create(user.id, expiresAt)
        return LoginResult(session.token, session.expiresAt, user.role.name)
    }

    fun logout(token: UUID): Boolean = sessions.delete(token)
}
