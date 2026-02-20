package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceConfigResponse(
    val userCode: String,
    val allowedApps: List<String>,

    val disableWifi: Boolean,
    val disableBluetooth: Boolean,
    val disableCamera: Boolean,
    val disableStatusBar: Boolean,
    val kioskMode: Boolean,
    val blockUninstall: Boolean,

    val showWifi: Boolean,
    val showBluetooth: Boolean,

    val configVersionEpochMillis: Long,
)
