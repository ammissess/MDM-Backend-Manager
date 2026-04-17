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
    val lockPrivateDnsConfig: Boolean,
    val lockVpnConfig: Boolean,
    val blockDebuggingFeatures: Boolean,
    val disableUsbDataSignaling: Boolean,
    val disallowSafeBoot: Boolean,
    val disallowFactoryReset: Boolean,

    val configVersionEpochMillis: Long,
)

@Serializable
data class CanonicalDesiredConfig(
    val userCode: String,
    val allowedApps: List<String>,
    val disableWifi: Boolean,
    val disableBluetooth: Boolean,
    val disableCamera: Boolean,
    val disableStatusBar: Boolean,
    val kioskMode: Boolean,
    val blockUninstall: Boolean,
    val lockPrivateDnsConfig: Boolean,
    val lockVpnConfig: Boolean,
    val blockDebuggingFeatures: Boolean,
    val disableUsbDataSignaling: Boolean,
    val disallowSafeBoot: Boolean,
    val disallowFactoryReset: Boolean,
)

