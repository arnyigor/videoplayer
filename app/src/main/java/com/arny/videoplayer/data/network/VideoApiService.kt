package com.arny.videoplayer.data.network

import okhttp3.ResponseBody
import retrofit2.http.*

interface VideoApiService {

    @FormUrlEncoded
    @POST("/index.php")
    suspend fun searchVideo(
        @Field("do") doAction: String,
        @Field("subaction") subaction: String,
        @Field("search_start") search_start: String,
        @Field("full_search") full_search: String,
        @Field("result_from") result_from: String,
        @Field("story") story: String,
        @HeaderMap headers: Map<String, String>
    ): ResponseBody

    @GET
    suspend fun requestMainpage(@Url url: String?): ResponseBody

    @GET
    suspend fun getVideoDetails(@Url url: String?, @Header("Referer") referer: String): ResponseBody

    @GET
    suspend fun getIframeData(@Url url: String?, @Header("Referer") referer: String): ResponseBody

}