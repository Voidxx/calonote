package com.example.calonote.model

data class Meal(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val calories: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val items: List<MealItem> = emptyList()
)