package com.arny.mobilecinema.presentation.details

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.mobilecinema.R
import com.arny.mobilecinema.databinding.FDetailsBinding
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.domain.models.SerialSeason
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.getWithDomain
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.updateSpinnerItems
import com.bumptech.glide.Glide
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class DetailsFragment : Fragment(R.layout.f_details) {
    private companion object {
        const val KEY_SEASON = "KEY_SEASON"
        const val KEY_EPISODE = "KEY_EPISODE"
    }

    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: DetailsViewModel by viewModels { vmFactory }
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentMovie: Movie? = null
    private var currentSeasonPosition: Int = 0
    private var currentEpisodePosition: Int = 0
    private val args: DetailsFragmentArgs by navArgs()

    private val seasonsChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
            currentEpisodePosition = 0
            fillSpinners(currentMovie)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
    private val episodesChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
    private lateinit var binding: FDetailsBinding

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        initListeners()
        initTrackAdapters()
        observeData()
        viewModel.loadVideo(args.id)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as AppCompatActivity).supportActionBar?.hide()
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as AppCompatActivity).supportActionBar?.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SEASON, currentSeasonPosition)
        outState.putInt(KEY_EPISODE, currentEpisodePosition)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        currentSeasonPosition = savedInstanceState?.getInt(KEY_SEASON) ?: 0
        currentEpisodePosition = savedInstanceState?.getInt(KEY_EPISODE) ?: 0
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.menu_action_clear_cache -> {
                        alertDialog(
                            getString(R.string.question_remove),
                            getString(R.string.question_remove_cache_title, currentMovie?.title),
                            getString(android.R.string.ok),
                            getString(android.R.string.cancel),
                            onConfirm = {
//                                viewModel.clearCache(currentMovie)
                            }
                        )
                        true
                    }
                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun initListeners() {
        binding.btnPlay.setOnClickListener {
            currentMovie?.let { movie ->
                findNavController().navigate(
                    DetailsFragmentDirections.actionNavDetailsToNavPlayerView(
                        null,
                        movie
                    )
                )
            }
        }
    }

    private fun observeData() {
        launchWhenCreated {
            viewModel.movie.collectLatest { movie ->
                onMovieLoaded(movie)
            }
        }
        launchWhenCreated {
            viewModel.loading.collectLatest { loading ->
                binding.progressBar.isVisible = loading
            }
        }
    }

    private fun onMovieLoaded(movie: Movie) {
        currentMovie = movie
        updateSpinData(movie)
        initUI(movie)
    }

    private fun initUI(movie: Movie) = with(binding) {
        Glide.with(requireContext())
            .load(movie.img.getWithDomain())
            .into(ivBanner)
        tvTitle.text = movie.title
        val info = movie.info
        tvDescription.text = info.description
        tvRating.text = StringBuilder().apply {
            if (info.ratingImdb > 0) {
                append("%s:%.02f".format("IMDB", info.ratingImdb))
                append(",")
            }
            if (info.ratingKP > 0) {
                append("%s:%.02f".format("KP", info.ratingImdb))
                append(" ")
            }
            append(String.format("%d\uD83D\uDC4D %d\uD83D\uDC4E", info.likes, info.dislikes))
        }.toString()
        val seasons = movie.seasons
        val episodes = seasons.sumOf { it.episodes.size }
        tvTypeYear.text = StringBuilder().apply {
            append(info.year)
            if (movie.type == MovieType.SERIAL) {
                append(" ")
                append("(")
                append(getString(R.string.serial))
                append("%d %s".format(seasons.size, getString(R.string.seasons_count)))
                append(" ")
                append("%d %s".format(episodes, getString(R.string.episodes_count)))
                append(")")
            } else {
                append(" ")
                append(getString(R.string.cinema))
            }
        }.toString()
        tvQuality.text = info.quality
    }

    private fun initTrackAdapters() {
        with(binding) {
            seasonsTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
            episodesTracksAdapter = TrackSelectorSpinnerAdapter(requireContext())
            spinSeasons.adapter = seasonsTracksAdapter
            spinEpisodes.adapter = episodesTracksAdapter
            spinSeasons.setSelection(currentSeasonPosition, false)
            spinEpisodes.setSelection(currentEpisodePosition, false)
            spinSeasons.updateSpinnerItems(seasonsChangeListener)
            spinEpisodes.updateSpinnerItems(episodesChangeListener)
        }
    }

    private fun updateCurrentSerialPosition() {
        currentSeasonPosition = binding.spinSeasons.selectedItemPosition
        currentEpisodePosition = binding.spinEpisodes.selectedItemPosition
    }

    private fun updateSpinData(movie: Movie) = with(binding) {
        if (movie.type == MovieType.SERIAL) {
            fillSpinners(movie)
            spinEpisodes.isVisible = true
            spinSeasons.isVisible = true
            tvSeasons.isVisible = true
        }
    }

    private fun fillSpinners(movie: Movie?) {
        val seasons = movie?.seasons.orEmpty()
        val seasonsList = seasons.map { getSeasonTitle(it) }
        if (seasonsList.isNotEmpty()) {
            with(binding) {
                spinSeasons.updateSpinnerItems(seasonsChangeListener) {
                    seasonsTracksAdapter?.clear()
                    seasonsTracksAdapter?.addAll(seasonsList)
                    spinSeasons.setSelection(currentSeasonPosition, false)
                }
                spinEpisodes.updateSpinnerItems(episodesChangeListener) {
                    val episodes = seasons.getOrNull(currentSeasonPosition)?.episodes.orEmpty()
                    val seriesList = episodes.map { getEpisodeTitle(it) }
                    episodesTracksAdapter?.clear()
                    episodesTracksAdapter?.addAll(seriesList)
                    spinEpisodes.setSelection(currentEpisodePosition, false)
                }
            }
        }
    }

    private fun getSeasonTitle(it: SerialSeason) =
        "%d %s".format(it.id, getString(R.string.spinner_season))

    private fun getEpisodeTitle(it: SerialEpisode) =
        "%s %s".format(it.episode, getString(R.string.spinner_episode))

}
