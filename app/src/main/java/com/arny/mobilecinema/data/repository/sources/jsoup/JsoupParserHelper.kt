package com.arny.mobilecinema.data.repository.sources.jsoup

import com.arny.mobilecinema.data.models.AnwapMovieData
import com.arny.mobilecinema.data.models.PageParserSelectors
import com.arny.mobilecinema.data.utils.cleanAnwapEncryptedData
import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.data.utils.getDomainName
import com.arny.mobilecinema.data.utils.getEncryptedData
import org.jsoup.nodes.Element

fun getPageInfo(
    page: Element?,
    selectors: PageParserSelectors
): String {
    val infoList = page?.select(selectors.infoSelector)
        ?.map { it.wholeText() }
        .orEmpty()
    val toString = StringBuilder().apply {
        for ((ind, info) in infoList.withIndex()) {
            if (ind != 0) {
                append("\n")
            }
            append(info)
        }
    }.toString()
    return toString
}

fun getTitle(
    page: Element?,
    selectors: PageParserSelectors
): String = page?.select(selectors.titleSelector)?.firstOrNull()?.text().orEmpty()

fun getImg(
    page: Element?,
    selectors: PageParserSelectors,
    location: String
): String {
    var img = page?.select(selectors.imgSelector)
        ?.firstOrNull()
        ?.attr(selectors.imgSelectorAttr).orEmpty()
    val domainName = getDomainName(location)
    if (!img.startsWith("http")) {
        img = "${domainName}$img"
    }
    return img
}

fun getVideoUrl(
    page: Element?,
    selectors: PageParserSelectors
): String {
    val scriptData = page?.selectFirst(selectors.scriptSelector)?.data().orEmpty().trimIndent()
    val regex = selectors.scriptDataSelectorRegexp.toRegex()
    val matchResult = regex.find(scriptData)
    val values = matchResult?.groupValues
    val ecryptedScriptData = values?.getOrNull(selectors.scriptDataSelectorRegexpGroup).orEmpty()
    val anwapData = cleanAnwapEncryptedData(ecryptedScriptData)
    val encryptedData = getEncryptedData(anwapData)
    val data = encryptedData.fromJson(AnwapMovieData::class.java)
    return data?.fileUrl.orEmpty()
}