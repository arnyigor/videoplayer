package com.arny.mobilecinema.presentation.tv.details

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.AppConstants
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.utils.getWithDomain
import com.arny.mobilecinema.domain.models.*
import com.arny.mobilecinema.presentation.services.UpdateService
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsAction
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import com.arny.mobilecinema.presentation.utils.registerLocalReceiver
import com.arny.mobilecinema.presentation.utils.sendServiceMessage
import com.arny.mobilecinema.presentation.utils.unregisterLocalReceiver
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

data class EpisodeItem(
    val episode: SerialEpisode,
    val seasonIndex: Int,
    val episodeIndex: Int
)

data class SourceItem(
    val url: String,
    val label: String,
    val quality: String,
    val index: Int
)

class TvDetailsFragment : DetailsSupportFragment(), KoinComponent {

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
        private const val ACTION_UPDATE = 3L
        private const val POSTER_WIDTH = 274
        private const val POSTER_HEIGHT = 400
    }

    private val viewModel: TvDetailsViewModel by viewModel()
    private val prefs: Prefs by inject()
    private val args: TvDetailsFragmentArgs by navArgs()

    private lateinit var detailsAdapter: ArrayObjectAdapter
    private var detailsRow: DetailsOverviewRow? = null
    private var currentMovie: Movie? = null
    private var sources: List<SourceItem> = emptyList()

    private val updateReceiver by lazy { makeBroadcastReceiver() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAdapter()
        setupClickListeners()
        viewModel.loadMovie(args.movieId)
        observeData()
        observeActions()
    }

    override fun onResume() {
        super.onResume()
        registerLocalReceiver(AppConstants.ACTION_UPDATE_STATUS, updateReceiver)
    }

    override fun onPause() {
        super.onPause()
        unregisterLocalReceiver(updateReceiver)
    }

    private fun buildAdapter() {
        val selector = ClassPresenterSelector()

        val overviewPresenter = FullWidthDetailsOverviewRowPresenter(
            TvDetailsDescriptionPresenter()
        ).apply {
            backgroundColor = ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)
            onActionClickedListener = OnActionClickedListener { action ->
                onActionClicked(action)
            }
        }

        selector.addClassPresenter(DetailsOverviewRow::class.java, overviewPresenter)
        selector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        detailsAdapter = ArrayObjectAdapter(selector)
        adapter = detailsAdapter
    }

    private fun setupClickListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is EpisodeItem -> {
                    navigateToPlayer(
                        seasonIndex = item.seasonIndex,
                        episodeIndex = item.episodeIndex
                    )
                }
                is SourceItem -> {
                    navigateToPlayerWithUrl(item.url)
                }
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.movie.collectLatest { movie ->
                movie?.let {
                    currentMovie = it
                    bindMovie(it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFavorite.collectLatest { isFav ->
                updateFavoriteLabel(isFav)
            }
        }
    }

    private fun observeActions() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.actions.collect { action ->
                handleAction(action)
            }
        }
    }

    private fun handleAction(action: TvDetailsAction) {
        when (action) {
            is TvDetailsAction.ShowToast -> {
                Toast.makeText(
                    requireContext(),
                    action.message.toString(requireContext()),
                    Toast.LENGTH_SHORT
                ).show()
            }
            is TvDetailsAction.ShowError -> {
                Toast.makeText(
                    requireContext(),
                    action.message.toString(requireContext()),
                    Toast.LENGTH_LONG
                ).show()
            }
            is TvDetailsAction.NavigateToUpdate -> {
                requireContext().sendServiceMessage(
                    Intent(requireContext().applicationContext, UpdateService::class.java),
                    AppConstants.ACTION_UPDATE_BY_URL
                ) {
                    putString(AppConstants.SERVICE_PARAM_UPDATE_URL, action.url)
                }
            }
        }
    }

    private fun bindMovie(movie: Movie) {
        val row = DetailsOverviewRow(movie)
        detailsRow = row

        row.imageDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_movie)
        loadPoster(movie, row)

        val actions = SparseArrayObjectAdapter().apply {
            set(ACTION_PLAY.toInt(), Action(ACTION_PLAY, getString(R.string.play)))
            set(
                ACTION_FAVORITE.toInt(),
                Action(
                    ACTION_FAVORITE,
                    if (viewModel.isFavorite.value) getString(R.string.remove_from_favorites)
                    else getString(R.string.add_to_favorites)
                )
            )
            set(ACTION_UPDATE.toInt(), Action(ACTION_UPDATE, getString(R.string.update_data)))
        }
        row.actionsAdapter = actions

        detailsAdapter.clear()
        detailsAdapter.add(row)

        when (movie.type) {
            MovieType.SERIAL -> {
                if (movie.seasons.isNotEmpty()) {
                    addSeasonRows(movie)
                }
            }
            MovieType.CINEMA -> {
                sources = prepareSourcesList(movie)
                if (sources.size > 1) {
                    addSourcesRow(sources)
                }
            }
            else -> {}
        }
    }

    private fun loadPoster(movie: Movie, row: DetailsOverviewRow) {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        val fullUrl = movie.img.getWithDomain(baseUrl)

        if (fullUrl.isBlank()) return

        Glide.with(requireContext())
            .asBitmap()
            .load(fullUrl)
            .override(POSTER_WIDTH, POSTER_HEIGHT)
            .centerCrop()
            .error(R.drawable.placeholder_movie)
            .into(object : CustomTarget<Bitmap>(POSTER_WIDTH, POSTER_HEIGHT) {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    row.imageDrawable = BitmapDrawable(resources, resource)
                    val idx = detailsAdapter.indexOf(row)
                    if (idx >= 0) {
                        detailsAdapter.notifyArrayItemRangeChanged(idx, 1)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    row.imageDrawable = errorDrawable
                        ?: ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_movie)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    row.imageDrawable = placeholder
                }
            })
    }

    private fun prepareSourcesList(movie: Movie): List<SourceItem> {
        val cinemaUrlData = movie.cinemaUrlData ?: return emptyList()

        val hdUrls = cinemaUrlData.hdUrl?.urls.orEmpty()
        val cinemaUrls = cinemaUrlData.cinemaUrl?.urls.orEmpty()
        val allUrls = (hdUrls + cinemaUrls).filter { it.isNotBlank() }.distinct()

        return allUrls.mapIndexed { index, url ->
            val isHD = index < hdUrls.size

            val afterProtocol = url.substringAfter("://")
            val host = afterProtocol.substringBefore("/")
            val extension = afterProtocol.substringAfterLast(".").take(4)

            val label = buildString {
                if (isHD) {
                    append("🎬 HD • ")
                } else {
                    append("📺 SD • ")
                }

                val shortHost = if (host.length > 20) {
                    "${host.take(17)}..."
                } else {
                    host
                }
                append(shortHost)

                if (extension.isNotBlank()) {
                    append(" • ")
                    append(extension.uppercase())
                }
            }

            SourceItem(
                url = url,
                label = label,
                quality = if (isHD) "HD" else "",
                index = index
            )
        }
    }

    private fun addSeasonRows(movie: Movie) {
        movie.seasons.sortedBy { it.id }.forEachIndexed { seasonIdx, season ->
            val header = HeaderItem(
                (season.id ?: seasonIdx).toLong(),
                "${getString(R.string.spinner_season)} ${season.id ?: (seasonIdx + 1)}"
            )
            val rowAdapter = ArrayObjectAdapter(TvEpisodeCardPresenter())

            season.episodes.sortedBy { it.episode }.forEachIndexed { episodeIdx, ep ->
                rowAdapter.add(EpisodeItem(ep, seasonIdx, episodeIdx))
            }

            detailsAdapter.add(ListRow(header, rowAdapter))
        }
    }

    private fun addSourcesRow(sources: List<SourceItem>) {
        val header = HeaderItem(1000, getString(R.string.available_sources))
        val rowAdapter = ArrayObjectAdapter(TvSourceCardPresenter())

        sources.forEach { source ->
            rowAdapter.add(source)
        }

        detailsAdapter.add(ListRow(header, rowAdapter))
    }

    private fun onActionClicked(action: Action) {
        when (action.id) {
            ACTION_PLAY -> navigateToPlayer()
            ACTION_FAVORITE -> viewModel.toggleFavorite(args.movieId)
            ACTION_UPDATE -> viewModel.updateMovieData(args.movieId)
        }
    }

    private fun updateFavoriteLabel(isFav: Boolean) {
        val row = detailsRow ?: return
        val actions = row.actionsAdapter as? SparseArrayObjectAdapter ?: return
        actions.set(
            ACTION_FAVORITE.toInt(),
            Action(
                ACTION_FAVORITE,
                if (isFav) getString(R.string.remove_from_favorites)
                else getString(R.string.add_to_favorites)
            )
        )
    }

    private fun navigateToPlayer(seasonIndex: Int = 0, episodeIndex: Int = 0) {
        findNavController().navigate(
            TvDetailsFragmentDirections.actionToPlayer(
            args.movieId,
            )
        )
    }

    private fun navigateToPlayerWithUrl(url: String) {
        findNavController().navigate(
            TvDetailsFragmentDirections.actionToPlayer(
             0L,
            )
        )
    }

    private fun makeBroadcastReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra(AppConstants.ACTION_UPDATE_STATUS)

            when (action) {
                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_SUCCESS -> {
                    Toast.makeText(
                        requireContext(),
                        R.string.update_finished_success,
                        Toast.LENGTH_LONG
                    ).show()
                    // Перезагружаем данные фильма
                    viewModel.loadMovie(args.movieId)
                }

                AppConstants.ACTION_UPDATE_STATUS_COMPLETE_ERROR -> {
                    val errorMsg = intent.getStringExtra("error_message") ?: "Неизвестная ошибка"
                    Toast.makeText(
                        requireContext(),
                        "Ошибка обновления: $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}