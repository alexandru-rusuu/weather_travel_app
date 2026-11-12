package ro.weatherwear.app.ui.trips

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.Trip
import ro.weatherwear.app.databinding.ActivityTripsBinding
import ro.weatherwear.app.ui.common.TripUi
import ro.weatherwear.app.ui.common.applySystemBarInsets
import ro.weatherwear.app.ui.common.collectWhenStarted
import ro.weatherwear.app.ui.common.snackbar

/** Activity 3: the user's saved trips (from Cloud Firestore). */
class TripsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripsBinding
    private val viewModel: TripsViewModel by viewModels { TripsViewModel.Factory }
    private val adapter = TripAdapter(onClick = ::openTrip, onLongClick = ::showContextMenu)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.tripList.adapter = adapter
        binding.addTripFab.setOnClickListener {
            startActivity(Intent(this, NewTripActivity::class.java))
        }

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is TripsEvent.TripDeleted -> Snackbar
                    .make(binding.root, getString(R.string.trip_deleted, event.trip.destination), Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) { viewModel.undoDelete(event.trip) }
                    .show()
                is TripsEvent.ShowMessage -> binding.root.snackbar(event.message)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render(state: TripsUiState) = with(binding) {
        progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        adapter.submitList(state.trips)
        val empty = !state.isLoading && state.trips.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        emptyText.setText(state.error ?: R.string.trips_empty)
    }

    private fun openTrip(trip: Trip) {
        startActivity(TripDetailActivity.intent(this, trip.id))
    }

    /** Long press on a trip: open, share or delete it. */
    private fun showContextMenu(trip: Trip, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_trip_context, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.trip_open -> openTrip(trip)
                    R.id.trip_share -> share(trip)
                    R.id.trip_delete -> confirmDelete(trip)
                }
                true
            }
            show()
        }
    }

    private fun share(trip: Trip) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, trip.destination)
            .putExtra(Intent.EXTRA_TEXT, TripUi.shareText(trip))
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    private fun confirmDelete(trip: Trip) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_trip_title)
            .setMessage(getString(R.string.delete_trip_message, trip.destination))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete(trip) }
            .show()
    }
}
