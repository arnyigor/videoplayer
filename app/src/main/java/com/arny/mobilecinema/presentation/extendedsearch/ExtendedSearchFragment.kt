package com.arny.mobilecinema.presentation.extendedsearch

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.databinding.FExtendedSearchBinding
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel.Companion.DIALOG_REQ_GENRES
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel.Companion.DIALOG_REQ_TYPES
import com.arny.mobilecinema.presentation.uimodels.Dialog
import com.arny.mobilecinema.presentation.uimodels.DialogType
import com.arny.mobilecinema.presentation.utils.getString
import com.arny.mobilecinema.presentation.utils.hideKeyboard
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.multiChoiceDialog
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import javax.inject.Inject

class ExtendedSearchFragment : Fragment(R.layout.f_extended_search) {
    private lateinit var binding: FExtendedSearchBinding

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory

    @Inject
    lateinit var prefs: Prefs
    private val viewModel: ExtendedSearchViewModel by viewModels { vmFactory }

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
        initUI()
        initMenu()
        initListeners()
        observeData()
    }

    private fun initUI() = with(binding) {
        edtRangeFrom.setOnFocusChangeListener { _, hasFocus ->
            if(!hasFocus){
                viewModel.onRangeFromChange(edtRangeFrom.text.toString())
            }
        }
        edtRangeTo.setOnFocusChangeListener { _, hasFocus ->
            if(!hasFocus){
                viewModel.onRangeToChange(edtRangeFrom.text.toString())
            }
        }
        clRoot.setOnClickListener {
            btnSearchExtend.clearFocus()
            requireActivity().hideKeyboard()
        }
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.dialog.collectLatest { dialog ->
                showDialog(dialog)
            }
        }
        launchWhenCreated {
            viewModel.types.collectLatest { typesString ->
                setTypes(typesString)
            }
        }
        launchWhenCreated {
            viewModel.selectedGenres.collectLatest { genres ->
                setGenres(genres)
            }
        }
        launchWhenCreated {
            viewModel.yearsRange.collectLatest { range ->
                initYearsRangeSlider(range)
            }
        }
    }

    private fun initYearsRangeSlider(yearsRange: YearsRangeUIModel?) = with(binding) {
        if (yearsRange != null) {
            val from = yearsRange.from
            val to = yearsRange.to
            edtRangeFrom.setText(from.toString())
            edtRangeTo.setText(to.toString())
            tvYearsRange.isVisible = true
            clRanges.isVisible = true
        }
    }

    private fun setTypes(typesString: List<IWrappedString>) {
        val types = typesString.map { it.toString(requireContext()) }
        binding.tiedtTypes.setText(types.joinToString(","))
    }

    private fun setGenres(genres: List<GenreUIModel>) = with(binding) {
        val genresTypes = genres.map { it.title }
        tiedtGenres.setText(genresTypes.joinToString(","))
        tilGenres.isEndIconVisible = genres.isNotEmpty()
    }

    private fun showDialog(dialog: Dialog) {
        when (dialog.type) {
            is DialogType.MultiChoose -> {
                when (dialog.request) {
                    DIALOG_REQ_TYPES -> {
                        multiChoiceDialog(
                            title = dialog.title.toString(requireContext()).orEmpty(),
                            btnOk = dialog.btnOk?.toString(requireContext()).orEmpty(),
                            btnCancel = dialog.btnCancel?.toString(requireContext()).orEmpty(),
                            initItems = dialog.type.items.map {
                                it.toString(requireContext()).orEmpty()
                            },
                            onSelect = { indices: IntArray, dlg ->
                                viewModel.onTypesChosen(indices)
                                dlg.dismiss()
                            }
                        )
                    }

                    DIALOG_REQ_GENRES -> {
                        multiChoiceDialog(
                            title = dialog.title.toString(requireContext()).orEmpty(),
                            btnOk = dialog.btnOk?.toString(requireContext()).orEmpty(),
                            btnCancel = dialog.btnCancel?.toString(requireContext()).orEmpty(),
                            initItems = dialog.type.items.map {
                                it.toString(requireContext()).orEmpty()
                            },
                            onSelect = { indices: IntArray, dlg ->
                                viewModel.onGenreSelectChanged(indices)
                                dlg.dismiss()
                            }
                        )
                    }

                    else -> {}
                }

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

    private fun initListeners() = with(binding) {
        btnSearchExtend.setOnClickListener {
            setFragmentResult(
                AppConstants.FRAGMENTS.RESULTS, bundleOf(
                    AppConstants.SearchType.TYPE to AppConstants.SearchType.CINEMA
                )
            )
            findNavController().popBackStack()
        }
        tiedtTypes.setOnClickListener {
            viewModel.onTypesClicked()
        }
        tiedtGenres.setOnClickListener {
            viewModel.onGenresClicked()
        }
        tilGenres.setEndIconOnClickListener {
            viewModel.onRemoveGenresClicked()
        }
    }
}