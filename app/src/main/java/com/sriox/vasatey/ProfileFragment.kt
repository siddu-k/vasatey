package com.sriox.vasatey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sriox.vasatey.databinding.FragmentProfileBinding
import com.sriox.vasatey.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var currentUser: User? = null

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
            if (newName.isNotEmpty()) {
                updateUserName(newName)
            }
        }

        binding.changePasswordButton.setOnClickListener {
            showSecurityQuestionsDialog()
        }
    }

    private fun loadUserData() {
        val userEmail = auth.currentUser?.email
        if (userEmail != null) {
            db.collection("users").document(userEmail).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        currentUser = document.toObject(User::class.java)
                        binding.nameInput.setText(currentUser?.name)
                        binding.emailInput.setText(currentUser?.email)
                        binding.emailInput.isEnabled = false // Don't allow email editing
                    }
                }
        }
    }

    private fun updateUserName(newName: String) {
        val userEmail = auth.currentUser?.email
        if (userEmail != null) {
            db.collection("users").document(userEmail).update("name", newName)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Name updated successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { 
                    Toast.makeText(requireContext(), "Failed to update name", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showSecurityQuestionsDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_security_questions, null)
        val schoolInput = dialogView.findViewById<EditText>(R.id.schoolAnswer)
        val petInput = dialogView.findViewById<EditText>(R.id.petAnswer)

        AlertDialog.Builder(requireContext())
            .setTitle("Verify Your Identity")
            .setView(dialogView)
            .setPositiveButton("Verify") { _, _ ->
                val schoolAnswer = schoolInput.text.toString().trim()
                val petAnswer = petInput.text.toString().trim()
                verifySecurityAnswers(schoolAnswer, petAnswer)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifySecurityAnswers(schoolAnswer: String, petAnswer: String) {
        val storedSchoolAnswer = currentUser?.securityQuestions?.get("school")
        val storedPetAnswer = currentUser?.securityQuestions?.get("pet")

        if (schoolAnswer.equals(storedSchoolAnswer, ignoreCase = true) && petAnswer.equals(storedPetAnswer, ignoreCase = true)) {
            sendPasswordResetEmail()
        } else {
            Toast.makeText(requireContext(), "Answers do not match. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendPasswordResetEmail() {
        val userEmail = auth.currentUser?.email
        if (userEmail != null) {
            auth.sendPasswordResetEmail(userEmail)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(requireContext(), "Password reset email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(require...