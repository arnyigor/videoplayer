package com.arny.mobilecinema.presentation.tv.search

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.search.SearchFilters
import com.arny.mobilecinema.domain.search.SearchHit
import com.arny.mobilecinema.domain.search.SearchSort
import com.arny.mobilecinema.domain.search.SearchSource
import com.arny.mobilecinema.presentation.sitesearch.ResultSection
import com.arny.mobilecinema.presentation.sitesearch.SearchUiState
import com.arny.mobilecinema.presentation.sitesearch.SiteSearchViewModel
import com.arny.mobilecinema.presentation.tv.presenters.SiteSearchCardPresenter
import com.arny.mobilecinema.presentation.utils.dpToPx
import com.arny.mobilecinema.presentation.utils.navigateSafely
import com.arny.mobilecinema.presentation.utils.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

private data class SearchAction(val key: String, val label: String, val selected: Boolean = false)

private class SearchActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder = ViewHolder(TextView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(-2, -2)
        // Отступы фокуса и внутренние поля: без них соседние чипы сливались в одну полосу.
        setPadding(24.dpToPx(context), 8.dpToPx(context), 24.dpToPx(context), 8.dpToPx(context))
        textSize = 16f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        maxWidth = 520.dpToPx(context)
        setBackgroundResource(R.drawable.tv_chip_background)
        setTextColor(ContextCompat.getColorStateList(context, R.color.tv_navigation_text))
    })
    override fun onBindViewHolder(holder: ViewHolder, item: Any?) {
        val action = item as SearchAction
        (holder.view as TextView).apply {
            text = action.label
            contentDescription = action.label
            isSelected = action.selected
            isFocusable = action.key != "status"
        }
    }
    override fun onUnbindViewHolder(holder: ViewHolder) = Unit
}

