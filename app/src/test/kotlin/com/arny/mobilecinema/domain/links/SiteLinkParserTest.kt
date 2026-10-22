package com.arny.mobilecinema.domain.links

import com.arny.mobilecinema.domain.models.MovieType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Проверки ссылки до любого действия: только http(s), только домены сайта, только карточки. */
class SiteLinkParserTest {

    private val baseUrl = "https://my.anwap.love"

    private fun reasonOf(raw: String): SiteLinkResult.Reason? =
        (SiteLinkParser.parse(raw, baseUrl) as? SiteLinkResult.Invalid)?.reason

    @Test
    fun `parses film and serial cards`() {
        val film = SiteLinkParser.parse("https://my.anwap.love/films/1091", baseUrl) as SiteLinkResult.Valid
        assertEquals(1091L, film.link.movieId)
        assertEquals(MovieType.CINEMA, film.link.kind)

        val serial = SiteLinkParser.parse("http://mm.anwap.media/serials/208/", baseUrl) as SiteLinkResult.Valid
        assertEquals(208L, serial.link.movieId)
        assertEquals(MovieType.SERIAL, serial.link.kind)
        // Ссылка нормализуется: без query, fragment и завершающего слэша.
        assertEquals("https://mm.anwap.media/serials/208", serial.link.url)
    }

    @Test
    fun `rejects non http schemes`() {
        assertEquals(SiteLinkResult.Reason.NOT_HTTP, reasonOf("file:///etc/passwd"))
        assertEquals(SiteLinkResult.Reason.NOT_HTTP, reasonOf("javascript:alert(1)"))
        assertEquals(SiteLinkResult.Reason.NOT_HTTP, reasonOf("intent://films/1091"))
        assertEquals(SiteLinkResult.Reason.NOT_HTTP, reasonOf("content://media/1"))
    }

    @Test
    fun `rejects local and private hosts`() {
        assertEquals(SiteLinkResult.Reason.UNSAFE_HOST, reasonOf("http://localhost/films/1"))
        assertEquals(SiteLinkResult.Reason.UNSAFE_HOST, reasonOf("http://127.0.0.1/films/1"))
        assertEquals(SiteLinkResult.Reason.UNSAFE_HOST, reasonOf("http://192.168.0.5/films/1"))
        assertEquals(SiteLinkResult.Reason.UNSAFE_HOST, reasonOf("http://10.1.2.3/films/1"))
        assertEquals(SiteLinkResult.Reason.UNSAFE_HOST, reasonOf("http://172.20.0.1/films/1"))
        assertEquals(SiteLinkResult.Reason.UNSAFE_HOST, reasonOf("http://router.local/films/1"))
    }

    @Test
    fun `rejects foreign hosts`() {
        assertEquals(SiteLinkResult.Reason.FOREIGN_HOST, reasonOf("https://example.com/films/1091"))
        assertEquals(
            SiteLinkResult.Reason.FOREIGN_HOST,
            reasonOf("https://anwap.evil.example/films/1091")
        )
    }

    @Test
    fun `rejects paths that are not title cards`() {
        assertEquals(SiteLinkResult.Reason.UNKNOWN_PATH, reasonOf("https://my.anwap.love/"))
        assertEquals(SiteLinkResult.Reason.UNKNOWN_PATH, reasonOf("https://my.anwap.love/films/search/"))
        assertEquals(SiteLinkResult.Reason.UNKNOWN_PATH, reasonOf("https://my.anwap.love/films/abc"))
        assertEquals(SiteLinkResult.Reason.UNKNOWN_PATH, reasonOf("https://my.anwap.love/films/0"))
    }

    @Test
    fun `empty input is reported separately from wrong input`() {
        assertEquals(SiteLinkResult.Reason.EMPTY, reasonOf("   "))
        assertTrue(SiteLinkParser.parse("https://my.anwap.love/films/1", baseUrl) is SiteLinkResult.Valid)
    }
}
