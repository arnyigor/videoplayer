package com.arny.mobilecinema.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.arny.mobilecinema.R
import com.arny.mobilecinema.data.db.daos.FavoritesDao
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.MovieDao
import com.arny.mobilecinema.data.db.daos.PlaybackProgressDao
import com.arny.mobilecinema.data.db.models.FavoriteEntity
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.models.MovieEntity
import com.arny.mobilecinema.data.db.models.PersonalizationMovieSignal
import com.arny.mobilecinema.data.db.sources.FavoritesPagingSource
import com.arny.mobilecinema.data.db.sources.getMoviesSQL
import com.arny.mobilecinema.data.db.sources.HistoryPagingSource
import com.arny.mobilecinema.data.db.sources.MainPagingSource
import com.arny.mobilecinema.data.models.MovieMapper
import com.arny.mobilecinema.data.repository.prefs.Prefs
import com.arny.mobilecinema.data.repository.resources.AppResourcesProvider
import com.arny.mobilecinema.data.search.GenreSearchHelper
import com.arny.mobilecinema.domain.models.Movie
import com.arny.mobilecinema.domain.models.MovieType
import com.arny.mobilecinema.domain.models.OrderKey
import com.arny.mobilecinema.domain.models.PrefsConstants
import com.arny.mobilecinema.domain.models.SimpleFloatRange
import com.arny.mobilecinema.domain.models.SimpleIntRange
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.repository.MoviesRepository

