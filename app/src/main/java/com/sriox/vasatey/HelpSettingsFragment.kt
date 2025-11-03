package com.sriox.vasatey

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentHelpSettingsBinding
import kotlinx.coroutines.launch

class HelpSettingsFragment : Fragment() {

    private var _binding: FragmentHelpSettingsBinding? = null
    private val binding get() = _binding!!

    private val authHelper = SupabaseAuthHelper()
    private val dbHelper = SupabaseDatabaseHelper()
    private val currentUserAuthId get() = authHelper.getCurrentUser()?.id ?: ""

    private var isProgrammaticCheck = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupListeners()
        loadInitialSettings()
    }

    private fun setupListeners() {
        binding.startButton.setOnClickListener {
            startListeningService()
        }
        
        binding.stopButton.setOnClickListener {
            stopListeningService()
        }
        
        binding.saveAccessKeyButton.setOnClickListener {
            saveSettings()
        }

        binding.deleteAccessKeyButton.setOnClickListener {
            deleteAccessKey()
        }

        binding.wakeWordRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!isProgrammaticCheck) {
                val wakeWord = when (checkedId) {
                    R.id.helpMeRadioButton -> "help-me"
                    R.id.leaveMeAloneRadioButton -> "leave-me-alone"
                    else -> "help-me"
                }
                
                lifecycleScope.launch {
                    val settings = UserSettings(
                        userId = "", // Will be set by the database helper
                        wakeWord = wakeWord,
                        voiceDetectionEnabled = true
                    )
                    dbHelper.saveUserSettings(currentUserAuthId, settings)
                }
            }
        }
    }

    private fun saveSettings() {
        val accessKey = binding.accessKeyInput.text.toString().trim()
        if (accessKey.isEmpty()) {
            showAccessKeyAlert()
            return
        }

        val wakeWord = when (binding.wakeWordRadioGroup.checkedRadioButtonId) {
            R.id.helpMeRadioButton -> "help-me"
            R.id.leaveMeAloneRadioButton -> "leave-me-alone"
            else -> "help-me"
        }

        lifecycleScope.launch {
            val settings = UserSettings(
                userId = "", // Will be set by the database helper
                wakeWord = wakeWord,
                picovoiceAccessKey = accessKey,
                voiceDetectionEnabled = true
            )
            
            dbHelper.saveUserSettings(currentUserAuthId, settings).fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Settings saved successfully", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Failed to save settings: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun deleteAccessKey() {
        lifecycleScope.launch {
            val settings = UserSettings(
                userId = "", // Will be set by the database helper
                wakeWord = "help-me",
                voiceDetectionEnabled = false
            )
            
            dbHelper.saveUserSettings(currentUserAuthId, settings).fold(
                onSuccess = {
                    Toast.makeText(requireContext(), "Access Key deleted", Toast.LENGTH_SHORT).show()
                    binding.accessKeyInput.text?.clear()
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "Failed to delete access key: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun loadInitialSettings() {
        if (currentUserAuthId.isEmpty()) return
        
        lifecycleScope.launch {
            dbHelper.getUserSettings(currentUserAuthId).fold(
                onSuccess = { settings ->
                    settings?.let {
                        binding.accessKeyInput.setText(it.picovoiceAccessKey ?: "")
                        
                        isProgrammaticCheck = true
                        if (it.wakeWord == "leave-me-alone") {
                            binding.wakeWordRadioGroup.check(R.id.leaveMeAloneRadioButton)
                        } else {
                            binding.wakeWordRadioGroup.check(R.id.helpMeRadioButton)
                        }
                        isProgrammaticCheck = false
                    }
                },
                onFailure = { /* Handle error */ }
            )
        }
    }

    private fun showAccessKeyAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle("Picovoice Access Key Required")
            .setMessage(
                "Please get your key from https://console.picovoice.ai/login\n\n" +
                "• Login and copy the key\n" +
                "• Use it in the field above\n\n" +
                "⚠️ Important:\n" +
                "• Key cannot be used on other devices for one month once used\n" +
                "• Picovoice may delete your key by removing your account\n" +
                "• If deleted, you cannot create account with same email - use different email\n\n" +
                "This is a testing feature. Picovoice helps us provide this feature for free."
            )
            .setPositiveButton("Get Key") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.picovoice.ai/login"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startListeningService() {
        // Check if we have the required access key first
        val accessKey = binding.accessKeyInput.text.toString().trim()
        if (accessKey.isEmpty()) {
            showAccessKeyAlert()
            return
        }
        
        // Save settings first to ensure access key is in database
        saveSettings()
        
        // Start the listening service
        try {
            val intent = Intent(requireContext(), ListeningService::class.java)
            requireContext().startForegroundService(intent)
            
            Toast.makeText(requireContext(), "Voice detection started! Listening for wake word...", Toast.LENGTH_LONG).show()
            
            // Update button states
            binding.startButton.isEnabled = false
            binding.stopButton.isEnabled = true
            binding.startButton.text = "Listening..."
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to start listening: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun stopListeningService() {
        try {
            val intent = Intent(requireContext(), ListeningService::class.java)
            requireContext().stopService(intent)
            
            Toast.makeText(requireContext(), "Voice detection stopped", Toast.LENGTH_SHORT).show()
            
            // Update button states
            binding.startButton.isEnabled = true
            binding.stopButton.isEnabled = false
            binding.startButton.text = "Start Listening"
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to stop listening: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}