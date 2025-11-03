package com.sriox.vasatey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val authHelper = SupabaseAuthHelper()
    private val dbHelper = SupabaseDatabaseHelper()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()

        binding.updateProfileButton.setOnClickListener {
            val newName = binding.nameInput.text.toString().trim()
            val newMobile = binding.mobileInput.text.toString().trim()

            if (newName.isNotEmpty() && newMobile.isNotEmpty()) {
                updateUserProfile(newName, newMobile)
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.changePasswordButton.setOnClickListener {
            showPasswordResetConfirmationDialog()
        }
    }

    private fun loadUserData() {
        val currentUser = authHelper.getCurrentUser()
        if (currentUser != null) {
            lifecycleScope.launch {
                authHelper.getUserProfile(currentUser.email ?: "").fold(
                    onSuccess = { profile ->
                        profile?.let {
                            binding.nameInput.setText(it.fullName)
                            binding.emailInput.setText(it.email)
                            binding.mobileInput.setText(it.phoneNumber ?: "")
                            binding.emailInput.isEnabled = false
                        }
                    },
                    onFailure = { 
                        Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun updateUserProfile(newName: String, newMobile: String) {
        val currentUser = authHelper.getCurrentUser()
        if (currentUser != null) {
            lifecycleScope.launch {
                // Show loading state
                binding.updateProfileButton.isEnabled = false
                binding.updateProfileButton.text = "Updating..."
                
                val updateMap = mutableMapOf<String, Any>()
                updateMap["full_name"] = newName
                if (newMobile.isNotEmpty()) {
                    updateMap["phone_number"] = newMobile
                }
                
                authHelper.updateUserProfile(currentUser.id, updateMap).fold(
                    onSuccess = {
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        binding.updateProfileButton.text = "Update Profile"
                        binding.updateProfileButton.isEnabled = true
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), "Failed to update profile: ${error.message}", Toast.LENGTH_SHORT).show()
                        binding.updateProfileButton.text = "Update Profile"
                        binding.updateProfileButton.isEnabled = true
                    }
                )
            }
        }
    }

    private fun showPasswordResetConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setMessage("Are you sure you want to send a password reset email?")
            .setPositiveButton("Yes") { _, _ ->
                sendPasswordResetEmail()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun sendPasswordResetEmail() {
        val userEmail = authHelper.getCurrentUser()?.email
        if (userEmail != null) {
            // Note: Supabase password reset would need to be implemented in SupabaseAuthHelper
            // For now, just show a message
            Toast.makeText(requireContext(), "Password reset functionality needs to be implemented with Supabase", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
