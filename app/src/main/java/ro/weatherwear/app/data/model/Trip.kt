package ro.weatherwear.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

/*
 * Firestore maps documents onto these classes by reflection, so every field is a
 * `var` with a default value (that gives the no-argument constructor it needs).
 */

/** One thing to pack. */
data class PackingItem(
    var name: String = "",
    var category: String = "",
    var packed: Boolean = false,
)

/** A saved trip: stored in Firestore under users/{uid}/trips/{id}. */
data class Trip(
    @DocumentId var id: String = "",
    var destination: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    /** ISO dates (yyyy-MM-dd), so they sort correctly as text. */
    var startDate: String = "",
    var endDate: String = "",
    var purpose: String = "",
    var summary: String = "",
    var tip: String = "",
    /** "AI" when Gemini wrote the list, "RULES" when the offline rules did. */
    var source: String = SuggestionSource.RULES.name,
    var items: List<PackingItem> = emptyList(),
    var createdAt: Long = 0L,
) {
    /** Computed, so it is excluded from the Firestore document. */
    @get:Exclude
    val packedCount: Int get() = items.count { it.packed }
}
