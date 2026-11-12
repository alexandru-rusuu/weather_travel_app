package ro.weatherwear.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.Trip
import ro.weatherwear.app.data.model.TripPurpose
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** How trips are shown on screen. */
object TripUi {

    fun purposeOf(trip: Trip): TripPurpose =
        TripPurpose.entries.firstOrNull { it.name == trip.purpose } ?: TripPurpose.LEISURE

    fun emoji(purpose: TripPurpose): String = when (purpose) {
        TripPurpose.LEISURE -> "🏖️"
        TripPurpose.CITY_BREAK -> "🏙️"
        TripPurpose.MOUNTAIN -> "🏔️"
        TripPurpose.BUSINESS -> "💼"
    }

    @StringRes
    fun label(purpose: TripPurpose): Int = when (purpose) {
        TripPurpose.LEISURE -> R.string.purpose_leisure
        TripPurpose.CITY_BREAK -> R.string.purpose_city_break
        TripPurpose.MOUNTAIN -> R.string.purpose_mountain
        TripPurpose.BUSINESS -> R.string.purpose_business
    }

    fun parseDate(iso: String): LocalDate? = try {
        LocalDate.parse(iso)
    } catch (_: DateTimeParseException) {
        null
    }

    /** Inclusive number of days: a trip from the 12th to the 14th lasts 3 days. */
    fun dayCount(start: LocalDate, end: LocalDate): Int = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    /** "1 zi", "5 zile", "21 de zile" (Romanian plural rules, from resources). */
    fun dayCountText(context: Context, start: LocalDate, end: LocalDate): String {
        val days = dayCount(start, end)
        return context.resources.getQuantityString(R.plurals.day_count, days, days)
    }

    /** "12 nov. – 16 nov." */
    fun dateRange(start: LocalDate, end: LocalDate): String =
        if (start == end) WeatherUi.shortDate(start)
        else "${WeatherUi.shortDate(start)} – ${WeatherUi.shortDate(end)}"

    /** Plain-text packing list, for sharing. */
    fun shareText(trip: Trip): String = buildString {
        append("🧳 ").append(trip.destination)
        val start = parseDate(trip.startDate)
        val end = parseDate(trip.endDate)
        if (start != null && end != null) append(" (").append(dateRange(start, end)).append(")")
        appendLine()
        if (trip.summary.isNotBlank()) appendLine(trip.summary)
        appendLine()
        trip.items.groupBy { it.category }.forEach { (category, items) ->
            appendLine(category.ifBlank { "Altele" } + ":")
            items.forEach { appendLine((if (it.packed) "☑ " else "☐ ") + it.name) }
            appendLine()
        }
    }.trim()
}
