package ro.weatherwear.app.domain

import ro.weatherwear.app.data.model.CurrentWeather
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.OutfitSuggestion
import ro.weatherwear.app.data.model.PackingItem
import ro.weatherwear.app.data.model.PackingSuggestion
import ro.weatherwear.app.data.model.SuggestionSource
import ro.weatherwear.app.data.model.TripPurpose

/** Packing list categories (also the ones Gemini is asked to use). */
object PackingCategory {
    const val CLOTHES = "Haine"
    const val SHOES = "Încălțăminte"
    const val ACCESSORIES = "Accesorii"
    const val TOILETRIES = "Igienă"
    const val ELECTRONICS = "Electronice"
    const val DOCUMENTS = "Documente"

    val ALL = listOf(CLOTHES, SHOES, ACCESSORIES, TOILETRIES, ELECTRONICS, DOCUMENTS)
}

/**
 * Offline, rule-based clothing advice. It answers when there is no Gemini key,
 * no internet for the AI call, or the free quota has run out, so the app always
 * has a useful answer. Pure Kotlin with no Android types, so it is unit-tested.
 *
 * The texts are in Romanian because the app is in Romanian; they are data shown
 * as-is, like the text Gemini returns.
 */
class ClothingAdvisor {

    /** How warm it feels, in bands. */
    enum class Band { FREEZING, COLD, COOL, MILD, WARM, HOT }

    fun bandFor(feelsLikeCelsius: Double): Band = when {
        feelsLikeCelsius <= -5 -> Band.FREEZING
        feelsLikeCelsius <= 5 -> Band.COLD
        feelsLikeCelsius <= 12 -> Band.COOL
        feelsLikeCelsius <= 18 -> Band.MILD
        feelsLikeCelsius <= 25 -> Band.WARM
        else -> Band.HOT
    }

    /** What to wear on [day]. [current] makes it more precise for today. */
    fun outfitFor(day: DailyForecast, current: CurrentWeather? = null): OutfitSuggestion {
        val feelsLike = current?.feelsLike ?: ((day.tempMax + day.tempMin) / 2)
        val band = bandFor(feelsLike)
        val snowy = WeatherConditions.isSnow(day.weatherCode)
        val rainy = !snowy && (WeatherConditions.isRain(day.weatherCode) ||
            day.precipitationProbability >= WeatherConditions.RAIN_PROBABILITY_THRESHOLD)
        val windy = day.windMax >= WeatherConditions.STRONG_WIND_KMH
        val sunny = day.uvMax >= WeatherConditions.HIGH_UV
        val bigSwing = day.tempMax - day.tempMin >= 10

        val items = mutableListOf<String>()
        items += when (band) {
            Band.FREEZING -> listOf("Geacă de iarnă groasă", "Lenjerie termică", "Căciulă", "Mănuși", "Fular", "Ghete îmblănite")
            Band.COLD -> listOf("Geacă groasă", "Pulover", "Căciulă", "Mănuși", "Pantaloni lungi", "Ghete")
            Band.COOL -> listOf("Geacă de tranziție", "Pulover subțire", "Pantaloni lungi", "Pantofi închiși")
            Band.MILD -> listOf("Jachetă ușoară sau hanorac", "Bluză cu mânecă lungă", "Blugi", "Sneakers")
            Band.WARM -> listOf("Tricou", "Pantaloni lejeri", "Sneakers ușori")
            Band.HOT -> listOf("Tricou din bumbac sau in", "Pantaloni scurți sau fustă", "Sandale", "Șapcă")
        }
        if (snowy) items += "Încălțăminte impermeabilă"
        if (rainy) items += if (windy) listOf("Geacă impermeabilă cu glugă") else listOf("Umbrelă", "Geacă impermeabilă")
        if (windy && !rainy) items += "Strat care oprește vântul"
        if (sunny) items += listOf("Ochelari de soare", "Cremă cu SPF 30+")

        val summary = buildString {
            append(
                when (band) {
                    Band.FREEZING -> "Ger"
                    Band.COLD -> "Frig"
                    Band.COOL -> "Răcoare"
                    Band.MILD -> "Vreme plăcută"
                    Band.WARM -> "Cald"
                    Band.HOT -> "Foarte cald"
                }
            )
            when {
                snowy -> append(", cu ninsoare")
                rainy -> append(", cu ploaie probabilă")
                windy -> append(", cu vânt puternic")
                sunny -> append(" și soare puternic")
            }
            append(". ")
            append(
                when (band) {
                    Band.FREEZING, Band.COLD -> "Îmbracă-te gros și acoperă-ți capul și mâinile."
                    Band.COOL -> "Ai nevoie de o geacă peste un pulover subțire."
                    Band.MILD -> "Un strat ușor peste tricou e suficient."
                    Band.WARM, Band.HOT -> "Haine lejere, din materiale care respiră."
                }
            )
        }

        val tip = when {
            WeatherConditions.isStorm(day.weatherCode) -> "Sunt anunțate furtuni: evită să stai mult afară în timpul lor."
            bigSwing -> "Diferență mare între dimineață și după-amiază: îmbracă-te în straturi pe care le poți da jos."
            rainy -> "Ia umbrela chiar dacă dimineața nu plouă."
            sunny -> "Soarele e puternic la prânz: reaplică crema la 2 ore."
            band == Band.FREEZING -> "La ger, mai multe straturi subțiri țin mai cald decât unul gros."
            else -> "Verifică din nou prognoza seara, pentru mâine."
        }

        return OutfitSuggestion(summary, items.distinct(), tip, SuggestionSource.RULES)
    }

