package com.example.calonote.db

import com.example.calonote.model.Meal
import com.example.calonote.model.NutritiveValues
import com.example.calonote.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Calendar

object FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun registerUser(email: String, password: String, username: String, callback: (Boolean, String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = User(auth.uid!!, email, username, false)
                    db.collection("users").document(auth.uid!!).set(user)
                        .addOnSuccessListener {
                            auth.currentUser?.sendEmailVerification()
                            callback(true, "Verification email sent")
                        }
                        .addOnFailureListener { callback(false, it.message ?: "Error saving user") }
                } else {
                    callback(false, task.exception?.message ?: "Registration failed")
                }
            }
    }

    fun loginUser(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (auth.currentUser?.isEmailVerified == true) {
                        callback(true, "Login successful")
                    } else {
                        callback(false, "Please verify your email first")
                    }
                } else {
                    callback(false, task.exception?.message ?: "Login failed")
                }
            }
    }

    fun getCurrentUser(callback: (User?) -> Unit) {
        val uid = auth.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val user = doc.toObject(User::class.java)
                    callback(user)
                }
                .addOnFailureListener { callback(null) }
        } else {
            callback(null)
        }
    }

    fun addMeal(meal: Meal, callback: (Boolean, String) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("meals")
            .add(meal)
            .addOnSuccessListener {
                callback(true, "Meal added successfully")
            }
            .addOnFailureListener { e ->
                callback(false, e.message ?: "Failed to add meal")
            }
    }

    fun getDailyNutrition(userId: String, callback: (DailyNutrition) -> Unit) {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        db.collection("meals")
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .get()
            .addOnSuccessListener { documents ->
                var totalCalories = 0f
                val totalNutritiveValues = NutritiveValues()

                for (document in documents) {
                    val meal = document.toObject(Meal::class.java)
                    totalCalories += meal.calories
                    totalNutritiveValues.protein += meal.totalNutritiveValues.protein
                    totalNutritiveValues.calcium += meal.totalNutritiveValues.calcium
                    totalNutritiveValues.fat += meal.totalNutritiveValues.fat
                    totalNutritiveValues.carbohydrates += meal.totalNutritiveValues.carbohydrates
                    totalNutritiveValues.vitamins += meal.totalNutritiveValues.vitamins
                }

                val dailyNutrition = DailyNutrition(totalCalories, totalNutritiveValues)
                callback(dailyNutrition)
            }
            .addOnFailureListener { exception ->
                println("Error getting daily nutrition: ${exception.message}")
                callback(DailyNutrition(0f, NutritiveValues()))
            }
    }

    fun getMeals(userId: String, callback: (List<Meal>) -> Unit) {
        db.collection("meals")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val meals = documents.mapNotNull { it.toObject(Meal::class.java) }
                callback(meals)
            }
            .addOnFailureListener { exception ->
                println("Error getting meals: ${exception.message}")
                callback(emptyList())
            }
    }

    fun resetPassword(email: String, callback: (Boolean, String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, "Password reset email sent to $email")
                } else {
                    callback(false, task.exception?.message ?: "Failed to send reset email")
                }
            }
    }
    fun updateUser(user: User, callback: (Boolean) -> Unit) {
        val updatedUser = calculateDailyNeeds(user)
        db.collection("users").document(user.uid).set(updatedUser)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener { callback(false) }
    }


    private fun calculateDailyNeeds(user: User) {
        val bmr = when {
            user.weight > 0 && user.height > 0 && user.age > 0 -> {
                // Mifflin-St Jeor Equation
                (10 * user.weight) + (6.25 * user.height) - (5 * user.age) + 5
            }
            else -> 2000.0 // Default value if data is missing
        }

        val activityFactor = when (user.activityLevel) {
            1 -> 1.2
            2 -> 1.375
            3 -> 1.55
            4 -> 1.725
            5 -> 1.9
            else -> 1.2
        }

        val dailyCalories = (bmr * activityFactor).toFloat()
        val dailyProtein = (dailyCalories * 0.3 / 4).toFloat()
        val dailyFat = (dailyCalories * 0.3 / 9).toFloat()
        val dailyCarbs = (dailyCalories * 0.4 / 4).toFloat()

        val updatedUser = user.copy(
            dailyCalories = dailyCalories,
            dailyProtein = dailyProtein,
            dailyFat = dailyFat,
            dailyCarbs = dailyCarbs,
        )

        db.collection("users").document(user.uid).set(updatedUser)
    }

    data class DailyNutrition(
        val calories: Float,
        val totalNutritiveValues: NutritiveValues
    )

}