package com.example.mdmbackend.routes

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.dto.DeviceAckCommandRequest
import com.example.mdmbackend.dto.DeviceAckCommandResponse
import com.example.mdmbackend.dto.DeviceEventRequest
import com.example.mdmbackend.dto.DevicePolicyStateReportRequest
import com.example.mdmbackend.dto.DevicePolicyStateResponse
import com.example.mdmbackend.dto.DevicePollCommandsRequest
import com.example.mdmbackend.dto.DevicePollCommandsResponse
import com.example.mdmbackend.dto.DeviceRegisterRequest
import com.example.mdmbackend.dto.DeviceStateSnapshotRequest
import com.example.mdmbackend.dto.DeviceUnlockRequest
import com.example.mdmbackend.dto.DeviceUnlockResponse
import com.example.mdmbackend.dto.LocationUpdateRequest
import com.example.mdmbackend.dto.UsageBatchReportRequest
import com.example.mdmbackend.dto.UsageBatchReportResponse
import com.example.mdmbackend.dto.UsageReportRequest
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.service.CommandDeliveryService
import com.example.mdmbackend.service.DeviceCommandService
import com.example.mdmbackend.service.DeviceService
import com.example.mdmbackend.service.PollingDeliveryStrategy
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.deviceRoutes(cfg: AppConfig) {
    val deviceRepo = DeviceRepository()
    val profileRepo = ProfileRepository()
    val privateRepo = DevicePrivateInfoRepository()
    val usageRepo = DeviceAppUsageRepository()
    val commandRepo = DeviceCommandRepository()

    val commandService = DeviceCommandService(deviceRepo, commandRepo)
    val deliveryService = CommandDeliveryService(
        strategy = PollingDeliveryStrategy(commandService)
    )

    val deviceService = DeviceService(cfg, deviceRepo, profileRepo, privateRepo, usageRepo)

    route("/device") {
        authenticate("session") {
            post("/poll") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DevicePollCommandsRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val resp: DevicePollCommandsResponse = runCatching {
                    deliveryService.deliverPendingCommands(
                        deviceCode = req.deviceCode,
                        sessionDeviceCode = principal.deviceCode,
                        limit = req.limit,
                        ipAddress = call.bestEffortClientIpAddress(),
                    )
                }.getOrElse { e ->
                    when (e) {
                        is HttpException -> throw e
                        is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                        else -> throw HttpException(
                            HttpStatusCode.Conflict,
                            "Command poll error: ${e.message ?: "Unknown error"}",
                            "POLL_ERROR"
                        )
                    }
                }

                call.respond(resp)
            }

            post("/ack") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceAckCommandRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                runCatching {
                    UUID.fromString(req.commandId)
                    UUID.fromString(req.leaseToken)
                }.getOrElse {
                    throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: commandId or leaseToken")
                }

                val resp: DeviceAckCommandResponse = runCatching {
                    commandService.deviceAck(
                        req = req,
                        sessionDeviceCode = principal.deviceCode,
                        ipAddress = call.bestEffortClientIpAddress(),
                    )
                }.getOrElse { e ->
                    when (e) {
                        is HttpException -> throw e
                        is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                        is IllegalStateException -> throw HttpException(
                            HttpStatusCode.Conflict,
                            e.message ?: "Lease mismatch or command not found",
                            "LEASE_MISMATCH"
                        )
                        else -> throw HttpException(
                            HttpStatusCode.Conflict,
                            "Command ack error: ${e.message ?: "Unknown error"}",
                            "ACK_ERROR"
                        )
                    }
                }

                call.respond(resp)
            }

            post("/register") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceRegisterRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val resp = deviceService.register(
                    req = req,
                    actorType = principal.role.name,
                    actorUserId = principal.userId,
                    actorDeviceCode = principal.deviceCode
                )
                call.respond(resp)
            }

            post("/unlock") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceUnlockRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val result = deviceService.unlock(
                    deviceCode = req.deviceCode,
                    password = req.password,
                    actorType = principal.role.name,
                    actorUserId = principal.userId,
                    actorDeviceCode = principal.deviceCode
                )

                if (!result.ok) {
                    call.respond(HttpStatusCode.Locked, mapOf("error" to result.message, "status" to result.status))
                    return@post
                }

                call.respond(DeviceUnlockResponse(status = result.status, message = result.message))
            }

            post("/location") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<LocationUpdateRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val ok = deviceService.updateLocation(req = req, actorType = principal.role.name, actorUserId = principal.userId)
                if (!ok) throw HttpException(HttpStatusCode.NotFound, "Device not found")

                call.respond(mapOf("ok" to true))
            }

            post("/usage") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<UsageReportRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val ok = deviceService.insertUsage(req = req, actorType = principal.role.name, actorUserId = principal.userId)
                if (!ok) throw HttpException(HttpStatusCode.NotFound, "Device not found")

                call.respond(mapOf("ok" to true))
            }

            post("/usage/batch") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<UsageBatchReportRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val resp: UsageBatchReportResponse = deviceService.insertUsageBatch(
                    req = req,
                    actorType = principal.role.name,
                    actorUserId = principal.userId
                )
                if (!resp.ok) throw HttpException(HttpStatusCode.NotFound, "Device not found")

                call.respond(resp)
            }

            post("/state") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceStateSnapshotRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                // Service handles full business validation and rejects invalid telemetry with HTTP 400.
                val resp = deviceService.upsertStateSnapshot(
                    req = req,
                    actorType = principal.role.name,
                    actorUserId = principal.userId
                ) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")

                call.respond(resp)
            }

            post("/policy-state") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DevicePolicyStateReportRequest>()
                requireDeviceCodeMatchIfDevice(principal, req.deviceCode)

                val resp: DevicePolicyStateResponse = deviceService.upsertPolicyState(
                    req = req,
                    actorType = principal.role.name,
                    actorUserId = principal.userId
                ) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")

                call.respond(resp)
            }

            post("/{deviceCode}/events") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val deviceCode = call.parameters["deviceCode"]!!
                requireDeviceCodeMatchIfDevice(principal, deviceCode)

                val req = call.receive<DeviceEventRequest>()
                val ok = deviceService.addEvent(
                    deviceCode = deviceCode,
                    req = req,
                    actorType = principal.role.name,
                    actorUserId = principal.userId
                )
                if (!ok) throw HttpException(HttpStatusCode.NotFound, "Device not found")

                call.respond(mapOf("ok" to true))
            }

            get("/config/current") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@get
                }

                val deviceCode = call.request.queryParameters["deviceCode"]
                if (deviceCode.isNullOrBlank()) {
                    throw HttpException(HttpStatusCode.BadRequest, "Missing query param: deviceCode")
                }

                requireDeviceCodeMatchIfDevice(principal, deviceCode)

                val status = deviceService.getDeviceStatus(deviceCode) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
                if (status != "ACTIVE") {
                    call.respond(HttpStatusCode.Locked, mapOf("error" to "Device is locked", "status" to status))
                    return@get
                }

                val config = deviceService.getCurrentConfigByDeviceCode(deviceCode)
                    ?: throw HttpException(HttpStatusCode.NotFound, "Device profile not linked")

                call.respond(config)
            }

            get("/config/{userCode}") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@get
                }

                call.response.headers.append("X-Deprecated", "true")
                call.response.headers.append(
                    "Warning",
                    """299 - "Deprecated endpoint. Use GET /api/device/config/current?deviceCode=..." """
                )

                val userCode = call.parameters["userCode"]!!
                val deviceCode = call.request.queryParameters["deviceCode"]
                if (deviceCode.isNullOrBlank()) {
                    throw HttpException(HttpStatusCode.BadRequest, "Missing query param: deviceCode")
                }

                requireDeviceCodeMatchIfDevice(principal, deviceCode)

                val status = deviceService.getDeviceStatus(deviceCode) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
                if (status != "ACTIVE") {
                    call.respond(HttpStatusCode.Locked, mapOf("error" to "Device is locked", "status" to status))
                    return@get
                }

                val config = deviceService.getConfigByUserCode(userCode) ?: throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                call.respond(config)
            }
        }
    }
}

private fun requireDeviceCodeMatchIfDevice(principal: UserPrincipal, requestedDeviceCode: String) {
    if (principal.role != Role.DEVICE) return

    if (principal.deviceCode.isNullOrBlank()) {
        throw HttpException(HttpStatusCode.Unauthorized, "DEVICE session must have deviceCode")
    }

    if (principal.deviceCode != requestedDeviceCode) {
        throw HttpException(
            HttpStatusCode.Conflict,
            "deviceCode mismatch: session has '${principal.deviceCode}', request has '$requestedDeviceCode'",
            "DEVICE_CODE_MISMATCH"
        )
    }
}

private fun ApplicationCall.bestEffortClientIpAddress(): String? {
    val forwardedFor = request.headers["X-Forwarded-For"]
        ?.split(',')
        ?.asSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

    return forwardedFor
        ?: request.origin.remoteHost
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
}
