package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.sriox.vasatey.databinding.ActivitySignupBinding
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var authHelper: SupabaseAuthHelper
    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authHelper = SupabaseAuthHelper()

        binding.signupBtn.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()
            val mobileNumber = binding.mobileInput.text.toString().trim()
            val school = binding.schoolInput.text.toString().trim()
            val pet = binding.petInput.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || 
                mobileNumber.isEmpty() || school.isEmpty() || pet.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading state
            binding.signupBtn.isEnabled = false
            binding.signupBtn.text = "Creating account..."

            FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
                lifecycleScope.launch {
                    val userProfile = UserProfile(
                        email = email,
                        fullName = name,
                        fcmToken = fcmToken,
                        phoneNumber = mobileNumber,
                        emergencyContact = mobileNumber, // Use mobile as emergency contact for now
                        medicalInfo = "School: $school, Pet: $pet" // Combine school and pet info
                    )

                    Log.d("SignupActivity", "Creating user profile: $userProfile")
                    authHelper.signUp(email, password, userProfile).fold(
                        onSuccess = { user ->
                            Log.d("SignupActivity", "User created successfully: ${user.email}")
                            Toast.makeText(this@SignupActivity, "Signup successful!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                            finish()
                        },
                        onFailure = { exception ->
                            Log.e("SignupActivity", "Signup failed", exception)
                            binding.signupBtn.isEnabled = true
                            binding.signupBtn.text = "Sign Up"
                            Toast.makeText(
                                this@SignupActivity,
                                "Signup failed: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }.addOnFailureListener { e ->
                binding.signupBtn.isEnabled = true
                binding.signupBtn.text = "Sign Up"
                Log.e("SignupActivity", "Failed to get FCM token", e)
                Toast.makeText(this, "Could not get notification token.", Toast.LENGTH_LONG).show()
            }
        }

        binding.loginRedirect.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
