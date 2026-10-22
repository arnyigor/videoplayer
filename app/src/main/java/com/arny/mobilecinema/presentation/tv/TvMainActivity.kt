package com.arny.mobilecinema.presentation.tv

import android.content.Intent
import android.os.Bundle
import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navController.popBackStack()) {
                    showExitDialog()
                }
            }
        })

        handleSharedUrl()
    }

    private fun handleSharedUrl() {
        val sharedUrl = intent.getStringExtra(StartActivity.KEY_SHARED_URL)
        if (!sharedUrl.isNullOrBlank()) {
            // TODO(SYS-05): открыть плеер по deep link; сейчас ссылка не теряется, но и не проигрывается.
        }
    }

    private fun stopUpdateService() {
        val intent = Intent(this, UpdateService::class.java).apply {
            action = AppConstants.ACTION_UPDATE_ALL_CANCEL
        }
        startService(intent)
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
