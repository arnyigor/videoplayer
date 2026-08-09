package com.arny.mobilecinema.domain.interactors.jsoupupdate

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JsoupParserHelperTest {

    @Test
    fun `getComments parses author date and text`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <div class="blm">
                        <div class="comm_title" id="comm2846970">
                            <a href="/user/945409"><img src="/style/images/man_off.gif" alt="" />Vasiliy0204</a>
                            (2026.08.02 18:41) <a href="/films/comm-add/48537/ans-2846970" class="comm_link">Отв.</a>
                            <div class="clear"></div>
                        </div>
                        <div class="comm_main">Классный фильм всем советую ))</div>
                    </div>
                </body>
            </html>
            """.trimIndent(),
            "https://my.anwap.love/films/comm/48537"
        )

        val comments = getComments(doc.body())

        assertEquals(1, comments.size)
        assertEquals("2846970", comments[0].id)
        assertEquals("Vasiliy0204", comments[0].author)
        assertEquals("/user/945409", comments[0].authorUrl)
        assertEquals("2026.08.02 18:41", comments[0].dateText)
        assertEquals("Классный фильм всем советую ))", comments[0].text)
    }

    @Test
    fun `getCommentsPagesCount returns last comments page`() {
        val doc = Jsoup.parse(
            """
            <div class="pages">
                <span>Страницы:</span> <span>1</span>
                <a href="/films/comm/22203/2"><strong>2</strong></a>
                <a href="/films/comm/22203/3"><strong>3</strong></a>
                ...
                <a href="/films/comm/22203/83"><strong>83</strong></a>
            </div>
            """.trimIndent()
        )

        assertEquals(83, getCommentsPagesCount(doc.body()))
    }

    @Test
    fun `getVenomEmbedUrl returns protocol-relative ortified iframe as https url`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <iframe src="//api.ortified.ws/embed/movie/37033"></iframe>
                </body>
            </html>
            """.trimIndent(),
            "https://my.anwap.love/films/37033"
        )

        assertEquals(
            "https://api.ortified.ws/embed/movie/37033",
            getVenomEmbedUrl(doc.body())
        )
    }

    @Test
    fun `getVenomEmbedUrl builds movie embed url from franchise id`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>window.player = { franchiseID: 37033 };</script>
                </body>
            </html>
            """.trimIndent(),
            "https://my.anwap.love/films/37033"
        )

        assertEquals(
            "https://api.ortified.ws/embed/movie/37033",
            getVenomEmbedUrl(doc.body())
        )
    }

    @Test
    fun `getVenomEmbedUrl builds serial movie embed url with season and episode from franchise id`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>window.player = { franchiseID: 85586 };</script>
                </body>
            </html>
            """.trimIndent(),
            "https://my.anwap.love/serials/down/94510"
        )

        assertEquals(
            "https://api.ortified.ws/embed/movie/85586?season=1&episode=1",
            getVenomEmbedUrl(doc.body())
        )
    }

    @Test
    fun `getVenomCinemaUrlData skips makePlayer function declaration and extracts invocation config`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>
                        function makePlayer(opts) {
                            return opts;
                        }
                        makePlayer({
                            source: {
                                hls: "https://cdn.example/hd-master.m3u8"
                            }
                        });
                    </script>
                </body>
            </html>
            """.trimIndent(),
            "https://api.ortified.ws/embed/movie/37033"
        )

        val data = getVenomCinemaUrlData(doc)

        assertEquals(listOf("https://cdn.example/hd-master.m3u8"), data.cinemaUrl?.urls)
    }

    @Test
    fun `getVenomCinemaUrlData appends Venom access token to source urls`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>
                        var lol = ', abcd1234="fake"';
                        var lok = 1, abcd1234 = "realToken";
                        function makePlayer(opts) {
                            var so = opts.source;
                            function add(o,k){ if(o[k]) o[k] += '&' + abcd1234; }
                        }
                        makePlayer({
                            source: {
                                dash: "https://cdn.example/video.mpd?x=1",
                                dasha: "https://cdn.example/hd.mpd?x=1",
                                hls: "https://cdn.example/master.m3u8?x=1"
                            }
                        });
                    </script>
                </body>
            </html>
            """.trimIndent(),
            "https://api.ortified.ws/embed/movie/37033"
        )

        val data = getVenomCinemaUrlData(doc)

        assertEquals(
            listOf(
                "https://cdn.example/video.mpd?x=1&realToken",
                "https://cdn.example/master.m3u8?x=1&realToken"
            ),
            data.cinemaUrl?.urls
        )
        assertEquals(listOf("https://cdn.example/hd.mpd?x=1&realToken"), data.hdUrl?.urls)
    }

    @Test
    fun `getVenomCinemaUrlData extracts dash hls and dasha from makePlayer config`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>
                        makePlayer({
                            source: {
                                dash: "https:\/\/cdn.example\/video.mpd",
                                hls: 'https://cdn.example/master.m3u8',
                                dasha: "https://cdn.example/hd.mpd"
                            },
                            title: "value with {braces}"
                        });
                    </script>
                </body>
            </html>
            """.trimIndent(),
            "https://api.ortified.ws/embed/movie/37033"
        )

        val data = getVenomCinemaUrlData(doc)

        assertNotNull(data.cinemaUrl)
        assertEquals(
            listOf(
                "https://cdn.example/video.mpd",
                "https://cdn.example/master.m3u8"
            ),
            data.cinemaUrl?.urls
        )
        assertEquals(listOf("https://cdn.example/hd.mpd"), data.hdUrl?.urls)
    }

    @Test
    fun `getVenomCinemaUrlData selects requested season and episode from seasons structure`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>
                        makePlayer({
                            "seasons": [
                                {
                                    "season": 1,
                                    "episodes": [
                                        { "episode": "1", "dash": "https://cdn.example/s1e1.mpd", "dasha": "https://cdn.example/s1e1-hd.mpd", "hls": "https://cdn.example/s1e1.m3u8" },
                                        { "episode": "2", "dash": "https://cdn.example/s1e2.mpd", "hls": "https://cdn.example/s1e2.m3u8" }
                                    ]
                                },
                                {
                                    "season": 2,
                                    "episodes": [
                                        { "episode": "1", "dash": "https://cdn.example/s2e1.mpd", "dasha": "https://cdn.example/s2e1-hd.mpd", "hls": "https://cdn.example/s2e1.m3u8" },
                                        { "episode": "2", "dash": "https://cdn.example/s2e2.mpd", "hls": "https://cdn.example/s2e2.m3u8" }
                                    ]
                                }
                            ]
                        });
                    </script>
                </body>
            </html>
            """.trimIndent(),
            "https://api.ortified.ws/embed/movie/57095?season=1&episode=1"
        )

        // Сезон 1, эпизод 1 — даже если в JSON сезон 2 идёт первым
        val season1 = getVenomCinemaUrlData(doc, seasonId = 1, episodeId = 1)
        assertEquals(
            listOf("https://cdn.example/s1e1.mpd", "https://cdn.example/s1e1.m3u8"),
            season1.cinemaUrl?.urls
        )
        assertEquals(listOf("https://cdn.example/s1e1-hd.mpd"), season1.hdUrl?.urls)

        // Сезон 2, эпизод 2
        val season2ep2 = getVenomCinemaUrlData(doc, seasonId = 2, episodeId = 2)
        assertEquals(
            listOf("https://cdn.example/s2e2.mpd", "https://cdn.example/s2e2.m3u8"),
            season2ep2.cinemaUrl?.urls
        )
        assertEquals(null, season2ep2.hdUrl?.urls)
    }

    @Test
    fun `getVenomCinemaUrlData falls back to first season when season missing`() {
        val doc = Jsoup.parse(
            """
            <html>
                <body>
                    <script>
                        makePlayer({
                            "seasons": [
                                { "season": 2, "episodes": [ { "episode": "1", "dash": "https://cdn.example/s2e1.mpd", "hls": "https://cdn.example/s2e1.m3u8" } ] },
                                { "season": 1, "episodes": [ { "episode": "1", "dash": "https://cdn.example/s1e1.mpd", "hls": "https://cdn.example/s1e1.m3u8" } ] }
                            ]
                        });
                    </script>
                </body>
            </html>
            """.trimIndent(),
            "https://api.ortified.ws/embed/movie/57095"
        )

        // Сезон 999 отсутствует — берётся первый сезон из JSON (2), чтобы не потерять данные
        val fallback = getVenomCinemaUrlData(doc, seasonId = 999, episodeId = 1)
        assertEquals(
            listOf("https://cdn.example/s2e1.mpd", "https://cdn.example/s2e1.m3u8"),
            fallback.cinemaUrl?.urls
        )
    }
}
