package ro.weatherwear.app.ui.trips

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
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
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.PackingSuggestion
import ro.weatherwear.app.data.model.Trip
import ro.weatherwear.app.data.model.TripPurpose
import ro.weatherwear.app.data.repository.SuggestionRepository
import ro.weatherwear.app.data.repository.TripRepository
import ro.weatherwear.app.data.repository.WeatherRepository
import ro.weatherwear.app.ui.common.ErrorMessages
import ro.weatherwear.app.ui.common.TripUi
import java.time.LocalDate

data class NewTripUiState(
    val city: City? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val purpose: TripPurpose = TripPurpose.LEISURE,
    val isSearching: Boolean = false,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val forecast: List<DailyForecast> = emptyList(),
    val suggestion: PackingSuggestion? = null,
    @param:StringRes val destinationError: Int? = null,
    @param:StringRes val datesError: Int? = null,
) {
    val isBusy: Boolean get() = isSearching || isGenerating || isSaving
}

sealed interface NewTripEvent {
    data class ChooseCity(val cities: List<City>) : NewTripEvent
    data class ShowMessage(@param:StringRes val message: Int) : NewTripEvent
    data object Saved : NewTripEvent
}

class NewTripViewModel(
    private val weatherRepository: WeatherRepository,
    private val suggestionRepository: SuggestionRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewTripUiState())
    val state: StateFlow<NewTripUiState> = _state.asStateFlow()

    private val _events = Channel<NewTripEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun searchDestination(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(destinationError = R.string.error_required) }
            return
        }
        lastQuery = query.trim()
        viewModelScope.launch {
            _state.update { it.copy(isSearching = true, destinationError = null) }
            try {
                val cities = weatherRepository.searchCities(query)
                _state.update { it.copy(isSearching = false) }
                when (cities.size) {
                    0 -> _state.update { it.copy(destinationError = R.string.error_city_not_found) }
                    1 -> selectCity(cities.first())
                    else -> _events.send(NewTripEvent.ChooseCity(cities))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false) }
                _events.send(NewTripEvent.ShowMessage(ErrorMessages.forThrowable(e)))
            }
        }
    }

    // Changing any input makes an earlier list stale, so it is dropped.
    fun selectCity(city: City) =
        _state.update { it.copy(city = city, destinationError = null, suggestion = null, forecast = emptyList()) }

    /**
     * The user typed in the destination field. A new text means a new search is
     * needed; the same text (e.g. restored after rotating the phone) keeps the city.
     */
    fun onDestinationEdited(text: String) {
        if (text.trim() == lastQuery) return
        _state.update {
            if (it.city == null && it.destinationError == null) it
            else it.copy(city = null, destinationError = null, suggestion = null)
        }
    }

    /** The text the current city was found with. */
    private var lastQuery: String = ""

    fun setDates(start: LocalDate, end: LocalDate) {
        val error = when {
            start.isBefore(LocalDate.now()) -> R.string.error_dates_past
            TripUi.dayCount(start, end) > MAX_TRIP_DAYS -> R.string.error_trip_too_long
            else -> null
        }
        _state.update {
            it.copy(startDate = start, endDate = end, datesError = error, suggestion = null, forecast = emptyList())
        }
    }

    fun setPurpose(purpose: TripPurpose) {
        if (purpose != _state.value.purpose) _state.update { it.copy(purpose = purpose, suggestion = null) }
    }

    fun generate() {
        val current = _state.value
        val city = current.city
        val start = current.startDate
        val end = current.endDate
        val destinationError = if (city == null) R.string.error_choose_destination else null
        val datesError = current.datesError ?: if (start == null || end == null) R.string.error_choose_dates else null
        if (destinationError != null || datesError != null || city == null || start == null || end == null) {
            _state.update { it.copy(destinationError = destinationError, datesError = datesError) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, suggestion = null) }
            // No forecast (trip too far ahead, or no connection) still gives a general list.
            val forecast = try {
                weatherRepository.forecastForDates(city.latitude, city.longitude, start, end)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(NewTripEvent.ShowMessage(R.string.error_forecast_for_trip))
                emptyList()
            }
            val suggestion = suggestionRepository.packingFor(
                destination = city.displayName,
                startDate = start.toString(),
                endDate = end.toString(),
                tripDays = TripUi.dayCount(start, end),
                purpose = current.purpose,
                forecast = forecast,
            )
            _state.update { it.copy(isGenerating = false, forecast = forecast, suggestion = suggestion) }
        }
    }

    fun save() {
        val current = _state.value
        val city = current.city ?: return
        val suggestion = current.suggestion ?: return
        if (current.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                tripRepository.addTrip(
                    Trip(
                        destination = city.displayName,
                        latitude = city.latitude,
                        longitude = city.longitude,
                        startDate = current.startDate.toString(),
                        endDate = current.endDate.toString(),
                        purpose = current.purpose.name,
                        summary = suggestion.summary,
                        tip = suggestion.tip,
                        source = suggestion.source.name,
                        items = suggestion.items,
                    )
                )
                _events.send(NewTripEvent.Saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(NewTripEvent.ShowMessage(ErrorMessages.forThrowable(e)))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    companion object {
        const val MAX_TRIP_DAYS = 30

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WeatherWearApp).container
                NewTripViewModel(
                    container.weatherRepository,
                    container.suggestionRepository,
                    container.tripRepository,
                )
            }
        }
    }
}
