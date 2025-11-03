package com.sriox.vasatey

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sriox.vasatey.databinding.FragmentAlertsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    private val dbHelper = SupabaseDatabaseHelper()
    private val authHelper = SupabaseAuthHelper()
    private lateinit var alertHistoryManager: AlertHistoryManager
    private lateinit var alertsAdapter: ArrayAdapter<String>
    private val alertsList = mutableListOf<Alert>()

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

        alertHistoryManager = AlertHistoryManager(requireContext())
        alertsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1)
        binding.alertsListView.adapter = alertsAdapter

        binding.alertsListView.setOnItemClickListener { _, _, position, _ ->
            val clickedAlert = alertsList[position]
            val intent = Intent(requireContext(), AlertDetailsActivity::class.java).apply {
                putExtra("USER_NAME", "Your Emergency Alert")
                putExtra("USER_EMAIL", authHelper.getCurrentUser()?.email ?: "Not available")
                putExtra("USER_MOBILE", "Not available") // Could be enhanced to get actual mobile from user profile
                putExtra("USER_LATITUDE", clickedAlert.locationLatitude?.toString() ?: "0.0")
                putExtra("USER_LONGITUDE", clickedAlert.locationLongitude?.toString() ?: "0.0")
            }
            startActivity(intent)
        }

        loadAlerts()
    }

    private fun loadAlerts() {
        val currentUser = authHelper.getCurrentUser()
        if (currentUser == null) {
            return
        }

        lifecycleScope.launch {
            try {
                // Get the user profile ID first
                val profileIdResult = dbHelper.getUserProfileId(currentUser.id)
                val profileId = profileIdResult.getOrNull()
                
                if (profileId == null) {
                    alertsAdapter.clear()
                    alertsAdapter.notifyDataSetChanged()
                    return@launch
                }
                
                val result = alertHistoryManager.getAllAlerts(profileId)
                result.fold(
                    onSuccess = { alerts ->
                        alertsList.clear()
                        alertsList.addAll(alerts)
                        
                        val displayList = alerts.map { alert ->
                            val formattedDate = alert.createdAt?.takeIf { it.isNotEmpty() }?.let { 
                                try {
                                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                    val outputFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                    val date = inputFormat.parse(it)
                                    outputFormat.format(date ?: Date())
                                } catch (e: Exception) {
                                    "Unknown time"
                                }
                            } ?: "Unknown time"
                            
                            val location = if (alert.locationLatitude != null && alert.locationLongitude != null) {
                                "Location: ${alert.locationLatitude}, ${alert.locationLongitude}"
                            } else {
                                "No location"
                            }
                            
                            "${alert.alertType.uppercase()} - ${alert.severity} - $formattedDate\n$location"
                        }
                        
                        alertsAdapter.clear()
                        alertsAdapter.addAll(displayList)
                        alertsAdapter.notifyDataSetChanged()
                    },
                    onFailure = { error ->
                        // Handle error - could show a toast or error message
                        alertsAdapter.clear()
                        alertsAdapter.notifyDataSetChanged()
                    }
                )
            } catch (e: Exception) {
                // Handle exception
                alertsAdapter.clear()
                alertsAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}