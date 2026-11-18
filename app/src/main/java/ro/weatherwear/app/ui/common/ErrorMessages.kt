package ro.weatherwear.app.ui.common

import androidx.annotation.StringRes
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import retrofit2.HttpException
import ro.weatherwear.app.R
import ro.weatherwear.app.domain.ValidationError
import java.io.IOException

/** Turns technical errors into short messages the user can act on. */
object ErrorMessages {

    @StringRes
    fun forThrowable(error: Throwable): Int = when (error) {
        is FirebaseAuthInvalidUserException -> R.string.error_auth_no_account
        is FirebaseAuthWeakPasswordException -> R.string.error_password_too_short
        is FirebaseAuthInvalidCredentialsException -> R.string.error_auth_wrong_credentials
        is FirebaseAuthUserCollisionException -> R.string.error_auth_account_exists
        is FirebaseTooManyRequestsException -> R.string.error_too_many_requests
        is FirebaseNetworkException, is IOException -> R.string.error_no_internet
        is FirebaseFirestoreException -> when {
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_cloud_permission
            // Firestore database not created yet in the Firebase console.
            error.code == FirebaseFirestoreException.Code.NOT_FOUND -> R.string.error_firestore_not_created
            else -> R.string.error_cloud
        }
        // Authentication (or this sign-in method) is not switched on in the Firebase console.
        is FirebaseException if error.isAuthNotEnabled() -> R.string.error_auth_not_enabled
        is HttpException -> R.string.error_weather_service
        else -> R.string.error_generic
    }

    /**
     * Firebase reports a console set-up problem only in the message text:
     * CONFIGURATION_NOT_FOUND (Authentication never started) or
     * OPERATION_NOT_ALLOWED (Email/Password provider switched off).
     */
    private fun FirebaseException.isAuthNotEnabled(): Boolean {
        val text = message.orEmpty()
        return "CONFIGURATION_NOT_FOUND" in text || "OPERATION_NOT_ALLOWED" in text
    }

    @StringRes
    fun forValidation(error: ValidationError): Int = when (error) {
        ValidationError.EMPTY -> R.string.error_required
        ValidationError.INVALID_EMAIL -> R.string.error_invalid_email
        ValidationError.PASSWORD_TOO_SHORT -> R.string.error_password_too_short
        ValidationError.PASSWORD_TOO_WEAK -> R.string.error_password_weak
        ValidationError.PASSWORDS_DIFFER -> R.string.error_passwords_differ
    }
}
