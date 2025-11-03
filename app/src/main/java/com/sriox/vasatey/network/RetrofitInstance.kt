package com.sriox.vasatey.network

import com.sriox.vasatey.Constants.FCM_BASE_URL
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitInstance {

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(FCM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val api: FcmApi by lazy {
        retrofit.create(FcmApi::class.java)
    }
}