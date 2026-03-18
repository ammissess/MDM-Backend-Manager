package com.example.mdmbackend.routes

import com.example.mdmbackend.config.AppConfig
import io.ktor.server.routing.*

fun Route.registerRoutes(cfg: AppConfig) {
    route("/api") {
        authRoutes(cfg)
        deviceRoutes(cfg)
        adminRoutes()
    }
}