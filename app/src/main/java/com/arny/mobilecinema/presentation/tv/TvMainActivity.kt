package com.arny.mobilecinema.presentation.tv

import android.content.Intent
import android.os.Bundle
import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.domain.interactors.jsoupupdate.JsoupUpdateInteractor
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.arny.mobilecinema.presentation.tv.player.TvPlayerFragment
import androidx.navigation.fragment.NavHostFragment
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.splash.StartActivity

class TvMainActivity : FragmentActivity() {

    private lateinit var navController: NavController
    private lateinit var navHost: NavHostFragment
    private val linkInteractor: JsoupUpdateInteractor by inject()
    private var sharedUrlJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        navHost = supportFragmentManager
            .findFragmentById(R.id.tv_nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        setupNavigation()
        // Intercept via the public Window API; FragmentActivity's override is library-restricted.
        val originalCallback = window.callback
        window.callback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (currentPlayer()?.handleMediaKey(event) == true) return true
                return originalCallback.dispatchKeyEvent(event)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPlayer()?.handleTvBack() == true) return
                if (!navController.popBackStack()) {
                    showExitDialog()
                }
            }
        })

        handleSharedUrl()
    }

    private fun currentPlayer(): TvPlayerFragment? =
        navHost.childFragmentManager.primaryNavigationFragment as? TvPlayerFragment

    private fun setupNavigation() {
        val routes = listOf(
            Triple(R.id.tvNavHome, R.id.tvHomeFragment, null),
            Triple(R.id.tvNavSearch, R.id.tvSiteSearchFragment, null),
            Triple(R.id.tvNavFilms, R.id.tvCatalogFragment, bundleOf("section" to "FILMS")),
            Triple(R.id.tvNavSeries, R.id.tvSeriesCatalogFragment, bundleOf("section" to "SERIES")),
            Triple(R.id.tvNavHistory, R.id.tvLibraryFragment, bundleOf("kind" to "HISTORY")),
            Triple(R.id.tvNavFavorites, R.id.tvFavoritesLibraryFragment, bundleOf("kind" to "FAVORITES")),
            Triple(R.id.tvNavSettings, R.id.tvSettingsFragment, null),
        )
        routes.forEach { (button, destination, args) ->
            findViewById<View>(button).setOnClickListener {
                if (destination == R.id.tvHomeFragment) {
                    navController.popBackStack(R.id.tvHomeFragment, false, true)
                } else {
                    val same = navController.currentDestination?.id == destination &&
                        (args == null || args.keySet().all { key ->
                            navController.currentBackStackEntry?.arguments?.getString(key) == args.getString(key)
                        })
                    if (findViewById<View>(button).isSelected && !same && navController.popBackStack(destination, false)) {
                        return@setOnClickListener
                    }
                    if (!same) navController.navigate(destination, args,
                        NavOptions.Builder().setPopUpTo(R.id.tvHomeFragment, false, true)
                            .setRestoreState(true).setLaunchSingleTop(true).build())
                }
            }
        }
        navController.addOnDestinationChangedListener { _, destination, args ->
            findViewById<View>(R.id.tvNavigation).isVisible = destination.id != R.id.tvPlayerFragment
            val selected = when (destination.id) {
                R.id.tvHomeFragment -> R.id.tvNavHome
                R.id.tvSearchFragment, R.id.tvSiteSearchFragment -> R.id.tvNavSearch
                R.id.tvCatalogFragment, R.id.tvSeriesCatalogFragment -> if (args?.getString("section") == "SERIES") R.id.tvNavSeries else R.id.tvNavFilms
                R.id.tvLibraryFragment, R.id.tvFavoritesLibraryFragment -> if (args?.getString("kind") == "HISTORY") R.id.tvNavHistory else R.id.tvNavFavorites
                R.id.tvSettingsFragment -> R.id.tvNavSettings
                else -> return@addOnDestinationChangedListener
            }
            routes.forEach { (button, _, _) -> findViewById<View>(button).isSelected = button == selected }
        }
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
            Toast.makeText(this@TvMainActivity, R.string.loading, Toast.LENGTH_SHORT).show()
            val result = linkInteractor.fetchAndSaveMovieCard(sharedUrl)
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            when (result) {
                is DataResult.Success -> navController.navigate(R.id.tvDetailsFragment,
                    bundleOf("movieId" to result.result),
                    NavOptions.Builder().setPopUpTo(R.id.tvHomeFragment, false).build())
                is DataResult.Error -> {
                    if (result.throwable is CancellationException) throw result.throwable
                    Toast.makeText(this@TvMainActivity, R.string.tv_shared_link_error, Toast.LENGTH_SHORT).show()
                }
            }
            // Keep the input while a cancelled configuration-change fetch is pending; consume only a result.
            intent.removeExtra(StartActivity.KEY_SHARED_URL)
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
