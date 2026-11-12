package ro.weatherwear.app.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import ro.weatherwear.app.data.model.CurrentWeather
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.OutfitSuggestion
import ro.weatherwear.app.data.model.PackingSuggestion
import ro.weatherwear.app.data.model.SuggestionSource
import ro.weatherwear.app.data.model.TripPurpose
import ro.weatherwear.app.data.remote.GeminiApi
import ro.weatherwear.app.data.remote.GeminiContent
import ro.weatherwear.app.data.remote.GeminiParser
import ro.weatherwear.app.data.remote.GeminiPart
import ro.weatherwear.app.data.remote.GeminiRequest
import ro.weatherwear.app.data.remote.GenerationConfig
import ro.weatherwear.app.data.remote.InvalidAiResponseException
import ro.weatherwear.app.domain.ClothingAdvisor
import ro.weatherwear.app.domain.PromptBuilder

/**
 * Clothing and packing suggestions. Asks Gemini first; when there is no key, no
 * connection, no quota left or an unusable answer, it falls back to
 * [ClothingAdvisor], so the user always gets an answer.
 */
class SuggestionRepository(
    private val api: GeminiApi,
    private val apiKey: String,
    private val model: String,
    private val advisor: ClothingAdvisor,
) {

    val isAiConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun outfitFor(place: String, day: DailyForecast, current: CurrentWeather?): OutfitSuggestion {
        val fallback = advisor.outfitFor(day, current)
        if (!isAiConfigured) return fallback
        return try {
            val answer = GeminiParser.parseOutfit(ask(PromptBuilder.outfitPrompt(place, day, current)))
            OutfitSuggestion(
                summary = answer.summary.ifBlank { fallback.summary },
                items = answer.items,
                tip = answer.tip.ifBlank { fallback.tip },
                source = SuggestionSource.AI,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini outfit failed, using rules", e)
            fallback.copy(aiFailed = true)
        }
    }

    suspend fun packingFor(
        destination: String,
        startDate: String,
        endDate: String,
        tripDays: Int,
        purpose: TripPurpose,
        forecast: List<DailyForecast>,
    ): PackingSuggestion {
        val fallback = advisor.packingFor(forecast, purpose, tripDays)
        if (!isAiConfigured) return fallback
        return try {
            val prompt = PromptBuilder.packingPrompt(destination, startDate, endDate, tripDays, purpose, forecast)
            val answer = GeminiParser.parsePacking(ask(prompt))
            PackingSuggestion(
                summary = answer.summary.ifBlank { fallback.summary },
                items = answer.items,
                tip = answer.tip.ifBlank { fallback.tip },
                source = SuggestionSource.AI,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini packing list failed, using rules", e)
            fallback.copy(aiFailed = true)
        }
    }

    private suspend fun ask(prompt: String): String {
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)), role = "user")),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.6),
        )
        val response = api.generateContent(model, apiKey, request)
        return GeminiParser.textOf(response) ?: throw InvalidAiResponseException("Gemini returned no text")
    }

    private companion object {
        const val TAG = "SuggestionRepository"
    }
}
