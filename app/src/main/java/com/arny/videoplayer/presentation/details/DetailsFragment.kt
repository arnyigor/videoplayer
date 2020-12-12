package com.arny.videoplayer.presentation.details

import android.content.Context
import android.content.res.Configuration.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.arny.videoplayer.R
import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.databinding.DetailsFragmentBinding
import com.arny.videoplayer.di.models.Video
import com.arny.videoplayer.presentation.utils.hideSystemBar
import com.arny.videoplayer.presentation.utils.showSystemBar
import com.arny.videoplayer.presentation.utils.viewBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject


class DetailsFragment : Fragment() {

    private var currentVideo: Video? = null
    private val args: DetailsFragmentArgs by navArgs()
    private var exoPlayer: SimpleExoPlayer? = null

    companion object {
        private const val KEY_VIDEO = "KEY_VIDEO"
        fun getInstance() = DetailsFragment()
    }

    @Inject
    lateinit var vm: DetailsViewModel

    private val binding by viewBinding { DetailsFragmentBinding.bind(it).also(::initBinding) }

    private fun initBinding(binding: DetailsFragmentBinding) = with(binding) {
        val video = args.video
        vm.loadVideo(video)
        vm.loading.observe(this@DetailsFragment, { load ->
            pbLoadingVideo.isVisible = load
        })
        requireActivity().title = video.title
        vm.data.observe(this@DetailsFragment, { dataResult ->
            when (dataResult) {
                is DataResult.Success -> {
                    val data = dataResult.data
//                    val hasPosition = currentVideo?.currentPosition ?: 0L != 0L
                    val sameVideo = currentVideo?.videoUrl == data.videoUrl
                    if (!sameVideo) {
                        currentVideo = data
                    }
                    initPlayer()
                }
                is DataResult.Error -> {
                    val throwable = dataResult.throwable
                    throwable.printStackTrace()
                    Toast.makeText(
                        requireContext(),
                        throwable.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun initPlayer() {
        currentVideo?.videoUrl?.let { url ->
            exoPlayer = SimpleExoPlayer.Builder(requireContext()).build()
            binding.plVideoPLayer.player = exoPlayer
            val dataSourceFactory: DataSource.Factory = DefaultHttpDataSourceFactory()
            val hlsMediaSource: HlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
            exoPlayer?.setMediaSource(hlsMediaSource);
            exoPlayer?.prepare()
        }
    }

    private fun restorePlayerState() {
        currentVideo?.let { video ->
            exoPlayer?.seekTo(video.currentPosition)
            exoPlayer?.playWhenReady = video.playWhenReady
        }
    }

    private fun releasePlayer() {
        if (exoPlayer != null) {
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
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
