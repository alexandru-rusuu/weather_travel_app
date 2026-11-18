package ro.weatherwear.app.ui.trips

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.City
import ro.weatherwear.app.data.model.PackingSuggestion
import ro.weatherwear.app.data.model.SuggestionSource
import ro.weatherwear.app.data.model.TripPurpose
import ro.weatherwear.app.databinding.ActivityNewTripBinding
import ro.weatherwear.app.ui.common.ForecastAdapter
import ro.weatherwear.app.ui.common.TripUi
import ro.weatherwear.app.ui.common.appContainer
import ro.weatherwear.app.ui.common.applySystemBarInsets
import ro.weatherwear.app.ui.common.collectWhenStarted
import ro.weatherwear.app.ui.common.snackbar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Activity 4: plan a trip — destination, dates, purpose — and get a packing list. */
class NewTripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewTripBinding
    private val viewModel: NewTripViewModel by viewModels { NewTripViewModel.Factory }
    private val forecastAdapter = ForecastAdapter(onDayClick = {})

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNewTripBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        forecastAdapter.useFahrenheit = appContainer.settingsRepository.useFahrenheit
        binding.forecastList.adapter = forecastAdapter
        setUpListeners()

        collectWhenStarted(viewModel.state, ::render)
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is NewTripEvent.ChooseCity -> chooseCity(event.cities)
                is NewTripEvent.ShowMessage -> binding.root.snackbar(event.message)
                NewTripEvent.Saved -> {
                    Toast.makeText(this, R.string.trip_saved, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setUpListeners() = with(binding) {
        destinationLayout.setEndIconOnClickListener { search() }
        destinationInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        destinationInput.doAfterTextChanged { viewModel.onDestinationEdited(it?.toString().orEmpty()) }
        datesButton.setOnClickListener { pickDates() }
        purposeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val purpose = when (checkedIds.firstOrNull()) {
                R.id.chipCityBreak -> TripPurpose.CITY_BREAK
                R.id.chipMountain -> TripPurpose.MOUNTAIN
                R.id.chipBusiness -> TripPurpose.BUSINESS
                else -> TripPurpose.LEISURE
            }
            viewModel.setPurpose(purpose)
        }
        generateButton.setOnClickListener {
            hideKeyboard()
            viewModel.generate()
        }
        saveButton.setOnClickListener { viewModel.save() }
    }

    private fun search() {
        hideKeyboard()
        viewModel.searchDestination(binding.destinationInput.text.toString())
    }

    private fun render(state: NewTripUiState) = with(binding) {
        destinationLayout.error = state.destinationError?.let(::getString)
        selectedCity.visibility = if (state.city != null) View.VISIBLE else View.GONE
        selectedCity.text = state.city?.detailedName

        val start = state.startDate
        val end = state.endDate
        datesButton.text = if (start != null && end != null) {
            getString(R.string.dates_selected, TripUi.dateRange(start, end), TripUi.dayCountText(this@NewTripActivity, start, end))
        } else getString(R.string.action_choose_dates)
        datesError.visibility = if (state.datesError != null) View.VISIBLE else View.GONE
        datesError.text = state.datesError?.let(::getString)

        progress.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        generateButton.isEnabled = !state.isBusy
        saveButton.isEnabled = !state.isBusy
        generateButton.setText(if (state.isGenerating) R.string.generating else R.string.action_generate)

        renderSuggestion(state.suggestion, state)
    }

    private fun renderSuggestion(suggestion: PackingSuggestion?, state: NewTripUiState) = with(binding) {
        resultCard.visibility = if (suggestion != null) View.VISIBLE else View.GONE
        if (suggestion == null) return@with
        val fromAi = suggestion.source == SuggestionSource.AI
        sourceChip.setText(if (fromAi) R.string.source_ai else R.string.source_rules)
        sourceChip.setChipIconResource(if (fromAi) R.drawable.ic_auto_awesome else R.drawable.ic_rule)
        resultSummary.text = suggestion.summary
        forecastList.visibility = if (state.forecast.isEmpty()) View.GONE else View.VISIBLE
        forecastAdapter.submitList(state.forecast)
        resultItems.text = suggestion.items.groupBy { it.category }.entries.joinToString("\n\n") { (category, items) ->
            category + "\n" + items.joinToString("\n") { "• " + it.name }
        }
        resultTip.visibility = if (suggestion.tip.isBlank()) View.GONE else View.VISIBLE
        resultTip.text = suggestion.tip
        resultOfflineNote.visibility = if (suggestion.aiFailed) View.VISIBLE else View.GONE
    }

    private fun chooseCity(cities: List<City>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_city_title)
            .setItems(cities.map { it.detailedName }.toTypedArray()) { _, which -> viewModel.selectCity(cities[which]) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Material date range picker; days before today cannot be picked. */
    private fun pickDates() {
        val constraints = CalendarConstraints.Builder()
            .setStart(MaterialDatePicker.todayInUtcMilliseconds())
            .setValidator(DateValidatorPointForward.now())
            .build()
        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.new_trip_step_when)
            .setCalendarConstraints(constraints)
        val state = viewModel.state.value
        if (state.startDate != null && state.endDate != null) {
            builder.setSelection(Pair(state.startDate.toUtcMillis(), state.endDate.toUtcMillis()))
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: start
            viewModel.setDates(start.toLocalDate(), end.toLocalDate())
        }
        picker.show(supportFragmentManager, "trip_dates")
    }

    // The date picker works with UTC midnight timestamps.
    private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
