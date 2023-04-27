package online.transporteari.transportecarga.providers

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import online.transporteari.transportecarga.models.Booking

class BookingProvider {
    val db = Firebase.firestore.collection("Bookings")
    val authProvider: AuthProvider = AuthProvider()

    fun create(booking: Booking): Task<Void> {
        return db.document(authProvider.getId()).set(booking).addOnFailureListener {
            Log.e("FIRESTORE", "ERROR: ${it.message}")
        }
    }

    fun getBooking(): DocumentReference {
        return db.document(authProvider.getId())
    }

    fun remove(): Task<Void> {
        return db.document(authProvider.getId()).delete().addOnFailureListener {
            Log.e("FIRESTORE", "ERROR: ${it.message}")
        }
    }
}