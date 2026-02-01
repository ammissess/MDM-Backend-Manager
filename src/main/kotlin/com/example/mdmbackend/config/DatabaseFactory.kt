package com.example.mdmbackend.config

import com.example.mdmbackend.model.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun init(cfg: AppConfig) {
        val hikari = HikariConfig().apply {
            jdbcUrl = cfg.db.jdbcUrl
            driverClassName = cfg.db.driver
            username = cfg.db.user
            password = cfg.db.password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        dataSource = HikariDataSource(hikari)
        Database.connect(dataSource!!)

        transaction {
            SchemaUtils.create(
                UsersTable,
                SessionsTable,
                ProfilesTable,
                ProfileAllowedAppsTable,
                DevicesTable,
                DeviceEventsTable,
            )
        }
    }

    fun close() {
        dataSource?.close()
    }
}