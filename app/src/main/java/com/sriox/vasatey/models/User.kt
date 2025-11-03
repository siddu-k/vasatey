package com.sriox.vasatey.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val fcmToken: String? = null,
    val securityQuestions: Map<String, String> = mapOf(),
    val guardians: List<String> = listOf(),
    val createdAt: String? = null
)
