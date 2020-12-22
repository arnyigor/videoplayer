package com.arny.homecinema.presentation.details

import android.content.Context
import android.content.res.Configuration.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import com.google.android.exoplayer2.util.EventLogger
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

class DetailsFragment : Fragment() {

    private var trackSelector: DefaultTrackSelector? = null
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentVideo: Video? = null
    private val args: DetailsFragmentArgs by navArgs()
    private var exoPlayer: SimpleExoPlayer? = null

    companion object {
        private const val KEY_VIDEO = "KEY_VIDEO"
    }

    @Inject
    lateinit var vm: DetailsViewModel

    private val binding by viewBinding { DetailsFragmentBinding.bind(it).also(::initBinding) }

    private val playerListener = object : Player.EventListener {
        override fun onPlayerError(error: ExoPlaybackException) {
            if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                val cause: IOException = error.sourceException
                if (cause is HttpDataSourceException) {
                    val httpError = cause
                    val requestDataSpec = httpError.dataSpec
                    Log.d(
                        DetailsFragment::class.java.simpleName,
                        "onPlayerError: httpError:$httpError"
                    )
                    if (httpError is InvalidResponseCodeException) {
                        Log.d(
                            DetailsFragment::class.java.simpleName,
                            "onPlayerError: responseMessage:${httpError.responseMessage}"
                        )
                    } else {
                        Log.d(
                            DetailsFragment::class.java.simpleName,
                            "onPlayerError: cause:${httpError.cause}"
                        )
                    }
                }
            }
        }

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

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> {
                    Log.i(
                        DetailsFragment::class.java.simpleName,
                        "onPlaybackStateChanged: STATE_IDLE"
                    )
                }

                Player.STATE_BUFFERING -> {
                    Log.i(
                        DetailsFragment::class.java.simpleName,
                        "onPlaybackStateChanged: STATE_BUFFERING"
                    )
                }
                Player.STATE_ENDED -> {
                    Log.i(
                        DetailsFragment::class.java.simpleName,
                        "onPlaybackStateChanged: STATE_ENDED"
                    )
                }
                Player.STATE_READY -> {
                    Log.i(
                        DetailsFragment::class.java.simpleName,
                        "onPlaybackStateChanged: STATE_READY"
                    )
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val window = requireActivity().window
            Log.d(DetailsFragment::class.java.simpleName, "onPlayerError: isPlaying:$isPlaying")
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
            requireActivity().title = movie.title
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
                            onMovieLoaded(
                                Movie(
                                    episode.title ?: "",
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
                            onMovieLoaded(
                                Movie(
                                    episode.title ?: "",
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

    private fun getVideoFromSerial(episode: SerialEpisode) = Video(
        episode.id,
        videoUrl = getUrl(episode, episode.selectedHls),
        hlsList = episode.hlsList,
        selectedHls = episode.selectedHls
    )

    private fun toastError(throwable1: Throwable) {
        toast(
            when (throwable1) {
                is DataThrowable -> getString(throwable1.errorRes)
                else -> throwable1.message
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
            when (movie?.type) {
                MovieType.CINEMA -> {
                    video.videoUrl?.let {
                        exoPlayer?.setMediaSource(createSource(it, video.id))
                    } ?: kotlin.run {
                        toastError(DataThrowable(R.string.video_link_not_found))
                    }
                }
                MovieType.SERIAL -> {
                    movie.serialData
                        ?.seasons
                        ?.flatMap { it.episodes ?: emptyList() }
                        ?.asSequence()
                        ?.forEach { episode ->
                            getUrl(episode)?.let {
                                exoPlayer?.addMediaSource(createSource(it, episode.id))
                            } ?: kotlin.run {
                                toastError(DataThrowable(R.string.video_link_not_found))
                            }
                        }
                }
            }
            exoPlayer?.prepare()
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
            appCompatActivity?.hideSystemBar()
            val window = appCompatActivity?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
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
            appCompatActivity?.showSystemBar()
            val window = appCompatActivity?.window
            window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
