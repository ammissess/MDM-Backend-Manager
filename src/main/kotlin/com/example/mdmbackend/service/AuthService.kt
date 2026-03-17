package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.LoginRequest
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.repository.SessionRepository
import com.example.mdmbackend.repository.UserRepository
import com.example.mdmbackend.util.PasswordHasher
import io.ktor.http.*
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
        val deviceCode: String? = null,
    )

    /**
     * ✅ login() - Ép DEVICE phải cung cấp deviceCode, ADMIN không cần
     */
    fun login(req: LoginRequest): LoginResult? {
        // 1) Tìm user theo username
        val user = users.findByUsername(req.username) ?: return null

        // 2) Verify password
        val ok = PasswordHasher.verify(req.password, user.passwordHash)
        if (!ok) return null

        // 3) ✅ Validate deviceCode cho DEVICE role
        if (user.role == Role.DEVICE) {
            if (req.deviceCode.isNullOrBlank()) {
                // Ép DEVICE phải có deviceCode
                throw HttpException(
                    HttpStatusCode.BadRequest,
                    "DEVICE role requires deviceCode in login request"
                )
            }
        }
        // ADMIN login không cần deviceCode

        // 4) Tạo session với deviceCode (có thể null cho ADMIN)
        val expiresAt = Instant.now().plus(cfg.auth.sessionTtlMinutes, ChronoUnit.MINUTES)
        val session = sessions.create(user.id, expiresAt, req.deviceCode)

        return LoginResult(
            token = session.token,
            expiresAt = session.expiresAt,
            role = user.role.name,
            deviceCode = session.deviceCode, // null cho ADMIN, deviceCode cho DEVICE
        )
    }

    fun logout(token: UUID): Boolean = sessions.delete(token)
}