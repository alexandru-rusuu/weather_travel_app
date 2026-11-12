package ro.weatherwear.app

import android.app.Application
import ro.weatherwear.app.di.AppContainer
import ro.weatherwear.app.notifications.NotificationHelper

class WeatherWearApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
    }
}
