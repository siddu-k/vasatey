package com.sriox.vasatey

import android.util.Log
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseDatabaseHelper {
    
    private val supabase = SupabaseClient.client
    
    // Get user profile ID from auth user ID
    suspend fun getUserProfileId(authUserId: String): Result<String?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseDB", "Getting user profile ID for auth user: $authUserId")
                val result = supabase.from("user_profiles")
                    .select()
                    .decodeList<UserProfile>()
                    .firstOrNull { it.authUserId == authUserId }
                
                val profileId = result?.id
                Log.d("SupabaseDB", "Found profile ID: $profileId")
                Result.success(profileId)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to get user profile ID", e)
            Result.failure(e)
        }
    }

    // Save user settings to Supabase
    suspend fun saveUserSettings(authUserId: String, settings: UserSettings): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseDB", "Saving user settings for auth user: $authUserId")
                
                // First get the user profile ID
                val profileIdResult = getUserProfileId(authUserId)
                val profileId = profileIdResult.getOrNull()
                
                if (profileId == null) {
                    Log.e("SupabaseDB", "Could not find user profile for auth user: $authUserId")
                    return@withContext Result.failure(Exception("User profile not found"))
                }
                
                // Check if settings already exist for this user
                val existingSettings = supabase.from("user_settings")
                    .select()
                    .decodeList<UserSettings>()
                    .firstOrNull { it.userId == profileId }
                
                val settingsToSave = settings.copy(userId = profileId)
                Log.d("SupabaseDB", "Settings data with profile ID: $settingsToSave")
                
                if (existingSettings != null) {
                    // Update existing settings
                    Log.d("SupabaseDB", "Updating existing settings for user: $profileId")
                    supabase.from("user_settings")
                        .update(settingsToSave) {
                            filter {
                                eq("user_id", profileId)
                            }
                        }
                } else {
                    // Insert new settings
                    Log.d("SupabaseDB", "Inserting new settings for user: $profileId")
                    supabase.from("user_settings")
                        .insert(settingsToSave)
                }
                
                Log.d("SupabaseDB", "User settings saved successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to save user settings", e)
            Result.failure(e)
        }
    }
    
    // Get user settings from Supabase
    suspend fun getUserSettings(authUserId: String): Result<UserSettings?> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseDB", "Getting user settings for auth user: $authUserId")
                
                // First get the user profile ID
                val profileIdResult = getUserProfileId(authUserId)
                val profileId = profileIdResult.getOrNull()
                
                if (profileId == null) {
                    Log.e("SupabaseDB", "Could not find user profile for auth user: $authUserId")
                    return@withContext Result.success(null)
                }
                
                val result = supabase.from("user_settings")
                    .select()
                    .decodeList<UserSettings>()
                    .firstOrNull { it.userId == profileId }
                
                Log.d("SupabaseDB", "Retrieved settings: $result")
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to get user settings", e)
            Result.failure(e)
        }
    }
    
    // Save alert to Supabase
    suspend fun saveAlert(alert: Alert): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                supabase.from("alerts")
                    .insert(alert)
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to save alert", e)
            Result.failure(e)
        }
    }
    
    // Get alerts for user from Supabase
    suspend fun getAlertsForUser(userId: String): Result<List<Alert>> {
        return try {
            withContext(Dispatchers.IO) {
                val result = supabase.from("alerts")
                    .select()
                    .decodeList<Alert>()
                    .filter { it.userId == userId }
                    .sortedByDescending { it.createdAt }
                
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to get alerts", e)
            Result.failure(e)
        }
    }
    
    // Add guardian to Supabase
    suspend fun addGuardian(userId: String, guardian: Guardian): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                supabase.from("guardians")
                    .insert(guardian.copy(userId = userId))
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to add guardian", e)
            Result.failure(e)
        }
    }
    
    // Get guardians for user from Supabase
    suspend fun getGuardiansForUser(userId: String): Result<List<Guardian>> {
        return try {
            withContext(Dispatchers.IO) {
                val result = supabase.from("guardians")
                    .select()
                    .decodeList<Guardian>()
                
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to get guardians", e)
            Result.failure(e)
        }
    }
    
    // Remove guardian from Supabase
    suspend fun removeGuardian(guardianId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                supabase.from("guardians")
                    .delete()
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to remove guardian", e)
            Result.failure(e)
        }
    }
    
    // Update FCM token for user
    suspend fun updateFCMToken(userId: String, token: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val updates = mapOf("fcm_token" to token)
                supabase.from("user_profiles")
                    .update(updates) {
                        filter {
                            eq("id", userId)
                        }
                    }
                
                Log.d("SupabaseDB", "FCM token updated for user: $userId")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to update FCM token for user $userId", e)
            Result.failure(e)
        }
    }
    
    // Get FCM tokens for guardians
    suspend fun getGuardianTokens(guardianIds: List<String>): Result<List<String>> {
        return try {
            withContext(Dispatchers.IO) {
                if (guardianIds.isEmpty()) {
                    Log.d("SupabaseDB", "No guardian IDs provided")
                    return@withContext Result.success(emptyList())
                }
                
                Log.d("SupabaseDB", "Getting FCM tokens for guardians: $guardianIds")
                
                val result = supabase.from("user_profiles")
                    .select("fcm_token")
                    .`in`("id", guardianIds)
                    .decodeList<Map<String, String>>()
                
                val tokens = result.mapNotNull { row -> 
                    row["fcm_token"]?.takeIf { it.isNotBlank() }
                }
                
                Log.d("SupabaseDB", "Found ${tokens.size} valid FCM tokens for ${guardianIds.size} guardians")
                Result.success(tokens)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to get guardian tokens", e)
            Result.failure(e)
        }
    }
    
    // Clean up invalid FCM token when we get registration error
    suspend fun clearInvalidFcmToken(userId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val updates = mapOf("fcm_token" to null)
                supabase.from("user_profiles")
                    .update(updates) {
                        filter {
                            eq("id", userId)
                        }
                    }
                
                Log.d("SupabaseDB", "Cleared invalid FCM token for user: $userId")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseDB", "Failed to clear invalid FCM token for user $userId", e)
            Result.failure(e)
        }
    }
}