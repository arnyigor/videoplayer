package com.arny.mobilecinema.presentation.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ActivitySplashBinding
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.tv.TvMainActivity
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class StartActivity : AppCompatActivity(), KoinComponent {
    private val viewModel: SplashViewModel by viewModel()
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // The first frame matches the starting window. Defer DI/database initialization
        // until after it is drawn, without animating or imposing a minimum display time.
        binding.root.doOnPreDraw {
            binding.root.post {
                if (!isFinishing && !isDestroyed) {
                    handleIntent(intent)
                    awaitStartup()
                }
            }
        }
    }

    private fun awaitStartup() {
        lifecycleScope.launch {
            val state = viewModel.state.first { it !is SplashState.Checking }
            launchMain(state)
        }
    }

    private fun launchMain(state: SplashState) {
        if (launched || isFinishing || isDestroyed) return
        launched = true
        val targetActivity = if (DeviceUtils.isTV(this)) {
            TvMainActivity::class.java
        } else {
            MainActivity::class.java
        }
        val intent = Intent(this, targetActivity).apply {
            putExtra(KEY_SHARED_URL, viewModel.sharedUrl)
            putExtra(KEY_SITE_OFFLINE, state is SplashState.SiteUnavailable)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                    val extra = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (extra?.contains("anwap") == true) {
                        viewModel.onIntentUrl(extra)
                    }
                }
            }
        }
    }

    companion object {
        const val KEY_SHARED_URL = "KEY_SHARED_URL"

        /** Сайт недоступен: MainActivity покажет ошибку и предложит повтор. */
        const val KEY_SITE_OFFLINE = "KEY_SITE_OFFLINE"
    }
}
