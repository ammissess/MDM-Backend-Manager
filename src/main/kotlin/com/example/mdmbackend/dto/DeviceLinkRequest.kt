package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceLinkRequest(
    val userCode: String? = null
)
