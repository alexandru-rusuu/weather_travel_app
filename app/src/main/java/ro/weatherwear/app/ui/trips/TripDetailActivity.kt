package ro.weatherwear.app.ui.trips

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.PackingItem
import ro.weatherwear.app.data.model.SuggestionSource
import ro.weatherwear.app.databinding.ActivityTripDetailBinding
import ro.weatherwear.app.databinding.DialogPackingItemBinding
import ro.weatherwear.app.domain.PackingCategory
import ro.weatherwear.app.ui.common.ForecastAdapter
import ro.weatherwear.app.ui.common.TripUi
import ro.weatherwear.app.ui.common.appContainer
import ro.weatherwear.app.ui.common.applySystemBarInsets
import ro.weatherwear.app.ui.common.collectWhenStarted
import ro.weatherwear.app.ui.common.snackbar

/** Activity 5: one trip — its forecast and a packing checklist the user ticks off. */
class TripDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailBinding
    private val viewModel: TripDetailViewModel by viewModels { TripDetailViewModel.Factory }
    private val forecastAdapter = ForecastAdapter(onDayClick = {})
    private val packingAdapter = PackingAdapter(
        onToggle = { viewModel.toggleItem(it) },
        onLongClick = ::showItemMenu,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTripDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        addMenuProvider(detailMenu, this)

        forecastAdapter.useFahrenheit = appContainer.settingsRepository.useFahrenheit
        binding.forecastList.adapter = forecastAdapter
        binding.packingList.adapter = packingAdapter
        binding.addItemFab.setOnClickListener { showItemDialog(position = null) }

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is TripDetailEvent.ShowMessage -> binding.root.snackbar(event.message)
                TripDetailEvent.Deleted -> finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun render(state: TripDetailUiState) = with(binding) {
        progress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        val trip = state.trip
        errorText.visibility = if (!state.isLoading && trip == null) View.VISIBLE else View.GONE
        errorText.text = state.error?.let(::getString)
        content.visibility = if (trip != null) View.VISIBLE else View.GONE
        addItemFab.visibility = content.visibility
        if (trip == null) return@with

        supportActionBar?.title = trip.destination
        val purpose = TripUi.purposeOf(trip)
        tripHeadline.text = getString(R.string.trip_headline, TripUi.emoji(purpose), getString(TripUi.label(purpose)))
        val start = TripUi.parseDate(trip.startDate)
        val end = TripUi.parseDate(trip.endDate)
        tripDates.text = if (start != null && end != null) {
            getString(R.string.dates_selected, TripUi.dateRange(start, end), TripUi.dayCountText(this@TripDetailActivity, start, end))
        } else ""
        tripSummary.text = trip.summary
        tripTip.visibility = if (trip.tip.isBlank()) View.GONE else View.VISIBLE
        tripTip.text = getString(R.string.tip_label, trip.tip)
        tripSource.setText(
            if (trip.source == SuggestionSource.AI.name) R.string.trip_source_ai else R.string.trip_source_rules
        )

        forecastProgress.visibility = if (state.isForecastLoading) View.VISIBLE else View.GONE
        forecastNote.visibility = if (state.forecastNote != null) View.VISIBLE else View.GONE
        forecastNote.text = state.forecastNote?.let(::getString)
        forecastList.visibility = if (state.forecast.isEmpty()) View.GONE else View.VISIBLE
        forecastAdapter.submitList(state.forecast)

        packingAdapter.submitList(trip.items)
        packingEmpty.visibility = if (trip.items.isEmpty()) View.VISIBLE else View.GONE
        packedCount.text = getString(R.string.packed_count, trip.packedCount, trip.items.size)
        packedProgress.max = trip.items.size.coerceAtLeast(1)
        packedProgress.setProgressCompat(trip.packedCount, true)
    }

    /** Long press on an item: edit or delete it. */
    private fun showItemMenu(position: Int, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_packing_item, menu)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.item_edit -> showItemDialog(position)
                    R.id.item_delete -> viewModel.deleteItem(position)
                }
                true
            }
            show()
        }
    }

    /** Add (position == null) or edit an item. The dialog stays open until the name is valid. */
    private fun showItemDialog(position: Int?) {
        val existing: PackingItem? = position?.let { viewModel.state.value.trip?.items?.getOrNull(it) }
        val dialogBinding = DialogPackingItemBinding.inflate(LayoutInflater.from(this))
        val categories = PackingCategory.ALL
        dialogBinding.categoryInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, categories))
        dialogBinding.categoryInput.setText(existing?.category?.takeIf { it in categories } ?: categories.first(), false)
        dialogBinding.nameInput.setText(existing?.name.orEmpty())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.action_add_item else R.string.action_edit_item)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null) // Set below, so a bad name does not close it.
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = dialogBinding.nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                dialogBinding.nameLayout.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            val category = dialogBinding.categoryInput.text.toString()
            if (position == null) viewModel.addItem(name, category) else viewModel.renameItem(position, name, category)
            dialog.dismiss()
        }
    }

    private val detailMenu = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) =
            menuInflater.inflate(R.menu.menu_trip_detail, menu)

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            val trip = viewModel.state.value.trip ?: return false
            when (menuItem.itemId) {
                R.id.action_share_trip -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, trip.destination)
                        .putExtra(Intent.EXTRA_TEXT, TripUi.shareText(trip))
                    startActivity(Intent.createChooser(send, getString(R.string.share_title)))
                }
                R.id.action_uncheck_all -> viewModel.uncheckAll()
                R.id.action_delete_trip -> MaterialAlertDialogBuilder(this@TripDetailActivity)
                    .setTitle(R.string.delete_trip_title)
                    .setMessage(getString(R.string.delete_trip_message, trip.destination))
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteTrip() }
                    .show()
                else -> return false
            }
            return true
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"

        fun intent(context: Context, tripId: String): Intent =
            Intent(context, TripDetailActivity::class.java).putExtra(EXTRA_TRIP_ID, tripId)
    }
}
