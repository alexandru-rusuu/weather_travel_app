package ro.weatherwear.app.ui.auth

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
import ro.weatherwear.app.data.repository.AuthRepository
import ro.weatherwear.app.domain.Validators
import ro.weatherwear.app.ui.common.ErrorMessages

data class LoginUiState(
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmError: Int? = null,
)

/** One-off things the screen reacts to once (navigation, a snackbar). */
sealed interface LoginEvent {
    data object SignedIn : LoginEvent
    data class ShowMessage(@param:StringRes val message: Int) : LoginEvent
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun toggleMode() = _state.update {
        LoginUiState(isRegisterMode = !it.isRegisterMode)
    }

    /** Clears the error under the fields as soon as the user starts fixing them. */
    fun onInputChanged() = _state.update { it.copy(emailError = null, passwordError = null, confirmError = null) }

    fun submit(email: String, password: String, confirmation: String) {
        val registering = _state.value.isRegisterMode
        val emailError = Validators.validateEmail(email)
        val passwordError = Validators.validatePassword(password, registering)
        val confirmError = if (registering) Validators.validateConfirmation(password, confirmation) else null
        if (emailError != null || passwordError != null || confirmError != null) {
            _state.update {
                it.copy(
                    emailError = emailError?.let(ErrorMessages::forValidation),
                    passwordError = passwordError?.let(ErrorMessages::forValidation),
                    confirmError = confirmError?.let(ErrorMessages::forValidation),
                )
            }
            return
        }
        runAuth {
            if (registering) authRepository.register(email, password)
            else authRepository.signIn(email, password)
        }
    }

    fun signInWithGoogle(idToken: String) = runAuth { authRepository.signInWithGoogle(idToken) }

    fun resetPassword(email: String) {
        Validators.validateEmail(email)?.let { error ->
            _state.update { it.copy(emailError = ErrorMessages.forValidation(error)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val message = try {
                authRepository.sendPasswordReset(email)
                R.string.password_reset_sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorMessages.forThrowable(e)
            }
            _state.update { it.copy(isLoading = false) }
            _events.send(LoginEvent.ShowMessage(message))
        }
    }

    private fun runAuth(block: suspend () -> Unit) {
        if (_state.value.isLoading) return // Ignore double taps while a request is running.
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                block()
                _events.send(LoginEvent.SignedIn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(LoginEvent.ShowMessage(ErrorMessages.forThrowable(e)))
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as WeatherWearApp
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
