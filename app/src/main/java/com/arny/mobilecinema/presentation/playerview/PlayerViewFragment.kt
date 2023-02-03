package com.arny.mobilecinema.presentation.playerview

import android.content.Context
import android.os.Build
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.utils.getConnectionType
import com.arny.mobilecinema.data.utils.getFullError
import com.arny.mobilecinema.databinding.FPlayerViewBinding
import com.arny.mobilecinema.presentation.player.PlayerSource
import com.arny.mobilecinema.presentation.player.generateQualityList
import com.arny.mobilecinema.presentation.utils.hideSystemUI
import com.arny.mobilecinema.presentation.utils.showSystemUI
import com.arny.mobilecinema.presentation.utils.toast
import com.arny.mobilecinema.presentation.utils.updateTitle
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView.ControllerVisibilityListener
import com.google.android.exoplayer2.util.Util
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class PlayerViewFragment : Fragment(R.layout.f_player_view), Player.Listener {
    private val args: PlayerViewFragmentArgs by navArgs()

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: PlayerViewModel by viewModels { vmFactory }
    private val resizeModes = arrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )
    private var qualityPopUp: PopupMenu? = null
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var qualityId: Int = 0
    private var resizeIndex = 0
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        args.path?.let { viewModel.setPath(it) }
        args.movie?.let { viewModel.setMovie(it) }
        binding.progressBar.isVisible = true
        observeState()
        initListener()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initListener() {
        binding.ivQuality.setOnClickListener {
            qualityPopUp?.show()
        }
        binding.ivResizes.setOnClickListener {
            changeResize()
        }
    }

    private fun changeResize() {
        resizeIndex += 1
        if (resizeIndex > resizeModes.size - 1) {
            resizeIndex = 0
        }
        binding.playerView.resizeMode = resizeModes[resizeIndex]

    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val movie = state.movie
                    val hdUrl = movie?.cinemaUrlData?.hdUrl?.urls?.firstOrNull()
                    val cinemaUrl = movie?.cinemaUrlData?.cinemaUrl?.urls?.firstOrNull()
                    val path = when {
                        !state.path.isNullOrBlank() -> state.path
                        !hdUrl.isNullOrBlank() -> hdUrl
                        !cinemaUrl.isNullOrBlank() -> cinemaUrl
                        else -> ""
                    }
                    binding.tvTitle.text = movie?.title ?: getString(R.string.no_movie_title)
                    path.takeIf { it.isNotBlank() }?.let {
                        try {
                            val mediaSource = playerSource.getSource(path)
                            setPlayerSource(state.position, mediaSource)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            toast(e.message)
                        }
                    } ?: kotlin.run {
                        toast(getString(R.string.path_not_found))
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.hide()
            window.hideSystemUI()
        }
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            preparePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        with((requireActivity() as AppCompatActivity)) {
            supportActionBar?.show()
            window.showSystemUI()
        }
        if (Util.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= Build.VERSION_CODES.N) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun preparePlayer() {
        with(binding) {
            val loadControl =
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(64 * 1024, 128 * 1024, 1024, 1024)
                    .build()
/*            val bandwidthMeter = DefaultBandwidthMeter.Builder(requireContext()).build().apply {
                addEventListener(Handler(Looper.getMainLooper())) { time, bytes, bitrate ->
                    Timber.d("onBandwidth timeMs:$time bitrate:${bitrate.div(1024)}")
                }
            }*/
            trackSelector = DefaultTrackSelector(requireContext(), AdaptiveTrackSelection.Factory())
            player = ExoPlayer.Builder(requireContext())
                .setLoadControl(loadControl)
//                .setBandwidthMeter(DefaultBandwidthMeter.Builder(requireContext()).build())
                .setRenderersFactory(DefaultRenderersFactory(requireContext()))
                .setTrackSelector(trackSelector!!)
                .setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .build()
            player?.playWhenReady = true
            playerView.player = player
            playerView.resizeMode = resizeModes[resizeIndex]
            playerView.setControllerVisibilityListener(ControllerVisibilityListener { vis ->
                if (isVisible) {
                    if (vis == View.VISIBLE) {
                        tvTitle.isVisible = true
                        ivQuality.isVisible = true
                        ivResizes.isVisible = true
                        activity?.window?.showSystemUI()
                    } else {
                        ivResizes.isVisible = false
                        ivQuality.isVisible = false
                        tvTitle.isVisible = false
                        activity?.window?.hideSystemUI()
                    }
                }
            })
        }
    }

    private fun setPlayerSource(position: Long, source: MediaSource?) {
        player?.apply {
            source?.let {
                val title = source.mediaItem.mediaMetadata.title
                if (!title.isNullOrBlank() && title != "null") {
                    updateTitle(title.toString())
                    binding.tvTitle.text = title.toString()
                }
                setMediaSource(source)
                seekTo(position)
                addListener(this@PlayerViewFragment)
                prepare()
            }
        }
    }

    private fun setUpQualityList() {
        qualityPopUp = PopupMenu(requireContext(), binding.ivQuality)
        qualityList.let { list ->
            for ((i, videoQuality) in list.withIndex()) {
                qualityPopUp?.menu?.add(0, i, 0, videoQuality.first)
            }
            // TODO fix by selected and after net changed
//            setQualityByConnection(list)
        }
        qualityPopUp?.setOnMenuItemClickListener { menuItem ->
            qualityId = menuItem.itemId
            setQuality(qualityList[qualityId].second)
            true
        }
    }

    private fun setQualityByConnection(list: ArrayList<Pair<String, TrackSelectionOverride>>) {
        val connectionType = getConnectionType(requireContext())
        val groupList = list.map { it.second }.map { it.mediaTrackGroup }
        val formats = groupList.mapIndexed { index, trackGroup -> trackGroup.getFormat(index) }
        val bitratesKbps = formats.map { it.bitrate.div(1024) }
        Timber.d("current QualityId:$qualityId")
        val newId =
            bitratesKbps.indexOfLast { it < connectionType.speedKbps }.takeIf { it >= 0 } ?: 0
        Timber.d("connectionType:$connectionType")
        Timber.d("bitrates:$bitratesKbps")
        Timber.d("selected qualityId:$qualityId")
        if (newId > qualityId) {// Check buufering time
            qualityId = newId
            Timber.d("set new qualityId:$qualityId")
            list.getOrNull(qualityId)?.second?.let { setQuality(it) }
            // FIXME NOT Need
        }
    }

    private fun setQuality(trackSelectionOverride: TrackSelectionOverride) {
        trackSelector?.let { selector ->
            selector.parameters = selector.parameters
                .buildUpon()
                .addOverride(trackSelectionOverride)
                .setTunnelingEnabled(true)
                .build()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        binding.progressBar.isVisible = false
        toast(getFullError(error))
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateState(playbackState)
    }

    private fun updateState(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                binding.progressBar.isVisible = true
            }

            Player.STATE_ENDED -> {
                binding.progressBar.isVisible = false
            }

            Player.STATE_READY -> {
                binding.progressBar.isVisible = false
                // TODO set first Time on load
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
            it.removeListener(this)
            it.stop()
            it.release()
            player = null
        }
    }
}