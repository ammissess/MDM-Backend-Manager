package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterRequest(
    val deviceCode: String,
    val userCode: String? = null,
    val manufacturer: String = "",
    val model: String = "",
    val serial: String = "",
    val sdkInt: Int = 0,
)

@Serializable
data class DeviceResponse(
    val id: String,
    val deviceCode: String,
    val userCode: String?,
    val manufacturer: String,
    val model: String,
    val serial: String,
    val sdkInt: Int,
    val lastSeenAtEpochMillis: Long,
)

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

@Serializable
data class DeviceEventRequest(
    val type: String,
    val payload: String = "{}",
)
