package com.arny.homecinema.presentation.details

import android.content.Context
import android.content.res.Configuration.*
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.arny.homecinema.R
import com.arny.homecinema.data.models.DataResult
import com.arny.homecinema.data.models.DataThrowable
import com.arny.homecinema.databinding.DetailsFragmentBinding
import com.arny.homecinema.di.models.Movie
import com.arny.homecinema.di.models.MovieType
import com.arny.homecinema.di.models.SerialEpisode
import com.arny.homecinema.di.models.Video
import com.arny.homecinema.presentation.utils.hideSystemBar
import com.arny.homecinema.presentation.utils.showSystemBar
import com.arny.homecinema.presentation.utils.toast
import com.arny.homecinema.presentation.utils.viewBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.EventLogger
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.properties.Delegates


class DetailsFragment : Fragment() {

    private var trackSelector: DefaultTrackSelector? = null
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentVideo: Video? = null
    private val args: DetailsFragmentArgs by navArgs()
    private var exoPlayer: SimpleExoPlayer? = null
    private var playControlsVisible by Delegates.observable(true) { _, oldValue, newValue ->
        if (oldValue != newValue && activity != null && isAdded) {
            if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
                setFullScreen(activity as AppCompatActivity?, !newValue)
            }
            binding.spinEpisodes.isVisible = newValue
            binding.spinSeasons.isVisible = newValue
//            binding.mtvTitle.isVisible = newValue
        }
    }

    companion object {
        private const val KEY_VIDEO = "KEY_VIDEO"
    }

    @Inject
    lateinit var vm: DetailsViewModel

    private val binding by viewBinding { DetailsFragmentBinding.bind(it).also(::initBinding) }

    private val playerListener = object : Player.EventListener {
        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {
            val mappedTrackInfo = trackSelector?.currentMappedTrackInfo
            if (mappedTrackInfo != null) {
                Log.d(
                    DetailsFragment::class.java.simpleName,
                    "onPlayerError: mappedTrackInfo:$mappedTrackInfo"
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val window = requireActivity().window
            if (isPlaying) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private fun initBinding(binding: DetailsFragmentBinding) {
        return with(binding) {
            val movie = args.movie
            initTrackAdapters()
            vm.loadVideo(movie)
            updateTitle(movie.title)
            vm.data.observe(this@DetailsFragment, { dataResult ->
                when (dataResult) {
                    is DataResult.Success -> onMovieLoaded(dataResult.data)
                    is DataResult.Error -> toastError(dataResult.throwable)
                }
            })

            vm.episodes.observe(this@DetailsFragment, { dataResult ->
                when (dataResult) {
                    is DataResult.Success -> {
                        dataResult.data.firstOrNull()?.let { episode ->
                            releasePlayer()
                            val title = episode.title ?: ""
                            updateTitle(title)
                            onMovieLoaded(
                                Movie(
                                    title,
                                    MovieType.CINEMA,
                                    video = getVideoFromSerial(episode)
                                )
                            )
                        }
                    }
                    is DataResult.Error -> toastError(dataResult.throwable)
                }
            })

            vm.episode.observe(this@DetailsFragment, { dataResult ->
                when (dataResult) {
                    is DataResult.Success -> {
                        dataResult.data?.let { episode ->
                            releasePlayer()
                            val title = episode.title ?: ""
                            updateTitle(title)
                            onMovieLoaded(
                                Movie(
                                    title,
                                    MovieType.CINEMA,
                                    video = getVideoFromSerial(episode)
                                )
                            )
                        }
                    }
                    is DataResult.Error -> toastError(dataResult.throwable)
                }
            })
        }
    }

    private fun updateTitle(title: String) {
        requireActivity().title = title
//        binding.mtvTitle.isVisible = resources.configuration.orientation == ORIENTATION_LANDSCAPE
//        binding.mtvTitle.text = title
    }

    private fun getVideoFromSerial(episode: SerialEpisode) = Video(
        episode.id,
        videoUrl = getUrl(episode, episode.selectedHls),
        hlsList = episode.hlsList,
        selectedHls = episode.selectedHls
    )

    private fun toastError(throwable: Throwable) {
        toast(
            when (throwable) {
                is DataThrowable -> getString(throwable.errorRes)
                else -> throwable.message
            }
        )
    }

    private fun DetailsFragmentBinding.initTrackAdapters() {
        seasonsTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
        episodesTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
        // TODO установить список из видео
        seasonsTracksAdapter?.addAll(resources.getStringArray(R.array.trackSeasons).toList())
        episodesTracksAdapter?.addAll(resources.getStringArray(R.array.trackEpisodes).toList())
        spinSeasons.adapter = seasonsTracksAdapter
        spinEpisodes.adapter = episodesTracksAdapter
        spinSeasons.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                vm.onSeasonChange(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spinEpisodes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                vm.onEpisodeChange(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun onMovieLoaded(movie: Movie?) {
        lifecycleScope.launch {
            initPlayer(movie)
        }
    }

    private fun initPlayer(movie: Movie? = null) {
        movie?.title?.let { updateTitle(it) }
        if (movie != null && movie.video != currentVideo) {
            currentVideo = movie.video
        }
        currentVideo?.let { video ->
            trackSelector = DefaultTrackSelector(requireContext())
            exoPlayer = SimpleExoPlayer.Builder(requireContext())
                .setTrackSelector(trackSelector!!)
                .build()
            exoPlayer?.addAnalyticsListener(EventLogger(trackSelector))
            exoPlayer?.addListener(playerListener)
            binding.plVideoPLayer.player = exoPlayer
            binding.plVideoPLayer.setControllerVisibilityListener { viewVisible ->
                if (activity != null && isAdded) {
                    playControlsVisible = when (viewVisible) {
                        View.VISIBLE -> true
                        else -> false
                    }
                }
            }
            when (movie?.type) {
                MovieType.CINEMA -> playerAddVideoData(video)
                MovieType.SERIAL -> playerAddSerialdata(movie)
            }
            exoPlayer?.prepare()
        }
    }

    private fun playerAddVideoData(video: Video) {
        video.videoUrl?.let { url ->
            if (url.contains(".m3u8")) {
                exoPlayer?.setMediaSource(createSource(url, video.id))
            } else {
                exoPlayer?.setMediaItem(MediaItem.fromUri(url))
            }
        } ?: kotlin.run {
            toastError(DataThrowable(R.string.video_link_not_found))
        }
    }

    private fun playerAddSerialdata(movie: Movie) {
        movie.serialData
            ?.seasons
            ?.flatMap { it.episodes ?: emptyList() }
            ?.asSequence()
            ?.forEach { episode ->
                getUrl(episode)?.let { url ->
                    if (url.contains(".m3u8")) {
                        exoPlayer?.addMediaSource(createSource(url, episode.id))
                    } else {
                        exoPlayer?.addMediaItem(MediaItem.fromUri(url))
                    }
                } ?: kotlin.run {
                    toastError(DataThrowable(R.string.video_link_not_found))
                }
            }
    }

    private fun getUrl(episode: SerialEpisode, key: String? = null): String? {
        val keys = episode.hlsList?.keys
        val qualityKey = key ?: keys?.map { it.toIntOrNull() ?: 0 }
            ?.minByOrNull { it }?.toString() ?: keys?.first()
        return episode.hlsList?.get(qualityKey)
    }

    private fun createSource(url: String?, id: Int?): HlsMediaSource {
        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSourceFactory()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaId(id.toString())
            .build()
        return HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(item)
    }

    private fun restorePlayerState() {
        currentVideo?.let { video ->
            exoPlayer?.seekTo(video.currentPosition)
            exoPlayer?.playWhenReady = video.playWhenReady
        }
    }

    private fun releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.stop()
            currentVideo?.currentPosition = exoPlayer?.contentPosition ?: 0
            currentVideo?.playWhenReady = exoPlayer?.playWhenReady ?: false
            binding.plVideoPLayer.player = null
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(KEY_VIDEO, currentVideo)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        currentVideo = savedInstanceState?.getParcelable(KEY_VIDEO)
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onResume() {
        super.onResume()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val appCompatActivity = activity as AppCompatActivity?
            appCompatActivity?.supportActionBar?.hide()
            setFullScreen(appCompatActivity, true)
        }
        restorePlayerState()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val appCompatActivity = activity as AppCompatActivity?
            appCompatActivity?.supportActionBar?.show()
            val window = appCompatActivity?.window
            setFullScreen(appCompatActivity, false)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setFullScreen(appCompatActivity: AppCompatActivity?, setFullScreen: Boolean) {
        if (setFullScreen) {
            appCompatActivity?.hideSystemBar()
            appCompatActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        } else {
            appCompatActivity?.showSystemBar()
            appCompatActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.details_fragment, container, false)
    }
}
