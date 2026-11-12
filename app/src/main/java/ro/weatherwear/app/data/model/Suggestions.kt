package ro.weatherwear.app.data.model

/** Who produced a suggestion. */
enum class SuggestionSource { AI, RULES }

/** What to wear today. */
data class OutfitSuggestion(
    val summary: String,
    val items: List<String>,
    val tip: String,
    val source: SuggestionSource,
    /** True when Gemini was configured but failed, so the rules answered instead. */
    val aiFailed: Boolean = false,
)

/** What to pack for a trip. */
data class PackingSuggestion(
    val summary: String,
    val items: List<PackingItem>,
    val tip: String,
    val source: SuggestionSource,
    val aiFailed: Boolean = false,
)

/** Why the user travels; changes what goes in the bag. */
enum class TripPurpose { LEISURE, CITY_BREAK, MOUNTAIN, BUSINESS }
