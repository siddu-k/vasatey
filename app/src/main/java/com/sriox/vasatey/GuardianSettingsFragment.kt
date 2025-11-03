package com.sriox.vasatey

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentGuardianSettingsBinding
import kotlinx.coroutines.launch

class GuardianSettingsFragment : Fragment() {

    private var _binding: FragmentGuardianSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var guardiansAdapter: ArrayAdapter<String>

    private val authHelper = SupabaseAuthHelper()
    private val dbHelper = SupabaseDatabaseHelper()
    private val currentUserAuthId get() = authHelper.getCurrentUser()?.id ?: ""

    private val guardiansList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGuardianSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        guardiansAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, guardiansList)
        binding.guardiansListView.adapter = guardiansAdapter

        loadGuardians()

        binding.addGuardianButton.setOnClickListener {
            val email = binding.guardianEmailInput.text.toString().trim()
            val name = binding.guardianNameInput.text.toString().trim()
            val phone = binding.guardianPhoneInput.text.toString().trim()
            
            if (email.isNotEmpty()) {
                addGuardian(email, name, phone)
            } else {
                Toast.makeText(requireContext(), "Please enter guardian email", Toast.LENGTH_SHORT).show()
            }
        }

        binding.guardiansListView.setOnItemLongClickListener { _, _, position, _ ->
            val guardianEmail = guardiansList[position]
            removeGuardian(guardianEmail)
            true
        }
    }

    private fun loadGuardians() {
        if (currentUserAuthId.isEmpty()) return
        
        lifecycleScope.launch {
            // First get the user profile ID
            val profileIdResult = dbHelper.getUserProfileId(currentUserAuthId)
            val profileId = profileIdResult.getOrNull()
            
            if (profileId == null) {
                Toast.makeText(requireContext(), "User profile not found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            dbHelper.getGuardiansForUser(profileId).fold(
                onSuccess = { guardians ->
                    guardiansList.clear()
                    guardiansList.addAll(guardians.map { "${it.guardianName} (${it.guardianEmail ?: "No Email"})" })
                    guardiansAdapter.notifyDataSetChanged()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Failed to load guardians: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun addGuardian(email: String, name: String = "", phone: String = "") {
        if (currentUserAuthId.isEmpty()) {
            Log.e("GuardianSettings", "Current user auth ID is empty")
            return
        }
        
        Log.d("GuardianSettings", "Adding guardian: email=$email, name=$name, phone=$phone")
        Log.d("GuardianSettings", "Current user auth ID: $currentUserAuthId")
        
        lifecycleScope.launch {
            // First get the user profile ID
            val profileIdResult = dbHelper.getUserProfileId(currentUserAuthId)
            val profileId = profileIdResult.getOrNull()
            
            Log.d("GuardianSettings", "Profile ID result: $profileId")
            
            if (profileId == null) {
                Log.e("GuardianSettings", "User profile not found for auth ID: $currentUserAuthId")
                Toast.makeText(requireContext(), "User profile not found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val guardian = Guardian(
                userId = profileId, 
                guardianEmail = email,
                guardianName = name.ifEmpty { "Guardian" },
                guardianPhone = phone.ifEmpty { "0000000000" }
            )
            
            Log.d("GuardianSettings", "Created guardian object: $guardian")
            
            dbHelper.addGuardian(profileId, guardian).fold(
                onSuccess = {
                    Log.d("GuardianSettings", "Guardian added successfully")
                    binding.guardianEmailInput.text?.clear()
                    binding.guardianNameInput.text?.clear()
                    binding.guardianPhoneInput.text?.clear()
                    loadGuardians() // Refresh the list
                    Toast.makeText(requireContext(), "Guardian added successfully", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Log.e("GuardianSettings", "Failed to add guardian", error)
                    Toast.makeText(requireContext(), "Failed to add guardian: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun removeGuardian(email: String) {
        if (currentUserAuthId.isEmpty()) return
        
        lifecycleScope.launch {
            // For now, we'll pass the email as the guardianId since we need a way to identify the guardian
            dbHelper.removeGuardian(email).fold(
                onSuccess = {
                    loadGuardians() // Refresh the list
                    Toast.makeText(requireContext(), "Guardian removed successfully", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Failed to remove guardian: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}