package ro.weatherwear.app.data.model

import java.time.LocalDate

/** A place returned by the geocoding service. */
data class City(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val region: String? = null,
) {
    /** "Brașov, România" — what we show to the user and save with a trip. */
    val displayName: String
        get() = listOfNotNull(name, country).joinToString(", ")

    /** Longer label used when several cities share a name ("Brașov, Brașov, România"). */
    val detailedName: String
        get() = listOfNotNull(name, region, country).distinct().joinToString(", ")
}

/** Weather right now. Temperatures are always in °C; the UI converts for display. */
data class CurrentWeather(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val weatherCode: Int,
    val isDay: Boolean,
)

/** One day of forecast. */
data class DailyForecast(
    val date: LocalDate,
    val weatherCode: Int,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationProbability: Int,
    val precipitationSum: Double,
    val windMax: Double,
    val uvMax: Double,
)

data class Forecast(
    val current: CurrentWeather?,
    val days: List<DailyForecast>,
)
