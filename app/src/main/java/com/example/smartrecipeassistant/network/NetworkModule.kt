package com.example.smartrecipeassistant.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val unsplashApi: UnsplashApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.unsplash.com/")
            .client(sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UnsplashApi::class.java)
    }

    val huggingFaceApi: HuggingFaceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://router.huggingface.co/")
            .client(sharedClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HuggingFaceApi::class.java)
    }
}
