package ro.weatherwear.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `email must be filled in and well formed`() {
        assertEquals(ValidationError.EMPTY, Validators.validateEmail("  "))
        assertEquals(ValidationError.INVALID_EMAIL, Validators.validateEmail("ana"))
        assertEquals(ValidationError.INVALID_EMAIL, Validators.validateEmail("ana@exemplu"))
        assertEquals(ValidationError.INVALID_EMAIL, Validators.validateEmail("ana exemplu@mail.ro"))
        assertNull(Validators.validateEmail("ana.pop@exemplu.ro"))
        assertNull(Validators.validateEmail(" ana@exemplu.ro "))
    }

    @Test
    fun `login only needs a password`() {
        assertEquals(ValidationError.EMPTY, Validators.validatePassword("", isRegistering = false))
        assertNull(Validators.validatePassword("x", isRegistering = false))
    }

    @Test
    fun `register needs a long enough password with letters and digits`() {
        assertEquals(ValidationError.PASSWORD_TOO_SHORT, Validators.validatePassword("ab1", isRegistering = true))
        assertEquals(ValidationError.PASSWORD_TOO_WEAK, Validators.validatePassword("abcdefgh", isRegistering = true))
        assertEquals(ValidationError.PASSWORD_TOO_WEAK, Validators.validatePassword("12345678", isRegistering = true))
        assertNull(Validators.validatePassword("vreme2026", isRegistering = true))
    }

    @Test
    fun `confirmation must match`() {
        assertEquals(ValidationError.EMPTY, Validators.validateConfirmation("vreme2026", ""))
        assertEquals(ValidationError.PASSWORDS_DIFFER, Validators.validateConfirmation("vreme2026", "vreme2025"))
        assertNull(Validators.validateConfirmation("vreme2026", "vreme2026"))
    }
}
