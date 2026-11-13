package com.arny.mobilecinema.presentation.splash

import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
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
    private var animatorsStarted = false

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
     * Сцена: логотип входит масштабом с поворотом, вокруг расходятся сонар-волны,
     * заголовок проявляется с бегущим бликом, под ним вращается дуга.
     * Всё нативных аниматорами — без зависимостей и без влияния на время старта.
     */
    private fun playIntroAnimation() {
        if (animatorsStarted) return
        animatorsStarted = true

        binding.logoStage.alpha = 0f
        binding.logoStage.scaleX = 0.35f
        binding.logoStage.scaleY = 0.35f
        binding.logoStage.rotation = -25f
        binding.logoStage.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(LOGO_APPEAR_MS)
            .setInterpolator(OvershootInterpolator(1.5f))
            .withEndAction { startBreathing() }
            .start()

        binding.titleStage.apply {
            translationY = 22f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(TITLE_DELAY_MS)
                .setDuration(TITLE_FADE_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        binding.ivSplashArc.apply {
            animate()
                .alpha(1f)
                .setStartDelay(ARC_DELAY_MS)
                .setDuration(ARC_FADE_MS)
                .start()
        }

        startSonar()
        startShimmer()
        startArcSpin()
    }

    /** Две расходящиеся волны со сдвигом фазы: логотип «излучает». */
    private fun startSonar() {
        listOf(binding.ringInner to 0L, binding.ringOuter to SONAR_STAGGER_MS).forEach { (ring, delayMs) ->
            val animator = ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.55f, 1.9f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.55f, 1.9f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.45f, 0f),
            ).apply {
                duration = SONAR_MS
                startDelay = delayMs
                repeatCount = ObjectAnimator.INFINITE
                interpolator = DecelerateInterpolator()
            }
            animator.start()
        }
    }

    /** Блик, бегущий по названию слева направо. */
    private fun startShimmer() {
        binding.vShimmer.post {
            val titleWidth = binding.tvSplashTitle.width.toFloat().coerceAtLeast(1f)
            val shimmerWidth = binding.vShimmer.width.toFloat().coerceAtLeast(1f)
            val animator = ObjectAnimator.ofFloat(
                binding.vShimmer,
                View.TRANSLATION_X,
                -shimmerWidth,
                titleWidth,
            ).apply {
                duration = SHIMMER_MS
                startDelay = SHIMMER_DELAY_MS
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                interpolator = LinearInterpolator()
            }
            binding.vShimmer.alpha = 1f
            animator.start()
        }
    }

    /** Вращение дуги-индикатора. */
    private fun startArcSpin() {
        ObjectAnimator.ofFloat(binding.ivSplashArc, View.ROTATION, 0f, 360f).apply {
            duration = ARC_SPIN_MS
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }.start()
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

    /** Выход: сцена «раскрывается», а не гаснет — переход в MainActivity остаётся плавным. */
    private fun playOutroAnimation(onEnd: () -> Unit) {
        binding.logoStage.animate()
            .scaleX(1.18f)
            .scaleY(1.18f)
            .setDuration(OUTRO_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        binding.splashRoot.animate()
            .alpha(0f)
            .setDuration(OUTRO_MS)
            .withEndAction(onEnd)
            .start()
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
        playOutroAnimation { startTargetActivity(state) }
    }

    private fun startTargetActivity(state: SplashState) {
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

        private const val LOGO_APPEAR_MS = 600L
        private const val SONAR_MS = 1900L
        private const val SONAR_STAGGER_MS = 950L
        private const val SHIMMER_MS = 1150L
        private const val SHIMMER_DELAY_MS = 700L
        private const val ARC_DELAY_MS = 250L
        private const val ARC_FADE_MS = 380L
        private const val ARC_SPIN_MS = 1400L
        private const val OUTRO_MS = 320L
        private const val TITLE_DELAY_MS = 120L
        private const val TITLE_FADE_MS = 420L
        private const val PROGRESS_DELAY_MS = 260L
        private const val BREATH_MS = 1400L
        private const val MIN_VISIBLE_MS = 1400L
    }
}
