package ro.weatherwear.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ro.weatherwear.app.data.model.PackingItem
import ro.weatherwear.app.data.model.Trip

/**
 * Saved trips in Cloud Firestore, at users/{uid}/trips/{tripId}, so each user
 * sees only their own (the security rules in the README enforce this).
 * Firestore also caches them on the device, so the list works offline too.
 */
class TripRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {

    private fun trips(): CollectionReference {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("No signed-in user")
        return db.collection("users").document(uid).collection("trips")
    }

    /** All trips, soonest first, updated live when anything changes. */
    fun observeTrips(): Flow<List<Trip>> = callbackFlow {
        val registration = trips().orderBy("startDate").addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObjects(Trip::class.java).orEmpty())
        }
        awaitClose { registration.remove() }
    }

    /** One trip, updated live; emits null if it gets deleted. */
    fun observeTrip(id: String): Flow<Trip?> = callbackFlow {
        val registration = trips().document(id).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Trip::class.java))
        }
        awaitClose { registration.remove() }
    }

    /** Saves a new trip and returns its id. */
    suspend fun addTrip(trip: Trip): String {
        val ref = trips().document()
        ref.set(trip.copy(createdAt = System.currentTimeMillis())).await()
        return ref.id
    }

    /** Puts a deleted trip back under its old id (the "Undo" on the snackbar). */
    suspend fun restoreTrip(trip: Trip) {
        trips().document(trip.id).set(trip).await()
    }

    suspend fun deleteTrip(id: String) {
        trips().document(id).delete().await()
    }

    suspend fun updateItems(tripId: String, items: List<PackingItem>) {
        // Plain maps, so the update does not depend on reflection over PackingItem.
        val plain = items.map { mapOf("name" to it.name, "category" to it.category, "packed" to it.packed) }
        trips().document(tripId).update("items", plain).await()
    }
}
