package com.example.calonote.model

data class Meal(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val calories: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val items: List<MealItem> = emptyList(),
    val totalNutritiveValues: NutritiveValues = NutritiveValues()
)