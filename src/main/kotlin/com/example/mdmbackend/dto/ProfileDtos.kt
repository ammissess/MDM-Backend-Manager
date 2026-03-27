package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfileCreateRequest(
    val userCode: String,
    val name: String = "Profile",
    val description: String = "",
    val allowedApps: List<String> = emptyList(),

    val disableWifi: Boolean = false,
    val disableBluetooth: Boolean = false,
    val disableCamera: Boolean = false,
    val disableStatusBar: Boolean = true,
    val kioskMode: Boolean = true,
    val blockUninstall: Boolean = true,

    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
)

@Serializable
data class ProfileUpdateRequest(
    val name: String? = null,
    val description: String? = null,

    val disableWifi: Boolean? = null,
    val disableBluetooth: Boolean? = null,
    val disableCamera: Boolean? = null,
    val disableStatusBar: Boolean? = null,
    val kioskMode: Boolean? = null,
    val blockUninstall: Boolean? = null,

    val showWifi: Boolean? = null,
    val showBluetooth: Boolean? = null,
)

@Serializable
data class ProfileResponse(
    val id: String,
    val userCode: String,
    val name: String,
    val description: String,
    val allowedApps: List<String>,

    val disableWifi: Boolean,
    val disableBluetooth: Boolean,
    val disableCamera: Boolean,
    val disableStatusBar: Boolean,
    val kioskMode: Boolean,
    val blockUninstall: Boolean,

    val showWifi: Boolean,
    val showBluetooth: Boolean,

    val updatedAtEpochMillis: Long,
)

fun List<String>.normalizeAllowedApps(): List<String> =
    asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .toList()

