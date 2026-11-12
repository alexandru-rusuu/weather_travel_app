package ro.weatherwear.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import ro.weatherwear.app.R
import ro.weatherwear.app.WeatherWearApp
import ro.weatherwear.app.domain.ClothingAdvisor
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs once a day in the morning: reads the forecast for the last place the user
 * looked at and posts "what to wear today". Uses the offline rules, so it does
 * not spend the Gemini quota in the background.
 */
class DailyOutfitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WeatherWearApp).container
        val place = container.settingsRepository.lastPlace ?: return Result.success()
        return try {
            val today = container.weatherRepository
                .forecast(place.latitude, place.longitude, days = 1)
                .days.firstOrNull() ?: return Result.success()
            val outfit = ClothingAdvisor().outfitFor(today)
            NotificationHelper.showDailyOutfit(
                applicationContext,
                applicationContext.getString(R.string.notification_title, place.name),
                outfit.summary + "\n" + outfit.items.take(5).joinToString(", "),
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "daily_outfit"
        private val NOTIFY_AT: LocalTime = LocalTime.of(7, 30)

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(NOTIFY_AT)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DailyOutfitWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
