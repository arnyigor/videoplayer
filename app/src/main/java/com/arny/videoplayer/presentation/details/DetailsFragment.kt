package com.arny.videoplayer.presentation.details

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.arny.videoplayer.R
import com.arny.videoplayer.databinding.DetailsFragmentBinding
import com.arny.videoplayer.presentation.home.HomeViewModel
import com.arny.videoplayer.presentation.utils.viewBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class DetailsFragment : Fragment() {

    val args: DetailsFragmentArgs by navArgs()

    companion object {
        fun getInstance() = DetailsFragment()
    }

    @Inject
    lateinit var vm: HomeViewModel

    private val binding by viewBinding { DetailsFragmentBinding.bind(it).also(::initBinding) }


    private fun initBinding(binding: DetailsFragmentBinding) = with(binding) {
        val video = args.video
        tvVideoTitle.text = video.title
        tvVideoUrl.text = video.url
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