/** One TV search over the same local/site state and filters as the phone. */
class TvSiteSearchFragment : SearchSupportFragment(), SearchSupportFragment.SearchResultProvider {
    private val model: SiteSearchViewModel by viewModel()
    private val voiceSearch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            setSearchQuery(it, true)
        }
    }
    private val rows = ArrayObjectAdapter(ListRowPresenter())
    private val sources = ArrayObjectAdapter(SearchActionPresenter())
    private val filters = ArrayObjectAdapter(SearchActionPresenter())
    private val status = ArrayObjectAdapter(SearchActionPresenter())
    private val results = ArrayObjectAdapter(SiteSearchCardPresenter())
    private val more = ArrayObjectAdapter(SearchActionPresenter())
    private var searchJob: Job? = null
    private var focusedKey: String? = null
    private var restoreResults = false
    private var lastItems = emptyList<SearchHit>()
    private val actionDiff = object : DiffCallback<SearchAction>() {
        override fun areItemsTheSame(a: SearchAction, b: SearchAction) = a.key == b.key
        override fun areContentsTheSame(a: SearchAction, b: SearchAction) = a == b
    }
    private val hitDiff = object : DiffCallback<SearchHit>() {
        override fun areItemsTheSame(a: SearchHit, b: SearchHit) = hitKey(a) == hitKey(b)
        override fun areContentsTheSame(a: SearchHit, b: SearchHit) = a == b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.site_search_hint)
        focusedKey = savedInstanceState?.getString("focused_hit")
        rows.add(ListRow(HeaderItem(0, getString(R.string.tv_search_source)), sources))
        rows.add(ListRow(HeaderItem(1, getString(R.string.search_filters_title)), filters))
        rows.add(ListRow(HeaderItem(2, getString(R.string.tv_search_status)), status))
        rows.add(ListRow(HeaderItem(3, getString(R.string.search_results)), results))
        rows.add(ListRow(HeaderItem(4, ""), more))
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is SearchHit -> { focusedKey = hitKey(item); model.openHit(item) }
                is SearchAction -> applyAction(item.key)
            }
        }
        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item is SearchHit) {
                focusedKey = hitKey(item)
                val state = model.uiState.value
                if (!state.remoteError && lastItems.indexOf(item) >= lastItems.size - 3) model.loadMore()
            }
        }
        setSearchResultProvider(this)
        setSpeechRecognitionCallback {
            try {
                voiceSearch.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.site_search_hint))
                })
            } catch (_: ActivityNotFoundException) {
                voiceUnavailable()
            } catch (_: SecurityException) {
                voiceUnavailable()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.initialize(arguments?.getString("query").orEmpty(),
            arguments?.getString("searchType") ?: AppConstants.SearchType.TITLE)
        restoreResults = focusedKey != null
        setSearchQuery(model.uiState.value.query, false)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.uiState.collectLatest(::render) }
                launch { model.toast.collectLatest { toast(it.toString(requireContext())) } }
                launch { model.openMovie.collectLatest { id ->
                    findNavController().navigateSafely(TvSiteSearchFragmentDirections.actionToDetails(id))
                } }
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rows
    override fun onQueryTextChange(newQuery: String): Boolean {
        if (newQuery == model.uiState.value.query) return true
        model.setQuery(newQuery)
        searchJob?.cancel()
        if (newQuery.isNotBlank()) searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(500)
            model.search()
        }
        return true
    }
    override fun onQueryTextSubmit(query: String): Boolean {
        searchJob?.cancel()
        model.setQuery(query)
        model.search()
        return true
    }

    private fun voiceUnavailable() {
        toast(getString(R.string.tv_voice_unavailable))
        view?.findViewById<View>(androidx.leanback.R.id.lb_search_text_editor)?.requestFocus()
    }

    private fun applyAction(key: String) {
        val f = model.uiState.value.filters
        when (key) {
            "ALL", "LOCAL", "REMOTE" -> model.setSource(SearchSource.valueOf(key))
            "kinds" -> choose(getString(R.string.search_filters_kind), listOf(
                getString(R.string.search_filters_kind_all), getString(R.string.home_content_cinema), getString(R.string.home_content_serial)
            )) { model.setFilters(f.copy(kinds = when (it) { 1 -> listOf(MovieType.CINEMA); 2 -> listOf(MovieType.SERIAL); else -> emptyList() })) }
            "sort" -> choose(getString(R.string.search_filters_sort), SearchSort.entries.map { getString(it.titleRes) }) {
                model.setFilters(f.copy(sort = SearchSort.entries[it]))
            }
            "genre" -> chooseGenres(f)
            "country" -> chooseNullable(getString(R.string.tv_search_country), model.uiState.value.countries, f.country) { model.setFilters(f.copy(country = it)) }
            "years" -> showYears()
            "reset" -> model.setFilters(f.reset())
            "retry" -> model.retry()
            "more" -> model.loadMore()
        }
    }

    private fun choose(title: String, labels: List<String>, selected: Int = -1, apply: (Int) -> Unit) {
        AlertDialog.Builder(requireContext()).setTitle(title).setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
            apply(which); dialog.dismiss()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    /**
     * Мульти-выбор жанров: несколько значений объединяются по ИЛИ (как в мобильной шторке).
     * Диалог держит выбор в одном списке, чтобы D-pad и Back не теряли состояние.
     */
    private fun chooseGenres(f: SearchFilters) {
        val values = model.uiState.value.genres
        if (values.isEmpty()) return
        val checked = BooleanArray(values.size) { index ->
            f.genres.any { it.equals(values[index], ignoreCase = true) }
        }
        var selected = f.genres
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.search_filters_genre)
            .setMultiChoiceItems(values.toTypedArray(), checked) { _, which, isChecked ->
                val genre = values[which]
                selected = if (isChecked) {
                    selected + genre
                } else {
                    selected.filterNot { it.equals(genre, ignoreCase = true) }
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                model.setFilters(f.copy(genres = selected))
            }
            .setNeutralButton(R.string.search_filters_genre_any) { _, _ ->
                model.setFilters(f.copy(genres = emptyList()))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun genreLabel(f: SearchFilters): String = when (f.genres.size) {
        0 -> getString(R.string.search_filters_genre)
        1 -> f.genres.first()
        else -> getString(R.string.search_filters_genre_count, f.genres.first(), f.genres.size - 1)
    }

    private fun chooseNullable(title: String, values: List<String>, current: String?, apply: (String?) -> Unit) {
        choose(title, listOf(getString(R.string.search_filters_genre_any)) + values,
            if (current == null) 0 else values.indexOf(current) + 1) { apply(if (it == 0) null else values[it - 1]) }
    }
    private fun showYears() {
        val f = model.uiState.value.filters
        val from = EditText(requireContext()).apply { inputType = InputType.TYPE_CLASS_NUMBER; setHint(R.string.search_filters_year_from); setText(f.yearFrom?.toString().orEmpty()) }
        val to = EditText(requireContext()).apply { inputType = InputType.TYPE_CLASS_NUMBER; setHint(R.string.search_filters_year_to); setText(f.yearTo?.toString().orEmpty()) }
        val content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; addView(from); addView(to) }
        AlertDialog.Builder(requireContext()).setTitle(R.string.tv_search_years).setView(content)
            .setPositiveButton(android.R.string.ok) { _, _ -> model.setFilters(f.copy(
                yearFrom = from.text.toString().toIntOrNull()?.coerceIn(0, 2100),
                yearTo = to.text.toString().toIntOrNull()?.coerceIn(0, 2100),
            )) }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun render(state: SearchUiState) {
        sources.setItems(SearchSource.entries.map { source -> SearchAction(source.name,
            getString(when (source) { SearchSource.ALL -> R.string.search_source_all; SearchSource.LOCAL -> R.string.search_source_local; SearchSource.REMOTE -> R.string.search_source_remote }), state.source == source) }, actionDiff)
        val f = state.filters
        val kind = when (f.kinds.singleOrNull()) { MovieType.CINEMA -> getString(R.string.home_content_cinema); MovieType.SERIAL -> getString(R.string.home_content_serial); else -> getString(R.string.search_filters_kind_all) }
        filters.setItems(listOf(
            SearchAction("kinds", getString(R.string.search_filters_kind) + ": " + kind, f.kinds.isNotEmpty()),
            SearchAction("genre", genreLabel(f), f.genres.isNotEmpty()),
            SearchAction("country", f.country ?: getString(R.string.tv_search_country), f.country != null),
            SearchAction("years", if (f.yearRange == null) getString(R.string.tv_search_years) else "${f.yearFrom ?: "…"}–${f.yearTo ?: "…"}", f.yearRange != null),
            // Без префикса подпись «По совпадению» читалась как отдельный фильтр, а не сортировка.
            SearchAction("sort", getString(R.string.search_filters_sort) + ": " + getString(f.sort.titleRes), f.sort != SearchSort.RELEVANCE),
            SearchAction("reset", getString(R.string.search_filters_reset)),
        ), actionDiff)
        val lines = listOf(
            getString(R.string.tv_search_status_line, getString(R.string.search_source_local), sectionLabel(state.localSection)),
            getString(R.string.tv_search_status_line, getString(R.string.search_source_remote), sectionLabel(state.remoteSection)),
        ) + if (state.filtersNotAppliedToSite) listOf(getString(R.string.search_filters_site_notice)) else emptyList()
        status.setItems(listOf(SearchAction("status", lines.joinToString("\n"))) +
            if (state.localError || state.remoteError) listOf(SearchAction("retry", getString(R.string.retry))) else emptyList(), actionDiff)
        if (state.hits != lastItems) {
            lastItems = state.hits
            results.setItems(state.hits, hitDiff)
        }
        more.setItems(if (state.hasMoreFilms && !state.isLoadingRemote) listOf(SearchAction("more", getString(R.string.site_search_load_more))) else emptyList(), actionDiff)
        if (restoreResults) {
            val position = state.hits.indexOfFirst { hitKey(it) == focusedKey }
            if (position >= 0) {
                rowsSupportFragment.setSelectedPosition(3, false, ListRowPresenter.SelectItemViewHolderTask(position))
                restoreResults = false
            }
        }
    }

    private fun sectionLabel(section: ResultSection): String = getString(when (section) {
        ResultSection.IDLE -> R.string.tv_search_idle
        ResultSection.LOADING -> R.string.loading
        ResultSection.SUCCESS -> R.string.tv_search_success
        ResultSection.EMPTY -> R.string.search_all_empty
        ResultSection.ERROR -> R.string.tv_search_error
    })
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("focused_hit", focusedKey)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { searchJob?.cancel(); super.onDestroyView() }
    private fun hitKey(hit: SearchHit) = if (hit.inCatalog) "db:${hit.stableId}" else "site:${hit.siteRef}"
}
