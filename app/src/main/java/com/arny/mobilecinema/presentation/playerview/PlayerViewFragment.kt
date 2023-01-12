package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
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
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.updateTitle
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.StyledPlayerView.ControllerVisibilityListener
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class PlayerViewFragment : Fragment(R.layout.f_player_view), Player.Listener {
    private val args: PlayerViewFragmentArgs by navArgs()
    private val viewModel: PlayerViewModel by viewModels()
    private var qualityPopUp: PopupMenu? = null
    private var player: ExoPlayer? = null
    private var playbackPosition = 0L
    private var playWhenReady = true
    private var trackSelector: DefaultTrackSelector? = null
    var qualityList = ArrayList<Pair<String, TrackSelectionOverride>>()
    private var _binding: FPlayerViewBinding? = null
    private val binding
        get() = _binding!!

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
        _binding = view
        return view.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        args.path?.let { viewModel.setPath(it) }
        observeState()
        initListener()
    }

    private fun initListener() {
        binding.ivQuality.setOnClickListener {
            qualityPopUp?.show()
        }
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
                    Timber.d("Url:$path")
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
        with(binding) {
            tvTitle.text = name
            val loadControl =
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(64 * 1024, 128 * 1024, 1024, 1024)
                    .build()
            trackSelector = DefaultTrackSelector(requireContext(), AdaptiveTrackSelection.Factory())
            player = ExoPlayer.Builder(requireContext())
                .setLoadControl(loadControl)
                .setRenderersFactory(DefaultRenderersFactory(requireContext()))
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build()
            player?.playWhenReady = true
            playerView.player = player
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
                player?.apply {
                    setMediaSource(mediaSource)
                    seekTo(position)
                    playWhenReady = playWhenReady
                    addListener(this@PlayerViewFragment)
                    prepare()
                }
            }
        }
    }

    private fun setUpQualityList() {
        qualityPopUp = PopupMenu(requireContext(), binding.ivQuality)
        qualityList.let {
            for ((i, videoQuality) in it.withIndex()) {
                qualityPopUp?.menu?.add(0, i, 0, videoQuality.first)
            }
        }
        qualityPopUp?.setOnMenuItemClickListener { menuItem ->
            qualityList[menuItem.itemId].let { quality ->
                trackSelector?.let { selector ->
                    selector.parameters = selector.parameters
                        .buildUpon()
                        .addOverride(quality.second)
                        .setTunnelingEnabled(true)
                        .build()
                }
            }
            true
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        binding.progressBar.isVisible = false
        toast(error.message)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                binding.progressBar.isVisible = true
            }

            Player.STATE_ENDED -> {
                binding.progressBar.isVisible = false
            }

            Player.STATE_READY -> {
                binding.progressBar.isVisible = false
                trackSelector?.generateQualityList(requireContext())?.let {
                    qualityList = it
                    setUpQualityList()
                }
            }

            else -> {}
        }
    }

    private fun releasePlayer() {
        player?.let {
            viewModel.setPosition(it.currentPosition)
            it.stop()
            it.release()
            player = null
        }
    }
}