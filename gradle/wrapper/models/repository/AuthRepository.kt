package com.example.demoapplication.repository

import android.util.Log
import com.example.demoapplication.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")

    // Sign Up with Email and Password
    suspend fun signUp(email: String, password: String, fullName: String): Result<FirebaseUser> {
        return try {
            // Create user in Firebase Auth
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user

            // Update user profile with name
            firebaseUser?.let { user ->
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build()
                user.updateProfile(profileUpdates).await()
            }

            // Store user data in Firestore
            firebaseUser?.let { user ->
                val userData = User(
                    userId = user.uid,
                    email = email,
                    fullName = fullName,
                    createdAt = com.google.firebase.Timestamp.now(),
                    isEmailVerified = user.isEmailVerified
                )
                usersCollection.document(user.uid).set(userData).await()
            }

            Result.success(firebaseUser!!)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign up failed", e)
            Result.failure(e)
        }
    }

    // Sign In with Email and Password
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> {
        return try {
            // Authenticate with Firebase
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user

            // Update last login time in Firestore
            firebaseUser?.let { user ->
                usersCollection.document(user.uid).update(
                    mapOf(
                        "lastLoginAt" to com.google.firebase.Timestamp.now(),
                        "email" to email
                    )
                ).await()
            }

            Result.success(firebaseUser!!)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Sign in failed", e)
            Result.failure(e)
        }
    }

    // Get current user data from Firestore
    suspend fun getUserData(): User? {
        val currentUser = auth.currentUser ?: return null
        return try {
            val document = usersCollection.document(currentUser.uid).get().await()
            document.toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to get user data", e)
            null
        }
    }

    // Sign Out
    fun signOut() {
        auth.signOut()
    }

    // Get current user from Auth
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // Check if user is logged in
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // Send email verification
    suspend fun sendEmailVerification(): Result<Unit> {
        return suspendCoroutine { continuation ->
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification()
                    .addOnSuccessListener {
                        continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { e ->
                        continuation.resumeWithException(e)
                    }
            } else {
                continuation.resumeWithException(Exception("No user logged in"))
            }
        }
    }

    // Reset password
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}