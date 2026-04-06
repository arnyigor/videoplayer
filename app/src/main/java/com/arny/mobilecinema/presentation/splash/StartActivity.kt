package com.arny.mobilecinema.presentation.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.arny.mobilecinema.R
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.tv.TvMainActivity
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class StartActivity : AppCompatActivity(), KoinComponent {
    private val viewModel: SplashViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Koin injection
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { true }
        val content: View = findViewById(android.R.id.content)
        handleIntent(intent)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (viewModel.readyComplete.value) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        val targetActivity = if (DeviceUtils.isTV(this@StartActivity)) {
                            TvMainActivity::class.java
                        } else {
                            MainActivity::class.java
                        }
                        val intent = Intent(this@StartActivity, targetActivity)
                        intent.putExtra(KEY_SHARED_URL, viewModel.sharedUrl)
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                        finish()
                        true
                    } else {
                        false
                    }
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
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
    }
}