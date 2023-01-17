package com.arny.mobilecinema.data.repository.sources.jsoup

import com.arny.mobilecinema.data.api.JsoupService
import com.arny.mobilecinema.data.models.DataResult
import com.arny.mobilecinema.data.models.PageParserSelectors
import com.arny.mobilecinema.data.models.getDataResult
import com.arny.mobilecinema.data.utils.getDomainName
import com.arny.mobilecinema.di.models.Movie
import com.arny.mobilecinema.di.models.MovieType
import com.arny.mobilecinema.di.models.Video
import com.arny.mobilecinema.domain.repository.JsoupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class JsoupRepositoryImpl @Inject constructor(
    private val service: JsoupService
) : JsoupRepository {
    override suspend fun loadLink(path: String): DataResult<String> = getDataResult {
        getPageData(path).video?.videoUrl.orEmpty()
    }

    private suspend fun getPageData(url: String) = withContext(Dispatchers.IO) {
        val doc = service.loadPage(url)
        val location = doc.location()
        val selectors = Selectors.pageVideoSelectors
        val page = doc.select(selectors.pageSelector).firstOrNull()
        requireNotNull(page)
        val title = getTitle(page, selectors)
        val img = getImg(page, selectors, location)
        val info = getPageInfo(page, selectors)
        val fullDescr = page.select(selectors.fullDescSelector)?.firstOrNull()?.text().orEmpty()
        val videoUrl = getVideoUrl(page, selectors)
        Movie(
            uuid = UUID.randomUUID().toString(),
            title = title,
            type = MovieType.CINEMA,
            detailUrl = location,
            img = img,
            video = Video(
                id = 0,
                title = title,
                videoUrl = videoUrl,
            ),
            baseUrl = getDomainName(location),
            info = info,
            fullDescr = fullDescr,
        )
    }
}