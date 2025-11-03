package com.sriox.vasatey

import android.util.Log
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SupabaseAuthHelper {
    
    private val supabase = SupabaseClient.client
    
    suspend fun signUp(email: String, password: String, userProfile: UserProfile): Result<UserInfo> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseAuth", "Starting signup for email: $email")
                
                // Create auth user
                val authResult = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                Log.d("SupabaseAuth", "Auth user created successfully")
                
                // Get the current user
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser != null) {
                    Log.d("SupabaseAuth", "Current user ID: ${currentUser.id}")
                    
                    // Save user profile to database with auth_user_id
                    val profileToSave = userProfile.copy(
                        authUserId = currentUser.id
                    )
                    Log.d("SupabaseAuth", "Attempting to save profile: $profileToSave")
                    
                    try {
                        supabase.from("user_profiles").insert(profileToSave)
                        Log.d("SupabaseAuth", "Profile saved successfully to user_profiles table")
                    } catch (dbError: Exception) {
                        Log.e("SupabaseAuth", "Database error while saving profile", dbError)
                        throw Exception("Failed to save user profile: ${dbError.message}")
                    }
                    
                    Result.success(currentUser)
                } else {
                    Log.e("SupabaseAuth", "Failed to get current user after signup")
                    Result.failure(Exception("Failed to get user after signup"))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Sign up failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun signIn(email: String, password: String): Result<UserInfo> {
        return try {
            withContext(Dispatchers.IO) {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser != null) {
                    Result.success(currentUser)
                } else {
                    Result.failure(Exception("Failed to get user after signin"))
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Sign in failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun signOut(): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                Log.d("SupabaseAuth", "Signing out user")
                supabase.auth.signOut()
                Log.d("SupabaseAuth", "User signed out successfully")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Sign out failed", e)
            Result.failure(e)
        }
    }
    
    fun getCurrentUser(): UserInfo? {
        return supabase.auth.currentUserOrNull()
    }
    
    fun isUserLoggedIn(): Boolean {
        return supabase.auth.currentUserOrNull() != null
    }
    
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                supabase.auth.resetPasswordForEmail(email)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Password reset failed", e)
            Result.failure(e)
        }
    }
    
    // Get user profile from database
    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        return try {
            withContext(Dispatchers.IO) {
                val result = supabase.from("user_profiles")
                    .select()
                    .decodeSingleOrNull<UserProfile>()
                
                Result.success(result)
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Failed to get user profile", e)
            Result.failure(e)
        }
    }
    
    // Update user profile in database
    suspend fun updateUserProfile(userId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                supabase.from("user_profiles")
                    .update(updates)
                
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Failed to update user profile", e)
            Result.failure(e)
        }
    }
}