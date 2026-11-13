package com.arny.mobilecinema.presentation.splash

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.ActivitySplashBinding
import com.arny.mobilecinema.presentation.MainActivity
import com.arny.mobilecinema.presentation.tv.TvMainActivity
import com.arny.mobilecinema.presentation.utils.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class StartActivity : AppCompatActivity(), KoinComponent {
    private val viewModel: SplashViewModel by viewModel()

    private lateinit var binding: ActivitySplashBinding
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Системного splash нет вовсе (тема AppTheme.Splash): раньше он показывал статичную
        // иконку, и пользователь видел две заставки подряд — картинку, затем анимацию.
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleIntent(intent)
        // Анимация стартует с первого реально нарисованного кадра: запуск в onCreate
        // «съедался» до того, как окно успевало показаться.
        binding.root.doOnPreDraw { playIntroAnimation() }
        awaitStartup()
    }

    /**
     * Логотип «выпрыгивает» с масштабом и прозрачностью, затем плавно дышит;
     * заголовок и индикатор поднимаются следом. Всё на нативных аниматорах —
     * без зависимостей и без риска для времени старта.
     */
    private fun playIntroAnimation() {
        binding.ivSplashLogo.apply {
            alpha = 0f
            scaleX = 0.6f
            scaleY = 0.6f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(LOGO_APPEAR_MS)
                .setInterpolator(OvershootInterpolator(1.4f))
                .withEndAction { startBreathing() }
                .start()
        }

        binding.tvSplashTitle.apply {
            translationY = 24f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(TITLE_DELAY_MS)
                .setDuration(TITLE_FADE_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.pbSplash.apply {
            translationY = 16f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(PROGRESS_DELAY_MS)
                .setDuration(TITLE_FADE_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Мягкое «дыхание» логотипа, пока идёт резолв зеркала. */
    private fun startBreathing() {
        val breath = ObjectAnimator.ofPropertyValuesHolder(
            binding.ivSplashLogo,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f),
        ).apply {
            duration = BREATH_MS
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = DecelerateInterpolator()
        }
        breath.start()
    }

    /** Ждём готовности, но не меньше [MIN_VISIBLE_MS]: анимация не должна мигать. */
    private fun awaitStartup() {
        lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            // Первое определённое состояние: Ready или SiteUnavailable.
            val state = viewModel.state.first { it !is SplashState.Checking }
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_VISIBLE_MS) delay(MIN_VISIBLE_MS - elapsed)
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
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
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

        /** Сайт недоступен: MainActivity покажет ошибку и предложит повтор. */
        const val KEY_SITE_OFFLINE = "KEY_SITE_OFFLINE"

        private const val LOGO_APPEAR_MS = 520L
        private const val TITLE_DELAY_MS = 120L
        private const val TITLE_FADE_MS = 420L
        private const val PROGRESS_DELAY_MS = 260L
        private const val BREATH_MS = 1400L
        private const val MIN_VISIBLE_MS = 1200L
    }
}
