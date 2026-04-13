package com.arny.mobilecinema.presentation.tv

import android.content.Intent
import android.os.Bundle
import android.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.splash.StartActivity

class TvMainActivity : FragmentActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.tv_nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        handleSharedUrl()
    }

    private fun handleSharedUrl() {
        val sharedUrl = intent.getStringExtra(StartActivity.KEY_SHARED_URL)
        if (!sharedUrl.isNullOrBlank()) {
            // Navigate to player with shared URL
            // For now, just navigate to home - player handling will be added later
        }
    }

    private fun stopUpdateService() {
        val intent = Intent(this, UpdateService::class.java).apply {
            action = AppConstants.ACTION_UPDATE_ALL_CANCEL
        }
        startService(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.tv_nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        if (!navController.popBackStack()) {
            showExitDialog()
        }
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                stopUpdateService()
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}