package com.sriox.vasatey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.sriox.vasatey.databinding.FragmentGuardianSettingsBinding

class GuardianSettingsFragment : Fragment() {

    private var _binding: FragmentGuardianSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var guardiansAdapter: ArrayAdapter<String>

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val currentUserEmail get() = auth.currentUser?.email ?: ""

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

        listenForGuardianUpdates()

        binding.addGuardianButton.setOnClickListener {
            val email = binding.guardianEmailInput.text.toString().trim()
            if (email.isNotEmpty()) addGuardian(email)
        }

        binding.removeGuardianButton.setOnClickListener {
            val email = binding.guardianEmailInput.text.toString().trim()
            if (email.isNotEmpty()) removeGuardian(email)
        }
    }

    private fun addGuardian(email: String) {
        val userRef = db.collection("users").document(currentUserEmail)
        userRef.set(mapOf("guardians" to FieldValue.arrayUnion(email)), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Guardian added: $email", Toast.LENGTH_SHORT).show()
                binding.guardianEmailInput.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to add guardian", Toast.LENGTH_SHORT).show()
            }
    }

    private fun removeGuardian(email: String) {
        val userRef = db.collection("users").document(currentUserEmail)
        userRef.update("guardians", FieldValue.arrayRemove(email))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Guardian removed: $email", Toast.LENGTH_SHORT).show()
                binding.guardianEmailInput.text.clear()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to remove guardian", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForGuardianUpdates() {
        if (currentUserEmail.isEmpty()) return
        val userRef = db.collection("users").document(currentUserEmail)
        userRef.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val list = snapshot?.get("guardians") as? List<String> ?: emptyList()
            guardiansList.clear()
            guardiansList.addAll(list)
            guardiansAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
