package ro.weatherwear.app.data.repository

import ro.weatherwear.app.data.model.City
import ro.weatherwear.app.data.model.CurrentWeather
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.Forecast
import ro.weatherwear.app.data.remote.CurrentDto
import ro.weatherwear.app.data.remote.DailyDto
import ro.weatherwear.app.data.remote.GeocodingApi
import ro.weatherwear.app.data.remote.OpenMeteoApi
import java.time.LocalDate

/** Weather and city search, backed by Open-Meteo. */
class WeatherRepository(
    private val forecastApi: OpenMeteoApi,
    private val geocodingApi: GeocodingApi,
) {

    suspend fun searchCities(query: String): List<City> =
        geocodingApi.search(query.trim()).results.orEmpty().mapNotNull { dto ->
            val name = dto.name ?: return@mapNotNull null
            val lat = dto.latitude ?: return@mapNotNull null
            val lon = dto.longitude ?: return@mapNotNull null
            City(name, lat, lon, dto.country, dto.admin1)
        }

    /** Current weather plus the next [days] days. */
    suspend fun forecast(latitude: Double, longitude: Double, days: Int = 7): Forecast {
        val response = forecastApi.forecast(latitude, longitude, forecastDays = days)
        return Forecast(response.current?.toDomain(), response.daily.toDomain())
    }

    /**
     * Daily forecast between two dates. The service only forecasts
     * [MAX_FORECAST_DAYS] days ahead, so the range is cut there; a trip that
     * starts later gets an empty list.
     */
    suspend fun forecastForDates(
        latitude: Double,
        longitude: Double,
        start: LocalDate,
        end: LocalDate,
        today: LocalDate = LocalDate.now(),
    ): List<DailyForecast> {
        val lastAvailable = today.plusDays(MAX_FORECAST_DAYS - 1L)
        // Open-Meteo also serves the recent past, so a trip under way still shows its first days.
        val from = maxOf(start, today.minusDays(PAST_DAYS_AVAILABLE))
        val to = minOf(end, lastAvailable)
        if (from.isAfter(to)) return emptyList()
        val response = forecastApi.forecast(
            latitude, longitude,
            current = null,
            startDate = from.toString(),
            endDate = to.toString(),
        )
        return response.daily.toDomain()
    }

    private fun CurrentDto.toDomain(): CurrentWeather? {
        val temp = temperature ?: return null
        return CurrentWeather(
            temperature = temp,
            feelsLike = apparentTemperature ?: temp,
            humidity = humidity ?: 0,
            windSpeed = windSpeed ?: 0.0,
            weatherCode = weatherCode ?: 0,
            isDay = isDay != 0,
        )
    }

    /** The service returns parallel arrays, one per variable; zip them into days. */
    private fun DailyDto?.toDomain(): List<DailyForecast> {
        val dates = this?.time ?: return emptyList()
        return dates.mapIndexedNotNull { i, date ->
            val max = tempMax?.getOrNull(i) ?: return@mapIndexedNotNull null
            val min = tempMin?.getOrNull(i) ?: return@mapIndexedNotNull null
            DailyForecast(
                date = LocalDate.parse(date),
                weatherCode = weatherCode?.getOrNull(i) ?: 0,
                tempMax = max,
                tempMin = min,
                precipitationProbability = precipitationProbability?.getOrNull(i) ?: 0,
                precipitationSum = precipitationSum?.getOrNull(i) ?: 0.0,
                windMax = windMax?.getOrNull(i) ?: 0.0,
                uvMax = uvMax?.getOrNull(i) ?: 0.0,
            )
        }
    }

    companion object {
        /** How far ahead Open-Meteo forecasts. */
        const val MAX_FORECAST_DAYS = 16
        private const val PAST_DAYS_AVAILABLE = 30L
    }
}
