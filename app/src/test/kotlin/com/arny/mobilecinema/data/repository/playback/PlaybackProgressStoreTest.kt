package com.arny.mobilecinema.data.repository.playback

import androidx.sqlite.db.SupportSQLiteQuery
import com.arny.mobilecinema.data.db.daos.HistoryDao
import com.arny.mobilecinema.data.db.daos.PlaybackProgressDao
import com.arny.mobilecinema.data.db.models.ContinueWatchingRow
import com.arny.mobilecinema.data.db.models.HistoryEntity
import com.arny.mobilecinema.data.db.models.PlaybackProgressEntity
import com.arny.mobilecinema.domain.models.ViewMovie
import com.arny.mobilecinema.domain.playback.PlaybackProgress
import com.arny.mobilecinema.domain.playback.ProgressCompletionPolicy
import com.arny.mobilecinema.domain.playback.legacyPlayableRef
import com.arny.mobilecinema.domain.playback.playableRefs
import com.arny.mobilecinema.domain.playback.testEpisode
import com.arny.mobilecinema.domain.playback.testSeason
import com.arny.mobilecinema.domain.playback.testSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressStoreTest {

    private val series = testSeries(
        listOf(
            testSeason(1, testEpisode(11, "1"), testEpisode(12, "2")),
            testSeason(2, testEpisode(21, "1")),
        )
    )
    private val refs = series.playableRefs()

    @Test
    fun `record inserts progress and updates the same row`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        store.record(refs[0], positionMs = 60_000, durationMs = 2_700_000)
        store.record(refs[0], positionMs = 120_000, durationMs = 2_700_000)

        assertEquals(1, dao.rows.size)
        val row = dao.rows.single()
        assertEquals(refs[0].id.value, row.playableId)
        assertEquals(120_000L, row.positionMs)
        assertEquals(2_700_000L, row.durationMs)
        assertFalse(row.completed)
        assertEquals(1, row.seasonNumber)
        assertEquals(1, row.episodeNumber)
    }

    // PROG-01: мобильный плеер пишет те же координаты, что и TV, — проверяем контракт общих записей.
    @Test
    fun `position from mobile coordinates lands on the same playable row as tv`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        // Телефон присылает индексы каталога, TV — playableId; строка должна быть одна.
        val fromMobileIndexes = series.legacyPlayableRef(seasonIndex = 1, episodeIndex = 0)!!
        store.record(fromMobileIndexes, positionMs = 90_000, durationMs = 2_700_000)
        store.record(refs.first { it.id == fromMobileIndexes.id }, positionMs = 120_000, durationMs = 2_700_000)

        assertEquals(1, dao.rows.size)
        assertEquals("ep:42:2:21", dao.rows.single().playableId)
        assertEquals(120_000L, dao.rows.single().positionMs)
    }

    @Test
    fun `switching episode keeps one row per playable`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        store.record(refs[0], positionMs = 60_000, durationMs = 2_700_000)
        store.record(refs[1], positionMs = 30_000, durationMs = 2_700_000)
        store.record(refs[1], positionMs = 45_000, durationMs = 2_700_000)

        assertEquals(2, dao.rows.size)
        assertEquals(
            listOf(refs[0].id.value, refs[1].id.value),
            dao.rows.map { it.playableId },
        )
        assertEquals(45_000L, dao.rows.last().positionMs)
    }

    @Test
    fun `repeated tick events do not duplicate rows`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        repeat(5) { index ->
            store.record(refs[0], positionMs = 20_000L + index * 15_000, durationMs = 2_700_000)
        }

        assertEquals(1, dao.rows.size)
        assertEquals(80_000L, dao.rows.single().positionMs)
    }

    @Test
    fun `short positions are not persisted`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        val written = store.record(refs[0], positionMs = 5_000, durationMs = 2_700_000)

        assertFalse(written)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `position near the end is stored as completed`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        store.record(refs[0], positionMs = 2_690_000, durationMs = 2_700_000)

        assertTrue(dao.rows.single().completed)
    }

    @Test
    fun `legacy row is upgraded instead of duplicated`() = runTest {
        val dao = FakePlaybackProgressDao()
        dao.rows += PlaybackProgressEntity(
            dbId = 7,
            movieDbId = series.dbId,
            season = 0,
            episode = 0,
            playableId = "legacy:42:0:0",
            positionMs = 0,
            updatedAt = 10,
        )
        val store = store(dao)

        store.record(
            ref = refs[0],
            positionMs = 90_000,
            durationMs = 2_700_000,
            legacySeasonIndex = 0,
            legacyEpisodeIndex = 0,
        )

        assertEquals(1, dao.rows.size)
        val row = dao.rows.single()
        assertEquals(7, row.dbId)
        assertEquals(refs[0].id.value, row.playableId)
        assertEquals(90_000L, row.positionMs)
    }

    @Test
    fun `migrateLegacy maps old indexes and keeps history position`() = runTest {
        val dao = FakePlaybackProgressDao()
        val history = FakeHistoryDao().apply {
            entity = HistoryEntity(
                movieDbId = series.dbId,
                position = 600_000,
                season = 1,
                episode = 0,
                latestTime = 50,
            )
        }
        dao.rows += PlaybackProgressEntity(
            dbId = 3,
            movieDbId = series.dbId,
            season = 1,
            episode = 0,
            playableId = "legacy:42:1:0",
            latestTime = 50,
        )
        val store = store(dao, history)

        store.migrateLegacy(series)

        val row = dao.rows.single()
        assertEquals("ep:42:2:21", row.playableId)
        assertEquals(600_000L, row.positionMs)
        assertEquals(2, row.seasonNumber)
        assertEquals(1, row.episodeNumber)
    }

    @Test
    fun `progress is read by playable id and movie`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)
        store.record(refs[1], positionMs = 45_000, durationMs = 2_700_000)

        val single = store.progressFor(refs[1].id.value)
        val all = store.progressForMovie(series.dbId)

        assertNotNull(single)
        assertEquals(45_000L, single?.positionMs)
        assertEquals(1, all.size)
        assertEquals(PlaybackProgress.DEFAULT_PROFILE_ID, single?.profileId)
    }

    // MOB-10B: позиция и время просмотра пишутся одной строкой по playableId,
    // а не отдельной legacy-строкой через addPlaybackProgress.
    @Test
    fun `played time is accumulated on the same row as the position`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        store.record(refs[0], positionMs = 20_000, durationMs = 2_700_000, playedMsDelta = 5_000)
        store.record(refs[0], positionMs = 25_000, durationMs = 2_700_000, playedMsDelta = 5_000)

        assertEquals(1, dao.rows.size)
        val row = dao.rows.single()
        assertEquals(refs[0].id.value, row.playableId)
        assertEquals(25_000L, row.positionMs)
        assertEquals(10_000L, row.playedMs)
        assertTrue(dao.rows.none { it.playableId.startsWith("legacy:") })
    }

    @Test
    fun `played time is credited before the position threshold`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        val written = store.record(
            refs[0],
            positionMs = 5_000,
            durationMs = 2_700_000,
            playedMsDelta = 5_000,
        )

        assertTrue(written)
        val row = dao.rows.single()
        assertEquals(5_000L, row.playedMs)
        assertEquals(0L, row.positionMs)
        assertEquals(0L, row.updatedAt)
    }

    @Test
    fun `position write without played delta keeps accumulated time`() = runTest {
        val dao = FakePlaybackProgressDao()
        val store = store(dao)

        store.record(refs[1], positionMs = 30_000, durationMs = 2_700_000, playedMsDelta = 4_000)
        store.record(refs[1], positionMs = 31_000, durationMs = 2_700_000)

        val row = dao.rows.single()
        assertEquals(4_000L, row.playedMs)
        assertEquals(31_000L, row.positionMs)
    }

    private fun store(
        dao: FakePlaybackProgressDao,
        history: FakeHistoryDao = FakeHistoryDao(),
    ) = PlaybackProgressStore(
        playbackProgressDao = dao,
        historyDao = history,
        policy = ProgressCompletionPolicy(),
        dispatcher = UnconfinedTestDispatcher(),
        clock = { 1_000L },
    )
}

