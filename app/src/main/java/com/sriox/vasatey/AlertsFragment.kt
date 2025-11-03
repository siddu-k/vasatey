package com.sriox.vasatey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sriox.vasatey.databinding.FragmentAlertsBinding
import java.text.SimpleDateFormat
import java.util.*

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private lateinit var alertsAdapter: ArrayAdapter<String>
    private val alertsList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        alertsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, alertsList)
        binding.alertsListView.adapter = alertsAdapter

        listenForAlerts()
    }

    private fun listenForAlerts() {
        db.collection("helpRequests")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                alertsList.clear()
                for (doc in snapshots!!) {
                    val userEmail = doc.getString("userEmail") ?: "Unknown user"
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()

                    val formattedDate = timestamp?.let {
                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(it)
                    } ?: "Unknown time"

                    alertsList.add("Help request from: $userEmail at $formattedDate")
                }
                alertsAdapter.notifyDataSetChanged()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
