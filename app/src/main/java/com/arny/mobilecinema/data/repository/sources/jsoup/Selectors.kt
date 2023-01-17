package com.arny.mobilecinema.data.repository.sources.jsoup

import com.arny.mobilecinema.data.models.PageParserSelectors

object Selectors {
      val pageVideoSelectors = PageParserSelectors(
        pageSelector = "body",
        titleSelector = "h1",
        imgSelector = ".screen img",
        imgSelectorAttr = "src",
        infoSelector = ".screen2 table tr",
        fullDescSelector = ".filmopis screen3>p",
        scriptSelector = "script:containsData(playerjs)",
        scriptDataSelectorRegexp = "Playerjs\\(\"(.+)\"\\);",
        scriptDataSelectorRegexpGroup = 1
    )
}