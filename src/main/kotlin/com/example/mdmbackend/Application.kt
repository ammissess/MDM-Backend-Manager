package com.example.mdmbackend

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.config.DatabaseFactory
import com.example.mdmbackend.config.Seeder
import com.example.mdmbackend.middleware.configureAuth
import com.example.mdmbackend.middleware.configureCors
import com.example.mdmbackend.middleware.configureErrorHandling
import com.example.mdmbackend.middleware.configureSerialization
import com.example.mdmbackend.middleware.configureLogging
import com.example.mdmbackend.routes.registerRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val cfg = AppConfig.from(environment.config)

    configureSerialization()
    configureLogging()
    configureCors(cfg)
    configureErrorHandling()

    DatabaseFactory.init(cfg)
    Seeder.seed(cfg)

    configureAuth(cfg)

    routing {
        get("/health") {
            call.respond(mapOf("status" to "OK"))
        }
        registerRoutes(cfg)
    }
}
