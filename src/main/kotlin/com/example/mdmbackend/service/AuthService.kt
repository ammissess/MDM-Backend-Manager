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
        val deviceCode: String? = null, // ✅ default để không phải sửa call site
    )

    fun login(req: LoginRequest): LoginResult? {
        val user = users.findByUsername(req.username) ?: return null
        val ok = PasswordHasher.verify(req.password, user.passwordHash)
        if (!ok) return null

        val expiresAt = Instant.now().plus(cfg.auth.sessionTtlMinutes, ChronoUnit.MINUTES)


        val session = sessions.create(user.id, expiresAt,req.deviceCode)
        return LoginResult(
            token = session.token,
            expiresAt = session.expiresAt,
            role = user.role.name,
            deviceCode = session.deviceCode, // optional, có thể bỏ cũng được
        )
    }

    fun logout(token: UUID): Boolean = sessions.delete(token)
}
