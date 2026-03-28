package com.example.mdmbackend.service

import com.example.mdmbackend.config.AppConfig
import com.example.mdmbackend.repository.DeviceAppUsageRepository
import com.example.mdmbackend.repository.DeviceRepository
import java.time.Instant

data class TelemetryRetentionPolicy(
    val eventRetentionDays: Long,
    val usageRetentionDays: Long,
)

data class TelemetryCleanupResult(
    val eventRowsDeleted: Int,
    val usageRowsDeleted: Int,
    val eventCutoffEpochMillis: Long,
    val usageCutoffEpochMillis: Long,
)

class TelemetryRetentionService(
    private val devices: DeviceRepository,
    private val usage: DeviceAppUsageRepository,
    private val policy: TelemetryRetentionPolicy,
) {

    fun runCleanup(now: Instant = Instant.now()): TelemetryCleanupResult {
        val eventCutoff = now.minusSeconds(policy.eventRetentionDays.coerceAtLeast(1) * 24 * 60 * 60)
        val usageCutoff = now.minusSeconds(policy.usageRetentionDays.coerceAtLeast(1) * 24 * 60 * 60)

        val eventRowsDeleted = devices.cleanupEventsOlderThan(eventCutoff)
        val usageRowsDeleted = usage.cleanupUsageOlderThan(usageCutoff)

        return TelemetryCleanupResult(
            eventRowsDeleted = eventRowsDeleted,
            usageRowsDeleted = usageRowsDeleted,
            eventCutoffEpochMillis = eventCutoff.toEpochMilli(),
            usageCutoffEpochMillis = usageCutoff.toEpochMilli(),
        )
    }

    companion object {
        fun fromConfig(
            cfg: AppConfig,
            devices: DeviceRepository = DeviceRepository(),
            usage: DeviceAppUsageRepository = DeviceAppUsageRepository(),
        ): TelemetryRetentionService = TelemetryRetentionService(
            devices = devices,
            usage = usage,
            policy = TelemetryRetentionPolicy(
                eventRetentionDays = cfg.telemetry.eventRetentionDays,
                usageRetentionDays = cfg.telemetry.usageRetentionDays,
            )
        )
    }
}

