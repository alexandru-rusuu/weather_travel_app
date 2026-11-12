package ro.weatherwear.app.ui.common

import androidx.annotation.StringRes
import ro.weatherwear.app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How weather data is shown on screen: icons, labels, units and dates. */
object WeatherUi {

    private val romanian: Locale = Locale.forLanguageTag("ro-RO")
    private val dayNameFormat = DateTimeFormatter.ofPattern("EEE", romanian)
    private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM", romanian)
    private val longDateFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", romanian)

    /** An emoji for a WMO weather code: readable at any size, no image assets needed. */
    fun emoji(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    @StringRes
    fun description(code: Int): Int = when (code) {
        0 -> R.string.wmo_clear
        1 -> R.string.wmo_mainly_clear
        2 -> R.string.wmo_partly_cloudy
        3 -> R.string.wmo_overcast
        45, 48 -> R.string.wmo_fog
        51, 53, 55 -> R.string.wmo_drizzle
        56, 57 -> R.string.wmo_freezing_drizzle
        61, 63, 65 -> R.string.wmo_rain
        66, 67 -> R.string.wmo_freezing_rain
        71, 73, 75, 77 -> R.string.wmo_snow
        80, 81, 82 -> R.string.wmo_showers
        85, 86 -> R.string.wmo_snow_showers
        95 -> R.string.wmo_thunderstorm
        96, 99 -> R.string.wmo_thunderstorm_hail
        else -> R.string.wmo_unknown
    }

    /** "12°" in the unit the user picked. Data is always fetched in °C. */
    fun temperature(celsius: Double, fahrenheit: Boolean): String {
        val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        return "${value.roundToInt()}°"
    }

    fun unitLabel(fahrenheit: Boolean): String = if (fahrenheit) "°F" else "°C"

    fun dayName(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Azi"
        today.plusDays(1) -> "Mâine"
        else -> date.format(dayNameFormat).replaceFirstChar { it.titlecase(romanian) }
    }

    fun shortDate(date: LocalDate): String = date.format(shortDateFormat)

    fun longDate(date: LocalDate): String =
        date.format(longDateFormat).replaceFirstChar { it.titlecase(romanian) }
}