private class FakePlaybackProgressDao : PlaybackProgressDao {
    val rows = mutableListOf<PlaybackProgressEntity>()
    private var nextId = 1L

    override fun insert(progress: PlaybackProgressEntity): Long {
        val id = if (progress.dbId > 0) progress.dbId else nextId++
        rows += progress.copy(dbId = id)
        return id
    }

    override fun findByPlayable(profileId: String, playableId: String): PlaybackProgressEntity? =
        rows.firstOrNull { it.profileId == profileId && it.playableId == playableId }

    override fun findAllForMovie(profileId: String, movieDbId: Long): List<PlaybackProgressEntity> =
        rows.filter { it.profileId == profileId && it.movieDbId == movieDbId }

    override fun findLegacy(
        profileId: String,
        movieDbId: Long,
        season: Int,
        episode: Int,
    ): PlaybackProgressEntity? = rows.firstOrNull {
        it.profileId == profileId &&
            it.movieDbId == movieDbId &&
            it.season == season &&
            it.episode == episode &&
            (it.playableId.isBlank() || it.playableId.startsWith("legacy:"))
    }

    override fun updateProgress(
        dbId: Long,
        playableId: String,
        season: Int,
        episode: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long,
        playedMs: Long,
        latestTime: Long,
        completed: Boolean,
        updatedAt: Long,
    ): Int {
        val index = rows.indexOfFirst { it.dbId == dbId }
        if (index < 0) return 0
        rows[index] = rows[index].copy(
            playableId = playableId,
            season = season,
            episode = episode,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            positionMs = positionMs,
            durationMs = durationMs,
            playedMs = playedMs,
            latestTime = latestTime,
            completed = completed,
            updatedAt = updatedAt,
        )
        return 1
    }

    override fun observeContinueWatching(
        profileId: String,
        minPositionMs: Long,
        limit: Int,
    ): Flow<List<ContinueWatchingRow>> = flowOf(emptyList())

    override fun deleteForMovie(movieDbId: Long?): Int {
        val size = rows.size
        rows.removeAll { it.movieDbId == movieDbId }
        return size - rows.size
    }

    override fun deleteAll(): Int {
        val size = rows.size
        rows.clear()
        return size
    }
}

private class FakeHistoryDao : HistoryDao {
    var entity: HistoryEntity? = null

    override fun insert(obj: HistoryEntity): Long {
        entity = obj
        return 1
    }

    override fun update(obj: HistoryEntity): Int {
        entity = obj
        return 1
    }

    override fun insertAll(objs: List<HistoryEntity>) {
        entity = objs.firstOrNull()
    }

    override suspend fun getMovies(query: SupportSQLiteQuery): List<ViewMovie> = emptyList()

    override suspend fun getFavoriteMovies(query: SupportSQLiteQuery): List<ViewMovie> = emptyList()

    override fun getHistoryCount(): Int = if (entity == null) 0 else 1

    override fun getHistory(movieDbId: Long?): HistoryEntity? =
        entity?.takeIf { it.movieDbId == movieDbId }

    override fun updateHistory(
        movieDbId: Long?,
        position: Long,
        season: Int,
        episode: Int,
        currentTimeMs: Long,
    ): Int = 0

    override fun deleteHistory(movieDbId: Long?): Int = 0

    override fun deleteAllHistory(): Int = 0
}
