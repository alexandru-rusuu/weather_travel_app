package ro.weatherwear.app.domain

import ro.weatherwear.app.data.model.CurrentWeather
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.TripPurpose
import java.util.Locale

/**
 * Builds the prompts sent to Gemini. The model is asked for strict JSON so the
 * app can show the answer as chips and checklists instead of free text.
 */
object PromptBuilder {

    private fun Double.fmt(): String = String.format(Locale.US, "%.0f", this)

    private fun describe(day: DailyForecast): String =
        "${day.date}: min ${day.tempMin.fmt()}°C, max ${day.tempMax.fmt()}°C, " +
            "probabilitate precipitații ${day.precipitationProbability}%, " +
            "vânt maxim ${day.windMax.fmt()} km/h, indice UV ${day.uvMax.fmt()}, " +
            "cod meteo WMO ${day.weatherCode}" +
            (if (WeatherConditions.isSnow(day.weatherCode)) " (ninsoare)" else "") +
            (if (WeatherConditions.isRain(day.weatherCode)) " (ploaie)" else "")

    fun outfitPrompt(place: String, today: DailyForecast, current: CurrentWeather?): String = buildString {
        appendLine("Ești un asistent practic care spune ce haine să porți în funcție de vreme.")
        appendLine("Locul: $place.")
        if (current != null) {
            appendLine(
                "Acum: ${current.temperature.fmt()}°C, resimțită ${current.feelsLike.fmt()}°C, " +
                    "umiditate ${current.humidity}%, vânt ${current.windSpeed.fmt()} km/h."
            )
        }
        appendLine("Ziua de azi: ${describe(today)}.")
        appendLine("Recomandă o ținută pentru toată ziua, în limba română.")
        appendLine("Răspunde DOAR cu JSON de forma:")
        appendLine("""{"summary": "o propoziție despre vreme și ținută", "items": ["articol", "..."], "tip": "un sfat scurt"}""")
        append("Între 4 și 8 articole, fiecare de cel mult 5 cuvinte.")
    }

    fun packingPrompt(
        destination: String,
        startDate: String,
        endDate: String,
        tripDays: Int,
        purpose: TripPurpose,
        forecast: List<DailyForecast>,
    ): String = buildString {
        appendLine("Ești un asistent care face liste de bagaj în funcție de vreme.")
        appendLine("Călătorie în $destination, între $startDate și $endDate ($tripDays zile).")
        appendLine(
            "Scopul: " + when (purpose) {
                TripPurpose.LEISURE -> "vacanță de relaxare"
                TripPurpose.CITY_BREAK -> "city break, multe plimbări prin oraș"
                TripPurpose.MOUNTAIN -> "munte și drumeții"
                TripPurpose.BUSINESS -> "călătorie de afaceri"
            } + "."
        )
        if (forecast.isEmpty()) {
            appendLine("Prognoza nu e încă disponibilă: folosește clima obișnuită a locului în acea perioadă.")
        } else {
            appendLine("Prognoza pe zile:")
            forecast.forEach { appendLine("- ${describe(it)}") }
        }
        appendLine("Fă lista de bagaj în limba română.")
        appendLine("Răspunde DOAR cu JSON de forma:")
        appendLine(
            """{"summary": "o propoziție despre vremea din călătorie", "tip": "un sfat scurt", """ +
                """"items": [{"name": "3 tricouri", "category": "Haine"}]}"""
        )
        appendLine("Categorii permise: ${PackingCategory.ALL.joinToString(", ")}.")
        append("Între 12 și 22 de articole; pune cantitatea în nume când e mai mult de unul.")
    }
}
