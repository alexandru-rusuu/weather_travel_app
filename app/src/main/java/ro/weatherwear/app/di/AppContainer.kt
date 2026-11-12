package ro.weatherwear.app.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import ro.weatherwear.app.BuildConfig
import ro.weatherwear.app.data.remote.NetworkModule
import ro.weatherwear.app.data.repository.AuthRepository
import ro.weatherwear.app.data.repository.SettingsRepository
import ro.weatherwear.app.data.repository.SuggestionRepository
import ro.weatherwear.app.data.repository.TripRepository
import ro.weatherwear.app.data.repository.WeatherRepository
import ro.weatherwear.app.domain.ClothingAdvisor

/**
 * Manual dependency injection: creates every repository once and hands it to
 * the ViewModels, which never build their own dependencies.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** False when app/google-services.json was missing at build time. */
    val isFirebaseConfigured: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    val settingsRepository = SettingsRepository(appContext)

    val weatherRepository by lazy {
        WeatherRepository(NetworkModule.openMeteoApi, NetworkModule.geocodingApi)
    }

    val suggestionRepository by lazy {
        SuggestionRepository(
            NetworkModule.geminiApi,
            BuildConfig.GEMINI_API_KEY,
            BuildConfig.GEMINI_MODEL,
            ClothingAdvisor(),
        )
    }

    // Firebase objects are only touched once we know Firebase is configured.
    val authRepository by lazy { AuthRepository(FirebaseAuth.getInstance()) }

    val tripRepository by lazy { TripRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()) }
}
