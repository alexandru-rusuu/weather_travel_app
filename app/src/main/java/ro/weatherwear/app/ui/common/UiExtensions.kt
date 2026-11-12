package ro.weatherwear.app.ui.common

import android.app.Activity
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import ro.weatherwear.app.WeatherWearApp
import ro.weatherwear.app.di.AppContainer

/** The app-wide dependencies. */
val Activity.appContainer: AppContainer
    get() = (application as WeatherWearApp).container

/**
 * Android 15+ draws apps edge to edge. Pad [root] by the system bars and the
 * keyboard so nothing hides behind them.
 */
fun applySystemBarInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}

/** Collects [flow] only while the screen is visible, the lifecycle-safe way. */
fun <T> AppCompatActivity.collectWhenStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

fun View.snackbar(@StringRes message: Int, length: Int = Snackbar.LENGTH_LONG): Snackbar =
    Snackbar.make(this, message, length).also { it.show() }
