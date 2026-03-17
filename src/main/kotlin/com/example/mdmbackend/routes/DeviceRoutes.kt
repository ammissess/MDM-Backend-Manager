package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.*
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.service.DeviceService
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.service.DeviceCommandService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID

fun Route.deviceRoutes() {
    val deviceRepo = DeviceRepository()
    val profileRepo = ProfileRepository()
    val privateRepo = DevicePrivateInfoRepository()
    val usageRepo = DeviceAppUsageRepository()
    val commandRepo = DeviceCommandRepository()
    val commandService = DeviceCommandService(deviceRepo, commandRepo)

    val deviceService = DeviceService(deviceRepo, profileRepo, privateRepo, usageRepo)

    route("/device") {
        authenticate("session") {

            /**
             * ✅ POST /api/device/poll
             * - 401: session.deviceCode null
             * - 409: session.deviceCode mismatch request.deviceCode
             * - 404: device not found
             * - 409: lease/command conflict
             */
            post("/poll") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DevicePollCommandsRequest>()

                // ✅ Validation 1: principal.deviceCode must not be null
                if (principal.deviceCode.isNullOrBlank()) {
                    throw HttpException(
                        HttpStatusCode.Unauthorized,
                        "DEVICE session must have deviceCode"
                    )
                }

                // ✅ Validation 2: req.deviceCode must match principal.deviceCode
                if (principal.deviceCode != req.deviceCode) {
                    throw HttpException(
                        HttpStatusCode.Conflict,
                        "deviceCode mismatch: session has '${principal.deviceCode}', request has '${req.deviceCode}'",
                        "DEVICE_CODE_MISMATCH"
                    )
                }

                val resp = runCatching {
                    commandService.devicePoll(
                        deviceCode = req.deviceCode,
                        sessionDeviceCode = principal.deviceCode,
                        limit = req.limit
                    )
                }.getOrElse { e ->
                    when (e) {
                        is HttpException -> throw e
                        is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                        else -> throw HttpException(HttpStatusCode.Conflict, "Command poll error: ${e.message ?: "Unknown error"}", "POLL_ERROR")
                    }
                }

                call.respond(resp)
            }

            /**
             * ✅ POST /api/device/ack
             * - 401: session.deviceCode null
             * - 409: session.deviceCode mismatch request.deviceCode
             * - 404: device not found
             * - 400: invalid UUID format for commandId/leaseToken
             * - 409: lease mismatch or command not found
             */
            post("/ack") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceAckCommandRequest>()

                // ✅ Validation 1: principal.deviceCode must not be null
                if (principal.deviceCode.isNullOrBlank()) {
                    throw HttpException(
                        HttpStatusCode.Unauthorized,
                        "DEVICE session must have deviceCode"
                    )
                }

                // ✅ Validation 2: req.deviceCode must match principal.deviceCode
                if (principal.deviceCode != req.deviceCode) {
                    throw HttpException(
                        HttpStatusCode.Conflict,
                        "deviceCode mismatch: session has '${principal.deviceCode}', request has '${req.deviceCode}'",
                        "DEVICE_CODE_MISMATCH"
                    )
                }

                // ✅ Validation 3: Parse UUID format for commandId and leaseToken
                runCatching {
                    UUID.fromString(req.commandId)
                    UUID.fromString(req.leaseToken)
                }.getOrElse { _ ->
                    throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: commandId or leaseToken")
                }

                val resp = runCatching {
                    commandService.deviceAck(
                        req = req,
                        sessionDeviceCode = principal.deviceCode,
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
                        else -> throw HttpException(HttpStatusCode.Conflict, "Command ack error: ${e.message ?: "Unknown error"}", "ACK_ERROR")
                    }
                }
                call.respond(resp)
            }

            // ✅ POST /api/device/register (giữ nguyên)
            post("/register") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceRegisterRequest>()
                val resp = deviceService.register(req)
                call.respond(resp)
            }

            // ✅ POST /api/device/unlock
            post("/unlock") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<DeviceUnlockRequest>()
                val result = deviceService.unlock(req.deviceCode, req.password)
                if (!result.ok) {
                    call.respond(HttpStatusCode.Locked, mapOf("error" to result.message, "status" to result.status))
                    return@post
                }
                call.respond(DeviceUnlockResponse(status = result.status, message = result.message))
            }

            // ✅ POST /api/device/location
            post("/location") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<LocationUpdateRequest>()
                val ok = deviceService.updateLocation(req)
                if (!ok) {
                    throw HttpException(HttpStatusCode.NotFound, "Device not found")
                }
                call.respond(mapOf("ok" to true))
            }

            // ✅ POST /api/device/usage
            post("/usage") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<UsageReportRequest>()
                val ok = deviceService.insertUsage(req)
                if (!ok) {
                    throw HttpException(HttpStatusCode.NotFound, "Device not found")
                }
                call.respond(mapOf("ok" to true))
            }

            post("/usage/batch") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<UsageBatchReportRequest>()
                val resp = deviceService.insertUsageBatch(req)
                if (!resp.ok) {
                    throw HttpException(HttpStatusCode.NotFound, "Device not found")
                }
                call.respond(resp)
            }

            // ✅ POST /api/device/{deviceCode}/events
            post("/{deviceCode}/events") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val deviceCode = call.parameters["deviceCode"]!!
                val req = call.receive<DeviceEventRequest>()
                val ok = deviceService.addEvent(deviceCode, req)
                if (!ok) {
                    throw HttpException(HttpStatusCode.NotFound, "Device not found")
                }
                call.respond(mapOf("ok" to true))
            }

            /**
             * ✅ GET /api/device/config/{userCode}?deviceCode=...
             * - 400: missing deviceCode query
             * - 404: device not found
             * - 404: profile not found
             * - 423: device is LOCKED
             */
            get("/config/{userCode}") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@get
                }

                val userCode = call.parameters["userCode"]!!
                val deviceCode = call.request.queryParameters["deviceCode"]
                if (deviceCode.isNullOrBlank()) {
                    throw HttpException(HttpStatusCode.BadRequest, "Missing query param: deviceCode")
                }

                // check status trước
                val status = deviceService.getDeviceStatus(deviceCode)
                if (status == null) {
                    throw HttpException(HttpStatusCode.NotFound, "Device not found")
                }
                if (status != "ACTIVE") {
                    call.respond(HttpStatusCode.Locked, mapOf("error" to "Device is locked", "status" to status))
                    return@get
                }

                val config = deviceService.getConfigByUserCode(userCode)
                if (config == null) {
                    throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                }
                call.respond(config)
            }
        }
    }
}