class MoviesRepositoryImpl(
    private val movieMapper: MovieMapper,
    private val movieDao: MovieDao,
    private val historyDao: HistoryDao,
    private val favoritesDao: FavoritesDao,
    private val playbackProgressDao: PlaybackProgressDao,
    private val prefs: Prefs,
    private val appResources: AppResourcesProvider,
) : MoviesRepository {

    override fun setOrder(key: OrderKey, value: String) =
        prefs.put(key.pref, value)

    override fun getOrder(key: OrderKey): String = prefs.get<String>(key.pref).orEmpty()

    override var orderPref: String
        get() = prefs.get<String>(PrefsConstants.ORDER).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.ORDER, value)
        }

    override var historyOrderPref: String
        get() = prefs.get<String>(PrefsConstants.HISTORY_ORDER).orEmpty()
        set(value) {
            prefs.put(PrefsConstants.HISTORY_ORDER, value)
        }

    override var pipModePref: Boolean
        get() = prefs.get<Boolean>(PrefsConstants.PREF_KEY_PIP_MODE) ?: false
        set(value) {
            prefs.put(PrefsConstants.PREF_KEY_PIP_MODE, value)
        }

    override fun getMovies(
        search: String,
        order: String,
        searchType: String,
        searchAddTypes: List<String>,
        genres: List<String>,
        countries: List<String>,
        years: SimpleIntRange?,
        imdbs: SimpleFloatRange?,
        kps: SimpleFloatRange?,
        likesPriority: Boolean,
    ): Pager<Int, ViewMovie> = Pager(
        PagingConfig(
            pageSize = 20,
            enablePlaceholders = false,
            initialLoadSize = 20
        ),
    ) {
        MainPagingSource(
            dao = movieDao,
            search = search.trim(),
            order = order,
            searchType = searchType,
            genres = genres,
            countries = countries,
            years = years,
            imdbs = imdbs,
            kps = kps,
            searchAddTypes = searchAddTypes,
            likesPriority = likesPriority
        )
    }

    override suspend fun getRecentMovies(
        updatedFrom: Long,
        movieTypes: List<MovieType>,
        limit: Int
    ): List<ViewMovie> =
        movieDao.getMovies(
            getMoviesSQL(
                search = "",
                order = AppConstants.Order.SMART,
                searchType = AppConstants.SearchType.TITLE,
                movieTypes = movieTypes,
                updatedFrom = updatedFrom,
                likesPriority = true,
                limit = limit,
                offset = 0
            )
        )

    override suspend fun getBestNowMovies(
        fromYear: Int,
        toYear: Int,
        movieTypes: List<MovieType>,
        limit: Int
    ): List<ViewMovie> =
        movieDao.getMovies(
            getMoviesSQL(
                search = "",
                order = AppConstants.Order.SMART,
                searchType = AppConstants.SearchType.TITLE,
                movieTypes = movieTypes,
                years = SimpleIntRange(from = fromYear, to = toYear),
                likesPriority = true,
                limit = limit,
                offset = 0
            )
        )

    override suspend fun getForYouMovies(
        movieTypes: List<MovieType>,
        excludedIds: Set<Long>,
        limit: Int
    ): List<ViewMovie> {
        val profile = buildPreferenceProfile(
            movieDao.getPersonalizationSignals(MEANINGFUL_SERIAL_EPISODE_WATCH_MS)
        )
        if (profile.strength < MIN_PROFILE_STRENGTH) return emptyList()

        val candidates = movieDao.getPersonalizationCandidates(
            movieTypes = movieTypes.map { it.value },
            limit = PERSONALIZATION_CANDIDATES_LIMIT
        )
        return candidates
            .asSequence()
            .filterNot { it.dbId in excludedIds }
            .mapNotNull { candidate ->
                val personalScore = profile.personalScore(candidate)
                if (personalScore <= 0.0) {
                    null
                } else {
                    candidate to ((personalScore * 0.60) + (candidate.smartScore() * 0.40))
                }
            }
            .sortedWith(
                compareByDescending<Pair<MovieEntity, Double>> { it.second }
                    .thenByDescending { it.first.likes }
                    .thenByDescending { it.first.year }
                    .thenByDescending { it.first.dbId }
            )
            .map { it.first.toViewMovie() }
            .take(limit)
            .toList()
            .takeIf { it.size >= MIN_FOR_YOU_ITEMS }
            .orEmpty()
    }

    private fun buildPreferenceProfile(signals: List<PersonalizationMovieSignal>): PreferenceProfile {
        val genres = mutableMapOf<String, Double>()
        val actors = mutableMapOf<String, Double>()
        val directors = mutableMapOf<String, Double>()
        val types = mutableMapOf<Int, Double>()
        var strength = 0

        signals.forEach { signal ->
            val weight = signal.signalWeight()
            if (weight <= 0) return@forEach

            strength += weight
            signal.movie.genre.addTermsTo(genres, weight.toDouble())
            signal.movie.actors.addTermsTo(actors, weight.toDouble())
            signal.movie.directors.addTermsTo(directors, weight.toDouble())
            types[signal.movie.type] = (types[signal.movie.type] ?: 0.0) + weight
        }

        return PreferenceProfile(
            strength = strength,
            genres = genres,
            actors = actors,
            directors = directors,
            types = types
        )
    }

    private fun PersonalizationMovieSignal.signalWeight(): Int {
        if (favoriteLatestTime != null) return FAVORITE_SIGNAL_WEIGHT
        return when (movie.type) {
            MovieType.CINEMA.value -> {
                if (playbackMs >= MEANINGFUL_CINEMA_WATCH_MS) MEANINGFUL_WATCH_SIGNAL_WEIGHT else 0
            }

            MovieType.SERIAL.value -> {
                if (meaningfulEpisodes >= MEANINGFUL_SERIAL_EPISODES_COUNT) {
                    MEANINGFUL_WATCH_SIGNAL_WEIGHT
                } else {
                    0
                }
            }

            else -> 0
        }
    }

    private fun String.addTermsTo(target: MutableMap<String, Double>, weight: Double) {
        split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .forEach { term ->
                target[term] = (target[term] ?: 0.0) + weight
            }
    }

    private fun MovieEntity.toViewMovie(): ViewMovie =
        ViewMovie(
            dbId = dbId,
            title = title,
            type = type,
            img = img,
            year = year,
            likes = likes,
            dislikes = dislikes,
            ratingImdb = ratingImdb,
            ratingKp = ratingKp,
            updated = updated,
            isFavorite = false
        )

    private fun MovieEntity.smartScore(): Double {
        val likesPopularity = likes.toDouble() / (likes + 100.0)
        val approval = (likes + 10.0) / (likes + dislikes + 20.0)
        val approvalQuality = maxOf(0.0, (approval - 0.5) * 2.0)
        val localScore = (likesPopularity * 0.70) + (approvalQuality * 0.30)
        val externalScore = when {
            ratingImdb > 0.0 && ratingKp > 0.0 -> ((ratingImdb / 10.0) * 0.60) + ((ratingKp / 10.0) * 0.40)
            ratingImdb > 0.0 -> ratingImdb / 10.0
            ratingKp > 0.0 -> ratingKp / 10.0
            else -> null
        }
        return if (externalScore != null) {
            (localScore * 0.80) + (externalScore * 0.20)
        } else {
            localScore
        }
    }

    override fun clearAllFavorites() {
        favoritesDao.deleteAllFavorites()
    }

    override fun toggleFavorite(movieId: Long): Boolean =
        if (favoritesDao.getCountForMovie(movieId) > 0) {
            favoritesDao.deleteFavorite(movieId)
            false
        } else {
            favoritesDao.insert(FavoriteEntity(movieDbId = movieId))
            true
        }

    override fun isFavorite(movieId: Long): Boolean = favoritesDao.getCountForMovie(movieId) > 0

    override suspend fun isMoviesEmpty(): Boolean = movieDao.getCount() == 0

    override fun getHistoryMovies(
        search: String,
        order: String,
        searchType: String
    ): Pager<Int, ViewMovie> =
        Pager(
            PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
        ) { HistoryPagingSource(historyDao, search, order, searchType) }


    override fun getFavoriteMoviesPager(
        search: String,
        order: String,
        searchType: String
    ): Pager<Int, ViewMovie> =
        Pager(PagingConfig(pageSize = 20)) {
            FavoritesPagingSource(favoritesDao, search, order, searchType)
        }

    override fun getMovie(id: Long): Movie? =
        movieDao.getMovie(id)?.let { movieMapper.transform(it) }

    override fun getMovie(pageUrl: String): Movie? =
        movieDao.getMovie(pageUrl)?.let { movieMapper.transform(it) }

    override fun getGenres(): List<String> = GenreSearchHelper
        .toDisplayGenres(movieDao.allGenres())
        .ifEmpty { GenreSearchHelper.toDisplayGenres(appResources.getStringArray(R.array.genres)) }

    override fun getMinMaxYears(): SimpleIntRange = movieDao.getYearsMinMax()

    override fun getCountries(): List<String> = appResources.getStringArray(R.array.countries)

    override fun getSaveData(movieDbId: Long?): HistoryEntity? = historyDao.getHistory(movieDbId)

    override fun insertCinemaPosition(
        movieDbId: Long,
        position: Long,
        currentTimeMillis: Long
    ): Boolean = historyDao.insert(
        HistoryEntity(
            movieDbId = movieDbId,
            position = position,
            latestTime = currentTimeMillis
        )
    ) > 0L

    override fun updateCinemaPosition(
        movieDbId: Long?,
        position: Long,
        currentTimeMillis: Long
    ): Boolean {
        return historyDao.updateHistory(
            movieDbId = movieDbId,
            position = position,
            currentTimeMs = currentTimeMillis
        ) != 0
    }

    override fun addPlaybackProgress(
        movieDbId: Long,
        season: Int,
        episode: Int,
        playedMs: Long,
        currentTimeMs: Long
    ): Boolean =
        playbackProgressDao.addProgress(
            movieDbId = movieDbId,
            season = season,
            episode = episode,
            playedMs = playedMs,
            latestTime = currentTimeMs
        )

    override fun insertSerialPosition(
        movieDbId: Long,
        season: Int,
        episode: Int,
        episodePosition: Long,
        currentTimeMs: Long
    ): Boolean = historyDao.insert(
        HistoryEntity(
            movieDbId = movieDbId,
            position = episodePosition,
            episode = episode,
            season = season,
            latestTime = currentTimeMs
        )
    ) > 0L

    override fun updateSerialPosition(
        movieDbId: Long?,
        season: Int,
        episode: Int,
        time: Long,
        currentTimeMs: Long
    ): Boolean = historyDao.updateHistory(
        movieDbId = movieDbId,
        season = season,
        episode = episode,
        position = time,
        currentTimeMs = currentTimeMs
    ) != 0

    override suspend fun isHistoryEmpty(): Boolean {
        return historyDao.getHistoryCount() == 0
    }

    override fun clearViewHistory(movieDbId: Long?): Boolean {
        val historyDeleted = historyDao.deleteHistory(movieDbId) > 0
        val playbackDeleted = playbackProgressDao.deleteForMovie(movieDbId) > 0
        return historyDeleted || playbackDeleted
    }

    override fun clearAllViewHistory(): Boolean {
        val historyDeleted = historyDao.deleteAllHistory() > 0
        val playbackDeleted = playbackProgressDao.deleteAll() > 0
        return historyDeleted || playbackDeleted
    }

    override fun saveOrder(order: String) {
        this.orderPref = order
    }

    override fun saveHistoryOrder(order: String) {
        this.historyOrderPref = order
    }

    override suspend fun isFavoriteEmpty(): Boolean = favoritesDao.getFavoritesCount() == 0

    private data class PreferenceProfile(
        val strength: Int,
        val genres: Map<String, Double>,
        val actors: Map<String, Double>,
        val directors: Map<String, Double>,
        val types: Map<Int, Double>
    ) {
        private val totalTypeWeight = types.values.sum().coerceAtLeast(1.0)
        private val maxGenreWeight = genres.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        private val maxActorWeight = actors.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        private val maxDirectorWeight = directors.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        fun personalScore(candidate: MovieEntity): Double {
            val genreScore = candidate.genre.matchScore(genres) / maxGenreWeight
            val actorScore = candidate.actors.matchScore(actors) / maxActorWeight
            val directorScore = candidate.directors.matchScore(directors) / maxDirectorWeight
            val typeScore = (types[candidate.type] ?: 0.0) / totalTypeWeight
            return (genreScore * 0.60) +
                    (typeScore * 0.15) +
                    (actorScore * 0.15) +
                    (directorScore * 0.10)
        }

        private fun String.matchScore(weights: Map<String, Double>): Double =
            split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .sumOf { weights[it] ?: 0.0 }
    }

    private companion object {
        const val MIN_PROFILE_STRENGTH = 10
        const val MIN_FOR_YOU_ITEMS = 6
        const val PERSONALIZATION_CANDIDATES_LIMIT = 500
        const val FAVORITE_SIGNAL_WEIGHT = 3
        const val MEANINGFUL_WATCH_SIGNAL_WEIGHT = 2
        const val MEANINGFUL_CINEMA_WATCH_MS = 10L * 60 * 1000
        const val MEANINGFUL_SERIAL_EPISODE_WATCH_MS = 10L * 60 * 1000
        const val MEANINGFUL_SERIAL_EPISODES_COUNT = 2
    }
}
