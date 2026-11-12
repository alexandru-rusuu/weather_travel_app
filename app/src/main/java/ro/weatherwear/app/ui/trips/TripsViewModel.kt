package ro.weatherwear.app.ui.trips

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ro.weatherwear.app.WeatherWearApp
import ro.weatherwear.app.data.model.Trip
import ro.weatherwear.app.data.repository.TripRepository
import ro.weatherwear.app.ui.common.ErrorMessages

data class TripsUiState(
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    @param:StringRes val error: Int? = null,
)

sealed interface TripsEvent {
    data class TripDeleted(val trip: Trip) : TripsEvent
    data class ShowMessage(@param:StringRes val message: Int) : TripsEvent
}

class TripsViewModel(private val repository: TripRepository) : ViewModel() {

    /** Live list from Firestore; stops listening 5 s after the screen goes away. */
    val state: StateFlow<TripsUiState> = repository.observeTrips()
        .map { TripsUiState(isLoading = false, trips = it) }
        .catch { emit(TripsUiState(isLoading = false, error = ErrorMessages.forThrowable(it))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsUiState())

    private val _events = Channel<TripsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun delete(trip: Trip) = viewModelScope.launch {
        try {
            repository.deleteTrip(trip.id)
            _events.send(TripsEvent.TripDeleted(trip))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(TripsEvent.ShowMessage(ErrorMessages.forThrowable(e)))
        }
    }

    fun undoDelete(trip: Trip) = viewModelScope.launch {
        try {
            repository.restoreTrip(trip)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(TripsEvent.ShowMessage(ErrorMessages.forThrowable(e)))
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                TripsViewModel((this[APPLICATION_KEY] as WeatherWearApp).container.tripRepository)
            }
        }
    }
}
