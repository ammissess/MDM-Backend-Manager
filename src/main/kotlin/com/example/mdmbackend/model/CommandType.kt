package com.example.mdmbackend.model

enum class CommandType(val wireValue: String) {
    LOCK_SCREEN("lock_screen"),
    REFRESH_CONFIG("refresh_config"),
    SYNC_CONFIG("sync_config");

    companion object {
        fun fromInput(rawType: String): CommandType? {
            val trimmed = rawType.trim()
            if (trimmed.isEmpty()) return null

            val normalizedWire = trimmed.lowercase().replace('-', '_')
            return entries.firstOrNull { it.wireValue == normalizedWire }
                ?: runCatching { valueOf(trimmed.uppercase()) }.getOrNull()
        }

        fun supportedWireValues(): String = entries.joinToString(", ") { it.wireValue }
    }
}

