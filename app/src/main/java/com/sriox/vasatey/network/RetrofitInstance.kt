package com.sriox.vasatey.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL = "https://vasatey-notify-msg.vercel.app/"

    // Create a client with a longer timeout to handle server cold starts
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Use the custom client
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: VercelApi by lazy {
        retrofit.create(VercelApi::class.java)
    }
}
