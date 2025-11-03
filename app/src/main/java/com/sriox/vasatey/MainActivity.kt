package com.sriox.vasatey

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.sriox.vasatey.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var authHelper: SupabaseAuthHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authHelper = SupabaseAuthHelper()

        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
            binding.navView.setCheckedItem(R.id.nav_home)
        }
        
        // Check and request permissions
        checkPermissions()
    }
    
    private fun checkPermissions() {
        if (!PermissionManager.checkAllPermissions(this)) {
            PermissionManager.showPermissionExplanation(this) {
                PermissionManager.requestPermissions(this)
            }
        } else {
            // All permissions granted, show battery optimization dialog
            PermissionManager.showBatteryOptimizationDialog(this)
            // Start listening service
            startListeningService()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                Toast.makeText(this, "All permissions granted! Vasatey is now protecting you.", Toast.LENGTH_LONG).show()
                PermissionManager.showBatteryOptimizationDialog(this)
                startListeningService()
            } else {
                Toast.makeText(this, "Some permissions were denied. Vasatey may not work properly.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startListeningService() {
        if (PermissionManager.hasMicrophonePermission(this)) {
            val intent = Intent(this, ListeningService::class.java)
            startForegroundService(intent)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> {
                replaceFragment(HomeFragment())
            }
            R.id.nav_alerts -> {
                replaceFragment(AlertsFragment())
            }
            R.id.nav_call_for_help -> {
                replaceFragment(HelpSettingsFragment())
            }
            R.id.nav_guardians -> {
                replaceFragment(GuardianSettingsFragment())
            }
            R.id.nav_profile -> {
                replaceFragment(ProfileFragment())
            }
            R.id.nav_logout -> {
                lifecycleScope.launch {
                    authHelper.signOut()
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}