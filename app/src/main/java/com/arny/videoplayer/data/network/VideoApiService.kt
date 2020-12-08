package com.arny.videoplayer.data.network

import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface VideoApiService {

    @FormUrlEncoded
    @POST("/index.php?do=search")
    suspend fun searchVideo(@Field("story") story: String): ResponseBody


    @GET("/index.php")
    suspend fun requestMainpage(): ResponseBody

}