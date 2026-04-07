package com.arny.mobilecinema.presentation.tv.details

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.interactors.movies.MoviesInteractor
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TvDetailsFragment : DetailsSupportFragment(), KoinComponent {

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
    }

    private val viewModel: TvDetailsViewModel by activityViewModel()

    private val prefs: Prefs by inject()

    /** Аргументы навигации (movieId) */
    private val args: TvDetailsFragmentArgs by navArgs()

    /** Адаптер для детального ряда */
    private lateinit var detailsAdapter: ArrayObjectAdapter

    /** Адаптер для кнопок действий */
    private lateinit var actionsAdapter: ArrayObjectAdapter

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
        viewModel.loadMovie(args.movieId)
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
            val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
            val fullUrl = movie.img.getWithDomain(baseUrl)

            Glide.with(requireContext())
                .load(fullUrl)
                .error(R.drawable.placeholder_movie)
                .into(object : CustomTarget<Drawable>() {
                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                        detailsRow.imageDrawable = resource
                        // Уведомляем адаптер об изменении — Leanback не обновляет UI автоматически
                        detailsAdapter.notifyArrayItemRangeChanged(0, detailsAdapter.size())
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {}
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
        val bundle = Bundle().apply {
            putLong("movieId", args.movieId)
        }
        findNavController().navigate(R.id.actionToPlayer, bundle)
    }

}
