package ro.weatherwear.app.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Google Gemini REST API (free tier key from https://aistudio.google.com/apikey).
 * The key goes in a header rather than the URL so it never shows up in logs of the URL.
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest,
    ): GeminiResponse

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
    }
}

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig,
)

data class GeminiContent(
    val parts: List<GeminiPart>?,
    val role: String? = null,
)

data class GeminiPart(val text: String?)

data class GenerationConfig(
    /** "application/json" makes Gemini answer with JSON only. */
    val responseMimeType: String,
    val temperature: Double,
)

data class GeminiResponse(val candidates: List<GeminiCandidate>?)

data class GeminiCandidate(val content: GeminiContent?)
