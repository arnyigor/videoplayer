package com.arny.homecinema.data.network.docparser

import com.arny.homecinema.data.network.HostStore
import com.arny.homecinema.data.network.IHostStore
import javax.inject.Inject

class DocumentParserFactory @Inject constructor() : IDocumentParserFactory {
    override fun createDocumentParser(hostStore: IHostStore): IDocumentParser {
        return when (hostStore.host) {
            HostStore.LORDFILM_AL_HOST -> AlLordFilmDocumentParser()
            HostStore.LORDFILM_20_ZONE_HOST -> LordFilm14ZoneDocumentParser(hostStore.host)
            HostStore.LORDFILM_19DEC_HOST -> LordFilm19DecDocumentParser(hostStore.host)
            else -> AlLordFilmDocumentParser()
        }
    }
}