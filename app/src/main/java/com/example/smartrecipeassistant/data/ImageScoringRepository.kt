package com.example.smartrecipeassistant.data

import android.util.Base64
import com.example.smartrecipeassistant.network.ClipParameters
import com.example.smartrecipeassistant.network.ClipRequest
import com.example.smartrecipeassistant.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class ImageResult {
    data class Success(val url: String, val wasClipScored: Boolean) : ImageResult()
    data class Failure(val reason: String) : ImageResult()
}

sealed class SmartSearchState {
    object Idle : SmartSearchState()
    object Loading : SmartSearchState()
    data class Success(val imageUrl: String, val wasClipScored: Boolean) : SmartSearchState()
    data class Error(val message: String) : SmartSearchState()
}

object ImageScoringRepository {

    private const val CANDIDATES_TO_SCORE = 6

    suspend fun findBestImage(
        dishName: String,
        unsplashAccessKey: String,
        huggingFaceToken: String
    ): ImageResult = withContext(Dispatchers.IO) {
        if (unsplashAccessKey.isBlank()) {
            return@withContext ImageResult.Failure("No Unsplash key configured")
        }

        val searchResults = try {
            NetworkModule.unsplashApi.searchPhotos(
                query = dishName,
                perPage = 20,
                authorization = "Client-ID $unsplashAccessKey"
            ).results
        } catch (e: Exception) {
            return@withContext ImageResult.Failure("Unsplash search failed: ${e.message}")
        }

        if (searchResults.isEmpty()) {
            return@withContext ImageResult.Failure("No Unsplash results for '$dishName'")
        }

        val firstResultUrl = searchResults.first().urls.regular

        if (huggingFaceToken.isBlank()) {
            return@withContext ImageResult.Success(firstResultUrl, wasClipScored = false)
        }

        val prompt = "high-quality food photography of $dishName, clean background, natural lighting, appetizing presentation"
        val negativePrompt = "a blurry, unrelated, or unappetizing photo"

        var bestUrl: String? = null
        var bestScore = -1.0

        for (candidate in searchResults.take(CANDIDATES_TO_SCORE)) {
            val score = try {
                scoreImageAgainstPrompt(candidate.urls.small, prompt, negativePrompt, huggingFaceToken)
            } catch (e: Exception) {
                null
            }
            if (score != null && score > bestScore) {
                bestScore = score
                bestUrl = candidate.urls.regular
            }
        }

        return@withContext if (bestUrl != null) {
            ImageResult.Success(bestUrl, wasClipScored = true)
        } else {
            ImageResult.Success(firstResultUrl, wasClipScored = false)
        }
    }

    private suspend fun scoreImageAgainstPrompt(
        imageUrl: String,
        positivePrompt: String,
        negativePrompt: String,
        huggingFaceToken: String
    ): Double? {
        val imageBytes = downloadBytes(imageUrl) ?: return null
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

        val response = NetworkModule.huggingFaceApi.scoreImage(
            request = ClipRequest(
                inputs = base64Image,
                parameters = ClipParameters(candidate_labels = listOf(positivePrompt, negativePrompt))
            ),
            authorization = "Bearer $huggingFaceToken"
        )

        return response.find { it.label == positivePrompt }?.score
    }

    private fun downloadBytes(url: String): ByteArray? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }
}
