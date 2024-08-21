package com.example.calonote.model

data class MealItem(
    val name: String = "",
    val weight: Float = 0f,
    val calories: Float = 0f,
    val nutritiveValues: NutritiveValues = NutritiveValues(),
    val imageData: String = ""
) {

    constructor() : this("", 0f, 0f, NutritiveValues(), "")
}