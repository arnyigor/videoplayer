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
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.AMainBinding
import com.arny.mobilecinema.presentation.listeners.OnPictureInPictureListener
import com.arny.mobilecinema.presentation.listeners.OnScrollToTopListener
import com.arny.mobilecinema.presentation.listeners.OnSearchListener
import com.arny.mobilecinema.presentation.utils.showSnackBar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: AMainBinding
    private lateinit var navController: NavController
    private var backPressedTime: Long = 0

    /**
     * Вкладка, выбранная пользователем в нижнем меню. Отличает корень вкладки от экрана,
     * который открыт с неё же (например, «Поиск» от главной): у первого Back выходит
     * из приложения, у второго — возвращает назад.
     */
    private var selectedTabId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        // Koin injection
        super.onCreate(savedInstanceState)
        binding = AMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Получаем NavController через NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Hook your navigation controller to bottom navigation view
        binding.bottomNavView.setupWithNavController(navController)
        // Та же навигация, что настраивает NavigationUI, плюс запоминание выбранной вкладки.
        binding.bottomNavView.setOnItemSelectedListener { item ->
            val handled = NavigationUI.onNavDestinationSelected(item, navController)
            if (handled) selectedTabId = item.itemId
            handled
        }
        // Повторный тап по текущей вкладке — наверх, без сброса её состояния.
        binding.bottomNavView.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id == item.itemId) {
                (getCurrentFragment() as? OnScrollToTopListener)?.scrollToTop()
            }
        }

        initOnBackPress()
        initUI()
    }

    private fun initUI() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Bottom navigation - скрываем для PlayerView, Details, ExtendedSearch, Comments
            showBottomNav(
                destination.id !in listOf(
                    R.id.nav_player_view,
                    R.id.nav_details,
                    R.id.nav_extended_search,
                    R.id.nav_comments,
                )
            )
            // Программный переход на корневой экран (действие на экране, deep link) должен
            // подсвечивать соответствующую вкладку, иначе нижняя панель противоречит экрану.
            val tabItem = binding.bottomNavView.menu.findItem(destination.id)
            if (tabItem != null && binding.bottomNavView.selectedItemId != tabItem.itemId) {
                binding.bottomNavView.selectedItemId = tabItem.itemId
            }
        }
    }

    private fun showBottomNav(show: Boolean) {
        binding.bottomNavView.isVisible = show
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
                val isLastFragment = navController.currentDestination?.id == selectedTabId
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