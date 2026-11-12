package ro.weatherwear.app.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import ro.weatherwear.app.data.model.PackingItem
import ro.weatherwear.app.domain.PackingCategory

/**
 * Turns Gemini's JSON text into app models, and rejects answers that are
 * unusable (empty lists, wrong shape) so the caller can fall back to the rules.
 */
object GeminiParser {

    private val gson = Gson()

    private data class OutfitJson(val summary: String?, val items: List<String?>?, val tip: String?)
    private data class PackingItemJson(val name: String?, val category: String?)
    private data class PackingJson(val summary: String?, val items: List<PackingItemJson?>?, val tip: String?)

    data class Outfit(val summary: String, val items: List<String>, val tip: String)
    data class Packing(val summary: String, val items: List<PackingItem>, val tip: String)

    /** The model sometimes wraps JSON in ```json fences even when asked not to. */
    private fun stripFences(text: String): String = text.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()

    fun parseOutfit(text: String): Outfit {
        val json = try {
            gson.fromJson(stripFences(text), OutfitJson::class.java)
        } catch (e: JsonParseException) {
            throw InvalidAiResponseException("Outfit JSON could not be parsed", e)
        } ?: throw InvalidAiResponseException("Empty outfit answer")

        val items = json.items.orEmpty().mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        if (items.isEmpty()) throw InvalidAiResponseException("Outfit answer has no items")
        return Outfit(json.summary.orEmpty().trim(), items, json.tip.orEmpty().trim())
    }

    fun parsePacking(text: String): Packing {
        val json = try {
            gson.fromJson(stripFences(text), PackingJson::class.java)
        } catch (e: JsonParseException) {
            throw InvalidAiResponseException("Packing JSON could not be parsed", e)
        } ?: throw InvalidAiResponseException("Empty packing answer")

        val items = json.items.orEmpty().mapNotNull { item ->
            val name = item?.name?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            // Unknown categories go under accessories instead of being dropped.
            val category = item.category?.trim()?.takeIf { it in PackingCategory.ALL } ?: PackingCategory.ACCESSORIES
            PackingItem(name, category)
        }.distinctBy { it.name.lowercase() }
        if (items.isEmpty()) throw InvalidAiResponseException("Packing answer has no items")
        return Packing(json.summary.orEmpty().trim(), items, json.tip.orEmpty().trim())
    }

    /** First text part of the first candidate, or null when the model sent nothing back. */
    fun textOf(response: GeminiResponse): String? =
        response.candidates?.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.text }
}

class InvalidAiResponseException(message: String, cause: Throwable? = null) : Exception(message, cause)
