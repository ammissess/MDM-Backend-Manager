package com.example.mdmbackend.middleware

import com.example.mdmbackend.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin

class InMemoryRateLimiter(
    private val config: AppConfig.RouteRateLimitConfig,
) {
    private data class Counter(
        var windowStartedAtMillis: Long,
        var count: Int,
    )

    data class Decision(
        val allowed: Boolean,
        val retryAfterSeconds: Long,
    )

    private val counters = LinkedHashMap<String, Counter>()

    @Synchronized
    fun tryAcquire(key: String): Decision {
        if (!config.enabled) return Decision(allowed = true, retryAfterSeconds = 0)

        val now = System.currentTimeMillis()
        val windowMillis = config.windowSeconds * 1000

        val iterator = counters.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.windowStartedAtMillis >= windowMillis) {
                iterator.remove()
            }
        }

        val counter = counters[key]
        if (counter == null || now - counter.windowStartedAtMillis >= windowMillis) {
            counters[key] = Counter(windowStartedAtMillis = now, count = 1)
            return Decision(allowed = true, retryAfterSeconds = 0)
        }

        if (counter.count < config.maxRequests) {
            counter.count += 1
            return Decision(allowed = true, retryAfterSeconds = 0)
        }

        val elapsedMillis = now - counter.windowStartedAtMillis
        val remainingMillis = (windowMillis - elapsedMillis).coerceAtLeast(0)
        val retryAfterSeconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
        return Decision(allowed = false, retryAfterSeconds = retryAfterSeconds)
    }
}

suspend fun ApplicationCall.enforceRateLimit(
    limiter: InMemoryRateLimiter,
    key: String,
    message: String,
) {
    val decision = limiter.tryAcquire(key)
    if (decision.allowed) return

    response.headers.append(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
    throw HttpException(
        status = HttpStatusCode.TooManyRequests,
        message = message,
        code = "RATE_LIMITED",
    )
}

fun ApplicationCall.bestEffortClientIpAddress(): String? {
    val forwardedFor = request.headers["X-Forwarded-For"]
        ?.split(',')
        ?.asSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

    return forwardedFor
        ?: request.origin.remoteHost
            .trim()
            .takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
}
