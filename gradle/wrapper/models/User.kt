package com.example.demoapplication.models

import com.google.firebase.Timestamp

data class User(
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val lastLoginAt: Timestamp = Timestamp.now(),
    val isEmailVerified: Boolean = false
)

// For Firestore mapping
data class UserProfile(
    val fullName: String = "",
    val phoneNumber: String = "",
    val email: String = ""
)