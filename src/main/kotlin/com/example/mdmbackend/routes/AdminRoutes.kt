package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.DeviceLinkRequest
import com.example.mdmbackend.dto.ProfileCreateRequest
import com.example.mdmbackend.dto.ProfileUpdateRequest
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.service.DeviceService
import com.example.mdmbackend.service.ProfileService
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.service.AdminDeviceService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.adminRoutes() {
    val profileRepo = ProfileRepository()
    val deviceRepo = DeviceRepository()

    val profiles = ProfileService(profileRepo)
    val devices = AdminDeviceService(deviceRepo, profileRepo)


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
            }
        }
    }
}
