package com.arny.mobilecinema.presentation

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavOptions
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import android.widget.Toast
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import com.arny.mobilecinema.presentation.splash.StartActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
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
    private val linkInteractor: JsoupUpdateInteractor by inject()
    private var sharedUrlJob: Job? = null

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
        handleSharedUrl()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedUrl()
    }

    private fun handleSharedUrl() {
        val sharedUrl = intent.getStringExtra(StartActivity.KEY_SHARED_URL)?.takeIf { it.isNotBlank() } ?: return
        sharedUrlJob?.cancel()
        sharedUrlJob = lifecycleScope.launch {
            Toast.makeText(this@MainActivity, R.string.loading, Toast.LENGTH_SHORT).show()
            when (val result = linkInteractor.fetchAndSaveMovieCard(sharedUrl)) {
                is DataResult.Success -> {
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    navController.navigate(R.id.nav_details, bundleOf("id" to result.result),
                        NavOptions.Builder().setPopUpTo(R.id.nav_home, false).build())
                }
                is DataResult.Error -> {
                    if (result.throwable is CancellationException) throw result.throwable
                    Toast.makeText(this@MainActivity, R.string.tv_shared_link_error, Toast.LENGTH_SHORT).show()
                }
            }
            intent.removeExtra(StartActivity.KEY_SHARED_URL)
        }
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
            if (tabItem != null) {
                selectedTabId = tabItem.itemId
                if (binding.bottomNavView.selectedItemId != tabItem.itemId) {
                    binding.bottomNavView.selectedItemId = tabItem.itemId
                }
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
                    // Первое «Назад» при активном поиске — сброс текста и результатов,
                    // поиск остаётся открытым. Второе — закрытие поиска. И только затем — выход.
                    currentFragment is OnSearchListener && !currentFragment.isSearchComplete() -> {
                        currentFragment.resetSearchContent()
                    }

                    currentFragment is OnSearchListener && currentFragment.isSearchOpen() -> {
                        currentFragment.collapseSearch()
                    }

                    else -> {
                        onBack(isLastFragment)
                    }
                }
            }

            private fun onBack(isLastFragment: Boolean) {
                val currentId = navController.currentDestination?.id
                when {
                    // Главная — корень: только на ней работает двойной выход.
                    currentId == R.id.nav_home -> {
                        if (backPressedTime + TIME_DELAY > System.currentTimeMillis()) {
                            finishAffinity()
                        } else {
                            binding.root.showSnackBar(getString(R.string.press_back_again_to_exit))
                        }
                        backPressedTime = System.currentTimeMillis()
                    }

                    // Любая другая вкладка: назад ведёт на главную и вычищает стек до неё.
                    // Ранее здесь был выход по двойному нажатию, хотя nav_home лежал под вкладкой.
                    isLastFragment -> {
                        if (!navController.popBackStack(R.id.nav_home, false)) {
                            // main не в стеке после restore/deep link — идём через bottom nav.
                            binding.bottomNavView.selectedItemId = R.id.nav_home
                        }
                    }

                    else -> navController.navigateUp()
                }
            }
        })
    }

    private companion object {
        const val TIME_DELAY = 2000
    }
}