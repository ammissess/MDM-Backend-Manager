package com.example.mdmbackend.dto

import kotlinx.serialization.Serializable

/**
 * Register/heartbeat từ DO -> Backend.
 * (Thiết kế mới: lấy tạm pin/wifi + status lock/unlock để test)
 */
@Serializable
data class DeviceRegisterRequest(
    val deviceCode: String,

    // thiết bị
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val manufacturer: String = "",
    val model: String = "",
    val imei: String = "",
    val serial: String = "",

    // trạng thái tạm để test
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val wifiEnabled: Boolean = false,
)

@Serializable
data class DeviceRegisterResponse(
    val deviceId: String,
    val deviceCode: String,
    val status: String,           // ACTIVE / LOCKED
    val message: String? = null
)

/**
 * Trả về thông tin thiết bị (admin list hoặc debug)
 */
@Serializable
data class DeviceResponse(
    val id: String,
    val deviceCode: String,
    val userCode: String? = null,


    // thông tin thiết bị
    val androidVersion: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val imei: String,
    val serial: String,

    // trạng thái tạm để test
    val batteryLevel: Int,
    val isCharging: Boolean,
    val wifiEnabled: Boolean,

    // trạng thái khóa
    val status: String, // ACTIVE/LOCKED

    val lastSeenAtEpochMillis: Long,
)

/**
 * Thiết bị nhập pass để mở khóa
 */
@Serializable
data class DeviceUnlockRequest(
    val deviceCode: String,
    val password: String
)

@Serializable
data class DeviceUnlockResponse(
    val status: String,   // ACTIVE / LOCKED
    val message: String
)

/**
 * Private info: update GPS 1 phút/lần
 */
@Serializable
data class LocationUpdateRequest(
    val deviceCode: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double = 0.0
)

/**
 * Usage app cá nhân (bảng riêng)
 */
@Serializable
data class UsageReportRequest(
    val deviceCode: String,
    val packageName: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val durationMs: Long
)

/**
 * Event/log từ device (tùy dùng)
 */
@Serializable
data class DeviceEventRequest(
    val type: String,
    val payload: String = "{}",
)
