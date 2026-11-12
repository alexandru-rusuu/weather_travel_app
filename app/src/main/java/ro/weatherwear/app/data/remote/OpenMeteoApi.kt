package ro.weatherwear.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo forecast API (https://open-meteo.com). Free, and needs no API key.
 * `forecast_days` and `start_date`/`end_date` are mutually exclusive: pass null for one pair.
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String? = CURRENT_FIELDS,
        @Query("daily") daily: String = DAILY_FIELDS,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int? = null,
        @Query("start_date") startDate: String? = null,
        @Query("end_date") endDate: String? = null,
    ): ForecastResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
        const val DAILY_FIELDS =
            "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
                "precipitation_sum,wind_speed_10m_max,uv_index_max"
    }
}

/** Open-Meteo geocoding: turns a city name into coordinates. */
interface GeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "ro",
        @Query("format") format: String = "json",
    ): GeocodingResponse

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}

// --- Network DTOs. Everything is nullable: the service may leave out any field. ---

data class ForecastResponse(
    val current: CurrentDto?,
    val daily: DailyDto?,
)

data class CurrentDto(
    @SerializedName("temperature_2m") val temperature: Double?,
    @SerializedName("apparent_temperature") val apparentTemperature: Double?,
    @SerializedName("relative_humidity_2m") val humidity: Int?,
    @SerializedName("weather_code") val weatherCode: Int?,
    @SerializedName("wind_speed_10m") val windSpeed: Double?,
    @SerializedName("is_day") val isDay: Int?,
)

data class DailyDto(
    val time: List<String>?,
    @SerializedName("weather_code") val weatherCode: List<Int?>?,
    @SerializedName("temperature_2m_max") val tempMax: List<Double?>?,
    @SerializedName("temperature_2m_min") val tempMin: List<Double?>?,
    @SerializedName("precipitation_probability_max") val precipitationProbability: List<Int?>?,
    @SerializedName("precipitation_sum") val precipitationSum: List<Double?>?,
    @SerializedName("wind_speed_10m_max") val windMax: List<Double?>?,
    @SerializedName("uv_index_max") val uvMax: List<Double?>?,
)

data class GeocodingResponse(
    val results: List<GeoResultDto>?,
)

data class GeoResultDto(
    val name: String?,
    val latitude: Double?,
    val longitude: Double?,
    val country: String?,
    val admin1: String?,
)
