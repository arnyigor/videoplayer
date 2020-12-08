package com.arny.videoplayer.data.network

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST

interface VideoApiService {

    @Multipart
    @POST("/index.php?do=search")
    suspend fun searchVideo(@Body body: RequestBody): String

}