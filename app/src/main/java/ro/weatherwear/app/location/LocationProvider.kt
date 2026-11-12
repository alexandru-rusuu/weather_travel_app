package ro.weatherwear.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Finds the phone's approximate position with the platform LocationManager
 * (no Google Play Services dependency). City-level precision is enough for weather.
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean = manager != null && LocationManagerCompat.isLocationEnabled(manager)

    /** A fresh position, or the last known one, or null if neither arrives within the timeout. */
    @SuppressLint("MissingPermission") // Checked by hasPermission() first.
    suspend fun currentLocation(): Location? {
        if (!hasPermission() || manager == null) return null
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, ContextCompat.getMainExecutor(appContext)
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }
        return fresh ?: manager.getLastKnownLocation(provider)
    }

    /** The town's name for a position (Android's reverse geocoder), or null if unknown. */
    suspend fun placeName(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(appContext, Locale.forLanguageTag("ro-RO"))
        val address = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(TIMEOUT_MS) {
                    suspendCancellableCoroutine<Address?> { cont ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                }
            }
        } catch (_: IOException) {
            null
        }
        return address?.locality ?: address?.subAdminArea ?: address?.adminArea
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
