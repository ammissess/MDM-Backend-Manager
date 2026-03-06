package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceCode: String? = null, // NEW, optional (web dashboard vẫn OK):contentReference[oaicite:35]{index=35}
)

@Serializable
data class LoginResponse(
    val token: String,
    val expiresAtEpochMillis: Long,
    val role: String,
)
