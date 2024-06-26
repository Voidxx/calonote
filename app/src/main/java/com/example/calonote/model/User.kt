package com.example.calonote.model

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val isEmailVerified: Boolean = false,
    val height: Double = 0.0, // in meters
    val weight: Double = 0.0, // in kilograms
    val age: Int = 0,
    val desiredWeight: Double = 0.0, // in kilograms
    val activityLevel: Int = 0 // 1 to 5
)