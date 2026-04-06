package com.arny.mobilecinema.presentation.tv.search

import android.app.LoaderManager
import android.content.Context
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.*
import androidx.navigation.fragment.findNavController
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.presentation.tv.presenters.MovieCardPresenter
import java.util.concurrent.TimeUnit

/**
 * TV-экран поиска фильмов и сериалов.
 *
 * Использует [SearchSupportFragment] из библиотеки Leanback для
 * реализации поиска с автодополнением и отображением результатов.
 *
 * Поддерживает:
 * - Поиск по названию
 * - Отображение результатов в виде сетки карточек
 * - D-pad навигацию (стрелки, OK для выбора)
 *
 * @see MovieCardPresenter - презентер для карточек фильмов
 * @see SearchResultsAdapter - адаптер для результатов поиска
 */
class TvSearchFragment : SearchSupportFragment(),
    SearchSupportFragment.SearchResultProvider,
    LoaderManager.LoaderCallbacks<Cursor> {

    private lateinit var searchAdapter: ArrayObjectAdapter
    private var savedQuery: String? = null

    override fun onAttach(context: Context) {
        // Koin injection
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Создаём адаптер для результатов поиска
        searchAdapter = ArrayObjectAdapter(MovieCardPresenter())

        // Устанавливаем этот фрагмент как провайдер результатов
        setSearchResultProvider(this)

        // Обработчик клика на результат
        setOnItemViewClickedListener { _, item, _, _ ->
            if (item is ViewMovie) {
                // Переходим к деталям фильма
                findNavController().navigate(
                    TvSearchFragmentDirections.actionToDetails(item.dbId)
                )
            }
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Text listening is handled by SearchSupportFragment internally
    }

    /**
     * Возвращает адаптер для отображения результатов поиска.
     */
    override fun getResultsAdapter(): ArrayObjectAdapter = searchAdapter

    /**
     * Обрабатывает результаты поиска.
     * Вызывается при получении результатов от запроса.
     *
     * @param result результат поиска (Cursor с фильмами)
     * @return true если результаты обработаны
     */
    override fun onQueryTextChange(newQuery: String?): Boolean {
        savedQuery = newQuery
        if (!newQuery.isNullOrBlank()) {
            startQuery(newQuery)
        } else {
            searchAdapter.clear()
        }
        return true
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        savedQuery = query
        if (!query.isNullOrBlank()) {
            startQuery(query)
        }
        return true
    }

    /**
     * Запускает запрос поиска.
     * В реальной реализации здесь должен быть вызов репозитория.
     *
     * @param query строка поиска
     */
    private fun startQuery(query: String) {
        if (query.length < 2) {
            searchAdapter.clear()
            return
        }

        // Заглушка - очищаем и показываем пустой результат
        // В реальном приложении здесь будет Paging запрос к БД
        searchAdapter.clear()

        // TODO: Реализовать реальный поиск через MoviesInteractor
        // moviesInteractor.search(query).collectLatest { pagingData ->
        //     val snapshot = pagingData.snapshot()
        //     searchAdapter.addAll(0, snapshot.items)
        // }
    }

    // --- LoaderCallbacks implementation ---

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return CursorLoader(requireContext())
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        // Обработка результатов
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        searchAdapter.clear()
    }

    companion object {
        fun newInstance() = TvSearchFragment()
    }
}
