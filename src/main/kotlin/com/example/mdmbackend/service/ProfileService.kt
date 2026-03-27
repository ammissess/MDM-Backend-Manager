package com.example.mdmbackend.service

import com.example.mdmbackend.dto.ProfileCreateRequest
import com.example.mdmbackend.dto.ProfileResponse
import com.example.mdmbackend.dto.ProfileUpdateRequest
import com.example.mdmbackend.dto.AdminCreateCommandRequest
import com.example.mdmbackend.model.CommandType
import com.example.mdmbackend.repository.DeviceRepository
import com.example.mdmbackend.repository.ProfileRepository
import com.example.mdmbackend.util.toEpochMillis
import java.util.UUID

class ProfileService(
    private val repo: ProfileRepository,
    private val devices: DeviceRepository,
    private val commands: DeviceCommandService,
    private val audit: AuditService,
) {
    fun list(): List<ProfileResponse> = repo.list().map { it.toResponse() }

    fun get(id: UUID): ProfileResponse? = repo.findById(id)?.toResponse()

    fun getByUserCode(userCode: String): ProfileResponse? = repo.findByUserCode(userCode)?.toResponse()

    fun create(req: ProfileCreateRequest, actorUserId: UUID): ProfileResponse {
        val created = repo.create(req)
        recomputeDesiredForProfile(created.id, created.userCode, actorUserId)
        return created.toResponse()
    }

    fun update(id: UUID, req: ProfileUpdateRequest, actorUserId: UUID): ProfileResponse? {
        val updated = repo.update(id, req) ?: return null
        recomputeDesiredForProfile(id, updated.userCode, actorUserId)
        return updated.toResponse()
    }

    fun delete(id: UUID): Boolean = repo.delete(id)

    fun setAllowedApps(id: UUID, apps: List<String>, actorUserId: UUID): ProfileResponse? {
        val updated = repo.setAllowedApps(id, apps) ?: return null
        recomputeDesiredForProfile(id, updated.userCode, actorUserId)
        return updated.toResponse()
    }

    fun recomputeDesiredForProfile(profileId: UUID, userCode: String? = null, actorUserId: UUID): Int {
        val profile = repo.findById(profileId) ?: return 0
        val linkedDevices = devices.list().filter { it.profileId == profileId }
        if (linkedDevices.isEmpty()) return 0

        val fingerprint = repo.buildDesiredConfigFingerprint(profile)
        val desiredVersionEpochMillis = System.currentTimeMillis()
        val changed = linkedDevices
            .filter { it.desiredConfigHash != fingerprint.desiredConfigHash }
            .map {
                DesiredDeviceChange(
                    deviceId = it.id,
                    profileId = profileId,
                    userCode = userCode ?: profile.userCode,
                    oldDesiredHash = it.desiredConfigHash,
                    newDesiredHash = fingerprint.desiredConfigHash,
                    desiredConfigVersionEpochMillis = desiredVersionEpochMillis,
                )
            }

        if (changed.isEmpty()) return 0

        devices.updateDesiredConfigForProfileDevices(
            profileId = profileId,
            desiredConfigHash = fingerprint.desiredConfigHash,
            desiredConfigVersionEpochMillis = desiredVersionEpochMillis,
        )
        emitDesiredLifecycle(changed, actorUserId)
        return changed.size
    }

    private fun emitDesiredLifecycle(changes: List<DesiredDeviceChange>, actorUserId: UUID) {
        changes.forEach { change ->
            audit.logPolicyDesiredChanged(
                actorUserId = actorUserId,
                deviceId = change.deviceId,
                profileId = change.profileId,
                userCode = change.userCode,
                oldDesiredHash = change.oldDesiredHash,
                newDesiredHash = change.newDesiredHash,
                desiredConfigVersionEpochMillis = change.desiredConfigVersionEpochMillis,
            )

            val created = commands.adminCreate(
                deviceId = change.deviceId,
                createdByUserId = actorUserId,
                req = AdminCreateCommandRequest(
                    type = CommandType.REFRESH_CONFIG.wireValue,
                    payload = "{}",
                    ttlSeconds = 600,
                )
            )

            audit.logPolicyRefreshEnqueued(
                actorUserId = actorUserId,
                deviceId = change.deviceId,
                profileId = change.profileId,
                userCode = change.userCode,
                oldDesiredHash = change.oldDesiredHash,
                newDesiredHash = change.newDesiredHash,
                desiredConfigVersionEpochMillis = change.desiredConfigVersionEpochMillis,
                commandId = UUID.fromString(created.id),
            )
        }
    }

    private data class DesiredDeviceChange(
        val deviceId: UUID,
        val profileId: UUID?,
        val userCode: String?,
        val oldDesiredHash: String?,
        val newDesiredHash: String?,
        val desiredConfigVersionEpochMillis: Long,
    )

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
            updatedAtEpochMillis = updatedAt.toEpochMillis(),
        )
    }
}
