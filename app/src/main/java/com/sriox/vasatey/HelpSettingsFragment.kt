package com.sriox.vasatey

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sriox.vasatey.databinding.FragmentHelpSettingsBinding

class HelpSettingsFragment : Fragment() {

    private var _binding: FragmentHelpSettingsBinding? = null
    private val binding get() = _binding!!

    private val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
    )

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val currentUserEmail get() = auth.currentUser?.email ?: ""

    private var accessKey: String? = null

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

        listenForAccessKeyUpdate()

        binding.startButton.setOnClickListener {
            if (accessKey.isNullOrEmpty()) {
                showAccessKeyAlert()
            } else if (hasPermissions()) {
                startListeningService()
            } else {
                requestPermissions.launch(permissions)
            }
        }

        binding.stopButton.setOnClickListener { stopListeningService() }

        binding.saveAccessKeyButton.setOnClickListener {
            val accessKey = binding.accessKeyInput.text.toString().trim()
            if (accessKey.isNotEmpty()) saveAccessKey(accessKey)
        }

        binding.deleteAccessKeyButton.setOnClickListener { deleteAccessKey() }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            if (perms.values.all { it }) startListeningService()
        }

    private fun hasPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startListeningService() {
        val intent = Intent(requireContext(), ListeningService::class.java)
        ContextCompat.startForegroundService(requireContext(), intent)
        Toast.makeText(requireContext(), "Listening started", Toast.LENGTH_SHORT).show()
    }

    private fun stopListeningService() {
        val intent = Intent(requireContext(), ListeningService::class.java)
        requireContext().stopService(intent)
        Toast.makeText(requireContext(), "Listening stopped", Toast.LENGTH_SHORT).show()
    }

    private fun saveAccessKey(accessKey: String) {
        val userRef = db.collection("users").document(currentUserEmail)
        userRef.set(mapOf("accessKey" to accessKey), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Access Key saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to save Access Key", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteAccessKey() {
        val userRef = db.collection("users").document(currentUserEmail)
        userRef.update(mapOf("accessKey" to FieldValue.delete()))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Access Key deleted", Toast.LENGTH_SHORT).show()
                binding.accessKeyInput.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to delete Access Key", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForAccessKeyUpdate() {
        if (currentUserEmail.isEmpty()) return
        val userRef = db.collection("users").document(currentUserEmail)
        userRef.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            accessKey = snapshot?.getString("accessKey")
            binding.accessKeyInput.setText(accessKey)
        }
    }

    private fun showAccessKeyAlert() {
        AlertDialog.Builder(requireContext())
            .setTitle("Access Key Required")
            .setMessage("Please add your Picovoice Access Key to start listening. You can get a free key by logging in to the Picovoice Console. Also, please add your mail ID to your GitHub profile link. Note: You cannot use this key in other device for one month.")
            .setPositiveButton("Get Key") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.picovoice.ai/login"))
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
