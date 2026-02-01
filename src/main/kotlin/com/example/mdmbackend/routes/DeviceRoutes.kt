package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.DeviceEventRequest
import com.example.mdmbackend.dto.DeviceRegisterRequest
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.service.DeviceService
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.deviceRoutes() {
    val deviceService = DeviceService(DeviceRepository(), ProfileRepository())

    route("/device") {
        /**
         * Thiết bị DO đăng ký/heartbeat.
         * Cần login trước (tạm thời token session).
         */
        authenticate("session") {
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
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Device not found"))
                    return@post
                }
                call.respond(mapOf("ok" to true))
            }

            /**
             * Thiết bị poll config theo userCode.
             * Đây là endpoint quan trọng để mdmappbasic lấy allowed apps + flags.
             */
            get("/config/{userCode}") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@get
                }

                val userCode = call.parameters["userCode"]!!
                val config = deviceService.getConfigByUserCode(userCode)
                if (config == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile not found"))
                    return@get
                }
                call.respond(config)
            }
        }
    }
}
