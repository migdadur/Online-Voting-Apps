// FirebaseService.kt - NEW FILE for Firebase operations
package com.example.demoapplication.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FirebaseService {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable.asStateFlow()

    init {
        // Check if Firebase is configured
        _isFirebaseAvailable.value = try {
            FirebaseAuth.getInstance() != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loginWithIdNumber(idNumber: String, password: String): Result<FirebaseUser> {
        return try {
            // Convert ID number to email format for Firebase
            val email = "${idNumber}@smartballot.com"
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUpWithIdNumber(idNumber: String, name: String, password: String): Result<FirebaseUser> {
        return try {
            val email = "${idNumber}@smartballot.com"
            val result = auth.createUserWithEmailAndPassword(email, password).await()

            // Save user data to Firestore
            val userData = hashMapOf(
                "idNumber" to idNumber,
                "name" to name,
                "email" to email,
                "isAdmin" to idNumber.startsWith("ADMIN"),
                "hasVoted" to emptyList<String>(),
                "createdAt" to System.currentTimeMillis()
            )

            firestore.collection("users")
                .document(result.user!!.uid)
                .set(userData)
                .await()

            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun isUserAdmin(uid: String): Boolean {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.getBoolean("isAdmin") ?: false
        } catch (e: Exception) {
            false
        }
    }
}