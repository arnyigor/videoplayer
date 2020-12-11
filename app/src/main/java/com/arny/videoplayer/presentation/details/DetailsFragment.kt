package com.arny.videoplayer.presentation.details

import android.content.Context
import android.content.res.Configuration.*
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.arny.videoplayer.R
import com.arny.videoplayer.data.models.DataResult
import com.arny.videoplayer.databinding.DetailsFragmentBinding
import com.arny.videoplayer.presentation.utils.viewBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject


class DetailsFragment : Fragment() {

    val args: DetailsFragmentArgs by navArgs()

    companion object {
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
        tvVideoTitle.text = video.title
        videoView.setOnPreparedListener {
            videoView.start()
        }
        vm.result.observe(this@DetailsFragment, {
            when (it) {
                is DataResult.Success -> {
                    videoView.setVideoURI(Uri.parse(it.data.playUrl));
                }
                is DataResult.Error -> Toast.makeText(
                    requireContext(),
                    it.throwable.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (resources.configuration.orientation == ORIENTATION_LANDSCAPE)
            (activity as AppCompatActivity?)?.supportActionBar?.hide()
    }

    override fun onStop() {
        super.onStop()
        if (resources.configuration.orientation == ORIENTATION_PORTRAIT)
            (activity as AppCompatActivity?)?.supportActionBar?.show()
    }

    override fun onPause() {
        super.onPause()
        binding.videoView.pause()
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
