package com.arny.mobilecinema.presentation.sitesearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.DSearchFiltersBinding
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchSort
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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
        current = SearchFilters.decode(args.getString(ARG_FILTERS))
        genres = args.getStringArray(ARG_GENRES)?.toList().orEmpty()

        binding.kindGroup.check(
            when (current.kinds.firstOrNull()) {
                MovieType.CINEMA -> R.id.btnKindCinema
                MovieType.SERIAL -> R.id.btnKindSerial
                else -> R.id.btnKindAll
            }
        )
        binding.kindGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            current = current.copy(
                kinds = when (checkedId) {
                    R.id.btnKindCinema -> listOf(MovieType.CINEMA)
                    R.id.btnKindSerial -> listOf(MovieType.SERIAL)
                    else -> emptyList()
                }
            )
        }

        val genreOptions = listOf(getString(R.string.search_filters_genre_any)) + genres
        binding.actGenre.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, genreOptions)
        )
        binding.actGenre.setText(current.genre ?: genreOptions.first(), false)
        binding.actGenre.setOnItemClickListener { _, _, position, _ ->
            current = current.copy(
                genre = genreOptions.getOrNull(position)
                    ?.takeIf { position > 0 }
            )
        }

        val sortTitles = SearchSort.entries.map { getString(it.titleRes) }
        binding.actSort.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sortTitles)
        )
        binding.actSort.setText(sortTitles[current.sort.ordinal], false)
        binding.actSort.setOnItemClickListener { _, _, position, _ ->
            current = current.copy(sort = SearchSort.entries[position])
        }

        binding.tiedtYearFrom.setText(current.yearFrom?.toString().orEmpty())
        binding.tiedtYearTo.setText(current.yearTo?.toString().orEmpty())

        binding.btnFiltersReset.setOnClickListener {
            current = current.reset()
            binding.kindGroup.check(R.id.btnKindAll)
            binding.actGenre.setText(genreOptions.first(), false)
            binding.tiedtYearFrom.setText("")
            binding.tiedtYearTo.setText("")
            binding.actSort.setText(sortTitles[SearchSort.RELEVANCE.ordinal], false)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
