package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if user logged in
        val authHelper = SupabaseAuthHelper()
        val user = authHelper.getCurrentUser()

        if (user != null) {
            // User already logged in → go to Main
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // Not logged in → go to Login
            startActivity(Intent(this, LoginActivity::class.java))
        }

        finish()
    }
}
