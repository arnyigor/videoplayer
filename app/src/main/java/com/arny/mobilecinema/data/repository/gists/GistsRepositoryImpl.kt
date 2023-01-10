package com.arny.mobilecinema.data.repository.gists

import com.arny.mobilecinema.data.api.VideoApiService
import com.arny.mobilecinema.data.models.doAsync
import com.arny.mobilecinema.data.network.response.ResponseBodyConverter
import com.arny.mobilecinema.data.network.responses.MockData
import com.arny.mobilecinema.data.network.responses.MoviesData
import com.arny.mobilecinema.data.network.responses.WrapGist
import com.arny.mobilecinema.data.utils.fromJson
import com.arny.mobilecinema.domain.repository.GistsRepository
import javax.inject.Inject

class GistsRepositoryImpl @Inject constructor(
    private val videoApiService: VideoApiService,
    private val responseBodyConverter: ResponseBodyConverter,
) : GistsRepository {
    private companion object {
        const val WRAP_API_KEY = "1lrbRPMUkiVNxlT8xAGShXB1sq1cSp3x"
    }

    private var data: MoviesData? = null

    override fun loadData() = doAsync {
        val gist = videoApiService.postRequest(
            url = "https://wrapapi.com/use/Arny/github/gist/latest",
            fields = mapOf("wrapAPIKey" to WRAP_API_KEY),
        )
        val document = responseBodyConverter.convert(gist)
        val wrapGist = document?.body()?.text().fromJson(WrapGist::class.java)
        val fileLink = wrapGist?.wrapGistData?.link
        val file = videoApiService.getRequest(
            url = "https://gist.github.com/${fileLink}",
        )
        val fileDoc = responseBodyConverter.convert(file, simpleText = true)
        data = fileDoc?.select("script")?.dataNodes()?.getOrNull(0)
            ?.fromJson(MoviesData::class.java)
        data
    }

    override fun getMockData(): List<MockData?> = data?.mock.orEmpty()
}