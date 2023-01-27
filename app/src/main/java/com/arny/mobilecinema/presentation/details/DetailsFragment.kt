package com.arny.mobilecinema.presentation.details

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.domain.models.SerialEpisode
import com.arny.mobilecinema.di.models.Video
import com.arny.mobilecinema.presentation.utils.alertDialog
import com.arny.mobilecinema.presentation.utils.launchWhenCreated
import com.arny.mobilecinema.presentation.utils.updateSpinnerItems
import com.arny.mobilecinema.presentation.utils.updateTitle
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class DetailsFragment : Fragment() {
    private companion object {
        const val KEY_MOVIE = "KEY_MOVIE"
        const val KEY_VIDEO = "KEY_VIDEO"
        const val KEY_SEASON = "KEY_SEASON"
        const val KEY_EPISODE = "KEY_EPISODE"
    }
    @Inject
    lateinit var vmFactory: ViewModelProvider.Factory
    private val viewModel: DetailsViewModel by viewModels { vmFactory }
    private var seasonsTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var episodesTracksAdapter: TrackSelectorSpinnerAdapter? = null
    private var currentMovie: Movie? = null
    private var currentVideo: Video? = null
    private var currentSeasonPosition: Int = 0
    private var currentEpisodePosition: Int = 0
    private val args: DetailsFragmentArgs by navArgs()

    /*
    private var trackSelector: DefaultTrackSelector? = null
    private var videoStartRestore = false
    private var videoRestored = false
    private var exoPlayer: ExoPlayer? = null
    private var orientationLocked: Boolean = false
    private var playControlsVisible by Delegates.observable(true) { _, oldValue, visible ->
        if (oldValue != visible && isVisible) {
            val land = resources.configuration.orientation == ORIENTATION_LANDSCAPE
            if (land) {
                setFullScreen(activity as AppCompatActivity?, !visible)
            }
            setSpinEpisodesVisible(visible && currentVideo?.type == MovieType.SERIAL)
            setCustomTitleVisible(land && visible)
            binding.mtvQuality.isVisible = visible
            binding.ivScreenLock.isVisible = visible
            if (!visible) {
                binding.plVideoPLayer.controllerShowTimeoutMs = 3000
            }
        }
    }*/
    private val seasonsChangeListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            updateCurrentSerialPosition()
            currentEpisodePosition = 0
            fillSpinners()
            currentVideo?.currentPosition = 0
            updateCurrentVideo()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            updateCurrentVideo()
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
            updateCurrentVideo()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            updateCurrentVideo()
        }
    }
    private lateinit var binding: FDetailsBinding

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
        updateTitle(args.movie.title)
        initMenu()
        initUI()
        initTrackAdapters()
        observeData()
        viewModel.loadVideo(args.movie)
    }

    private fun initUI() {
        binding.btnPlay.setOnClickListener {
            currentVideo?.let { video ->
                val episodesData = StringBuilder().apply {
                    video.season?.let { append(" Сезон $it") }
                    video.episode?.let { append(" Эпизод $it") }
                }.toString()
                findNavController().navigate(
                    DetailsFragmentDirections.actionNavDetailsToNavPlayerView(
                        video.videoUrl, "${video.title}$episodesData"
                    )
                )
            }
        }
    }

    private fun updateUI() {
        currentVideo?.let { video ->
            binding.tvTitle.text = video.title
            binding.btnPlay.isVisible = !video.videoUrl.isNullOrBlank()
            currentVideo = video
            setSpinEpisodesVisible(currentVideo?.type == MovieType.SERIAL)
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

    private fun updateEpisodeSelection(map: HashMap<*, *>) = with(binding) {
        for ((key, value) in map.entries) {
            val season = (key as? Int) ?: 0
            val episode = (value as? Int) ?: 0
            val isValidSeasonsData = currentSeasonPosition == season
                    && currentEpisodePosition == episode
            if (!isValidSeasonsData) {
                if (currentSeasonPosition != season) {
                    currentSeasonPosition = season
                    currentEpisodePosition = episode
                    fillSpinners()
                } else {
                    currentSeasonPosition = season
                    currentEpisodePosition = episode
                    if (currentSeasonPosition != spinSeasons.selectedItemPosition) {
                        spinSeasons.setSelection(season, false)
                    }
                    if (currentEpisodePosition != spinEpisodes.selectedItemPosition) {
                        spinEpisodes.setSelection(episode, false)
                    }
                }
            }
        }
    }

    private fun initBinding() {
//            mtvQuality.setOnClickListener { tv -> initQualityPopup(tv) }
//            ivScreenLock.setOnClickListener { toggleLockOrientaion() }
    }

    /*private fun toggleLockOrientaion() = with(binding) {
        if (orientationLocked) {
            orientationLocked = false
            requireActivity().unlockOrientation()
        } else {
            orientationLocked = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireActivity().lockOrientation()
            }
        }
        setScreenLockImg()
    }*/
    /*private fun setScreenLockImg() = with(binding) {
        ivScreenLock.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                if (orientationLocked) R.drawable.ic_screen_lock_rotation_active
                else R.drawable.ic_screen_lock_rotation_inactive
            )
        )
    }*/
    /*private fun initQualityPopup(view: View) = with(binding) {
        currentVideo?.hlsList?.keys?.toList()?.let { keys ->
            val qualityPopup = PopupMenu(requireContext(), view)
            qualityPopup.setOnMenuItemClickListener { item ->
                val quality = keys.getOrNull(item.itemId - 1)
                if (quality != currentVideo?.selectedHls) {
                    mtvQuality.text = getString(R.string.quality_format, quality)
                    currentVideo = currentVideo?.copy(
                        selectedHls = quality,
                        playWhenReady = exoPlayer?.playWhenReady == true,
                        currentPosition = exoPlayer?.currentPosition ?: 0
                    )
                    currentMovie = currentMovie?.copy(
                        selectedQuality = quality,
                        video = currentVideo
                    )
                    if (currentMovie != null) {
                        releasePlayer()
                        initPlayer(currentMovie)
                    }
                }
                false
            }
            val menu = qualityPopup.menu
            menu.add(Menu.NONE, 0, 0, getString(R.string.video_quality))
            for ((ind, key) in keys.withIndex()) {
                menu.add(1, (ind + 1), (ind + 1), key)
            }
            menu.setGroupCheckable(1, true, true)
            val selectedIndex = keys.indexOf(currentVideo?.selectedHls)
            if (selectedIndex >= 0) {
                menu.findItem(selectedIndex + 1).isChecked = true
            }
            qualityPopup.show()
        }
    }*/
    private fun setSpinEpisodesVisible(visible: Boolean) = with(binding) {
        spinEpisodes.isVisible = visible
        spinSeasons.isVisible = visible
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
//        spinEpisodes.setOnTouchListener { _, event ->
//            if (event.action == MotionEvent.ACTION_UP) {
//                plVideoPLayer.controllerShowTimeoutMs = -1
//            }
//            false
//        }
//        spinSeasons.setOnTouchListener { _, event ->
//            if (event.action == MotionEvent.ACTION_UP) {
//                plVideoPLayer.controllerShowTimeoutMs = -1
//            }
//            false
//        }
    }

    private fun updateCurrentVideo() {
        val seasons = currentMovie?.serialData?.seasons
        seasons?.let {
            val playerSeason = seasons.getOrNull(currentSeasonPosition)
            val episode = playerSeason?.episodes?.getOrNull(currentEpisodePosition)
            currentVideo = currentVideo?.copy(
                id = episode?.id,
                title = episode?.title,
                type = MovieType.SERIAL,
                hlsList = hashMapOf(),
                season = playerSeason?.id,
                episode = episode?.id
            )
        }
        updateUI()
    }

    private fun updateCurrentSerialPosition() {
        currentSeasonPosition = binding.spinSeasons.selectedItemPosition
        currentEpisodePosition = binding.spinEpisodes.selectedItemPosition
    }

    private fun onMovieLoaded(movie: Movie?) {
        currentMovie = movie
        currentVideo = movie?.video
        updateSpinData()
        updateCurrentVideo()
    }

    private fun updateSpinData() = with(binding) {
        val cachedSeasonPosition = currentMovie?.currentSeasonPosition ?: 0
        val cachedEpisodePosition = currentMovie?.currentEpisodePosition ?: 0
        if (cachedSeasonPosition != 0 && currentSeasonPosition == 0) {
            currentSeasonPosition = cachedSeasonPosition
        }
        if (cachedEpisodePosition != 0 && currentEpisodePosition == 0) {
            currentEpisodePosition = cachedEpisodePosition
        }
        fillSpinners()
    }

    private fun fillSpinners() {
        val seasons = currentMovie?.serialData?.seasons
        seasons?.let {
            val seasonsList = seasons.map { "${it.id} сезон" }
            if (seasonsList.isNotEmpty()) {
                with(binding) {
                    spinSeasons.updateSpinnerItems(seasonsChangeListener) {
                        seasonsTracksAdapter?.clear()
                        seasonsTracksAdapter?.addAll(seasonsList)
                        spinSeasons.setSelection(currentSeasonPosition, false)
                    }
                    spinEpisodes.updateSpinnerItems(episodesChangeListener) {
                        val season = seasons.getOrNull(currentSeasonPosition)
                        val seriesList = season?.episodes?.map { "${it.id} серия" }
                        episodesTracksAdapter?.clear()
                        episodesTracksAdapter?.addAll(seriesList)
                        spinEpisodes.setSelection(currentEpisodePosition, false)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(KEY_VIDEO, currentVideo)
        outState.putParcelable(KEY_MOVIE, currentMovie)
        outState.putInt(KEY_SEASON, currentSeasonPosition)
        outState.putInt(KEY_EPISODE, currentEpisodePosition)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        currentMovie = savedInstanceState?.getParcelable(KEY_MOVIE)
        currentVideo = savedInstanceState?.getParcelable(KEY_VIDEO)
        currentSeasonPosition = savedInstanceState?.getInt(KEY_SEASON) ?: 0
        currentEpisodePosition = savedInstanceState?.getInt(KEY_EPISODE) ?: 0
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
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
                                viewModel.clearCache(currentMovie)
                            }
                        )
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }
}
