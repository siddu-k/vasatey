package com.sriox.vasatey.models

data class VercelNotificationRequest(
    val token: String,
    val title: String,
    val body: String
)
