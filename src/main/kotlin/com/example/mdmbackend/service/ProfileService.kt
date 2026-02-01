package com.example.mdmbackend.service

import com.example.mdmbackend.dto.ProfileCreateRequest
import com.example.mdmbackend.dto.ProfileResponse
import com.example.mdmbackend.dto.ProfileUpdateRequest
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.util.toEpochMillis
import java.util.UUID

class ProfileService(
    private val repo: ProfileRepository,
) {
    fun list(): List<ProfileResponse> = repo.list().map { it.toResponse() }

    fun get(id: UUID): ProfileResponse? = repo.findById(id)?.toResponse()

    fun getByUserCode(userCode: String): ProfileResponse? = repo.findByUserCode(userCode)?.toResponse()

    fun create(req: ProfileCreateRequest): ProfileResponse = repo.create(req).toResponse()

    fun update(id: UUID, req: ProfileUpdateRequest): ProfileResponse? = repo.update(id, req)?.toResponse()

    fun delete(id: UUID): Boolean = repo.delete(id)

    fun setAllowedApps(id: UUID, apps: List<String>): ProfileResponse? = repo.setAllowedApps(id, apps)?.toResponse()

    private fun com.example.mdmbackend.repository.ProfileRecord.toResponse(): ProfileResponse {
        return ProfileResponse(
            id = id.toString(),
            userCode = userCode,
            name = name,
            description = description,
            allowedApps = allowedApps,
            disableWifi = disableWifi,
            disableBluetooth = disableBluetooth,
            disableCamera = disableCamera,
            disableStatusBar = disableStatusBar,
            kioskMode = kioskMode,
            blockUninstall = blockUninstall,
            showWifi = showWifi,
            showBluetooth = showBluetooth,
            updatedAtEpochMillis = updatedAt.toEpochMillis(),
        )
    }
}
