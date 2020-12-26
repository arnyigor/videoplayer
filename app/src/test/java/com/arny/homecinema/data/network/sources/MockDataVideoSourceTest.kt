package com.arny.homecinema.data.network.sources

import com.arny.homecinema.data.network.hosts.IHostStore
import com.arny.homecinema.data.repository.sources.AssetsReader
import com.arny.homecinema.di.models.SerialData
import com.nhaarman.mockitokotlin2.mock
import org.junit.jupiter.api.Test

internal class MockDataVideoSourceTest {
    private val hosStore: IHostStore = mock()
    private val reader: AssetsReader = mock()
    private val source = MockDataVideoSource(hosStore, reader)

    companion object {
        val hlsList = this::class.java.classLoader.getResource("input_1.txt").readText()
    }

    @Test
    fun parsingSerialData() {
        val parsingSerialData = source.parsingSerialData(hlsList)
        checkTitle(parsingSerialData, 19, 0, "Тайны следствия (20 сезон) - 1 серия")
        checkTitle(parsingSerialData, 5, 2, "Тайны следствия (6 сезон) - 3 серия")
    }

    private fun checkTitle(
        parsingSerialData: SerialData,
        seasonIndex: Int,
        episodeIndex: Int,
        title: String
    ) {
        val season = parsingSerialData.seasons?.get(seasonIndex)
        val episode = season?.episodes?.get(episodeIndex)
        assert(episode?.title == title)
    }
}