package com.arny.homecinema.data.network.docparser

import com.arny.homecinema.data.network.IHostStore

interface IDocumentParserFactory {
    fun createDocumentParser(hostStore: IHostStore): IDocumentParser
}