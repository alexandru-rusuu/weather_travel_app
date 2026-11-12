package ro.weatherwear.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import ro.weatherwear.app.BuildConfig
import java.util.concurrent.TimeUnit

/** Builds the HTTP clients once for the whole app. */
object NetworkModule {

    private val logging = HttpLoggingInterceptor().apply {
        // Headers are not logged, so the Gemini key never reaches Logcat.
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val weatherClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    // Language models take longer to answer than a weather service.
    private val aiClient: OkHttpClient = weatherClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val openMeteoApi: OpenMeteoApi by lazy {
        retrofit(OpenMeteoApi.BASE_URL, weatherClient).create(OpenMeteoApi::class.java)
    }

    val geocodingApi: GeocodingApi by lazy {
        retrofit(GeocodingApi.BASE_URL, weatherClient).create(GeocodingApi::class.java)
    }

    val geminiApi: GeminiApi by lazy {
        retrofit(GeminiApi.BASE_URL, aiClient).create(GeminiApi::class.java)
    }
}
