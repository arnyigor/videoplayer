package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FPlayerViewBinding
import com.arny.mobilecinema.presentation.home.HomeViewModel
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.updateTitle
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView.ControllerVisibilityListener
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerViewFragment : Fragment(R.layout.f_player_view) {
    private val args: PlayerViewFragmentArgs by navArgs()
    private var exoPlayer: ExoPlayer? = null
    private val viewModel: PlayerViewModel by viewModels()
    private var binding: FPlayerViewBinding? = null

    @Inject
    lateinit var playerSource: PlayerSource

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = FPlayerViewBinding.inflate(inflater, container, false)
        binding = view
        return view.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        args.path?.let { viewModel.setPath(it) }
        observeState()
    }

    override fun onResume() {
        super.onResume()
        updateTitle(args.name)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val path = state.path
                    path?.let {
                        preparePlayer(it, state.position, args.name)
                    } ?: kotlin.run {
                        toast("Не найден путь")
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentOrientation = resources.configuration.orientation
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            with((requireActivity() as AppCompatActivity)) {
                supportActionBar?.hide()
                window.hideSystemUI()
            }
        } else {
            with((requireActivity() as AppCompatActivity)) {
                supportActionBar?.show()
                window.showSystemUI()
            }
        }
    }

    private fun preparePlayer(path: String, position: Long, name: String?) {
        binding?.let {
            with(it) {
                tvTitle.text = name
                exoPlayer = ExoPlayer.Builder(requireContext())
                    .setSeekBackIncrementMs(5000)
                    .setSeekForwardIncrementMs(5000)
                    .build()
                exoPlayer?.playWhenReady = true
                playerView.player = exoPlayer
                playerView.setControllerVisibilityListener(ControllerVisibilityListener { vis ->
                    if (isVisible) {
                        if (vis == View.VISIBLE) {
                            tvTitle.isVisible = true
                            activity?.window?.showSystemUI()
                        } else {
                            tvTitle.isVisible = false
                            activity?.window?.hideSystemUI()
                        }
                    }
                })
                val mediaSource = playerSource.getSource(path)
                mediaSource?.let {
                    exoPlayer?.apply {
                        setMediaSource(mediaSource)
                        seekTo(position)
                        playWhenReady = playWhenReady
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                when (playbackState) {
                                    Player.STATE_BUFFERING -> {
                                        progressBar.isVisible = true
                                    }

                                    Player.STATE_READY, Player.STATE_ENDED -> {
                                        progressBar.isVisible = false
                                    }

                                    else -> {}
                                }
                            }
                        })
                        prepare()
                    }
                }
            }
        }
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            viewModel.setPosition(player.currentPosition)
            player.stop()
            player.release()
            exoPlayer = null
        }
    }
}