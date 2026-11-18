package ro.weatherwear.app.ui.trips

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ro.weatherwear.app.R
import ro.weatherwear.app.data.model.Trip
import ro.weatherwear.app.databinding.ItemTripBinding
import ro.weatherwear.app.ui.common.TripUi

/** List of saved trips. Click opens a trip; long click opens its context menu. */
class TripAdapter(
    private val onClick: (Trip) -> Unit,
    private val onLongClick: (Trip, View) -> Unit,
) : ListAdapter<Trip, TripAdapter.TripViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder =
        TripViewHolder(ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) = holder.bind(getItem(position))

    inner class TripViewHolder(private val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(trip: Trip) = with(binding) {
            val context = root.context
            val purpose = TripUi.purposeOf(trip)
            tripIcon.setImageResource(TripUi.icon(purpose))
            tripDestination.text = trip.destination

            val start = TripUi.parseDate(trip.startDate)
            val end = TripUi.parseDate(trip.endDate)
            tripDates.text = if (start != null && end != null) {
                context.getString(
                    R.string.trip_dates_line,
                    TripUi.dateRange(start, end),
                    TripUi.dayCountText(context, start, end),
                    context.getString(TripUi.label(purpose)),
                )
            } else ""

            val total = trip.items.size
            tripProgressText.text = context.getString(R.string.packed_progress, trip.packedCount, total)
            tripProgress.max = total.coerceAtLeast(1)
            tripProgress.setProgressCompat(trip.packedCount, false)

            root.setOnClickListener { onClick(trip) }
            root.setOnLongClickListener {
                onLongClick(trip, it)
                true
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Trip>() {
            override fun areItemsTheSame(old: Trip, new: Trip) = old.id == new.id
            override fun areContentsTheSame(old: Trip, new: Trip) = old == new
        }
    }
}
