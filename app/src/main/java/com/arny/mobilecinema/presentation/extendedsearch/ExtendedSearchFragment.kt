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
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.databinding.FExtendedSearchBinding
import com.arny.mobilecinema.di.viewModelFactory
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel.Companion.DIALOG_REQ_COUNTRIES
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel.Companion.DIALOG_REQ_GENRES
import com.arny.mobilecinema.presentation.extendedsearch.ExtendedSearchViewModel.Companion.DIALOG_REQ_TYPES
import com.arny.mobilecinema.presentation.uimodels.Dialog
import com.arny.mobilecinema.presentation.uimodels.DialogType
import com.arny.mobilecinema.presentation.utils.getString
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.multiChoiceDialog
import com.arny.mobilecinema.presentation.utils.strings.IWrappedString
import com.arny.mobilecinema.presentation.utils.strings.ResourceString
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import java.text.NumberFormat
import javax.inject.Inject

class ExtendedSearchFragment : Fragment(R.layout.f_extended_search) {

    @Inject
    lateinit var prefs: Prefs

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): ExtendedSearchViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: ExtendedSearchViewModel by viewModelFactory { viewModelFactory.create() }

    private companion object {
        const val MIN_IMDB = 0.0f
        const val MAX_IMDB = 10.0f
        const val MIN_KP = 0.0f
        const val MAX_KP = 10.0f
    }

    private lateinit var binding: FExtendedSearchBinding

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
        initUi()
        initMenu()
        initListeners()
        observeData()
    }

    private fun initUi() = with(binding) {
        initYearsRangeSlider()
        initImdbRangeSlider()
        initKpRangeSlider()
    }

    private fun FExtendedSearchBinding.initYearsRangeSlider() {
        rslYears.setLabelFormatter { value: Float ->
            NumberFormat.getInstance().format(value.toInt()).replace(",", "")
        }
        rslYears.addOnChangeListener { slider, _, _ ->
            val from = slider.values[0]
            val to = slider.values[1]
            tvYearsRange.text = getString(
                ResourceString(
                    R.string.years_range,
                    from.toInt().toString(),
                    to.toInt().toString()
                )
            )
            viewModel.updateYears(from, to)
        }
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.dialog.collectLatest { dialog ->
                showDialog(dialog)
            }
        }
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.pbLoading.isVisible = loading
            }
        }
        launchWhenCreated {
            viewModel.showContent.collectLatest { showContent ->
                binding.svContent.isVisible = showContent
                binding.btnSearchExtend.isVisible = showContent
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
            viewModel.selectedCountries.collectLatest { countries ->
                setCountries(countries)
            }
        }
        launchWhenCreated {
            viewModel.yearsRange.collectLatest { range ->
                initYearsRangeSlider(range)
            }
        }
        launchWhenCreated {
            viewModel.onResult.collectLatest { result ->
                onResultSend(result)
            }
        }
    }

    private fun onResultSend(result: ExtendSearchResult) {
        setFragmentResult(
            AppConstants.FRAGMENTS.RESULTS, bundleOf(
                AppConstants.SearchType.SEARCH_RESULT to result,
            )
        )
        findNavController().popBackStack()
    }

    private fun initYearsRangeSlider(yearsRange: SimpleIntRange?) = with(binding) {
        if (yearsRange != null) {
            val from = yearsRange.from
            val to = yearsRange.to
            rslYears.valueFrom = from.toFloat()
            rslYears.valueTo = to.toFloat()
            rslYears.values = listOf(from.toFloat(), to.toFloat())
            tvYearsRange.text = getString(
                ResourceString(R.string.years_range, from.toString(), to.toString())
            )
            tvYearsRange.isVisible = true
            rslYears.isVisible = true
        }
    }

    private fun initImdbRangeSlider() = with(binding) {
        rslImdb.valueFrom = MIN_IMDB
        rslImdb.valueTo = MAX_IMDB
        rslImdb.values = listOf(MIN_IMDB, MAX_IMDB)
        tvImdbRange.text = getString(
            ResourceString(R.string.imdb_range, MIN_IMDB.toString(), MAX_IMDB.toString())
        )
        tvImdbRange.isVisible = true
        rslImdb.isVisible = true
        rslImdb.setLabelFormatter { value: Float ->
            NumberFormat.getInstance().format(value)
        }
        rslImdb.addOnChangeListener { slider, _, _ ->
            val from = slider.values[0]
            val to = slider.values[1]
            tvImdbRange.text = getString(
                ResourceString(
                    R.string.imdb_range,
                    from.toInt().toString(),
                    to.toInt().toString()
                )
            )
            viewModel.updateImdb(from, to)
        }
    }

    private fun initKpRangeSlider() = with(binding) {
        rslKp.valueFrom = MIN_KP
        rslKp.valueTo = MAX_KP
        rslKp.values = listOf(MIN_KP, MAX_KP)
        tvKpRange.text = getString(
            ResourceString(R.string.kp_range, MIN_KP.toString(), MAX_KP.toString())
        )
        tvKpRange.isVisible = true
        rslKp.isVisible = true
        rslKp.setLabelFormatter { value: Float ->
            NumberFormat.getInstance().format(value)
        }
        rslKp.addOnChangeListener { slider, _, _ ->
            val from = slider.values[0]
            val to = slider.values[1]
            tvKpRange.text = getString(
                ResourceString(
                    R.string.kp_range,
                    from.toInt().toString(),
                    to.toInt().toString()
                )
            )
            viewModel.updateKp(from, to)
        }
    }

    private fun setTypes(typesString: List<IWrappedString>) {
        if (typesString.isEmpty()) {
            binding.tiedtTypes.setText("")
        } else {
            binding.tiedtTypes.setText(typesString.joinToString(",") { getString(it) })
        }
        binding.tilTypes.isEndIconVisible = typesString.isNotEmpty()
    }

    private fun setGenres(genres: List<SelectUIModel>) = with(binding) {
        if (genres.isEmpty()) {
            tiedtGenres.setText("")
        } else {
            tiedtGenres.setText(genres.joinToString(",") { it.title })
        }
        tilGenres.isEndIconVisible = genres.isNotEmpty()
    }

    private fun setCountries(countries: List<SelectUIModel>) = with(binding) {
        tiedtCountries.setText(countries.joinToString(",") { it.title })
        tilCountries.isEndIconVisible = countries.isNotEmpty()
    }

    private fun showDialog(dialog: Dialog) {
        when (dialog.type) {
            is DialogType.MultiChoose -> {
                when (dialog.request) {
                    DIALOG_REQ_TYPES -> {
                        showDialogMultiChoose(dialog, dialog.type) {
                            viewModel.onTypesChosen(it)
                        }
                    }

                    DIALOG_REQ_GENRES -> {
                        showDialogMultiChoose(dialog, dialog.type) {
                            viewModel.onGenreSelectChanged(it)
                        }
                    }

                    DIALOG_REQ_COUNTRIES -> {
                        showDialogMultiChoose(dialog, dialog.type) {
                            viewModel.onCountriesSelectChanged(it)
                        }
                    }

                    else -> {}
                }

            }
        }
    }

    private fun showDialogMultiChoose(
        dialog: Dialog,
        type: DialogType.MultiChoose,
        onSelect: (indices: IntArray) -> Unit
    ) {
        multiChoiceDialog(
            title = dialog.title.toString(requireContext()).orEmpty(),
            btnOk = dialog.btnOk?.toString(requireContext()).orEmpty(),
            btnCancel = dialog.btnCancel?.toString(requireContext()).orEmpty(),
            initItems = type.items.map {
                it.toString(requireContext()).orEmpty()
            },
            onSelect = { indices: IntArray, dlg ->
                onSelect(indices)
                dlg.dismiss()
            },
            selected = type.selected.toIntArray()
        )
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
        tiedtSearch.doAfterTextChanged {
            viewModel.onSearchChange(it.toString())
        }
        btnSearchExtend.setOnClickListener {
            viewModel.onResultClick()
        }
        tiedtTypes.setOnClickListener {
            viewModel.onTypesClicked()
        }
        tiedtGenres.setOnClickListener {
            viewModel.onGenresClicked()
        }
        tiedtCountries.setOnClickListener {
            viewModel.onCountriesClicked()
        }
        tilTypes.setEndIconOnClickListener {
            viewModel.onRemoveTypesClicked()
        }
        tilGenres.setEndIconOnClickListener {
            viewModel.onRemoveGenresClicked()
        }
        tilCountries.setEndIconOnClickListener {
            viewModel.onRemoveCountriesClicked()
        }
    }
}