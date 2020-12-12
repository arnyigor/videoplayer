package com.arny.videoplayer.presentation.details

import android.content.Context
import android.content.res.Configuration.*
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.Log
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
import com.arny.videoplayer.presentation.utils.viewBinding
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject


class DetailsFragment : Fragment(), Player.EventListener {

    private var playUrl: String? = null
    private val args: DetailsFragmentArgs by navArgs()
    private var exoPlayer: SimpleExoPlayer? = null

    companion object {
        fun getInstance() = DetailsFragment()
    }

    @Inject
    lateinit var vm: DetailsViewModel

    private val binding by viewBinding { DetailsFragmentBinding.bind(it).also(::initBinding) }

    private fun initBinding(binding: DetailsFragmentBinding) = with(binding) {
        exoPlayer = SimpleExoPlayer.Builder(requireContext()).build()
        exoPlayer?.addListener(this@DetailsFragment)
        plVideoPLayer.player = exoPlayer
        val video = args.video
        vm.loadVideo(video)
        vm.loading.observe(this@DetailsFragment, { load ->
            pbLoadingVideo.isVisible = load
        })
        tvVideoTitle.text = video.title

        vm.data.observe(this@DetailsFragment, { dataResult ->
            when (dataResult) {
                is DataResult.Success -> {
                    playUrl = dataResult.data.playUrl
                    initPlayer()
                }
                is DataResult.Error -> Toast.makeText(
                    requireContext(),
                    dataResult.throwable.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.d(DetailsFragment::class.java.canonicalName, "onIsPlayingChanged:$isPlaying ");
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        Log.e(
            DetailsFragment::class.java.canonicalName,
            "ExoPlaybackException:${error.sourceException.message} "
        );
    }

    private fun initPlayer() {
        playUrl?.let {
            exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(playUrl)), false)
            exoPlayer?.prepare()
        }
    }

    private fun releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    override fun onResume() {
        super.onResume()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val appCompatActivity = activity as AppCompatActivity?
            appCompatActivity?.supportActionBar?.hide()
            appCompatActivity?.window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        if (SDK_INT <= 23 || exoPlayer == null) {
            initPlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE) {
            val appCompatActivity = activity as AppCompatActivity?
            appCompatActivity?.supportActionBar?.show()
            appCompatActivity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }
        if (SDK_INT > 23) {
            releasePlayer()
        }
    }


    override fun onStart() {
        super.onStart()
        if (SDK_INT > 23) {
            initPlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (SDK_INT <= 23) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
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
