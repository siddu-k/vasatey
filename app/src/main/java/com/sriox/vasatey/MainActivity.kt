package com.sriox.vasatey

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.firebase.messaging.FirebaseMessaging
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
        
        // Refresh FCM token on app start to ensure it's up to date
        refreshFcmToken()
        
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
            R.id.nav_refresh_token -> {
                refreshFCMToken()
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
    
    private fun refreshFcmToken() {
        // Delete the current token and get a fresh one
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("MainActivity", "Successfully deleted old FCM token")
                
                // Now get a fresh token
                FirebaseMessaging.getInstance().token.addOnSuccessListener { newToken ->
                    val currentUser = authHelper.getCurrentUser()
                    if (currentUser != null) {
                        lifecycleScope.launch {
                            val dbHelper = SupabaseDatabaseHelper()
                            dbHelper.updateFCMToken(currentUser.id, newToken).fold(
                                onSuccess = { 
                                    Log.d("MainActivity", "Fresh FCM token saved successfully for user: ${currentUser.id}")
                                    Log.d("MainActivity", "New token: ${newToken.take(20)}...")
                                },
                                onFailure = { error ->
                                    Log.e("MainActivity", "Failed to save fresh FCM token", error)
                                }
                            )
                        }
                    } else {
                        Log.w("MainActivity", "Cannot refresh FCM token - user not logged in")
                    }
                }.addOnFailureListener { error ->
                    Log.e("MainActivity", "Failed to get fresh FCM token", error)
                }
            } else {
                Log.e("MainActivity", "Failed to delete old FCM token", task.exception)
                // Fallback to just getting the current token
                getFcmTokenFallback()
            }
        }
    }
    
    private fun getFcmTokenFallback() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val currentUser = authHelper.getCurrentUser()
            if (currentUser != null) {
                lifecycleScope.launch {
                    val dbHelper = SupabaseDatabaseHelper()
                    dbHelper.updateFCMToken(currentUser.id, token).fold(
                        onSuccess = { 
                            Log.d("MainActivity", "FCM token updated successfully for user: ${currentUser.id}")
                        },
                        onFailure = { error ->
                            Log.e("MainActivity", "Failed to update FCM token", error)
                        }
                    )
                }
            }
        }.addOnFailureListener { error ->
            Log.e("MainActivity", "Failed to get FCM token", error)
        }
    }

    // Add this method to manually refresh FCM token
    fun refreshFCMToken() {
        Log.d("MainActivity", "Manual FCM token refresh requested")
        Toast.makeText(this, "Refreshing FCM token...", Toast.LENGTH_SHORT).show()
        
        val messaging = FirebaseMessaging.getInstance()
        
        // First, let's see what the current token is
        messaging.token.addOnCompleteListener { currentTokenTask ->
            val currentToken = if (currentTokenTask.isSuccessful) currentTokenTask.result else "failed"
            Log.d("MainActivity", "Current token before refresh: ${currentToken.take(30)}...")
            
            // Now try to delete and get a new one
            messaging.deleteToken().addOnCompleteListener { deleteTask ->
                Log.d("MainActivity", "Delete token result: ${deleteTask.isSuccessful}")
                if (deleteTask.exception != null) {
                    Log.e("MainActivity", "Delete token error: ${deleteTask.exception?.message}")
                }
                
                // Try to clear FCM app instance as well
                try {
                    val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
                    Log.d("MainActivity", "Firebase app instance: ${firebaseApp.name}")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Firebase app access error: ${e.message}")
                }
                
                // Wait and get a new token
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    messaging.token.addOnCompleteListener { newTokenTask ->
                        if (newTokenTask.isSuccessful) {
                            val newToken = newTokenTask.result
                            Log.d("MainActivity", "New token after refresh: ${newToken.take(30)}...")
                            
                            if (newToken == currentToken) {
                                Log.w("MainActivity", "WARNING: Token didn't change! Still same token")
                                Toast.makeText(this@MainActivity, "Warning: Token didn't change - may need app restart", Toast.LENGTH_LONG).show()
                            } else {
                                Log.d("MainActivity", "SUCCESS: Got a different token!")
                                Toast.makeText(this@MainActivity, "Success: Got new token!", Toast.LENGTH_SHORT).show()
                            }
                            
                            val currentUser = authHelper.getCurrentUser()
                            if (currentUser != null) {
                                lifecycleScope.launch {
                                    val dbHelper = SupabaseDatabaseHelper()
                                    dbHelper.updateFCMToken(currentUser.id, newToken).fold(
                                        onSuccess = { 
                                            Log.d("MainActivity", "FCM token updated in database for user: ${currentUser.id}")
                                            Toast.makeText(this@MainActivity, "Token saved! Try notification again.", Toast.LENGTH_LONG).show()
                                        },
                                        onFailure = { error ->
                                            Log.e("MainActivity", "Failed to save FCM token", error)
                                            Toast.makeText(this@MainActivity, "Failed to save: ${error.message}", Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        } else {
                            Log.e("MainActivity", "Failed to get new token", newTokenTask.exception)
                            Toast.makeText(this@MainActivity, "Failed to get new token: ${newTokenTask.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }, 2000) // Wait 2 seconds
            }
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}