    /**
     * What to pack for a trip of [tripDays] days. [forecast] covers the days the
     * forecast reaches (it can be shorter than the trip, or empty).
     */
    fun packingFor(forecast: List<DailyForecast>, purpose: TripPurpose, tripDays: Int): PackingSuggestion {
        val days = tripDays.coerceAtLeast(1)
        // Clothes beyond a week get washed rather than packed.
        val clothesCount = days.coerceAtMost(7)
        val minTemp = forecast.minOfOrNull { it.tempMin }
        val maxTemp = forecast.maxOfOrNull { it.tempMax }
        val anySnow = forecast.any { WeatherConditions.isSnow(it.weatherCode) }
        val anyRain = forecast.any {
            WeatherConditions.isRain(it.weatherCode) ||
                it.precipitationProbability >= WeatherConditions.RAIN_PROBABILITY_THRESHOLD
        }
        val strongSun = forecast.any { it.uvMax >= WeatherConditions.HIGH_UV - 1 }

        val items = mutableListOf<PackingItem>()
        fun add(name: String, category: String) {
            if (items.none { it.name == name }) items += PackingItem(name, category)
        }

        // Clothes every trip needs.
        add("$clothesCount tricouri sau bluze", PackingCategory.CLOTHES)
        add("$clothesCount perechi de lenjerie", PackingCategory.CLOTHES)
        add("$clothesCount perechi de șosete", PackingCategory.CLOTHES)
        add("Pijama", PackingCategory.CLOTHES)
        add("${(days / 3).coerceIn(1, 3)} perechi de pantaloni", PackingCategory.CLOTHES)

        // Clothes for the temperature range.
        if (minTemp != null && minTemp <= 5) {
            add("Geacă groasă", PackingCategory.CLOTHES)
            add("2 pulovere", PackingCategory.CLOTHES)
            add("Căciulă, fular și mănuși", PackingCategory.ACCESSORIES)
            if (minTemp <= -5) add("Lenjerie termică", PackingCategory.CLOTHES)
        } else if (minTemp == null || minTemp <= 15) {
            add("Jachetă ușoară", PackingCategory.CLOTHES)
            add("Hanorac sau pulover subțire", PackingCategory.CLOTHES)
        }
        if (maxTemp != null && maxTemp >= 25) add("Pantaloni scurți", PackingCategory.CLOTHES)

        // Shoes.
        add("Pantofi comozi de mers", PackingCategory.SHOES)
        if (anySnow) add("Ghete impermeabile", PackingCategory.SHOES)
        else if (anyRain) add("Încălțăminte care nu lasă apa să treacă", PackingCategory.SHOES)
        if (maxTemp != null && maxTemp >= 24) add("Sandale sau papuci", PackingCategory.SHOES)

        // Weather accessories.
        if (anyRain) add("Umbrelă compactă", PackingCategory.ACCESSORIES)
        if (strongSun) {
            add("Ochelari de soare", PackingCategory.ACCESSORIES)
            add("Cremă cu SPF 30+", PackingCategory.TOILETRIES)
        }

        // Depends on the purpose of the trip.
        when (purpose) {
            TripPurpose.LEISURE -> if (maxTemp == null || maxTemp >= 22) {
                add("Costum de baie", PackingCategory.CLOTHES)
                add("Prosop de plajă", PackingCategory.ACCESSORIES)
            }
            TripPurpose.CITY_BREAK -> add("Rucsac mic de oraș", PackingCategory.ACCESSORIES)
            TripPurpose.MOUNTAIN -> {
                add("Bocanci de munte", PackingCategory.SHOES)
                add("Rucsac de tură", PackingCategory.ACCESSORIES)
                add("Pelerină de ploaie", PackingCategory.CLOTHES)
                add("Lanternă frontală", PackingCategory.ELECTRONICS)
                add("Trusă de prim ajutor", PackingCategory.TOILETRIES)
            }
            TripPurpose.BUSINESS -> {
                add("Ținută office", PackingCategory.CLOTHES)
                add("Pantofi eleganți", PackingCategory.SHOES)
                add("Laptop și încărcător", PackingCategory.ELECTRONICS)
                add("Documente pentru întâlniri", PackingCategory.DOCUMENTS)
            }
        }

        // Always.
        add("Trusă de toaletă", PackingCategory.TOILETRIES)
        add("Medicamente personale", PackingCategory.TOILETRIES)
        add("Încărcător de telefon", PackingCategory.ELECTRONICS)
        add("Baterie externă", PackingCategory.ELECTRONICS)
        add("Buletin sau pașaport", PackingCategory.DOCUMENTS)
        add("Card și puțin numerar", PackingCategory.DOCUMENTS)

        val summary = when {
            minTemp == null || maxTemp == null ->
                "Prognoza nu acoperă încă perioada, așa că lista e una generală. Revino cu câteva zile înainte de plecare."
            else -> buildString {
                append("Temperaturi între ${minTemp.toInt()}° și ${maxTemp.toInt()}°C")
                when {
                    anySnow -> append(", cu ninsoare în unele zile")
                    anyRain -> append(", cu ploaie în unele zile")
                }
                append(".")
            }
        }
        val tip = when {
            anyRain && anySnow -> "Pune hainele într-o pungă impermeabilă în bagaj."
            minTemp != null && maxTemp != null && maxTemp - minTemp >= 12 ->
                "Temperaturile variază mult: ia haine pe care le poți purta în straturi."
            days > 7 -> "Pentru o călătorie lungă, plănuiește să speli haine o dată pe săptămână."
            else -> "Pune lucrurile grele la baza bagajului și pe cele de care ai nevoie repede deasupra."
        }
        return PackingSuggestion(summary, items, tip, SuggestionSource.RULES)
    }
}
