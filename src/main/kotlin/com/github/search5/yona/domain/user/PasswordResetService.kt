package com.github.search5.yona.domain.user

interface PasswordResetService {
    fun generateResetHash(loginId: String): String
    fun addHashToResetTable(loginId: String, hashString: String)
    fun isValidResetHash(hashString: String): Boolean
    fun resetPassword(hashString: String, newPassword: String): Boolean
}
