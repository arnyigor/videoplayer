package com.arny.mobilecinema.domain.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NextEpisodeResolverTest {

    private val resolver = NextEpisodeResolver()

    private val twoSeasons = testSeries(
        listOf(
            testSeason(
                1,
                testEpisode(101, "1"),
                testEpisode(102, "2"),
                testEpisode(103, "3"),
                testEpisode(104, "4"),
                testEpisode(105, "5"),
                testEpisode(106, "6"),
                testEpisode(107, "7"),
                testEpisode(108, "8"),
                testEpisode(109, "9"),
                testEpisode(110, "10"),
            ),
            testSeason(2, testEpisode(201, "1"), testEpisode(202, "2")),
        )
    )

    @Test
    fun `without history starts from first available episode`() {
        val target = resolver.resumeTarget(twoSeasons, emptyMap())

        assertTrue(target is ResumeTarget.Start)
        assertEquals("ep:42:1:101", (target as ResumeTarget.Start).ref.id.value)
        assertEquals(1, target.ref.episodeNumber)
    }

    @Test
    fun `completed last episode of season moves to next season`() {
        val progress = mapOf(
            "ep:42:1:110" to testProgress("ep:42:1:110", positionMs = 2_700_000, durationMs = 2_700_000, completed = true)
        )

        val target = resolver.resumeTarget(twoSeasons, progress)

        assertTrue(target is ResumeTarget.Start)
        assertEquals("ep:42:2:201", (target as ResumeTarget.Start).ref.id.value)
    }

    @Test
    fun `completed last episode of series offers restart`() {
        val progress = mapOf(
            "ep:42:2:202" to testProgress("ep:42:2:202", positionMs = 2_700_000, durationMs = 2_700_000, completed = true)
        )

        val target = resolver.resumeTarget(twoSeasons, progress)

        assertTrue(target is ResumeTarget.Restart)
        assertEquals("ep:42:1:101", (target as ResumeTarget.Restart).ref.id.value)
    }

    @Test
    fun `active progress resumes that episode and clamps position`() {
        val progress = mapOf(
            "ep:42:1:103" to testProgress("ep:42:1:103", positionMs = 600_000, durationMs = 2_700_000, updatedAt = 5)
        )

        val target = resolver.resumeTarget(twoSeasons, progress)

        assertTrue(target is ResumeTarget.Resume)
        target as ResumeTarget.Resume
        assertEquals("ep:42:1:103", target.ref.id.value)
        assertEquals(600_000L, target.positionMs)
    }

    @Test
    fun `stale position near the end is treated as completed and advances`() {
        val progress = mapOf(
            "ep:42:1:103" to testProgress(
                "ep:42:1:103",
                positionMs = 2_690_000,
                durationMs = 2_700_000,
                completed = false,
                updatedAt = 5,
            )
        )

        val target = resolver.resumeTarget(twoSeasons, progress)

        assertTrue(target is ResumeTarget.Start)
        assertEquals("ep:42:1:104", (target as ResumeTarget.Start).ref.id.value)
    }

    @Test
    fun `unavailable episode is skipped in favor of the next available one`() {
        val series = testSeries(
            listOf(
                testSeason(1, testEpisode(101, "1"), testEpisode(102, "2"), testEpisode(103, "3", hls = "")),
                testSeason(2, testEpisode(201, "1")),
            )
        )
        val progress = mapOf(
            "ep:42:1:102" to testProgress("ep:42:1:102", positionMs = 2_700_000, durationMs = 2_700_000, completed = true)
        )

        val target = resolver.resumeTarget(series, progress)

        assertTrue(target is ResumeTarget.Start)
        assertEquals("ep:42:2:201", (target as ResumeTarget.Start).ref.id.value)
    }

    @Test
    fun `unavailable next episode without a following one is reported, not restarted`() {
        val series = testSeries(
            listOf(
                testSeason(1, testEpisode(101, "1"), testEpisode(102, "2"), testEpisode(103, "3", hls = "")),
            )
        )
        val progress = mapOf(
            "ep:42:1:102" to testProgress("ep:42:1:102", positionMs = 2_700_000, durationMs = 2_700_000, completed = true)
        )

        val target = resolver.resumeTarget(series, progress)

        assertTrue(target is ResumeTarget.Unavailable)
    }

    @Test
    fun `no available sources produces diagnosable result`() {
        val series = testSeries(listOf(testSeason(1, testEpisode(101, "1", hls = ""))))

        val target = resolver.resumeTarget(series, emptyMap())

        assertTrue(target is ResumeTarget.Unavailable)
        assertTrue((target as ResumeTarget.Unavailable).reason.isNotBlank())
    }

    @Test
    fun `film without history starts from the beginning`() {
        val target = resolver.resumeTarget(testFilm(durationSec = 7200), emptyMap())

        assertTrue(target is ResumeTarget.Start)
        assertEquals("film:42", (target as ResumeTarget.Start).ref.id.value)
        assertEquals(7_200_000L, target.ref.durationMs)
    }

    @Test
    fun `finished film offers restart`() {
        val progress = mapOf(
            "film:42" to testProgress("film:42", positionMs = 7_190_000, durationMs = 7_200_000, completed = true)
        )

        val target = resolver.resumeTarget(testFilm(durationSec = 7200), progress)

        assertTrue(target is ResumeTarget.Restart)
    }

    @Test
    fun `next and previous follow catalog order`() {
        val all = resolver.ordered(twoSeasons)
        val current = all[9].id

        assertEquals("ep:42:2:201", resolver.next(twoSeasons, current)?.id?.value)
        assertEquals("ep:42:1:109", resolver.previous(twoSeasons, current)?.id?.value)
        assertNull(resolver.next(twoSeasons, all.last().id))
        assertNull(resolver.previous(twoSeasons, all.first().id))
    }
}
