package com.example.smartrecipeassistant.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ClipParameters(
    val candidate_labels: List<String>
)

data class ClipRequest(
    val inputs: String,
    val parameters: ClipParameters
)

data class ClipLabelScore(
    val label: String,
    val score: Double
)

interface HuggingFaceApi {
    @POST("hf-inference/models/openai/clip-vit-base-patch32")
    suspend fun scoreImage(
        @Body request: ClipRequest,
        @Header("Authorization") authorization: String
    ): List<ClipLabelScore>
}
