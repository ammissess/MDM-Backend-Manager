package com.example.mdmbackend.routes

import com.example.mdmbackend.dto.*
import com.example.mdmbackend.middleware.UserPrincipal
import com.example.mdmbackend.model.Role
import com.example.mdmbackend.service.DeviceService
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DevicePrivateInfoRepository
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.deviceRoutes() {
    val deviceRepo = DeviceRepository()
    val profileRepo = ProfileRepository()
    val privateRepo = DevicePrivateInfoRepository()
    val usageRepo = DeviceAppUsageRepository()

    val deviceService = DeviceService(deviceRepo, profileRepo, privateRepo, usageRepo)

    route("/device") {
        /**
         * Thiết bị DO đăng ký/heartbeat.
         * Cần login trước (tạm thời token session).
         */
        authenticate("session") {

            // ✅ POST /api/device/register  (đã có sẵn, giữ nguyên)
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
                    // 423 Locked cho đúng semantics “chưa mở khoá”
                    call.respond(HttpStatusCode.Locked, mapOf("error" to result.message, "status" to result.status))
                    return@post
                }
                call.respond(DeviceUnlockResponse(status = result.status, message = result.message))
            }

            // ✅ POST /api/device/location  (1 phút/ lần)
            post("/location") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<LocationUpdateRequest>()
                val ok = deviceService.updateLocation(req)
                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Device not found"))
                    return@post
                }
                call.respond(mapOf("ok" to true))
            }

            // ✅ POST /api/device/usage  (bảng usage app cá nhân riêng)
            post("/usage") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != Role.DEVICE && principal.role != Role.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Forbidden"))
                    return@post
                }

                val req = call.receive<UsageReportRequest>()
                val ok = deviceService.insertUsage(req)
                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Device not found"))
                    return@post
                }
                call.respond(mapOf("ok" to true))
            }

            // ✅ POST /api/device/{deviceCode}/events (giữ)
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
             * ✅ GET /api/device/config/{userCode}?deviceCode=...
             * - Nếu device LOCKED => 423 Locked
             * - Nếu ACTIVE => trả config profile theo userCode
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
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing query param: deviceCode"))
                    return@get
                }

                // check status trước
                val status = deviceService.getDeviceStatus(deviceCode)
                if (status == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Device not found"))
                    return@get
                }
                if (status != "ACTIVE") {
                    call.respond(HttpStatusCode.Locked, mapOf("error" to "Device is locked", "status" to status))
                    return@get
                }

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
