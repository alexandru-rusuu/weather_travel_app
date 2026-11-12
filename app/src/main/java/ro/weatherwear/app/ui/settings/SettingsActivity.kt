package ro.weatherwear.app.ui.settings

import android.Manifest
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ro.weatherwear.app.BuildConfig
import ro.weatherwear.app.R
import ro.weatherwear.app.databinding.ActivitySettingsBinding
import ro.weatherwear.app.notifications.DailyOutfitWorker
import ro.weatherwear.app.notifications.NotificationHelper
import ro.weatherwear.app.ui.common.appContainer
import ro.weatherwear.app.ui.common.applySystemBarInsets
import ro.weatherwear.app.ui.common.snackbar

/** Activity 6: temperature unit, the daily notification, and account info. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val settings get() = appContainer.settingsRepository

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) setDailyNotification(true)
            else {
                binding.notificationSwitch.isChecked = false
                binding.root.snackbar(R.string.error_notification_permission)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        with(binding) {
            fahrenheitSwitch.isChecked = settings.useFahrenheit
            fahrenheitSwitch.setOnCheckedChangeListener { _, checked -> settings.useFahrenheit = checked }

            notificationSwitch.isChecked = settings.dailyNotification && NotificationHelper.canPost(this@SettingsActivity)
            notificationSwitch.setOnCheckedChangeListener { button, checked ->
                if (!button.isPressed) return@setOnCheckedChangeListener // Ignore changes made from code.
                when {
                    !checked -> setDailyNotification(false)
                    NotificationHelper.canPost(this@SettingsActivity) -> setDailyNotification(true)
                    NotificationHelper.needsPermission() ->
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else -> {
                        // Android 10-12: notifications were turned off for the app in system settings.
                        notificationSwitch.isChecked = false
                        root.snackbar(R.string.error_notification_permission)
                    }
                }
            }

            val email = if (appContainer.isFirebaseConfigured) appContainer.authRepository.currentUser?.email else null
            accountText.text = if (email != null) getString(R.string.settings_signed_in_as, email)
            else getString(R.string.settings_signed_in_google)
            aiStatusText.setText(
                if (appContainer.suggestionRepository.isAiConfigured) R.string.settings_ai_on else R.string.settings_ai_off
            )
            versionText.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setDailyNotification(enabled: Boolean) {
        settings.dailyNotification = enabled
        if (enabled) {
            DailyOutfitWorker.schedule(this)
            binding.root.snackbar(R.string.notification_scheduled)
        } else {
            DailyOutfitWorker.cancel(this)
        }
    }
}
