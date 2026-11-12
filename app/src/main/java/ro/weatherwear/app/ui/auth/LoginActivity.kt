package ro.weatherwear.app.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import ro.weatherwear.app.R
import ro.weatherwear.app.databinding.ActivityLoginBinding
import ro.weatherwear.app.ui.common.appContainer
import ro.weatherwear.app.ui.common.applySystemBarInsets
import ro.weatherwear.app.ui.common.collectWhenStarted
import ro.weatherwear.app.ui.common.snackbar
import ro.weatherwear.app.ui.main.MainActivity

/** Activity 1: sign in or create an account (email + password, or Google). */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firebaseReady = appContainer.isFirebaseConfigured

        // Already signed in: go straight to the weather.
        if (firebaseReady && appContainer.authRepository.currentUser != null) {
            openMain()
            return
        }

        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        if (!firebaseReady) {
            // Without google-services.json there is no backend to log in to: say so plainly.
            binding.configWarning.visibility = View.VISIBLE
            binding.primaryButton.isEnabled = false
            binding.googleButton.isEnabled = false
            binding.forgotPassword.isEnabled = false
            return
        }

        setUpListeners()
        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                LoginEvent.SignedIn -> openMain()
                is LoginEvent.ShowMessage -> binding.root.snackbar(event.message)
            }
        }
    }

    private fun setUpListeners() = with(binding) {
        primaryButton.setOnClickListener { submit() }
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && !viewModel.state.value.isRegisterMode) {
                submit(); true
            } else false
        }
        confirmInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        toggleModeButton.setOnClickListener { viewModel.toggleMode() }
        forgotPassword.setOnClickListener { viewModel.resetPassword(emailInput.text.toString()) }
        googleButton.setOnClickListener { startGoogleSignIn() }

        emailInput.doAfterTextChanged { viewModel.onInputChanged() }
        passwordInput.doAfterTextChanged { viewModel.onInputChanged() }
        confirmInput.doAfterTextChanged { viewModel.onInputChanged() }
    }

    private fun submit() = viewModel.submit(
        binding.emailInput.text.toString(),
        binding.passwordInput.text.toString(),
        binding.confirmInput.text.toString(),
    )

    private fun render(state: LoginUiState) = with(binding) {
        val register = state.isRegisterMode
        confirmLayout.visibility = if (register) View.VISIBLE else View.GONE
        forgotPassword.visibility = if (register) View.GONE else View.VISIBLE
        primaryButton.setText(if (register) R.string.action_register else R.string.action_login)
        toggleModeButton.setText(if (register) R.string.login_have_account else R.string.login_no_account)
        passwordInput.imeOptions = if (register) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
        passwordLayout.helperText = if (register) getString(R.string.password_helper) else null

        emailLayout.error = state.emailError?.let(::getString)
        passwordLayout.error = state.passwordError?.let(::getString)
        confirmLayout.error = state.confirmError?.let(::getString)

        progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        primaryButton.isEnabled = !state.isLoading
        googleButton.isEnabled = !state.isLoading
        toggleModeButton.isEnabled = !state.isLoading
    }

    /**
     * "Sign in with Google" through Credential Manager. The OAuth client id is
     * generated from google-services.json, so it is looked up by name: the
     * project still compiles before Firebase is set up.
     */
    @SuppressLint("DiscouragedApi")
    private fun startGoogleSignIn() {
        val clientIdRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (clientIdRes == 0) {
            binding.root.snackbar(R.string.error_google_not_configured)
            return
        }
        val option = GetSignInWithGoogleOption.Builder(getString(clientIdRes)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        lifecycleScope.launch {
            try {
                val credential = CredentialManager.create(this@LoginActivity)
                    .getCredential(this@LoginActivity, request).credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    viewModel.signInWithGoogle(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                } else {
                    binding.root.snackbar(R.string.error_google_failed)
                }
            } catch (_: GetCredentialCancellationException) {
                // The user closed the account picker: nothing to report.
            } catch (_: NoCredentialException) {
                binding.root.snackbar(R.string.error_google_no_account)
            } catch (_: GetCredentialException) {
                binding.root.snackbar(R.string.error_google_failed)
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
