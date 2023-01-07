package com.arny.mobilecinema.presentation.playerview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FPlayerViewBinding
import com.arny.mobilecinema.presentation.utils.toast
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import kotlinx.coroutines.launch

class PlayerViewFragment : Fragment(R.layout.f_player_view) {
    private val args: PlayerViewFragmentArgs by navArgs()
    private var exoPlayer: ExoPlayer? = null
    private val viewModel: PlayerViewModel by viewModels()
    private var _binding: FPlayerViewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FPlayerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        args.path?.let { viewModel.setPath(it) }
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val data = state.playerData
                    data.path?.let {
                        preparePlayer(it, data.position)
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

    private fun preparePlayer(path: String, position: Long) {
        exoPlayer = ExoPlayer.Builder(requireContext()).build()
        exoPlayer?.playWhenReady = true
        binding.playerView.player = exoPlayer
        val mediaSource = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
            .createMediaSource(MediaItem.fromUri(path))
        exoPlayer?.apply {
            setMediaSource(mediaSource)
            seekTo(position)
            playWhenReady = playWhenReady
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.progressBar.isVisible = true
                        }

                        Player.STATE_READY, Player.STATE_ENDED -> {
                            binding.progressBar.isVisible = false
                        }

                        else -> {}
                    }
                }
            })
            prepare()
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