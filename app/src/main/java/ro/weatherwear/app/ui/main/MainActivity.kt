package ro.weatherwear.app.ui.main

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.City
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.OutfitSuggestion
import ro.weatherwear.app.data.model.SuggestionSource
import ro.weatherwear.app.data.repository.SavedPlace
import ro.weatherwear.app.databinding.ActivityMainBinding
import ro.weatherwear.app.location.LocationProvider
import ro.weatherwear.app.sensors.ShakeDetector
import ro.weatherwear.app.ui.auth.LoginActivity
import ro.weatherwear.app.ui.common.ForecastAdapter
import ro.weatherwear.app.ui.common.WeatherUi
import ro.weatherwear.app.ui.common.appContainer
import ro.weatherwear.app.ui.common.applySystemBarInsets
import ro.weatherwear.app.ui.common.collectWhenStarted
import ro.weatherwear.app.ui.common.snackbar
import ro.weatherwear.app.ui.settings.SettingsActivity
import ro.weatherwear.app.ui.trips.NewTripActivity
import ro.weatherwear.app.ui.trips.TripsActivity
import kotlin.math.roundToInt

/** Activity 2: today's weather where the user is, what to wear, and the next days. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private lateinit var locationProvider: LocationProvider
    private lateinit var shakeDetector: ShakeDetector
    private val forecastAdapter = ForecastAdapter(onDayClick = ::showDayDetails)

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) locate() else viewModel.showLocationUnavailable()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This screen is only for signed-in users.
        if (appContainer.isFirebaseConfigured && appContainer.authRepository.currentUser == null) {
            openLogin()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        addMenuProvider(mainMenu, this)

        locationProvider = LocationProvider(this)
        shakeDetector = ShakeDetector(this) {
            if (viewModel.hasPlace) {
                Toast.makeText(this, R.string.shake_refreshing, Toast.LENGTH_SHORT).show()
                viewModel.refresh()
            }
        }

        setUpViews()
        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is MainEvent.ShowMessage -> binding.root.snackbar(event.message)
                is MainEvent.ChooseCity -> chooseCity(event.cities)
            }
        }

        // First launch: no saved place yet, so try the phone's location.
        if (savedInstanceState == null && !viewModel.hasPlace) locateOrAskPermission()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        shakeDetector.start()
        viewModel.reloadSettings() // The unit may have changed on the settings screen.
    }

    override fun onPause() {
        super.onPause()
        if (::shakeDetector.isInitialized) shakeDetector.stop()
    }

    private fun setUpViews() = with(binding) {
        forecastList.adapter = forecastAdapter
        swipeRefresh.setOnRefreshListener {
            if (viewModel.hasPlace) viewModel.refresh()
            else {
                swipeRefresh.isRefreshing = false
                locateOrAskPermission()
            }
        }
        emptyLocateButton.setOnClickListener { locateOrAskPermission() }
        emptyRetryButton.setOnClickListener { viewModel.refresh() }
        planTripFab.setOnClickListener { startActivity(Intent(this@MainActivity, NewTripActivity::class.java)) }
        // Long press on today's card: share the outfit with someone.
        heroCard.setOnLongClickListener {
            shareToday()
            true
        }
        heroCard.setOnClickListener { viewModel.state.value.today?.let(::showDayDetails) }
        hintText.visibility = if (shakeDetector.isAvailable) View.VISIBLE else View.GONE
    }

    private fun render(state: MainUiState) = with(binding) {
        swipeRefresh.isRefreshing = state.isLoading
        forecastAdapter.useFahrenheit = state.useFahrenheit

        val noData = state.days.isEmpty()
        val showEmpty = noData && !state.isLoading && (state.fullScreenError != null || state.place == null)
        emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        content.visibility = if (noData) View.GONE else View.VISIBLE
        emptyText.setText(state.fullScreenError ?: R.string.empty_no_place)
        emptyRetryButton.visibility = if (state.fullScreenError != null && state.place != null) View.VISIBLE else View.GONE
        if (noData) return@with

        val f = state.useFahrenheit
        placeName.text = getString(R.string.place_label, state.place?.name.orEmpty())
        state.today?.let { today ->
            todayDate.text = WeatherUi.longDate(today.date)
            todayRange.text = getString(
                R.string.today_range,
                WeatherUi.temperature(today.tempMax, f),
                WeatherUi.temperature(today.tempMin, f),
                today.precipitationProbability,
            )
        }
        val current = state.current
        if (current != null) {
            currentIcon.text = WeatherUi.emoji(current.weatherCode, current.isDay)
            currentTemp.text = WeatherUi.temperature(current.temperature, f)
            currentDescription.setText(WeatherUi.description(current.weatherCode))
            currentDetails.text = getString(
                R.string.current_details,
                WeatherUi.temperature(current.feelsLike, f),
                current.humidity,
                current.windSpeed.roundToInt(),
            )
        }
        forecastAdapter.submitList(state.days)
        renderOutfit(state.outfit, state.isOutfitLoading)
    }

    private fun renderOutfit(outfit: OutfitSuggestion?, loading: Boolean) = with(binding) {
        outfitProgress.visibility = if (loading) View.VISIBLE else View.GONE
        if (outfit == null) {
            outfitSummary.setText(if (loading) R.string.outfit_loading else R.string.outfit_none)
            outfitItems.removeAllViews()
            outfitTip.visibility = View.GONE
            outfitSource.visibility = View.GONE
            outfitOfflineNote.visibility = View.GONE
            return@with
        }
        outfitSummary.text = outfit.summary
        outfitItems.removeAllViews()
        outfit.items.forEach { item ->
            outfitItems.addView(Chip(this@MainActivity).apply {
                text = item
                isClickable = false
                isCheckable = false
            })
        }
        outfitTip.visibility = if (outfit.tip.isBlank()) View.GONE else View.VISIBLE
        outfitTip.text = getString(R.string.tip_label, outfit.tip)
        outfitSource.visibility = View.VISIBLE
        outfitSource.setText(if (outfit.source == SuggestionSource.AI) R.string.source_ai else R.string.source_rules)
        outfitOfflineNote.visibility = if (outfit.aiFailed) View.VISIBLE else View.GONE
    }

    /** Click on a day card: its details and what to wear that day. */
    private fun showDayDetails(day: DailyForecast) {
        val f = viewModel.state.value.useFahrenheit
        val outfit = viewModel.outfitForDay(day)
        val message = getString(
            R.string.day_details,
            WeatherUi.emoji(day.weatherCode), getString(WeatherUi.description(day.weatherCode)),
            WeatherUi.temperature(day.tempMax, f), WeatherUi.temperature(day.tempMin, f),
            day.precipitationProbability, day.windMax.roundToInt(), day.uvMax.roundToInt(),
            outfit.summary, outfit.items.joinToString("\n• ", prefix = "• "),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(WeatherUi.longDate(day.date))
            .setMessage(message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun shareToday() {
        val state = viewModel.state.value
        val today = state.today ?: return
        val outfit = state.outfit ?: viewModel.outfitForDay(today)
        val text = getString(
            R.string.share_today,
            state.place?.name.orEmpty(),
            WeatherUi.temperature(today.tempMax, state.useFahrenheit),
            WeatherUi.temperature(today.tempMin, state.useFahrenheit),
            outfit.summary,
            outfit.items.joinToString(", "),
        )
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    private fun chooseCity(cities: List<City>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_city_title)
            .setItems(cities.map { it.detailedName }.toTypedArray()) { _, which -> viewModel.selectCity(cities[which]) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun locateOrAskPermission() {
        if (locationProvider.hasPermission()) locate()
        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun locate() {
        if (!locationProvider.isLocationEnabled()) {
            binding.root.snackbar(R.string.error_location_disabled)
            viewModel.showLocationUnavailable()
            return
        }
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val location = locationProvider.currentLocation()
            if (location == null) {
                binding.swipeRefresh.isRefreshing = false
                viewModel.showLocationUnavailable()
                return@launch
            }
            val name = locationProvider.placeName(location) ?: getString(R.string.my_location)
            viewModel.loadPlace(SavedPlace(name, location.latitude, location.longitude))
        }
    }

    private val mainMenu = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_main, menu)
            val searchItem = menu.findItem(R.id.action_search)
            (searchItem.actionView as SearchView).apply {
                queryHint = getString(R.string.search_hint)
                setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String): Boolean {
                        viewModel.searchCity(query)
                        searchItem.collapseActionView()
                        return true
                    }

                    override fun onQueryTextChange(newText: String) = false
                })
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.action_locate -> locateOrAskPermission()
                R.id.action_trips -> startActivity(Intent(this@MainActivity, TripsActivity::class.java))
                R.id.action_settings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                R.id.action_about -> showAbout()
                R.id.action_logout -> confirmLogout()
                else -> return false
            }
            return true
        }
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_about)
            .setMessage(R.string.about_text)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.menu_logout) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        if (appContainer.isFirebaseConfigured) appContainer.authRepository.signOut()
        lifecycleScope.launch {
            // Also forget the Google account choice, so the next login can pick another one.
            runCatching {
                CredentialManager.create(this@MainActivity).clearCredentialState(ClearCredentialStateRequest())
            }
            openLogin()
        }
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
