package com.arny.mobilecinema.presentation.sitesearch

import android.os.Bundle
import android.graphics.Rect
import androidx.core.view.doOnLayout
import androidx.core.view.forEach
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.view.WindowManager
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.DSearchFiltersBinding
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchSort
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Панель фильтров единого поиска (SRCH-03).
 *
 * Поля ограничены тем, что реально поддерживает локальный SQL: тип, жанр, годы, сортировка.
 * Панель возвращает новый [SearchFilters] через `setFragmentResult`; поиск запускает экран.
 */
class SearchFiltersSheet : BottomSheetDialogFragment() {

    companion object {
        const val REQUEST_KEY = "search_filters_request"
        const val RESULT_FILTERS = "search_filters"
        private const val ARG_FILTERS = "arg_filters"
        private const val ARG_GENRES = "arg_genres"
        private const val ARG_YEAR_MIN = "arg_year_min"
        private const val ARG_YEAR_MAX = "arg_year_max"
        /** Быстрые жанры для мульти-выбора прямо в шторке: частые запросы пользователей. */
        private val QUICK_GENRES = listOf(
            "Аниме", "Мультфильм", "Боевик", "Комедия", "Драма", "Триллер",
            "Ужасы", "Фантастика", "Фэнтези", "Приключения", "Детектив", "Мелодрама",
        )
        private const val STATE_DRAFT = "draft_filters"
        private const val STATE_YEAR_FROM = "draft_year_from"
        private const val STATE_YEAR_TO = "draft_year_to"

        fun newInstance(
            filters: SearchFilters,
            genres: List<String>,
            yearMin: Int,
            yearMax: Int,
        ): SearchFiltersSheet = SearchFiltersSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_FILTERS, filters.encode())
                putStringArray(ARG_GENRES, genres.toTypedArray())
                putInt(ARG_YEAR_MIN, yearMin)
                putInt(ARG_YEAR_MAX, yearMax)
            }
        }
    }

    private var _binding: DSearchFiltersBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var current: SearchFilters = SearchFilters()
    private var genres: List<String> = emptyList()

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DSearchFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireNotNull(arguments)
        current = SearchFilters.decode(savedInstanceState?.getString(STATE_DRAFT) ?: args.getString(ARG_FILTERS))
        genres = args.getStringArray(ARG_GENRES)?.toList().orEmpty()
        val initialKind = when (current.kinds.firstOrNull()) {
            MovieType.CINEMA -> R.id.btnKindCinema
            MovieType.SERIAL -> R.id.btnKindSerial
            else -> R.id.btnKindAll
        }
        binding.kindGroup.check(initialKind)
        revealKind(initialKind)
        binding.kindGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            revealKind(checkedId)
            current = current.copy(
                kinds = when (checkedId) {
                    R.id.btnKindCinema -> listOf(MovieType.CINEMA)
                    R.id.btnKindSerial -> listOf(MovieType.SERIAL)
                    else -> emptyList()
                }
            )
        }

        setupGenrePicker()

        val sortTitles = SearchSort.entries.map { getString(it.titleRes) }
        binding.actSort.setDropDownBackgroundResource(R.drawable.bg_dropdown_dark)
        binding.actSort.setAdapter(
            ArrayAdapter(requireContext(), R.layout.item_dropdown_dark, sortTitles)
        )
        binding.actSort.setText(sortTitles[current.sort.ordinal], false)
        binding.actSort.setOnItemClickListener { _, _, position, _ ->
            current = current.copy(sort = SearchSort.entries[position])
        }

        binding.tiedtYearFrom.setText(savedInstanceState?.getString(STATE_YEAR_FROM) ?: current.yearFrom?.toString().orEmpty())
        binding.tiedtYearTo.setText(savedInstanceState?.getString(STATE_YEAR_TO) ?: current.yearTo?.toString().orEmpty())

        binding.btnFiltersReset.setOnClickListener {
            current = current.reset()
            binding.kindGroup.check(R.id.btnKindAll)
            // Чипы — только отображение current: без перерисовки они оставались
            // отмеченными, хотя жанры уже сброшены.
            renderQuickChips()
            renderGenreSelection()
            binding.tiedtYearFrom.setText("")
            binding.tiedtYearTo.setText("")
            // Domain reset() сохраняет сортировку — подпись не должна обещать другую.
            binding.actSort.setText(sortTitles[current.sort.ordinal], false)
        }

        binding.btnFiltersApply.setOnClickListener {
            val from = binding.tiedtYearFrom.text?.toString()?.trim()?.toIntOrNull()
            val to = binding.tiedtYearTo.text?.toString()?.trim()?.toIntOrNull()
            val yearMin = args.getInt(ARG_YEAR_MIN, 0)
            val yearMax = args.getInt(ARG_YEAR_MAX, SearchFilters.MAX_YEAR)
            val filters = current.copy(
                yearFrom = from?.takeIf { it in yearMin..yearMax },
                yearTo = to?.takeIf { it in yearMin..yearMax },
            )
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putString(RESULT_FILTERS, filters.encode()) },
            )
            dismiss()
        }
    }

    /**
     * Мульти-выбор жанров: быстрые чипы популярных категорий в шторке плюс
     * полный список чекбоксами. Выбранные жанры объединяются по ИЛИ.
     */
    private fun setupGenrePicker() {
        binding.chipsQuickGenres.removeAllViews()
        QUICK_GENRES.forEach { title ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = title
                isCheckable = true
                isChecked = current.genres.any { it.equals(title, ignoreCase = true) }
                setChipBackgroundColorResource(R.color.chip_bg)
                setTextColor(requireContext().getColor(R.color.chip_text))
                setOnCheckedChangeListener { _, checked ->
                    current = if (checked) {
                        current.copy(genres = current.genres + title)
                    } else {
                        current.copy(genres = current.genres.filterNot { it.equals(title, ignoreCase = true) })
                    }
                    renderGenreSelection()
                }
            }
            binding.chipsQuickGenres.addView(chip)
        }
        binding.actGenre.inputType = android.text.InputType.TYPE_NULL
        binding.actGenre.setText(genreSummary(), false)
        binding.tilGenre.setStartIconDrawable(null)
        binding.tilGenre.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
        binding.actGenre.setOnClickListener { showAllGenresDialog() }
        binding.tilGenre.setEndIconOnClickListener { showAllGenresDialog() }
    }

    private fun showAllGenresDialog() {
        val selection = BooleanArray(genres.size) { index ->
            current.genres.any { selected -> selected.equals(genres[index], ignoreCase = true) }
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.PlayerAlertDialogTheme)
            .setTitle(R.string.search_filters_genre)
            .setMultiChoiceItems(genres.toTypedArray(), selection) { _, which, checked ->
                val genre = genres[which]
                current = if (checked) {
                    current.copy(genres = current.genres + genre)
                } else {
                    current.copy(genres = current.genres.filterNot { it.equals(genre, ignoreCase = true) })
                }
                renderQuickChips()
                renderGenreSelection()
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.search_filters_genre_any) { _, _ ->
                current = current.copy(genres = emptyList())
                renderQuickChips()
                renderGenreSelection()
            }
            .show()
    }

    private fun genreSummary(): String = when (current.genres.size) {
        0 -> getString(R.string.search_filters_genre_any)
        1 -> current.genres.first()
        else -> getString(
            R.string.search_filters_genre_count,
            current.genres.first(),
            current.genres.size - 1,
        )
    }

    private fun renderGenreSelection() {
        binding.actGenre.setText(genreSummary(), false)
    }

    private fun renderQuickChips() {
        binding.chipsQuickGenres.forEach { view ->
            (view as? com.google.android.material.chip.Chip)?.let { chip ->
                chip.isChecked = current.genres.any { it.equals(chip.text.toString(), ignoreCase = true) }
            }
        }
    }

    private fun revealKind(checkedId: Int) {
        binding.kindGroup.findViewById<View>(checkedId).doOnLayout { selected ->
            selected.requestRectangleOnScreen(Rect(0, 0, selected.width, selected.height), false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, current.encode())
        _binding?.let {
            outState.putString(STATE_YEAR_FROM, it.tiedtYearFrom.text?.toString().orEmpty())
            outState.putString(STATE_YEAR_TO, it.tiedtYearTo.text?.toString().orEmpty())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
