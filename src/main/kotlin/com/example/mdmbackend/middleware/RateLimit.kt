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
        ?.map { normalizeClientIpCandidate(it) }
        ?.firstOrNull { it != null }

    if (forwardedFor != null) return forwardedFor

    val forwarded = request.headers["Forwarded"]
        ?.split(',')
        ?.asSequence()
        ?.flatMap { extractForwardedForCandidates(it).asSequence() }
        ?.map { normalizeClientIpCandidate(it) }
        ?.firstOrNull { it != null }
    if (forwarded != null) return forwarded

    return normalizeClientIpCandidate(request.origin.remoteHost)
}

internal fun extractForwardedForCandidates(value: String): List<String> {
    return value.split(';')
        .asSequence()
        .map { it.trim() }
        .filter { it.startsWith("for=", ignoreCase = true) }
        .map { it.substringAfter('=', missingDelimiterValue = "").trim() }
        .filter { it.isNotEmpty() }
        .toList()
}

internal fun normalizeClientIpCandidate(raw: String?): String? {
    if (raw == null) return null
    var candidate = raw.trim()
    if (candidate.isEmpty() || candidate.equals("unknown", ignoreCase = true)) return null

    // Drop optional surrounding quotes from Forwarded header values.
    if (candidate.length >= 2 && candidate.first() == '"' && candidate.last() == '"') {
        candidate = candidate.substring(1, candidate.length - 1).trim()
    }

    // [IPv6]:port
    if (candidate.startsWith("[") && candidate.contains(']')) {
        candidate = candidate.substringAfter('[').substringBefore(']').trim()
    } else if (candidate.count { it == ':' } == 1 && candidate.contains('.')) {
        // IPv4:port
        val hostPart = candidate.substringBefore(':').trim()
        val portPart = candidate.substringAfter(':').trim()
        if (portPart.all { it.isDigit() }) {
            candidate = hostPart
        }
    }

    // Strip IPv6 zone id if present (for example: fe80::1%eth0).
    val zoneIndex = candidate.indexOf('%')
    if (zoneIndex > 0) {
        candidate = candidate.substring(0, zoneIndex)
    }

    return candidate.takeIf { isValidIpLiteral(it) }
}

internal fun isValidIpLiteral(value: String): Boolean {
    return isValidIpv4(value) || isValidIpv6(value)
}

private fun isValidIpv4(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        if (part.isEmpty() || part.length > 3) return@all false
        if (!part.all { it.isDigit() }) return@all false
        val number = part.toIntOrNull() ?: return@all false
        number in 0..255
    }
}

private fun isValidIpv6(value: String): Boolean {
    if (!value.contains(':')) return false
    if (value.count { it == ':' } < 2) return false
    if (value.contains(":::")) return false

    val hasCompression = value.contains("::")
    if (value.indexOf("::") != value.lastIndexOf("::")) return false

    val segments = value.split(":", ignoreCase = false, limit = Int.MAX_VALUE)
    var groups = 0
    var hasEmbeddedIpv4 = false

    for (segment in segments) {
        if (segment.isEmpty()) continue
        if (segment.contains('.')) {
            if (hasEmbeddedIpv4 || !isValidIpv4(segment)) return false
            hasEmbeddedIpv4 = true
            groups += 2
            continue
        }
        if (segment.length !in 1..4) return false
        if (!segment.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return false
        groups += 1
    }

    return if (hasCompression) groups < 8 else groups == 8
}
