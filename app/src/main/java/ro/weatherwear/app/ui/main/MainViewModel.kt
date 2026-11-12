package ro.weatherwear.app.ui.main

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.weatherwear.app.R
import ro.weatherwear.app.WeatherWearApp
import ro.weatherwear.app.data.model.City
import ro.weatherwear.app.data.model.CurrentWeather
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.OutfitSuggestion
import ro.weatherwear.app.data.repository.SavedPlace
import ro.weatherwear.app.data.repository.SettingsRepository
import ro.weatherwear.app.data.repository.SuggestionRepository
import ro.weatherwear.app.data.repository.WeatherRepository
import ro.weatherwear.app.domain.ClothingAdvisor
import ro.weatherwear.app.ui.common.ErrorMessages

data class MainUiState(
    val place: SavedPlace? = null,
    val isLoading: Boolean = false,
    val current: CurrentWeather? = null,
    val days: List<DailyForecast> = emptyList(),
    val outfit: OutfitSuggestion? = null,
    val isOutfitLoading: Boolean = false,
    val useFahrenheit: Boolean = false,
    /** Shown in place of the weather when there is nothing to show. */
    @param:StringRes val fullScreenError: Int? = null,
) {
    val today: DailyForecast? get() = days.firstOrNull()
}

sealed interface MainEvent {
    data class ShowMessage(@param:StringRes val message: Int) : MainEvent
    /** A search matched several towns: let the user pick one. */
    data class ChooseCity(val cities: List<City>) : MainEvent
}

class MainViewModel(
    private val weatherRepository: WeatherRepository,
    private val suggestionRepository: SuggestionRepository,
    private val settings: SettingsRepository,
    private val advisor: ClothingAdvisor,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState(useFahrenheit = settings.useFahrenheit))
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        settings.lastPlace?.let(::loadPlace)
    }

    val hasPlace: Boolean get() = _state.value.place != null

    fun loadPlace(place: SavedPlace) {
        settings.lastPlace = place
        _state.update { it.copy(place = place) }
        refresh()
    }

    fun refresh() {
        val place = _state.value.place ?: return
        loadJob?.cancel() // A new place or a pull-to-refresh replaces any load still running.
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, fullScreenError = null) }
            try {
                val forecast = weatherRepository.forecast(place.latitude, place.longitude)
                _state.update {
                    it.copy(isLoading = false, current = forecast.current, days = forecast.days, outfit = null)
                }
                loadOutfit(place, forecast.days.firstOrNull(), forecast.current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = ErrorMessages.forThrowable(e)
                // Keep showing old data if we have it; otherwise the error takes the whole screen.
                _state.update {
                    it.copy(isLoading = false, fullScreenError = if (it.days.isEmpty()) message else null)
                }
                if (_state.value.days.isNotEmpty()) _events.send(MainEvent.ShowMessage(message))
            }
        }
    }

    private suspend fun loadOutfit(place: SavedPlace, today: DailyForecast?, current: CurrentWeather?) {
        if (today == null) return
        _state.update { it.copy(isOutfitLoading = true) }
        val outfit = suggestionRepository.outfitFor(place.name, today, current)
        _state.update { it.copy(outfit = outfit, isOutfitLoading = false) }
    }

    fun searchCity(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val cities = weatherRepository.searchCities(query)
                _state.update { it.copy(isLoading = false) }
                when (cities.size) {
                    0 -> _events.send(MainEvent.ShowMessage(R.string.error_city_not_found))
                    1 -> selectCity(cities.first())
                    else -> _events.send(MainEvent.ChooseCity(cities))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
                _events.send(MainEvent.ShowMessage(ErrorMessages.forThrowable(e)))
            }
        }
    }

    fun selectCity(city: City) = loadPlace(SavedPlace(city.displayName, city.latitude, city.longitude))

    /** Re-reads settings changed on the settings screen (°C / °F). */
    fun reloadSettings() = _state.update { it.copy(useFahrenheit = settings.useFahrenheit) }

    /** Quick advice for another day of the forecast, from the offline rules (no AI quota used). */
    fun outfitForDay(day: DailyForecast): OutfitSuggestion = advisor.outfitFor(day)

    fun showLocationUnavailable() {
        if (!hasPlace) _state.update { it.copy(fullScreenError = R.string.error_location_unavailable) }
        else viewModelScope.launch { _events.send(MainEvent.ShowMessage(R.string.error_location_unavailable)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WeatherWearApp).container
                MainViewModel(
                    container.weatherRepository,
                    container.suggestionRepository,
                    container.settingsRepository,
                    ClothingAdvisor(),
                )
            }
        }
    }
}
