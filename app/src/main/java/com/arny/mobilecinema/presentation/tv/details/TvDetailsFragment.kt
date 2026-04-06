package com.arny.mobilecinema.presentation.tv.details

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TvDetailsFragment : DetailsSupportFragment(), KoinComponent {

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
    }

    private val moviesInteractor: MoviesInteractor by inject()
    private val viewModel: TvDetailsViewModel by viewModel()

    /** Аргументы навигации (movieId) */
    private val args: TvDetailsFragmentArgs by navArgs()

    /** Адаптер для детального ряда */
    private lateinit var detailsAdapter: ArrayObjectAdapter

    /** Адаптер для кнопок действий */
    private lateinit var actionsAdapter: ArrayObjectAdapter

    override fun onAttach(context: Context) {
        // Koin injection
        super.onAttach(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDetailsAdapter()
        loadMovie()
        observeData()
    }

    /**
     * Настраивает адаптеры для отображения деталей фильма.
     * Использует [FullWidthDetailsOverviewRowPresenter] для красивого отображения.
     */
    private fun setupDetailsAdapter() {
        val presenterSelector = ClassPresenterSelector()

        // Presenter для основного ряда с деталями
        val detailsPresenter = FullWidthDetailsOverviewRowPresenter(
            TvDetailsDescriptionPresenter()
        ).apply {
            onActionClickedListener = OnActionClickedListener { action ->
                handleAction(action)
            }
        }

        presenterSelector.addClassPresenter(
            DetailsOverviewRow::class.java,
            detailsPresenter
        )

        // Presenter для списка эпизодов (для сериалов)
        presenterSelector.addClassPresenter(
            ListRow::class.java,
            ListRowPresenter()
        )

        detailsAdapter = ArrayObjectAdapter(presenterSelector)
        adapter = detailsAdapter
    }

    /** Загружает данные фильма по ID */
    private fun loadMovie() {
        viewModel.loadMovie(args.movieId.toLong())
    }

    /**
     * Наблюдает за данными из ViewModel.
     * При изменении данных обновляет UI.
     */
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.movie.collectLatest { movie ->
                movie?.let { bindMovie(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFavorite.collectLatest { isFav ->
                updateFavoriteAction(isFav)
            }
        }
    }

    /**
     * Привязывает данные фильма к UI.
     * Создаёт [DetailsOverviewRow] с информацией о фильме.
     */
    private fun bindMovie(movie: Movie) {
        val detailsRow = DetailsOverviewRow(movie)

        // Устанавливаем обложку
        if (movie.img.isNotBlank()) {
            Glide.with(requireContext())
                .load(movie.img)
                .into(object : com.bumptech.glide.request.target.SimpleTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?) {
                        detailsRow.imageDrawable = resource
                    }
                })
        }

        // Создаём кнопки действий
        actionsAdapter = ArrayObjectAdapter().apply {
            add(Action(ACTION_PLAY, getString(R.string.play)))
            val favLabel = if (viewModel.isFavorite.value) {
                getString(R.string.remove_from_favorites)
            } else {
                getString(R.string.add_to_favorites)
            }
            add(Action(ACTION_FAVORITE, favLabel))
        }

        detailsRow.actionsAdapter = actionsAdapter
        detailsAdapter.clear()
        detailsAdapter.add(detailsRow)

        // Для сериалов добавляем список эпизодов
        if (movie.type == MovieType.SERIAL && movie.seasons.isNotEmpty()) {
            addSeasonsList(movie)
        }
    }

    /**
     * Добавляет список сезонов для сериала.
     * Каждый сезон - отдельный ряд с эпизодами.
     */
    private fun addSeasonsList(movie: Movie) {
        val seasonsPresenter = ListRowPresenter()
        val episodesAdapter = ArrayObjectAdapter(seasonsPresenter)

        movie.seasons.sortedBy { it.id }.forEach { seasonItem ->
            val seasonHeader = HeaderItem(
                (seasonItem.id ?: 0).toLong(),
                "${getString(R.string.spinner_season)} ${seasonItem.id ?: 0}"
            )
            val episodes = seasonItem.episodes.sortedBy { it.episode }
            val rowAdapter = ArrayObjectAdapter(
                TvEpisodeCardPresenter()
            )
            episodes.forEach { episode ->
                rowAdapter.add(episode)
            }
            episodesAdapter.add(ListRow(seasonHeader, rowAdapter))
        }

        detailsAdapter.add(episodesAdapter)
    }

    /**
     * Обновляет текст кнопки избранного.
     * @param isFavorite текущее состояние избранного
     */
    private fun updateFavoriteAction(isFavorite: Boolean) {
        if (!::actionsAdapter.isInitialized) return

        // Находим кнопку избранного
        val actions = List(actionsAdapter.size()) { i -> actionsAdapter.get(i) }
        val favAction = actions.filterIsInstance<Action>().find { it.id == ACTION_FAVORITE } ?: return

        // Обновляем текст кнопки - пересоздаём действие
        val newLabel = if (isFavorite) {
            getString(R.string.remove_from_favorites)
        } else {
            getString(R.string.add_to_favorites)
        }
        val newAction = Action(ACTION_FAVORITE, newLabel)
        val index = actionsAdapter.indexOf(favAction)
        if (index >= 0) {
            actionsAdapter.replace(index, newAction)
        }
    }

    /**
     * Обрабатывает нажатия на кнопки действий.
     * @param action действие, на которое нажали
     */
    private fun handleAction(action: Action) {
        when (action.id) {
            ACTION_PLAY -> navigateToPlayer()
            ACTION_FAVORITE -> viewModel.toggleFavorite(args.movieId.toLong())
        }
    }

    /** Переходит на экран плеера для воспроизведения */
    private fun navigateToPlayer() {
        val movie = viewModel.movie.value ?: return

        val directions = when (movie.type) {
            MovieType.CINEMA -> {
                TvDetailsFragmentDirections.actionToPlayer()
            }
            MovieType.SERIAL -> {
                // Для сериала начинаем с первого эпизода первого сезона
                TvDetailsFragmentDirections.actionToPlayer()
            }
            else -> {
                TvDetailsFragmentDirections.actionToPlayer()
            }
        }

        findNavController().navigate(directions)
    }

}
