package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.*
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.service.AdminDeviceService
import com.example.mdmbackend.service.DeviceCommandService
import com.example.mdmbackend.service.ProfileService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID

fun Route.adminRoutes() {
    val profileRepo = ProfileRepository()
    val deviceRepo = DeviceRepository()
    val commandRepo = DeviceCommandRepository()
    val privateInfoRepo = DevicePrivateInfoRepository()
    val usageRepo = DeviceAppUsageRepository()

    val profiles = ProfileService(profileRepo)
    val devices = AdminDeviceService(deviceRepo, profileRepo)
    val commandService = DeviceCommandService(deviceRepo, commandRepo)

    authenticate("session") {
        route("/admin") {

            // ===== Profiles =====
            route("/profiles") {
                get {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    call.respond(profiles.list())
                }

                post {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@post
                    }
                    val req = call.receive<ProfileCreateRequest>()
                    call.respond(HttpStatusCode.Created, profiles.create(req))
                }

                // ✅ GET /{id} - Parse UUID + 404 if not found
                get("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val p = profiles.get(id) ?: run {
                        throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                    }
                    call.respond(p)
                }

                // ✅ PUT /{id} - Parse UUID + 404 if not found
                put("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val req = call.receive<ProfileUpdateRequest>()
                    val p = profiles.update(id, req) ?: run {
                        throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                    }
                    call.respond(p)
                }

                // ✅ DELETE /{id} - Parse UUID + 404 if not found
                delete("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@delete
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    if (!profiles.delete(id)) {
                        throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                    }
                    call.respond(mapOf("ok" to true))
                }

                put("/{id}/allowed-apps") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val apps = call.receive<List<String>>()
                    val p = profiles.setAllowedApps(id, apps) ?: run {
                        throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                    }
                    call.respond(p)
                }
            }

            // ===== Devices =====
            route("/devices") {
                get {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    call.respond(devices.list())
                }

                // ✅ GET /{id} - Parse UUID + 404 if not found
                get("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val d = devices.getById(id) ?: run {
                        throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    }
                    call.respond(d)
                }

                put("/{id}/link") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val body = call.receive<DeviceLinkRequest>()
                    val d = devices.linkDeviceToUserCode(id, body.userCode) ?: run {
                        throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    }
                    call.respond(d)
                }

                // ✅ POST /{id}/lock - Parse UUID + 404 if not found
                post("/{id}/lock") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@post
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    if (!devices.lockDevice(id)) {
                        throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    }
                    call.respond(mapOf("ok" to true))
                }

                // ✅ POST /{id}/reset-unlock-pass - Parse UUID + 404 if not found
                post("/{id}/reset-unlock-pass") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@post
                    }
                    val id = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val req = call.receive<AdminResetUnlockPassRequest>()
                    if (!devices.resetUnlockPass(id, req.newPassword)) {
                        throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    }
                    call.respond(mapOf("ok" to true))
                }

                // ===== Commands =====
                route("/{id}/commands") {
                    // ✅ POST - Create command, 404 device not found, 400 invalid payload
                    post {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@post
                        }
                        val deviceId = runCatching {
                            UUID.fromString(call.parameters["id"]!!)
                        }.getOrElse { _ ->
                            throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                        }
                        val req = call.receive<AdminCreateCommandRequest>()

                        val result = runCatching {
                            commandService.adminCreate(deviceId, principal.userId, req)
                        }.getOrElse { e ->
                            when (e) {
                                is HttpException -> throw e
                                is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                                else -> throw e
                            }
                        }
                        call.respond(HttpStatusCode.Created, result)
                    }

                    // ✅ GET - List commands, 404 device not found, 400 invalid status enum
                    get {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@get
                        }
                        val deviceId = runCatching {
                            UUID.fromString(call.parameters["id"]!!)
                        }.getOrElse { _ ->
                            throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                        }
                        val status = call.request.queryParameters["status"]

                        // ✅ Validate status enum if provided
                        if (status != null) {
                            runCatching {
                                com.example.mdmbackend.model.CommandStatus.valueOf(status.uppercase())
                            }.getOrElse { _ ->
                                throw HttpException(HttpStatusCode.BadRequest, "Invalid status enum: $status")
                            }
                        }

                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                        val result = runCatching {
                            commandService.adminList(deviceId, status, limit, offset)
                        }.getOrElse { e ->
                            when (e) {
                                is HttpException -> throw e
                                is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                                else -> throw e
                            }
                        }
                        call.respond(result)
                    }
                }

                // ✅ GET /{id}/location/latest - Parse UUID + 404 if not found
                get("/{id}/location/latest") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val deviceId = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val loc = privateInfoRepo.getLatestByDeviceId(deviceId) ?: run {
                        throw HttpException(HttpStatusCode.NotFound, "No location data")
                    }
                    call.respond(AdminLatestLocationResponse(
                        deviceId = deviceId.toString(),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = loc.accuracyMeters,
                        updatedAtEpochMillis = loc.updatedAt.toEpochMilli(),
                    ))
                }

                // ✅ GET /{id}/events - Parse UUID
                get("/{id}/events") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val deviceId = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val events = deviceRepo.listEvents(deviceId, limit)
                    call.respond(events.map { e ->
                        AdminDeviceEventView(
                            id = e.id.toString(),
                            deviceId = e.deviceId.toString(),
                            type = e.type,
                            payload = e.payload,
                            createdAtEpochMillis = e.createdAt.toEpochMilli(),
                        )
                    })
                }

                // ✅ GET /{id}/usage/summary - Parse UUID
                get("/{id}/usage/summary") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val deviceId = runCatching {
                        UUID.fromString(call.parameters["id"]!!)
                    }.getOrElse { _ ->
                        throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}")
                    }
                    val from = call.request.queryParameters["fromEpochMillis"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    val to = call.request.queryParameters["toEpochMillis"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    val items = usageRepo.summaryByDeviceId(deviceId, from, to)
                    call.respond(AdminUsageSummaryResponse(
                        deviceId = deviceId.toString(),
                        fromEpochMillis = from?.toEpochMilli(),
                        toEpochMillis = to?.toEpochMilli(),
                        items = items.map { AdminUsageSummaryItem(it.packageName, it.totalDurationMs, it.sessions) },
                    ))
                }
            }
        }
    }
}