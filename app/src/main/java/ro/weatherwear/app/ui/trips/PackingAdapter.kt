package ro.weatherwear.app.ui.trips

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ro.weatherwear.app.data.model.PackingItem
import ro.weatherwear.app.databinding.ItemPackingBinding

/** Packing checklist. Click ticks an item; long click opens edit/delete. */
class PackingAdapter(
    private val onToggle: (position: Int) -> Unit,
    private val onLongClick: (position: Int, anchor: View) -> Unit,
) : ListAdapter<PackingItem, PackingAdapter.ItemViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder =
        ItemViewHolder(ItemPackingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ItemViewHolder(private val binding: ItemPackingBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Positions are read at click time, so they stay right after inserts and deletes.
            binding.root.setOnClickListener {
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(onToggle)
            }
            binding.root.setOnLongClickListener { view ->
                bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { onLongClick(it, view) }
                true
            }
        }

        fun bind(item: PackingItem) = with(binding) {
            itemCheck.isChecked = item.packed
            itemName.text = item.name
            itemName.paintFlags = if (item.packed) {
                itemName.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                itemName.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            itemName.alpha = if (item.packed) 0.6f else 1f
            itemCategory.text = item.category
            itemCategory.visibility = if (item.category.isBlank()) View.GONE else View.VISIBLE
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<PackingItem>() {
            override fun areItemsTheSame(old: PackingItem, new: PackingItem) =
                old.name == new.name && old.category == new.category
            override fun areContentsTheSame(old: PackingItem, new: PackingItem) = old == new
        }
    }
}
