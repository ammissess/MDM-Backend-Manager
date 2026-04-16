package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.AdminCreateCommandRequest
import com.example.mdmbackend.dto.AdminAuditFilter
import com.example.mdmbackend.dto.AdminDeviceEventsFilter
import com.example.mdmbackend.dto.AdminLatestLocationResponse
import com.example.mdmbackend.dto.AdminResetUnlockPassRequest
import com.example.mdmbackend.dto.AdminUsageSummaryItem
import com.example.mdmbackend.dto.AdminUsageSummaryResponse
import com.example.mdmbackend.dto.DeviceLinkRequest
import com.example.mdmbackend.dto.ProfileCreateRequest
import com.example.mdmbackend.dto.ProfileUpdateRequest
import com.example.mdmbackend.middleware.HttpException
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DeviceCommandRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.service.AdminDeviceService
import com.example.mdmbackend.service.AuditService
import com.example.mdmbackend.service.DeviceCommandService
import com.example.mdmbackend.service.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.Instant
import java.util.UUID

fun Route.adminRoutes() {
    val profileRepo = ProfileRepository()
    val deviceRepo = DeviceRepository()
    val commandRepo = DeviceCommandRepository()
    val privateInfoRepo = DevicePrivateInfoRepository()
    val usageRepo = DeviceAppUsageRepository()

    val commandService = DeviceCommandService(deviceRepo, commandRepo)
    val audit = AuditService()
    val profiles = ProfileService(profileRepo, deviceRepo, commandService, audit)
    val devices = AdminDeviceService(deviceRepo, profileRepo, commandService, audit)

    authenticate("session") {
        route("/admin") {
            get("/audit") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@get
                }

                val q = call.request.queryParameters
                val filter = AdminAuditFilter(
                    action = q["action"],
                    actorType = q["actorType"],
                    targetType = q["targetType"],
                    targetId = q["targetId"],
                    fromEpochMillis = q["fromEpochMillis"]?.toLongOrNull(),
                    toEpochMillis = q["toEpochMillis"]?.toLongOrNull(),
                    limit = q["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50,
                    offset = q["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L,
                )

                call.respond(audit.list(filter))
            }

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
                    call.respond(HttpStatusCode.Created, profiles.create(req, principal.userId))
                }

                get("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val p = profiles.get(id) ?: throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                    call.respond(p)
                }

                put("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val req = call.receive<ProfileUpdateRequest>()
                    val p = profiles.update(id, req, principal.userId) ?: throw HttpException(HttpStatusCode.NotFound, "Profile not found")

                    call.respond(p)
                }

                delete("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@delete
                    }
                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    if (!profiles.delete(id)) throw HttpException(HttpStatusCode.NotFound, "Profile not found")
                    call.respond(mapOf("ok" to true))
                }

                put("/{id}/allowed-apps") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val apps = call.receive<List<String>>()
                    val p = profiles.setAllowedApps(id, apps, principal.userId)
                        ?: throw HttpException(HttpStatusCode.NotFound, "Profile not found")

                    call.respond(p)
                }
            }

            route("/devices") {
                get {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    call.respond(devices.list())
                }

                get("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }

                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val detail = devices.getDetailById(id) ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    call.respond(detail)
                }

                get("/{id}/apps") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }

                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val inventory = devices.getAppInventoryById(id)
                        ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    call.respond(inventory)
                }

                put("/{id}/link") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }

                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val body = call.receive<DeviceLinkRequest>()
                    val d = devices.linkDeviceToUserCode(id, body.userCode, principal.userId)
                        ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")


                    call.respond(d)
                }

                post("/{id}/lock") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@post
                    }

                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    if (!devices.lockDevice(id, principal.userId)) throw HttpException(HttpStatusCode.NotFound, "Device not found")

                    call.respond(mapOf("ok" to true))
                }

                post("/{id}/reset-unlock-pass") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@post
                    }

                    val id = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val req = call.receive<AdminResetUnlockPassRequest>()
                    if (!devices.resetUnlockPass(id, req.newPassword, principal.userId)) {
                        throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    }

                    call.respond(mapOf("ok" to true))
                }

                get("/{id}/location/latest") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }

                    val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val loc = privateInfoRepo.getLatestByDeviceId(deviceId)
                        ?: throw HttpException(HttpStatusCode.NotFound, "No location data")

                    call.respond(
                        AdminLatestLocationResponse(
                            deviceId = deviceId.toString(),
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracyMeters = loc.accuracyMeters,
                            updatedAtEpochMillis = loc.updatedAt.toEpochMilli()
                        )
                    )
                }

                get("/{id}/events") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }

                    val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val q = call.request.queryParameters
                    val filter = AdminDeviceEventsFilter(
                        category = q["category"]?.uppercase(),
                        severity = q["severity"]?.uppercase(),
                        type = q["type"],
                        errorCode = q["errorCode"],
                        fromEpochMillis = q["fromEpochMillis"]?.toLongOrNull(),
                        toEpochMillis = q["toEpochMillis"]?.toLongOrNull(),
                        limit = q["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50,
                    )
                    call.respond(devices.listEvents(deviceId, filter))
                }

                get("/{id}/telemetry/summary") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }

                    val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val summary = devices.getTelemetrySummaryById(deviceId)
                        ?: throw HttpException(HttpStatusCode.NotFound, "Device not found")
                    call.respond(summary)
                }

                get("/{id}/usage/summary") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }

                    val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                        .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                    val from = call.request.queryParameters["fromEpochMillis"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    val to = call.request.queryParameters["toEpochMillis"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    val items = usageRepo.summaryByDeviceId(deviceId, from, to)

                    call.respond(
                        AdminUsageSummaryResponse(
                            deviceId = deviceId.toString(),
                            fromEpochMillis = from?.toEpochMilli(),
                            toEpochMillis = to?.toEpochMilli(),
                            items = items.map { AdminUsageSummaryItem(it.packageName, it.totalDurationMs, it.sessions) }
                        )
                    )
                }

                route("/{id}/commands") {
                    post {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@post
                        }

                        val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                            .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                        val req = call.receive<AdminCreateCommandRequest>()
                        val result = runCatching { commandService.adminCreate(deviceId, principal.userId, req) }
                            .getOrElse { e ->
                                when (e) {
                                    is HttpException -> throw e
                                    is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                                    else -> throw e
                                }
                            }

                        call.respond(HttpStatusCode.Created, result)
                    }

                    get {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@get
                        }

                        val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                            .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                        val status = call.request.queryParameters["status"]
                        if (status != null) {
                            runCatching { com.example.mdmbackend.model.CommandStatus.valueOf(status.uppercase()) }
                                .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid status enum: $status") }
                        }

                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                        val result = runCatching { commandService.adminList(deviceId, status, limit, offset) }
                            .getOrElse { e ->
                                when (e) {
                                    is HttpException -> throw e
                                    is IllegalArgumentException -> throw HttpException(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                                    else -> throw e
                                }
                            }

                        call.respond(result)
                    }

                    post("/{commandId}/cancel") {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@post
                        }

                        val deviceId = runCatching { UUID.fromString(call.parameters["id"]!!) }
                            .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["id"]}") }

                        val commandId = runCatching { UUID.fromString(call.parameters["commandId"]!!) }
                            .getOrElse { throw HttpException(HttpStatusCode.BadRequest, "Invalid UUID format: ${call.parameters["commandId"]}") }

                        val req = call.receive<com.example.mdmbackend.dto.AdminCancelCommandRequest>()
                        val result = commandService.adminCancel(deviceId, commandId, principal.userId, req)

                        call.respond(result)
                    }
                }
            }
        }
    }
}
