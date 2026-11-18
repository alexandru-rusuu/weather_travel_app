package ro.weatherwear.app.ui.common

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.DailyForecast
import ro.weatherwear.app.databinding.ItemForecastDayBinding

/** Horizontal strip of day cards. Used on the main screen and in trip details. */
class ForecastAdapter(
    private val onDayClick: (DailyForecast) -> Unit,
) : ListAdapter<DailyForecast, ForecastAdapter.DayViewHolder>(DIFF) {

    var useFahrenheit: Boolean = false
        @SuppressLint("NotifyDataSetChanged") // Every visible card changes unit at once.
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder =
        DayViewHolder(ItemForecastDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) = holder.bind(getItem(position))

    inner class DayViewHolder(private val binding: ItemForecastDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(day: DailyForecast) = with(binding) {
            dayName.text = WeatherUi.dayName(day.date)
            dayDate.text = WeatherUi.shortDate(day.date)
            dayIcon.setImageResource(WeatherUi.icon(day.weatherCode))
            dayMax.text = WeatherUi.temperature(day.tempMax, useFahrenheit)
            dayMin.text = WeatherUi.temperature(day.tempMin, useFahrenheit)
            dayRain.text = root.context.getString(R.string.precipitation_percent, day.precipitationProbability)
            root.contentDescription = root.context.getString(
                R.string.cd_forecast_day,
                WeatherUi.dayName(day.date),
                root.context.getString(WeatherUi.description(day.weatherCode)),
                dayMax.text, dayMin.text,
            )
            root.setOnClickListener { onDayClick(day) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<DailyForecast>() {
            override fun areItemsTheSame(old: DailyForecast, new: DailyForecast) = old.date == new.date
            override fun areContentsTheSame(old: DailyForecast, new: DailyForecast) = old == new
        }
    }
}
