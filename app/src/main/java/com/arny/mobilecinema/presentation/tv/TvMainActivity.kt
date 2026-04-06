package com.arny.mobilecinema.presentation.tv

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.appcompat.app.AppCompatActivity
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.splash.StartActivity

class TvMainActivity : AppCompatActivity() {

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
        AlertDialog.Builder(this, R.style.TvDialogTheme)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_message)
            .setPositiveButton(R.string.yes) { _, _ -> finish() }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}