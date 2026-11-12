package ro.weatherwear.app.data.repository

import android.content.Context
import androidx.core.content.edit

/** A place the user last looked at, remembered for the next launch and the daily notification. */
data class SavedPlace(val name: String, val latitude: Double, val longitude: Double)

/** Local settings on the device (SharedPreferences). */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("weatherwear_settings", Context.MODE_PRIVATE)

    var useFahrenheit: Boolean
        get() = prefs.getBoolean(KEY_FAHRENHEIT, false)
        set(value) = prefs.edit { putBoolean(KEY_FAHRENHEIT, value) }

    var dailyNotification: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION, value) }

    var lastPlace: SavedPlace?
        get() {
            val name = prefs.getString(KEY_PLACE_NAME, null) ?: return null
            if (!prefs.contains(KEY_PLACE_LAT) || !prefs.contains(KEY_PLACE_LON)) return null
            return SavedPlace(
                name,
                Double.fromBits(prefs.getLong(KEY_PLACE_LAT, 0L)),
                Double.fromBits(prefs.getLong(KEY_PLACE_LON, 0L)),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_PLACE_NAME); remove(KEY_PLACE_LAT); remove(KEY_PLACE_LON)
            } else {
                // SharedPreferences has no putDouble; store the exact bits in a Long.
                putString(KEY_PLACE_NAME, value.name)
                putLong(KEY_PLACE_LAT, value.latitude.toRawBits())
                putLong(KEY_PLACE_LON, value.longitude.toRawBits())
            }
        }

    private companion object {
        const val KEY_FAHRENHEIT = "use_fahrenheit"
        const val KEY_NOTIFICATION = "daily_notification"
        const val KEY_PLACE_NAME = "place_name"
        const val KEY_PLACE_LAT = "place_lat"
        const val KEY_PLACE_LON = "place_lon"
    }
}
