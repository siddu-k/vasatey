package com.sriox.vasatey

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.ActivityAddGuardianBinding
import kotlinx.coroutines.launch

class AddGuardianActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddGuardianBinding
    private val supabaseAuthHelper = SupabaseAuthHelper()
    private val supabaseDatabaseHelper = SupabaseDatabaseHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddGuardianBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addGuardianButton.setOnClickListener {
            val email = binding.guardianEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, getString(R.string.please_enter_guardian_email), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addGuardianToSupabase(email)
        }
    }

    private fun addGuardianToSupabase(email: String) {
        val currentUser = supabaseAuthHelper.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, getString(R.string.user_not_logged_in), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val guardian = Guardian(
                userId = currentUser.id, 
                guardianEmail = email,
                guardianName = "Guardian", // Default name
                guardianPhone = "0000000000" // Default phone
            )
            val result = supabaseDatabaseHelper.addGuardian(currentUser.id, guardian)
            result.fold(
                onSuccess = {
                    binding.statusText.text = getString(R.string.add_guardian_success)
                    binding.guardianEmail.text.clear()
                },
                onFailure = { e ->
                    binding.statusText.text = getString(R.string.add_guardian_failure, e.message)
                }
            )
        }
    }
}
