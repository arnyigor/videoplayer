package com.arny.mobilecinema.data.models

data class PageParserSelectors(
    val pageSelector: String = "",
    val titleSelector: String = "",
    val imgSelector: String = "",
    val imgSelectorAttr: String = "",
    val infoSelector: String = "",
    val fullDescSelector: String = "",
    val scriptSelector: String = "",
    val scriptDataSelectorRegexp: String = "",
    val scriptDataSelectorRegexpGroup: Int = 0,
)