package com.arny.videoplayer.data.network

import okhttp3.ResponseBody
import retrofit2.http.*

interface VideoApiService {

    @FormUrlEncoded
    @POST("/index.php?do=search")
    suspend fun searchVideo(@Field("story") story: String): ResponseBody

    @GET("/index.php")
    suspend fun requestMainpage(): ResponseBody

    @Headers(
        "Referer: https://my.lordfilm.so/index.php",
    )
    @GET
    suspend fun getVideoDetails(@Url url: String?): ResponseBody

    @Headers(
        "Referer: https://my.lordfilm.so/index.php",
    )
    @GET
    suspend fun getIframeData(@Url url: String?): ResponseBody

}