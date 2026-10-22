package com.arny.mobilecinema.domain.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayableIdTest {

    @Test
    fun `film id roundtrip`() {
        val id = PlayableId.Film(42L)
        assertEquals("film:42", id.value)
        assertEquals(id, PlayableId.parse(id.value))
    }

    @Test
    fun `episode id roundtrip with catalog keys`() {
        val id = PlayableId.Episode(movieDbId = 7L, seasonKey = "2", episodeKey = "15")
        assertEquals("ep:7:2:15", id.value)
        assertEquals(id, PlayableId.parse(id.value))
    }

    @Test
    fun `fallback index keys survive roundtrip`() {
        val id = PlayableId.Episode(movieDbId = 7L, seasonKey = "i0", episodeKey = "i3")
        assertEquals(id, PlayableId.parse(id.value))
    }

    @Test
    fun `invalid ids are rejected`() {
        assertNull(PlayableId.parse(""))
        assertNull(PlayableId.parse("film:"))
        assertNull(PlayableId.parse("ep:1:2"))
        assertNull(PlayableId.parse("legacy:1:2:3"))
        assertTrue(PlayableId.parse("film:abc") == null)
    }
}
