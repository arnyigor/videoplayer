package com.arny.mobilecinema.domain.interactors.jsoupupdate

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class JsoupParserHelperTest {

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
}
