package com.arny.homecinema.di.models

import okhttp3.ResponseBody
import retrofit2.http.*

interface VideoApiService {

    @FormUrlEncoded
    @POST("/index.php")
    suspend fun searchVideo(
        @Field("do") doAction: String = "search",
        @Field("subaction") subaction: String = "search",
        @Field("search_start") search_start: String = "0",
        @Field("full_search") full_search: String = "0",
        @Field("result_from") result_from: String = "1",
        @Field("story") story: String,
    ): ResponseBody

    @GET
    suspend fun requestMainPage(@Url url: String?,@HeaderMap headers: Map<String, String?>?): ResponseBody

    @GET
    suspend fun requestTyped(@Url url: String?): ResponseBody

    @GET
    suspend fun getVideoDetails(
        @Url url: String?,
        @HeaderMap headers: Map<String, String>
    ): ResponseBody

    @GET
    suspend fun getUrlData(
        @Url url: String?,
        @HeaderMap headers: Map<String, String>
    ): ResponseBody

}