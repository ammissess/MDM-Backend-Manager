package com.example.mdmbackend.service

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

interface DomainEvent {
    val occurredAt: Instant
}

data class DeviceRegisteredEvent(
    val deviceId: UUID,
    val deviceCode: String,
    val status: String,
    val actorType: String,
    val actorUserId: UUID?,
    val actorDeviceCode: String?,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

data class DeviceUnlockedEvent(
    val deviceCode: String,
    val status: String,
    val actorType: String,
    val actorUserId: UUID?,
    val actorDeviceCode: String?,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

data class ProfileLinkedEvent(
    val deviceId: UUID,
    val userCode: String?,
    val actorUserId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

data class CommandCreatedEvent(
    val commandId: UUID,
    val deviceId: UUID,
    val type: String,
    val ttlSeconds: Long,
    val actorUserId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

data class TelemetryReceivedEvent(
    val telemetryType: String, // location|usage|usage_batch|event
    val deviceCode: String,
    val actorType: String,
    val actorUserId: UUID?,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

typealias EventHandler<T> = (T) -> Unit

class EventBus {
    private val subscribers = CopyOnWriteArrayList<(DomainEvent) -> Unit>()

    fun subscribe(handler: (DomainEvent) -> Unit) {
        subscribers += handler
    }

    fun publish(event: DomainEvent) {
        subscribers.forEach { it.invoke(event) }
    }
}

object EventBusHolder {
    val bus: EventBus = EventBus()
}