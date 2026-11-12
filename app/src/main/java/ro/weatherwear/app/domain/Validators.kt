package ro.weatherwear.app.domain

/** What can be wrong with a form field. The UI turns each into a message. */
enum class ValidationError {
    EMPTY,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_WEAK,
    PASSWORDS_DIFFER,
}

/**
 * Input validation for the login and register forms. Plain Kotlin (no
 * android.util.Patterns) so it runs in unit tests.
 */
object Validators {

    /** Firebase Authentication refuses passwords shorter than this. */
    const val MIN_PASSWORD_LENGTH = 6

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): ValidationError? = when {
        email.isBlank() -> ValidationError.EMPTY
        !EMAIL_REGEX.matches(email.trim()) -> ValidationError.INVALID_EMAIL
        else -> null
    }

    /**
     * On login we only check the field is filled in (the server decides if it's right).
     * On register the password needs [MIN_PASSWORD_LENGTH] characters, a letter and a digit.
     */
    fun validatePassword(password: String, isRegistering: Boolean): ValidationError? = when {
        password.isEmpty() -> ValidationError.EMPTY
        !isRegistering -> null
        password.length < MIN_PASSWORD_LENGTH -> ValidationError.PASSWORD_TOO_SHORT
        password.none { it.isLetter() } || password.none { it.isDigit() } -> ValidationError.PASSWORD_TOO_WEAK
        else -> null
    }

    fun validateConfirmation(password: String, confirmation: String): ValidationError? = when {
        confirmation.isEmpty() -> ValidationError.EMPTY
        password != confirmation -> ValidationError.PASSWORDS_DIFFER
        else -> null
    }
}
