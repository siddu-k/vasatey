package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sriox.vasatey.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var authHelper: SupabaseAuthHelper
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = SupabaseAuthHelper()

        // Check if user is already logged in
        if (authHelper.getCurrentUser() != null) {
            getAndSaveFcmToken()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Initialize UI if not logged in
        initializeUI()
    }
    
    private fun initializeUI() {
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading state
            binding.loginButton.isEnabled = false
            binding.loginButton.text = "Signing in..."

            lifecycleScope.launch {
                authHelper.signIn(email, password).fold(
                    onSuccess = { user ->
                        getAndSaveFcmToken()
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    },
                    onFailure = { exception ->
                        binding.loginButton.isEnabled = true
                        binding.loginButton.text = "Login"
                        Toast.makeText(
                            this@LoginActivity,
                            "Login failed: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        binding.signupRedirect.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun getAndSaveFcmToken() {
        // Force delete any existing token first to ensure we get a fresh one
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { deleteTask ->
            if (deleteTask.isSuccessful) {
                Log.d("LoginActivity", "Successfully deleted old FCM token")
            }
            
            // Get fresh token (whether delete succeeded or not)
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val currentUser = authHelper.getCurrentUser()
                if (currentUser != null && currentUser.email != null) {
                    lifecycleScope.launch {
                        val dbHelper = SupabaseDatabaseHelper()
                        dbHelper.updateFCMToken(currentUser.id, token).fold(
                            onSuccess = { 
                                Log.d("LoginActivity", "Fresh FCM token saved successfully for user: ${currentUser.id}")
                                Log.d("LoginActivity", "Token: ${token.take(20)}...")
                            },
                            onFailure = { error ->
                                Log.e("LoginActivity", "Failed to save FCM token", error)
                            }
                        )
                    }
                } else {
                    Log.w("LoginActivity", "Cannot save FCM token - user not logged in")
                }
            }.addOnFailureListener { error ->
                Log.e("LoginActivity", "Failed to get FCM token", error)
            }
        }
    }
}
