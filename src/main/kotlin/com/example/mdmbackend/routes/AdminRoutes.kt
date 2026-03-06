package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.DeviceLinkRequest
import com.example.mdmbackend.dto.ProfileCreateRequest
import com.example.mdmbackend.dto.ProfileUpdateRequest
// Import thêm DTO cho command
// import com.example.mdmbackend.dto.AdminCreateCommandRequest
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.service.DeviceService
import com.example.mdmbackend.service.ProfileService
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.service.AdminDeviceService
// Cần import thêm CommandService (nếu có)
// import com.example.mdmbackend.service.CommandService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.adminRoutes(
    // Tốt nhất bạn nên inject các service vào đây thay vì khởi tạo cứng bên trong hàm
    // commandService: CommandService
) {
    val profileRepo = ProfileRepository()
    val deviceRepo = DeviceRepository()

    val profiles = ProfileService(profileRepo)
    val devices = AdminDeviceService(deviceRepo, profileRepo)

    // Giả lập khởi tạo CommandService (Bạn cần điều chỉnh lại theo logic code thực tế của bạn)
    // val commandService = CommandService(...)

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

                get("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val p = profiles.get(id)
                    if (p == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                        return@get
                    }
                    call.respond(p)
                }

                put("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val req = call.receive<ProfileUpdateRequest>()
                    val p = profiles.update(id, req)
                    if (p == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                        return@put
                    }
                    call.respond(p)
                }

                delete("/{id}") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@delete
                    }
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val ok = profiles.delete(id)
                    call.respond(mapOf("ok" to ok))
                }

                put("/{id}/allowed-apps") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val apps = call.receive<List<String>>()
                    val p = profiles.setAllowedApps(id, apps)
                    if (p == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
                        return@put
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

                put("/{id}/link") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@put
                    }
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val body = call.receive<DeviceLinkRequest>()
                    val userCode = body.userCode
                    val d = devices.linkDeviceToUserCode(id, userCode)

                    if (d == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Device not found"))
                        return@put
                    }
                    call.respond(d)
                }

                // ===== Commands =====
                route("/{id}/commands") {
                    post {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@post
                        }
                        val deviceId = UUID.fromString(call.parameters["id"]!!)
                        // val req = call.receive<AdminCreateCommandRequest>()
                        // call.respond(HttpStatusCode.Created, commandService.adminCreate(deviceId, principal.userId, req))

                        // Placeholder response (Xóa khi implement thật)
                        call.respond(HttpStatusCode.Created, mapOf("message" to "Command created"))
                    }
                    get {
                        val principal = call.principal<UserPrincipal>()!!
                        if (principal.role != Role.ADMIN) {
                            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                            return@get
                        }
                        val deviceId = UUID.fromString(call.parameters["id"]!!)
                        val status = call.request.queryParameters["status"]
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                        val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                        // call.respond(commandService.adminList(deviceId, status, limit, offset))

                        // Placeholder response (Xóa khi implement thật)
                        call.respond(mapOf("message" to "Command list"))
                    }
                }

                // ===== Telemetry =====
                get("/{id}/location") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val deviceId = UUID.fromString(call.parameters["id"]!!)
                    // Xử lý logic lấy location ở đây
                    call.respond(mapOf("message" to "Location data for device $deviceId"))
                }

                get("/{id}/usage") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val deviceId = UUID.fromString(call.parameters["id"]!!)
                    // Xử lý logic lấy usage ở đây
                    call.respond(mapOf("message" to "Usage data for device $deviceId"))
                }

                get("/{id}/events") {
                    val principal = call.principal<UserPrincipal>()!!
                    if (principal.role != Role.ADMIN) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                        return@get
                    }
                    val deviceId = UUID.fromString(call.parameters["id"]!!)
                    // Xử lý logic lấy events ở đây
                    call.respond(mapOf("message" to "Events data for device $deviceId"))
                }
            }
        }
    }
}