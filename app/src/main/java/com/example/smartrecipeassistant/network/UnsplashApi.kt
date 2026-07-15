package com.example.smartrecipeassistant.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class UnsplashSearchResponse(
    val results: List<UnsplashPhoto>
)

data class UnsplashPhoto(
    val id: String,
    val urls: UnsplashPhotoUrls
)

data class UnsplashPhotoUrls(
    val regular: String,
    val small: String
)

interface UnsplashApi {
    @GET("search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String,
        @Query("per_page") perPage: Int,
        @Header("Authorization") authorization: String
    ): UnsplashSearchResponse
}
