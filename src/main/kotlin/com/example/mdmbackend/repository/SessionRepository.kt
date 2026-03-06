package com.example.mdmbackend.repository

import com.example.mdmbackend.model.SessionsTable
import com.example.mdmbackend.model.UsersTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class SessionRecord(
    val token: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val deviceCode: String? = null,
)

class SessionRepository {

/*
    fun create(userId: UUID, expiresAt: Instant): SessionRecord = transaction {
        val token = UUID.randomUUID()

        SessionsTable.insert {
            it[SessionsTable.token] = token
            it[SessionsTable.userId] = EntityID(userId, UsersTable)   // ✅ convert UUID -> EntityID
            it[SessionsTable.expiresAt] = expiresAt
        }

        SessionRecord(token, userId, expiresAt)
    }
*/

    fun create(userId: UUID, expiresAt: Instant, deviceCode: String?): SessionRecord = transaction {
        val token = UUID.randomUUID()
        SessionsTable.insert {
            it[SessionsTable.token] = token
            it[SessionsTable.userId] = EntityID(userId, UsersTable)
            it[SessionsTable.expiresAt] = expiresAt
            it[SessionsTable.deviceCode] = deviceCode
        }
        SessionRecord(token, userId, expiresAt, deviceCode)
    }

/*    fun find(token: UUID): SessionRecord? = transaction {
        SessionsTable
            .selectAll()
            .where { SessionsTable.token eq token }
            .limit(1)
            .map { row ->
                SessionRecord(
                    token = row[SessionsTable.token],
                    userId = row[SessionsTable.userId].value,          // ✅ EntityID -> UUID
                    expiresAt = row[SessionsTable.expiresAt]
                )
            }
            .firstOrNull()
    }*/

    fun find(token: UUID): SessionRecord? = transaction {
        SessionsTable.selectAll().where { SessionsTable.token eq token }.limit(1).map { row ->
            SessionRecord(
                token = row[SessionsTable.token],
                userId = row[SessionsTable.userId].value,
                expiresAt = row[SessionsTable.expiresAt],
                deviceCode = row[SessionsTable.deviceCode]
            )
        }.firstOrNull()
    }

    fun delete(token: UUID): Boolean = transaction {
        SessionsTable.deleteWhere { SessionsTable.token eq token } > 0
    }

    fun deleteExpired(now: Instant = Instant.now()): Int = transaction {
        SessionsTable.deleteWhere { SessionsTable.expiresAt lessEq now }
    }
}
