package ro.weatherwear.app.domain

/**
 * Groups the WMO weather codes used by Open-Meteo
 * (https://open-meteo.com/en/docs#weathervariables) into what matters for clothing.
 */
object WeatherConditions {

    private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
    private val STORM_CODES = setOf(95, 96, 99)

    fun isRain(code: Int): Boolean = code in RAIN_CODES
    fun isSnow(code: Int): Boolean = code in SNOW_CODES
    fun isStorm(code: Int): Boolean = code in STORM_CODES

    /** Precipitation probability (%) from which we advise an umbrella. */
    const val RAIN_PROBABILITY_THRESHOLD = 50

    /** UV index from which sun protection is advised (WHO: 6 = high). */
    const val HIGH_UV = 6.0

    /** Wind speed (km/h) that makes an umbrella useless and calls for a windproof layer. */
    const val STRONG_WIND_KMH = 35.0
}
