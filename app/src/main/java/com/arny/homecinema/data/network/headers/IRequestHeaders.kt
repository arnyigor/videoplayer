package com.arny.homecinema.data.network.headers

interface IRequestHeaders {
    val iFrameHeaders: Map<String, String>
    val detailHeaders: Map<String, String>
}