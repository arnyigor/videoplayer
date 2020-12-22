package com.arny.homecinema.di.models

import okhttp3.ResponseBody
import retrofit2.http.*

interface VideoApiService {

    @FormUrlEncoded
    @POST
    suspend fun searchVideo(
        @Url url: String,
        @FieldMap fields: Map<String, String>,
        @HeaderMap headers: Map<String, String?>?
    ): ResponseBody

    @GET
    suspend fun requestMainPage(
        @Url url: String?,
        @HeaderMap headers: Map<String, String?>?
    ): ResponseBody

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