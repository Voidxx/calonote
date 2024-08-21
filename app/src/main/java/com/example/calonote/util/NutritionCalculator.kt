package com.example.calonote.util

import android.content.Context
import android.util.Log
import com.example.calonote.model.NutritiveValues
import java.io.BufferedReader
import java.io.InputStreamReader

object NutritionCalculator {
    private lateinit var nutritionTable: Map<String, NutritiveValues>

    fun initialize(context: Context) {
        nutritionTable = mutableMapOf()
        val inputStream = context.assets.open("nutrition101.csv")
        val reader = BufferedReader(InputStreamReader(inputStream))
        reader.readLine() // Skip header
        reader.forEachLine { line ->
            val values = line.split(",")
            if (values.size >= 7) {
                val name = values[1].toLowerCase().trim()
                val nutritiveValues = NutritiveValues(
                    protein = values[2].toFloatOrNull() ?: 0f,
                    calcium = values[3].toFloatOrNull() ?: 0f,
                    fat = values[4].toFloatOrNull() ?: 0f,
                    carbohydrates = values[5].toFloatOrNull() ?: 0f,
                    vitamins = values[6].toFloatOrNull() ?: 0f
                )
                (nutritionTable as MutableMap<String, NutritiveValues>)[name] = nutritiveValues
                Log.d("NutritionCalculator", "Loaded $name: $nutritiveValues")
            }
        }
        Log.d("NutritionCalculator", "Loaded ${nutritionTable.size} food items")
    }

    fun calculateNutritiveValues(foodName: String, weight: Float): Pair<Float, NutritiveValues> {
        val normalizedFoodName = foodName.toLowerCase().trim()
        val baseValues = nutritionTable[normalizedFoodName]
        if (baseValues == null) {
            Log.w("NutritionCalculator", "No nutritive values found for $normalizedFoodName")
            return Pair(0f, NutritiveValues())
        }

        val scaleFactor = weight / 100f
        val calories = (baseValues.protein * 4 + baseValues.carbohydrates * 4 + baseValues.fat * 9) * scaleFactor
        return Pair(calories, NutritiveValues(
            protein = baseValues.protein * scaleFactor,
            calcium = baseValues.calcium * scaleFactor,
            fat = baseValues.fat * scaleFactor,
            carbohydrates = baseValues.carbohydrates * scaleFactor,
            vitamins = baseValues.vitamins * scaleFactor
        ))
    }
}