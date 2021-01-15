package com.arny.mobilecinema.data.network.sources

import com.arny.mobilecinema.data.network.hosts.IHostStore
import com.nhaarman.mockitokotlin2.mock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BaseVideoSourceTest {

    private val hostStore: IHostStore = mock()
    lateinit var source: BaseVideoSource

    @BeforeEach
    fun init() {
        source = BaseVideoSource(hostStore)
    }

    @Test
    fun replaceIFragmeUrl() {
        val correctedIFragmeUrl = BaseVideoSource(hostStore).correctedIFragmeUrl(
            "https://api1597064783.multikland.net/embed/movie/43633",
            "api.multikland.net",
            "onlinevkino.com"
        )
        assert(correctedIFragmeUrl == "https://api.multikland.net/embed/movie/43633?host=onlinevkino.com")
    }

    @Test
    fun replaceIFragmeUrl2() {
        val correctedIFragmeUrl = source.correctedIFragmeUrl(
            "https://api1597064783.multikland.net/embed/movie/43633",
            "api.multikland.net",
        )
        assert(correctedIFragmeUrl == "https://api.multikland.net/embed/movie/43633")
    }

    @Test
    fun replaceIFragmeUrl3() {
        val correctedIFragmeUrl = source.correctedIFragmeUrl(
            "https://api1597064783.multikland.net/embed/movie/43633",
            baseHost = "api.multikland.net"
        )
        assert(correctedIFragmeUrl == "https://api1597064783.multikland.net/embed/movie/43633?host=api.multikland.net")
    }

    @Test
    fun replaceIFragmeUrl4() {
        val correctedIFragmeUrl = source.correctedIFragmeUrl(
            null,
            baseHost = "api.multikland.net"
        )
        assert(correctedIFragmeUrl.isBlank())
    }
}