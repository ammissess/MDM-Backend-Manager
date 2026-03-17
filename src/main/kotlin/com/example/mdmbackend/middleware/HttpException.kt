package com.example.mdmbackend.middleware

import io.ktor.http.*

/**
 * Exception dùng để trả lỗi HTTP "có chủ đích" (400/401/403/404/409/423/...)
 * qua StatusPages.
 */
class HttpException(
    val status: HttpStatusCode,
    override val message: String,
    val code: String? = null, // optional error code (e.g., "LEASE_MISMATCH")
) : RuntimeException(message)