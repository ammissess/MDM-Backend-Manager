package com.example.mdmbackend.util

import org.mindrot.jbcrypt.BCrypt

object PasswordHasher {
    fun hash(plain: String): String = BCrypt.hashpw(plain, BCrypt.gensalt(12))

    fun verify(plain: String, hash: String): Boolean = try {
        BCrypt.checkpw(plain, hash)
    } catch (_: Throwable) {
        false
    }
}
