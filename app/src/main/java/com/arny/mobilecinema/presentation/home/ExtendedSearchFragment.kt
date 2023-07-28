package com.arny.mobilecinema.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.databinding.FExtendedSearchBinding
import com.arny.mobilecinema.presentation.utils.updateTitle

class ExtendedSearchFragment : Fragment(R.layout.f_extended_search) {
    private lateinit var binding: FExtendedSearchBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FExtendedSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateTitle(getString(R.string.search_extended_title))
        initListeners()
    }

    private fun initListeners() {
        binding.btnSearchExtend.setOnClickListener {
            setFragmentResult(
                AppConstants.FRAGMENTS.RESULTS, bundleOf(
                    AppConstants.SearchType.TYPE to AppConstants.SearchType.CINEMA
                )
            )
            findNavController().popBackStack()
        }
    }
}