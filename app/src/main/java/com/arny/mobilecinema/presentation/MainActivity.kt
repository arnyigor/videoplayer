package com.arny.mobilecinema.presentation

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.AMainBinding
import com.arny.mobilecinema.presentation.listeners.OnPictureInPictureListener
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.utils.showSnackBar
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.android.AndroidInjection
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity(), HasAndroidInjector {
    private lateinit var binding: AMainBinding
    private var backPressedTime: Long = 0

    @Inject
    lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector(): AndroidInjector<Any> = dispatchingAndroidInjector

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        binding = AMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val navController = findNavController(R.id.nav_host_fragment)
        // Find reference to bottom navigation view
        val navView: BottomNavigationView = findViewById(R.id.bottom_nav_view)
        // Hook your navigation controller to bottom navigation view
        navView.setupWithNavController(navController)
        initOnBackPress(navController)
        initUI(navController)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun initUI(navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            showBottomNav(
                destination.id !in listOf(
                    R.id.nav_player_view,
                    R.id.nav_details,
                    R.id.nav_extended_search
                )
            )
            showHome(destination.id !in listOf(R.id.nav_home, R.id.nav_prefs, R.id.nav_history))
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                    val extra = intent.getStringExtra(Intent.EXTRA_TEXT)
                    Timber.i("handleIntent:  url:$extra");
                    if (extra?.contains("anwap") == true) {
                        Timber.i("handleIntent: anwap url:$extra");
                    }
                }
            }
        }
    }

    private fun showBottomNav(show: Boolean) {
        binding.bottomNavView.isVisible = show
    }

    private fun showHome(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    private fun getCurrentFragment() =
        supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.fragments?.getOrNull(
            0
        )

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val currentFragment = getCurrentFragment()
        if (currentFragment is OnPictureInPictureListener) {
            if (currentFragment.isPiPAvailable()) {
                currentFragment.enterPiPMode()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val currentFragment = getCurrentFragment()
        if (currentFragment is OnPictureInPictureListener) {
            currentFragment.onPiPMode(isInPictureInPictureMode)
        }
    }

    override fun onStop() {
        super.onStop()
        finishPicInPicTask()
    }

    private fun finishPicInPicTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            finishAndRemoveTask()
        }
    }

    private fun initOnBackPress(navController: NavController) {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val isLastFragment = navController.currentDestination?.id == R.id.nav_home
                val currentFragment = getCurrentFragment()
                when {
                    currentFragment is OnSearchListener && currentFragment.isSearchComplete() -> {
                        onBack(isLastFragment)
                    }

                    currentFragment is OnSearchListener && !currentFragment.isSearchComplete() -> {
                        currentFragment.collapseSearch()
                    }

                    else -> {
                        onBack(isLastFragment)
                    }
                }
            }

            private fun onBack(isLastFragment: Boolean) {
                if (isLastFragment) {
                    if (backPressedTime + TIME_DELAY > System.currentTimeMillis()) {
                        finishAffinity()
                    } else {
                        binding.root.showSnackBar(getString(R.string.press_back_again_to_exit))
                    }
                    backPressedTime = System.currentTimeMillis()
                } else {
                    navController.navigateUp()
                }
            }
        })
    }

    private companion object {
        const val TIME_DELAY = 2000
    }
}
