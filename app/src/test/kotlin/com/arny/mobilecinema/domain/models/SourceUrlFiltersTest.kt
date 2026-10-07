package com.arny.mobilecinema.domain.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceUrlFiltersTest {

    @Test
    fun `dead collaps hosts are dropped, live hosts kept`() {
        val urls = listOf(
            "https://hye1eaipby4w.takedwn.ws/01_22/01/QK6ZFW64/GMZTMAXQ.mp4/master.m3u8",
            "https://ab.matham.ws/09_24/01/02/M7NDEDY2/1108107.mpd",
            "https://o12.anwap.be/ohls/XA7e/1791414210/Film-48596.m3u8",
        )

        assertEquals(
            listOf("https://o12.anwap.be/ohls/XA7e/1791414210/Film-48596.m3u8"),
            urls.playableSourceUrls()
        )
    }

    @Test
    fun `empty string from an empty db column is not a source`() {
        // "".split(",") даёт listOf("") — раньше это считалось ссылкой
        assertTrue("".split(",").playableSourceUrls().isEmpty())
        assertEquals("", "".playableSourceUrl())
    }

    @Test
    fun `subdomains of dead hosts are dropped too`() {
        assertTrue("https://cdn.deliveryv.ws/a/b.mpd".isDeadSourceUrl())
        assertTrue("https://ab.takedwn.ws/a/master.m3u8".isDeadSourceUrl())
        assertFalse("https://deliveryvideo.ws/a.mpd".isDeadSourceUrl())
        assertFalse("".isDeadSourceUrl())
        assertFalse("not a url".isDeadSourceUrl())
    }

    @Test
    fun `cinemaUrlData keeps only live urls`() {
        val data = CinemaUrlData(
            hdUrl = AnwapUrl(
                poster = "p",
                urls = listOf("https://hye1eaipby4w.otsdkstub.ws/a/b.mpd")
            ),
            cinemaUrl = AnwapUrl(
                poster = "p",
                urls = listOf("", "https://o3.anwap.be/ohls/x/y.m3u8")
            )
        )

        val filtered = data.withPlayableSourceUrls()

        assertTrue(filtered.hdUrl?.urls.orEmpty().isEmpty())
        assertEquals("p", filtered.hdUrl?.poster)
        assertEquals(listOf("https://o3.anwap.be/ohls/x/y.m3u8"), filtered.cinemaUrl?.urls)
    }

    @Test
    fun `serial episode keeps live hls and drops dead dash`() {
        val seasons = listOf(
            SerialSeason(
                id = 1,
                episodes = listOf(
                    SerialEpisode(
                        episode = "1",
                        hls = "https://o1.anwap.be/e1.m3u8",
                        dash = "https://hye1eaipby4w.matham.ws/e1.mpd"
                    )
                )
            )
        )

        val episode = seasons.withPlayableSourceUrls().first().episodes.first()

        assertEquals("https://o1.anwap.be/e1.m3u8", episode.hls)
        assertEquals("", episode.dash)
    }
}
