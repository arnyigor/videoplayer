package com.arny.mobilecinema.presentation

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.AMainBinding
import com.arny.mobilecinema.presentation.listeners.OnPictureInPictureListener
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.utils.showSnackBar
import dagger.android.AndroidInjection
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class MainActivity : AppCompatActivity(), HasAndroidInjector {

    private lateinit var binding: AMainBinding
    private lateinit var navController: NavController
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

        // Получаем NavController через NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Hook your navigation controller to bottom navigation view
        binding.bottomNavView.setupWithNavController(navController)

        initOnBackPress()
        initUI()
    }

    private fun initUI() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Bottom navigation - скрываем для PlayerView, Details, ExtendedSearch
            showBottomNav(
                destination.id !in listOf(
                    R.id.nav_player_view,
                    R.id.nav_details,
                    R.id.nav_extended_search
                )
            )

            // Скрываем Activity toolbar для экранов со своим toolbar
            showActivityToolbar(
                destination.id !in listOf(
                    R.id.nav_player_view,
                    R.id.nav_details
                )
            )

            // Кнопка "назад"
            showHome(
                destination.id !in listOf(
                    R.id.nav_home,
                    R.id.nav_prefs,
                    R.id.nav_history,
                    R.id.nav_favorite
                )
            )
        }
    }

    private fun showBottomNav(show: Boolean) {
        binding.bottomNavView.isVisible = show
    }

    /**
     * Показывает/скрывает Activity Toolbar
     */
    private fun showActivityToolbar(show: Boolean) {
        binding.toolbar.isVisible = show
        if (show) {
            supportActionBar?.show()
        } else {
            supportActionBar?.hide()
        }
    }

    private fun showHome(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    private fun getCurrentFragment() =
        supportFragmentManager
            .primaryNavigationFragment
            ?.childFragmentManager
            ?.fragments
            ?.getOrNull(0)

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

    private fun initOnBackPress() {
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