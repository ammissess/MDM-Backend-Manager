package com.example.mdmbackend.repository

import com.example.mdmbackend.model.Role
import com.example.mdmbackend.model.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class UserRecord(
    val id: UUID,
    val username: String,
    val passwordHash: String,
    val role: Role,
)

class UserRepository {
    fun findByUsername(username: String): UserRecord? = transaction {
        UsersTable
            .selectAll().where{ UsersTable.username eq username }
            .limit(1)
            .map { row ->
                UserRecord(
                    id = row[UsersTable.id].value,
                    username = row[UsersTable.username],
                    passwordHash = row[UsersTable.passwordHash],
                    role = Role.valueOf(row[UsersTable.role]),
                )
            }
            .firstOrNull()
    }

    fun findById(id: UUID): UserRecord? = transaction {
        UsersTable
            .selectAll().where{ UsersTable.id eq id }
            .limit(1)
            .map { row ->
                UserRecord(
                    id = row[UsersTable.id].value,
                    username = row[UsersTable.username],
                    passwordHash = row[UsersTable.passwordHash],
                    role = Role.valueOf(row[UsersTable.role]),
                )
            }
            .firstOrNull()
    }
}
