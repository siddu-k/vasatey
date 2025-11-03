package com.sriox.vasatey

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    @SerialName("auth_user_id") val authUserId: String = "",
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("emergency_contact") val emergencyContact: String? = null,
    @SerialName("medical_info") val medicalInfo: String? = null,
    @SerialName("blood_type") val bloodType: String? = null,
    val allergies: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null,
    @SerialName("is_emergency_mode") val isEmergencyMode: Boolean = false,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("last_known_latitude") val lastKnownLatitude: Double? = null,
    @SerialName("last_known_longitude") val lastKnownLongitude: Double? = null,
    @SerialName("last_location_update") val lastLocationUpdate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserSettings(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("voice_detection_enabled") val voiceDetectionEnabled: Boolean = true,
    @SerialName("voice_sensitivity") val voiceSensitivity: Float = 0.7f,
    @SerialName("wake_word") val wakeWord: String = "hey vasatey",
    @SerialName("picovoice_access_key") val picovoiceAccessKey: String? = null,
    @SerialName("auto_location_sharing") val autoLocationSharing: Boolean = true,
    @SerialName("emergency_auto_call") val emergencyAutoCall: Boolean = false,
    @SerialName("notification_sound") val notificationSound: Boolean = true,
    @SerialName("notification_vibration") val notificationVibration: Boolean = true,
    @SerialName("dark_mode") val darkMode: Boolean = false,
    val language: String = "en",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Guardian(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("guardian_name") val guardianName: String = "",
    @SerialName("guardian_phone") val guardianPhone: String = "",
    @SerialName("guardian_email") val guardianEmail: String? = null,
    val relationship: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Alert(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("alert_type") val alertType: String = "",
    val severity: String = "medium",
    @SerialName("trigger_method") val triggerMethod: String = "",
    @SerialName("location_address") val locationAddress: String? = null,
    @SerialName("location_latitude") val locationLatitude: Double? = null,
    @SerialName("location_longitude") val locationLongitude: Double? = null,
    @SerialName("alert_message") val alertMessage: String? = null,
    @SerialName("is_resolved") val isResolved: Boolean = false,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)