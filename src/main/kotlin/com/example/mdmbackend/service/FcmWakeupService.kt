package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.DeviceWakeupTarget
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.inputStream
import kotlin.io.path.readText

data class DeviceWakeupRequest(
    val triggerSource: String,
    val reason: String,
    val commandId: String? = null,
    val commandType: String? = null,
)

data class DeviceWakeupSendResult(
    val attempted: Boolean,
    val delivered: Boolean,
    val detail: String,
)

interface DeviceWakeupSender {
    fun send(target: DeviceWakeupTarget, request: DeviceWakeupRequest): DeviceWakeupSendResult
}

class FcmWakeupService(
    private val devices: DeviceRepository,
    private val sender: DeviceWakeupSender,
) {
    private val log = LoggerFactory.getLogger(FcmWakeupService::class.java)

    fun register(bus: EventBus) {
        bus.subscribe { event ->
            when (event) {
                is CommandCreatedEvent -> handleCommandCreated(event)
            }
        }
    }

    fun handleCommandCreated(event: CommandCreatedEvent) {
        val target = devices.findWakeupTargetByDeviceId(event.deviceId)
        val triggerSource = if (event.type == "refresh_config") "refresh_config_enqueued" else "command_created"
        val wakeupReason = buildWakeupReason(triggerSource = triggerSource, reason = "pending_command")

        if (target == null) {
            devices.recordWakeupAttempt(
                deviceId = event.deviceId,
                attemptedAt = Instant.now(),
                reason = wakeupReason,
                result = "skipped_no_token",
            )
            log.info(
                "wake-up attempt triggerSource={} deviceId={} commandId={} commandType={} outcome=skipped_no_token",
                triggerSource,
                event.deviceId,
                event.commandId,
                event.type,
            )
            return
        }

        val result = runCatching {
            sender.send(
                target = target,
                request = DeviceWakeupRequest(
                    triggerSource = triggerSource,
                    reason = "pending_command",
                    commandId = event.commandId.toString(),
                    commandType = event.type,
                )
            )
        }.getOrElse { error ->
            log.warn(
                "wake-up attempt triggerSource={} deviceCode={} commandId={} commandType={} outcome=failed error={}",
                triggerSource,
                target.deviceCode,
                event.commandId,
                event.type,
                error.message ?: error::class.java.simpleName,
            )
            devices.recordWakeupAttempt(
                deviceId = event.deviceId,
                attemptedAt = Instant.now(),
                reason = wakeupReason,
                result = "failed_exception",
            )
            return
        }

        val safeResult = when {
            !result.attempted -> result.detail
            result.delivered -> "delivered:${result.detail}"
            else -> "not_delivered:${result.detail}"
        }
        devices.recordWakeupAttempt(
            deviceId = event.deviceId,
            attemptedAt = Instant.now(),
            reason = wakeupReason,
            result = safeResult,
        )

        log.info(
            "wake-up attempt triggerSource={} deviceCode={} commandId={} commandType={} attempted={} delivered={} detail={}",
            triggerSource,
            target.deviceCode,
            event.commandId,
            event.type,
            result.attempted,
            result.delivered,
            result.detail,
        )
    }

    private fun buildWakeupReason(triggerSource: String, reason: String): String {
        val source = triggerSource.trim()
        val detail = reason.trim()
        return when {
            source.isEmpty() -> detail
            detail.isEmpty() -> source
            else -> "$source:$detail"
        }
    }
}

class FcmHttpV1WakeupSender(
    private val cfg: AppConfig.FcmConfig,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) : DeviceWakeupSender {
    private val json = Json { explicitNulls = false }
    private val log = LoggerFactory.getLogger(FcmHttpV1WakeupSender::class.java)

    @Volatile
    private var cachedCredentials: GoogleCredentials? = null

    @Volatile
    private var cachedProjectId: String? = null

    override fun send(target: DeviceWakeupTarget, request: DeviceWakeupRequest): DeviceWakeupSendResult {
        if (!cfg.enabled) {
            return DeviceWakeupSendResult(attempted = false, delivered = false, detail = "disabled")
        }

        if (cfg.credentialsFile.isBlank()) {
            return DeviceWakeupSendResult(attempted = false, delivered = false, detail = "missing_credentials_file")
        }

        val credentialsPath = Path.of(cfg.credentialsFile)
        if (!Files.isRegularFile(credentialsPath)) {
            return DeviceWakeupSendResult(attempted = false, delivered = false, detail = "credentials_not_found")
        }

        val credentials = loadCredentials(credentialsPath)
        credentials.refreshIfExpired()
        val accessToken = credentials.accessToken?.tokenValue
            ?: return DeviceWakeupSendResult(attempted = false, delivered = false, detail = "missing_access_token")
        val projectId = cachedProjectId ?: readProjectId(credentialsPath)
            ?: return DeviceWakeupSendResult(attempted = false, delivered = false, detail = "missing_project_id")

        val requestBody = json.encodeToString(
            buildJsonObject {
                put("message", buildJsonObject {
                    put("token", target.fcmToken)
                    put("data", buildJsonObject {
                        put("type", "wake_up")
                        put("reason", request.reason)
                        put("triggerSource", request.triggerSource)
                        request.commandId?.let { put("commandId", it) }
                        request.commandType?.let { put("commandType", it) }
                        target.fcmTokenUpdatedAt?.let { put("tokenUpdatedAtEpochMillis", it.toEpochMilli().toString()) }
                    })
                    put("android", buildJsonObject {
                        put("priority", "high")
                        put("ttl", "60s")
                    })
                })
            }
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=utf-8")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        val delivered = response.statusCode() in 200..299
        if (!delivered) {
            log.warn(
                "fcm send failed status={} triggerSource={} deviceCode={} responseBody={}",
                response.statusCode(),
                request.triggerSource,
                target.deviceCode,
                response.body(),
            )
        }

        return DeviceWakeupSendResult(
            attempted = true,
            delivered = delivered,
            detail = "http_${response.statusCode()}",
        )
    }

    private fun loadCredentials(credentialsPath: Path): GoogleCredentials {
        val existing = cachedCredentials
        if (existing != null) return existing

        synchronized(this) {
            val secondRead = cachedCredentials
            if (secondRead != null) return secondRead

            val loaded = credentialsPath.inputStream().use { input ->
                GoogleCredentials.fromStream(input)
                    .createScoped("https://www.googleapis.com/auth/firebase.messaging")
            }
            cachedCredentials = loaded
            return loaded
        }
    }

    private fun readProjectId(credentialsPath: Path): String? {
        val existing = cachedProjectId
        if (existing != null) return existing

        synchronized(this) {
            val secondRead = cachedProjectId
            if (secondRead != null) return secondRead

            val parsed = runCatching {
                json.parseToJsonElement(credentialsPath.readText())
                    .jsonObject["project_id"]
                    ?.toString()
                    ?.trim('"')
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()

            cachedProjectId = parsed
            return parsed
        }
    }
}
