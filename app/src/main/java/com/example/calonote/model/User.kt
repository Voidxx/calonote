package com.example.calonote.model

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val isEmailVerified: Boolean = false,
    val height: Double = 0.0,
    val weight: Double = 0.0,
    val age: Int = 0,
    val desiredWeight: Double = 0.0,
    val activityLevel: Int = 0,
    val dailyCalories: Float = 0f,
    val dailyProtein: Float = 0f,
    val dailyFat: Float = 0f,
    val dailyCarbs: Float = 0f,
    val dailyVitamins: Float = 0f
)