package com.example.mdmbackend.config

import com.example.mdmbackend.model.*
import com.example.mdmbackend.util.PasswordHasher
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object Seeder {

    fun seed(cfg: AppConfig) {
        val seed = cfg.seed

        transaction {
            // 1) Seed users
            val adminExists = UsersTable
                .selectAll()
                .where { UsersTable.username eq seed.adminUser }
                .any()

            if (!adminExists) {
                UsersTable.insert {
                    it[id] = UUID.randomUUID()
                    it[username] = seed.adminUser
                    it[passwordHash] = PasswordHasher.hash(seed.adminPass)
                    it[role] = Role.ADMIN.name
                    it[createdAt] = Instant.now()
                }
            }

            val deviceExists = UsersTable
                .selectAll()
                .where { UsersTable.username eq seed.deviceUser }
                .any()

            if (!deviceExists) {
                UsersTable.insert {
                    it[id] = UUID.randomUUID()
                    it[username] = seed.deviceUser
                    it[passwordHash] = PasswordHasher.hash(seed.devicePass)
                    it[role] = Role.DEVICE.name
                    it[createdAt] = Instant.now()
                }
            }

            // 2) Seed default profile
            val profileExists = ProfilesTable
                .selectAll()
                .where { ProfilesTable.userCode eq seed.defaultUserCode }
                .any()

            if (!profileExists) {
                val profileId = UUID.randomUUID()
                ProfilesTable.insert {
                    it[id] = profileId
                    it[userCode] = seed.defaultUserCode
                    it[name] = "Default profile"
                    it[description] = "Seed profile (settings + chrome)"

                    // Flags mặc định phù hợp hướng kiosk cơ bản
                    it[disableWifi] = false
                    it[disableBluetooth] = false
                    it[disableCamera] = false
                    it[disableStatusBar] = true
                    it[kioskMode] = true
                    it[blockUninstall] = true

                    // UI flags (để app hiển thị/ẩn nút)
                    it[showWifi] = true
                    it[showBluetooth] = true

                    it[updatedAt] = Instant.now()
                }

                seed.defaultAllowedApps.forEach { pkg ->
                    ProfileAllowedAppsTable.insert {
                        it[this.profileId] = profileId
                        it[packageName] = pkg
                    }
                }
            }
        }
    }
}
