package com.example.mdmbackend

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.config.DatabaseFactory
import com.example.mdmbackend.config.Seeder
import com.example.mdmbackend.middleware.configureAuth
import com.example.mdmbackend.middleware.configureCors
import com.example.mdmbackend.middleware.configureErrorHandling
import com.example.mdmbackend.middleware.configureLogging
import com.example.mdmbackend.middleware.configureSerialization
import com.example.mdmbackend.routes.registerRoutes
import com.example.mdmbackend.service.AuditService
import com.example.mdmbackend.service.EventBusHolder
import com.example.mdmbackend.service.FcmHttpV1WakeupSender
import com.example.mdmbackend.service.FcmWakeupService
import com.example.mdmbackend.service.TelemetryRetentionService
import com.example.mdmbackend.repository.DeviceRepository
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val cfg = AppConfig.from(environment.config)

    require(cfg.seed.defaultDeviceUnlockPass.isNotBlank()) {
        "mdm.seed.defaultDeviceUnlockPass must not be blank"
    }

    configureSerialization()
    configureLogging()
    configureCors(cfg)
    configureErrorHandling()

    DatabaseFactory.init(cfg)
    Seeder.seed(cfg)

    // Register audit subscribers once at startup
    AuditService().registerEventSubscribers(EventBusHolder.bus)
    FcmWakeupService(
        devices = DeviceRepository(),
        sender = FcmHttpV1WakeupSender(cfg.fcm),
    ).register(EventBusHolder.bus)

    // Lightweight retention pass at startup, without introducing scheduler complexity.
    val cleanup = TelemetryRetentionService.fromConfig(cfg).runCleanup()
    environment.log.info(
        "Telemetry retention cleanup: deleted events=${cleanup.eventRowsDeleted}, usage=${cleanup.usageRowsDeleted}, " +
            "eventCutoffEpochMillis=${cleanup.eventCutoffEpochMillis}, usageCutoffEpochMillis=${cleanup.usageCutoffEpochMillis}"
    )

    configureAuth(cfg)

    routing {
        get("/health") {
            call.respond(mapOf("status" to "OK"))
        }
        registerRoutes(cfg)
    }
}
