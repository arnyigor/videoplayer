package com.arny.mobilecinema.presentation.tv.details

import com.arny.mobilecinema.R
import com.arny.mobilecinema.domain.models.AnwapUrl
import com.arny.mobilecinema.domain.models.CinemaUrlData
import com.arny.mobilecinema.domain.models.MovieInfo
import com.arny.mobilecinema.domain.playback.PlayableId
import com.arny.mobilecinema.domain.playback.ResumeTarget
import com.arny.mobilecinema.domain.playback.TEST_MOVIE_ID
import com.arny.mobilecinema.domain.playback.testEpisode
import com.arny.mobilecinema.domain.playback.testFilm
import com.arny.mobilecinema.domain.playback.testProgress
import com.arny.mobilecinema.domain.playback.testSeason
import com.arny.mobilecinema.domain.playback.testSeries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailsContentFactoryTest {

    private fun series() = testSeries(
        listOf(
            // Каталог отдаёт сезоны не по порядку: порядок должен браться из номеров.
            testSeason(2, testEpisode(21, "1"), testEpisode(22, "2")),
            testSeason(1, testEpisode(11, "1"), testEpisode(12, "2"), testEpisode(13, "3")),
        ),
    )

    @Test
    fun `seasons follow catalog numbers and keep episode counts`() {
        val seasons = DetailsContentFactory.seasons(series())

        assertEquals(listOf(1, 2), seasons.map { it.seasonNumber })
        assertEquals(listOf(3, 2), seasons.map { it.episodeCount })
        // seasonIndex остаётся координатой каталога, нужной плееру.
        assertEquals(listOf(1, 0), seasons.map { it.seasonIndex })
    }

    @Test
    fun `episodes of second season keep numbers and availability`() {
        val episodes = DetailsContentFactory.episodes(series(), seasonIndex = 0)

        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
        // episodeIndex — позиция в массиве сезона, а не id серии из каталога.
        assertEquals(listOf(0, 1), episodes.map { it.episodeIndex })
        assertTrue(episodes.all { it.isAvailable })
        assertTrue(episodes.all { it.status == EpisodeStatus.NOT_STARTED })
        assertTrue(episodes.all { it.progressPercent == null })
    }

    @Test
    fun `episode without any source is marked unavailable`() {
        val movie = testSeries(
            listOf(testSeason(1, testEpisode(11, "1", hls = ""), testEpisode(12, "2")))
        )

        val episodes = DetailsContentFactory.episodes(movie, seasonIndex = 0)

        assertFalse(episodes.first().isAvailable)
        assertTrue(episodes.last().isAvailable)
    }

    @Test
    fun `episode status comes from playback progress`() {
        val movie = series()
        val episodes = DetailsContentFactory.episodes(
            movie = movie,
            seasonIndex = 0,
            progress = mapOf(
                "ep:$TEST_MOVIE_ID:2:21" to testProgress("ep:$TEST_MOVIE_ID:2:21", positionMs = 20 * 60_000L),
                "ep:$TEST_MOVIE_ID:2:22" to testProgress("ep:$TEST_MOVIE_ID:2:22", positionMs = 45 * 60_000L),
            ),
        )

        val inProgress = episodes.first()
        assertEquals(EpisodeStatus.IN_PROGRESS, inProgress.status)
        assertEquals(44, inProgress.progressPercent)

        val completed = episodes.last()
        assertEquals(EpisodeStatus.COMPLETED, completed.status)
        assertNull(completed.progressPercent)
    }

    @Test
    fun `recommended episode is flagged`() {
        val ref = DetailsContentFactory.playableRefs(series())
            .first { it.seasonNumber == 2 && it.episodeNumber == 2 }

        val episodes = DetailsContentFactory.episodes(
            movie = series(),
            seasonIndex = 0,
            recommendedPlayableId = ref.id.value,
        )

        assertEquals(listOf(ref.id.value), episodes.filter { it.isRecommended }.map { it.playableId })
    }

    @Test
    fun `film sources put hd first and keep labels short`() {
        val movie = testFilm().copy(
            cinemaUrlData = CinemaUrlData(
                hdUrl = AnwapUrl(urls = listOf("https://cdn.test/hd.m3u8?token=1")),
                cinemaUrl = AnwapUrl(urls = listOf("https://other.test/film.mp4")),
            )
        )

        val sources = DetailsContentFactory.sources(movie, playableId = null)

        assertEquals(2, sources.size)
        assertTrue(sources.first().isHd)
        assertTrue(sources.first().label.startsWith("HD • cdn.test"))
        assertTrue(sources.last().label.startsWith("SD • other.test"))
    }

    @Test
    fun `serial sources prefer dash for selected episode`() {
        val movie = testSeries(
            listOf(
                testSeason(
                    1,
                    testEpisode(11, "1").copy(dash = "https://dash.test/11.mpd"),
                )
            )
        )
        val ref = DetailsContentFactory.playableRefs(movie).first()

        val sources = DetailsContentFactory.sources(movie, ref.id.value)

        assertEquals(2, sources.size)
        assertTrue(sources.first().isHd)
        assertTrue(sources.first().url.endsWith(".mpd"))
    }

    @Test
    fun `cta for series continues exact episode`() {
        val movie = series()
        val ref = DetailsContentFactory.playableRefs(movie)
            .first { it.seasonNumber == 1 && it.episodeNumber == 3 }

        val cta = DetailsContentFactory.cta(movie, ResumeTarget.Resume(ref, positionMs = 60_000L))

        assertEquals(R.string.tv_continue_s_e, cta.labelRes)
        assertEquals(listOf(1, 3), cta.labelArgs)
        assertEquals(ref.seasonIndex, cta.seasonIndex)
        assertEquals(ref.episodeIndex, cta.episodeIndex)
        assertEquals(ref.id.value, cta.playableId)
    }

    @Test
    fun `cta for started film shows position`() {
        val movie = testFilm()
        val ref = DetailsContentFactory.playableRefs(movie).first()

        val cta = DetailsContentFactory.cta(movie, ResumeTarget.Resume(ref, positionMs = 19 * 60_000L + 26_000L))

        assertEquals(R.string.tv_continue_at, cta.labelRes)
        assertEquals(listOf("19:26"), cta.labelArgs)
    }

    @Test
    fun `cta for fresh series starts from first episode`() {
        val movie = series()
        val ref = DetailsContentFactory.playableRefs(movie).first()

        val cta = DetailsContentFactory.cta(movie, ResumeTarget.Start(ref))

        assertEquals(R.string.tv_play_s_e, cta.labelRes)
        assertEquals(listOf(1, 1), cta.labelArgs)
    }

    @Test
    fun `cta restarts finished title`() {
        val movie = series()
        val ref = DetailsContentFactory.playableRefs(movie).first { it.seasonNumber == 2 }

        val cta = DetailsContentFactory.cta(movie, ResumeTarget.Restart(ref))

        assertEquals(R.string.tv_watch_again, cta.labelRes)
        assertEquals(ref.seasonIndex, cta.seasonIndex)
    }

    @Test
    fun `cta falls back to play when nothing is playable`() {
        val cta = DetailsContentFactory.cta(series(), ResumeTarget.Unavailable("no sources"))

        assertEquals(R.string.play, cta.labelRes)
        assertEquals(0, cta.seasonIndex)
        assertNull(cta.playableId)
    }

    @Test
    fun `genres and actors are split and deduplicated`() {
        val movie = series().copy(
            info = MovieInfo(
                genres = listOf("Фэнтези, драма", "драма"),
                actors = listOf("Питер Клэффи, Декстер Сол Анселл"),
            )
        )

        assertEquals(listOf("Фэнтези", "драма"), DetailsContentFactory.genres(movie))
        assertEquals(listOf("Питер Клэффи", "Декстер Сол Анселл"), DetailsContentFactory.actors(movie))
    }

    @Test
    fun `position formatting switches to hours when needed`() {
        assertEquals("00:42", DetailsContentFactory.formatPosition(42_000L))
        assertEquals("19:26", DetailsContentFactory.formatPosition(1_166_000L))
        assertEquals("1:02:03", DetailsContentFactory.formatPosition(3_723_000L))
    }

    @Test
    fun `recommended ref is extracted from every target type`() {
        val movie = series()
        val ref = DetailsContentFactory.playableRefs(movie).first()

        assertEquals(ref, DetailsContentFactory.recommendedRef(ResumeTarget.Start(ref)))
        assertEquals(ref, DetailsContentFactory.recommendedRef(ResumeTarget.Resume(ref, 1_000L)))
        assertEquals(ref, DetailsContentFactory.recommendedRef(ResumeTarget.Restart(ref)))
        assertNull(DetailsContentFactory.recommendedRef(ResumeTarget.Unavailable("none")))
        assertNull(DetailsContentFactory.recommendedRef(null))
        assertTrue(DetailsContentFactory.recommendedRef(null) == null)
    }

    @Test
    fun `film has single playable ref`() {
        val refs = DetailsContentFactory.playableRefs(testFilm())

        assertEquals(1, refs.size)
        assertTrue(refs.first().id is PlayableId.Film)
    }
}
