package ro.weatherwear.app.ui.trips

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ro.weatherwear.app.R
import ro.weatherwear.app.WeatherWearApp
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.data.model.PackingItem
import ro.weatherwear.app.data.model.Trip
import ro.weatherwear.app.data.repository.TripRepository
import ro.weatherwear.app.data.repository.WeatherRepository
import ro.weatherwear.app.ui.common.ErrorMessages
import ro.weatherwear.app.ui.common.TripUi

data class TripDetailUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val forecast: List<DailyForecast> = emptyList(),
    val isForecastLoading: Boolean = false,
    /** Why there is no forecast (too far ahead, no connection). */
    @param:StringRes val forecastNote: Int? = null,
    /** The trip is gone (deleted here or on another device) or could not be read. */
    @param:StringRes val error: Int? = null,
)

sealed interface TripDetailEvent {
    data class ShowMessage(@param:StringRes val message: Int) : TripDetailEvent
    data object Deleted : TripDetailEvent
}

class TripDetailViewModel(
    savedState: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    /** Read from the intent extras that started the activity. */
    private val tripId: String = checkNotNull(savedState[TripDetailActivity.EXTRA_TRIP_ID]) {
        "TripDetailActivity needs a trip id"
    }

    private val _state = MutableStateFlow(TripDetailUiState())
    val state: StateFlow<TripDetailUiState> = _state.asStateFlow()

    private val _events = Channel<TripDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var forecastRequested = false

    init {
        viewModelScope.launch {
            tripRepository.observeTrip(tripId)
                .catch { e -> _state.update { it.copy(isLoading = false, error = ErrorMessages.forThrowable(e)) } }
                .collect { trip ->
                    _state.update {
                        it.copy(isLoading = false, trip = trip, error = if (trip == null) R.string.error_trip_missing else null)
                    }
                    if (trip != null && !forecastRequested) {
                        forecastRequested = true
                        loadForecast(trip)
                    }
                }
        }
    }

    private fun loadForecast(trip: Trip) = viewModelScope.launch {
        val start = TripUi.parseDate(trip.startDate) ?: return@launch
        val end = TripUi.parseDate(trip.endDate) ?: return@launch
        _state.update { it.copy(isForecastLoading = true, forecastNote = null) }
        try {
            val days = weatherRepository.forecastForDates(trip.latitude, trip.longitude, start, end)
            _state.update {
                it.copy(
                    isForecastLoading = false,
                    forecast = days,
                    forecastNote = if (days.isEmpty()) R.string.forecast_not_yet else null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isForecastLoading = false, forecastNote = ErrorMessages.forThrowable(e)) }
        }
    }

    // --- Packing list edits. The screen updates at once; Firestore syncs behind it. ---

    fun toggleItem(position: Int) = editItems { items ->
        items[position] = items[position].copy(packed = !items[position].packed)
    }

    fun addItem(name: String, category: String) = editItems { it += PackingItem(name.trim(), category) }

    fun renameItem(position: Int, name: String, category: String) = editItems { items ->
        items[position] = items[position].copy(name = name.trim(), category = category)
    }

    fun deleteItem(position: Int) = editItems { it.removeAt(position) }

    /** Start packing again for the trip home. */
    fun uncheckAll() = editItems { items -> items.replaceAll { it.copy(packed = false) } }

    private fun editItems(change: (MutableList<PackingItem>) -> Unit) {
        val trip = _state.value.trip ?: return
        val items = trip.items.toMutableList()
        try {
            change(items)
        } catch (_: IndexOutOfBoundsException) {
            return // The list changed under a stale tap: ignore it.
        }
        _state.update { it.copy(trip = trip.copy(items = items)) }
        viewModelScope.launch {
            try {
                tripRepository.updateItems(trip.id, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(TripDetailEvent.ShowMessage(ErrorMessages.forThrowable(e)))
            }
        }
    }

    fun deleteTrip() = viewModelScope.launch {
        try {
            tripRepository.deleteTrip(tripId)
            _events.send(TripDetailEvent.Deleted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(TripDetailEvent.ShowMessage(ErrorMessages.forThrowable(e)))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WeatherWearApp).container
                TripDetailViewModel(
                    createSavedStateHandle(),
                    container.tripRepository,
                    container.weatherRepository,
                )
            }
        }
    }
}
