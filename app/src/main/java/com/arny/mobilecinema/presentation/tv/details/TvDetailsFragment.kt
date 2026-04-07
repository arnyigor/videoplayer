// presentation/tv/details/TvDetailsFragment.kt
package com.arny.mobilecinema.presentation.tv.details

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.SparseArrayObjectAdapter
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.presentation.tv.viewmodel.TvDetailsViewModel
import com.arny.mobilecinema.data.utils.getWithDomain // ← ИСПРАВЛЕН ИМПОРТ
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
    val seasonIndex: Int
)

class TvDetailsFragment : DetailsSupportFragment(), KoinComponent {

    companion object {
        private const val ACTION_PLAY = 1L
        private const val ACTION_FAVORITE = 2L
        private const val POSTER_WIDTH = 274
        private const val POSTER_HEIGHT = 400
    }

    private val viewModel: TvDetailsViewModel by viewModel()
    private val prefs: Prefs by inject()
    private val args: TvDetailsFragmentArgs by navArgs()

    private lateinit var detailsAdapter: ArrayObjectAdapter
    private var detailsRow: DetailsOverviewRow? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildAdapter()
        viewModel.loadMovie(args.movieId)
        observeData()
    }

    private fun buildAdapter() {
        val selector = ClassPresenterSelector()

        val overviewPresenter = FullWidthDetailsOverviewRowPresenter(
            TvDetailsDescriptionPresenter()
        ).apply {
            backgroundColor =
                ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark)

            onActionClickedListener = OnActionClickedListener { action ->
                onActionClicked(action)
            }
        }
        selector.addClassPresenter(DetailsOverviewRow::class.java, overviewPresenter)
        selector.addClassPresenter(ListRow::class.java, ListRowPresenter())

        detailsAdapter = ArrayObjectAdapter(selector)
        adapter = detailsAdapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.movie.collectLatest { movie ->
                movie?.let { bindMovie(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isFavorite.collectLatest { isFav ->
                updateFavoriteLabel(isFav)
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
        }
        row.actionsAdapter = actions

        detailsAdapter.clear()
        detailsAdapter.add(row)

        if (movie.type == MovieType.SERIAL && movie.seasons.isNotEmpty()) {
            addSeasonRows(movie)
        }
    }

    private fun loadPoster(movie: Movie, row: DetailsOverviewRow) {
        val baseUrl = prefs.get<String>(PrefsConstants.BASE_URL).orEmpty()
        val fullUrl = movie.img.getWithDomain(baseUrl)
        Timber.d("loadPoster: url=%s", fullUrl)

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
                    Timber.d("loadPoster: bitmap ready %dx%d", resource.width, resource.height)
                    row.imageDrawable = BitmapDrawable(resources, resource)
                    val idx = detailsAdapter.indexOf(row)
                    if (idx >= 0) {
                        detailsAdapter.notifyArrayItemRangeChanged(idx, 1)
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    Timber.w("loadPoster: failed, setting placeholder")
                    row.imageDrawable = errorDrawable
                        ?: ContextCompat.getDrawable(requireContext(), R.drawable.placeholder_movie)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    row.imageDrawable = placeholder
                }
            })
    }

    private fun addSeasonRows(movie: Movie) {
        movie.seasons.sortedBy { it.id }.forEachIndexed { seasonIdx, season ->
            val header = HeaderItem(
                (season.id ?: seasonIdx).toLong(),
                "${getString(R.string.spinner_season)} ${season.id ?: (seasonIdx + 1)}"
            )
            val rowAdapter = ArrayObjectAdapter(TvEpisodeCardPresenter())
            season.episodes.sortedBy { it.episode }.forEach { ep ->
                rowAdapter.add(EpisodeItem(ep, seasonIdx))
            }
            detailsAdapter.add(ListRow(header, rowAdapter))
        }
    }

    private fun onActionClicked(action: Action) {
        when (action.id) {
            ACTION_PLAY -> navigateToPlayer()
            ACTION_FAVORITE -> viewModel.toggleFavorite(args.movieId)
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

    private fun navigateToPlayer() {
        findNavController().navigate(
            TvDetailsFragmentDirections.actionToPlayer(args.movieId)
        )
    }
}