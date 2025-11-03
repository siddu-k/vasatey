package com.sriox.vasatey

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.sriox.vasatey.databinding.ActivityAlertDetailsBinding

class AlertDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityAlertDetailsBinding
    private var googleMap: GoogleMap? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var displayName: String = "Unknown User"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Retrieve Data ---
        val name = intent.getStringExtra("USER_NAME")
        val email = intent.getStringExtra("USER_EMAIL")
        val mobile = intent.getStringExtra("USER_MOBILE")
        val latitudeStr = intent.getStringExtra("USER_LATITUDE")
        val longitudeStr = intent.getStringExtra("USER_LONGITUDE")

        // --- Determine the best display name ---
        displayName = if (!name.isNullOrBlank()) {
            name
        } else if (!email.isNullOrBlank()) {
            email
        } else {
            "Unknown User"
        }

        // --- Populate Text Fields ---
        binding.nameTextView.text = displayName // Display the name directly
        binding.emailTextView.text = "Email: $email"
        binding.mobileTextView.text = if (!mobile.isNullOrBlank()) "Mobile: $mobile" else "Mobile: Not available"

        // --- Handle Location and Map ---
        if (!latitudeStr.isNullOrBlank() && !longitudeStr.isNullOrBlank() && latitudeStr != "0.0" && longitudeStr != "0.0") {
            this.latitude = latitudeStr.toDoubleOrNull()
            this.longitude = longitudeStr.toDoubleOrNull()

            binding.locationTextView.text = "Location: $latitudeStr, $longitudeStr"
            binding.openInMapsButton.visibility = View.VISIBLE
            binding.mapView.visibility = View.VISIBLE

            binding.openInMapsButton.setOnClickListener {
                val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${this.latitude},${this.longitude}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                startActivity(mapIntent)
            }

            // --- Initialize MapView ---
            binding.mapView.onCreate(savedInstanceState)
            binding.mapView.getMapAsync(this)

        } else {
            binding.locationTextView.text = "Location: Not available"
            binding.openInMapsButton.visibility = View.GONE
            binding.mapView.visibility = View.GONE
        }

        // --- Handle Call Button ---
        if (!mobile.isNullOrBlank()) {
            binding.callNowButton.visibility = View.VISIBLE
            binding.callNowButton.setOnClickListener {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$mobile"))
                startActivity(dialIntent)
            }
        } else {
            binding.callNowButton.visibility = View.GONE
        }
    }

    // --- Map Ready Callback ---
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val lat = latitude
        val lon = longitude
        if (lat != null && lon != null) {
            val location = LatLng(lat, lon)
            // Use the consistent displayName for the marker
            googleMap?.addMarker(MarkerOptions().position(location).title("$displayName's Location"))
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
        }
    }

    // --- MapView Lifecycle Management ---
    override fun onResume() {
        super.onResume()
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onLowMemory()
    }

    override fun onDestroy() {
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (binding.mapView.visibility == View.VISIBLE) binding.mapView.onSaveInstanceState(outState)
    }
}
