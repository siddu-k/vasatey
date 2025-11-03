package com.sriox.vasatey

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sriox.vasatey.databinding.ActivityAlertDetailsBinding

class AlertDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve the data passed from the notification
        val name = intent.getStringExtra("USER_NAME") ?: "N/A"
        val email = intent.getStringExtra("USER_EMAIL") ?: "N/A"
        val mobile = intent.getStringExtra("USER_MOBILE") ?: "N/A"

        // Set the text in the TextViews
        binding.nameTextView.text = "User Name: $name"
        binding.emailTextView.text = "Email: $email"
        binding.mobileTextView.text = "Mobile: $mobile"
    }
}
