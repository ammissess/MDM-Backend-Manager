package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val expiresAtEpochMillis: Long,
    val role: String,
)
