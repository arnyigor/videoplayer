package com.arny.mobilecinema.presentation.extendedsearch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.databinding.FExtendedSearchBinding
import com.arny.mobilecinema.presentation.utils.autoClean
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class ExtendedSearchFragment : Fragment(R.layout.f_extended_search) {
    private lateinit var binding: FExtendedSearchBinding

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: ExtendedSearchViewModel by viewModels { vmFactory }
    private val genresAdapter by autoClean {
        GenresChoiceAdapter { position, isChecked ->
            viewModel.onGenreSelectChanged(position, isChecked)
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

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
        initMenu()
        initUI()
        initListeners()
        observeData()
    }

    private fun initUI() {
        binding.rvGenres.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        binding.rvGenres.adapter = genresAdapter
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.genres.collectLatest { genres ->
                genresAdapter.submitList(genres.toList())
            }
        }
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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