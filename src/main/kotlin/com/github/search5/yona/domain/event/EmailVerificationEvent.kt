package com.github.search5.yona.domain.event

data class EmailVerificationEvent(
    val email: String,
    val userName: String,
    val confirmUrl: String
)
