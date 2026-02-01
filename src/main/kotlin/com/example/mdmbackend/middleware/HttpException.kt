package com.example.mdmbackend.middleware

import io.ktor.http.*

/**
 * Exception dùng để trả lỗi HTTP "có chủ đích" (401/403/404/...) qua StatusPages.
 */
class HttpException(
    val status: HttpStatusCode,
    override val message: String,
) : RuntimeException(message)
