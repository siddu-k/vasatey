package com.sriox.vasatey.models

data class VercelNotificationRequest(
    val token: String,
    val title: String,
    val body: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String?,
    val lastKnownLatitude: Double?,
    val lastKnownLongitude: Double?
)
