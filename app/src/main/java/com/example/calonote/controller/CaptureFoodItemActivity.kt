    package com.example.calonote.controller

    import android.app.Activity
    import android.content.Intent
    import android.os.Bundle
    import android.widget.Button
    import android.widget.TextView
    import android.widget.Toast
    import androidx.appcompat.app.AppCompatActivity
    import com.example.calonote.R
    import com.example.calonote.client.HardwareClient
    import com.example.calonote.db.FirebaseManager
    import com.example.calonote.model.Meal
    import com.google.firebase.firestore.FirebaseFirestore

    class CaptureFoodItemActivity : AppCompatActivity() {
        private lateinit var btnCapture: Button
        private lateinit var tvWeight: TextView
        private lateinit var tvFoodName: TextView
        private lateinit var tvCalories: TextView
        private lateinit var btnAddAnotherItem: Button
        private lateinit var btnFinishMeal: Button
        private var ESP32_ARDUINO_URL: String? = null
        private var isConnected: Boolean = false
        private val mealItems = mutableListOf<Pair<String, Int>>()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_capture_food_item)

            ESP32_ARDUINO_URL = intent.getStringExtra("ESP32_ARDUINO_URL")
            isConnected = intent.getBooleanExtra("isConnected", false)

            if (ESP32_ARDUINO_URL == null || !isConnected) {
                Toast.makeText(this, "Error: ESP32 not connected", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            initializeViews()
            setListeners()
        }

        private fun initializeViews() {
            btnCapture = findViewById(R.id.btnCapture)
            tvWeight = findViewById(R.id.tvWeight)
            tvFoodName = findViewById(R.id.tvFoodName)
            tvCalories = findViewById(R.id.tvCalories)
            btnAddAnotherItem = findViewById(R.id.btnAddAnotherItem)
            btnFinishMeal = findViewById(R.id.btnFinishMeal)
        }

        private fun setListeners() {
            btnCapture.setOnClickListener { captureFood() }
            btnAddAnotherItem.setOnClickListener { addAnotherItem() }
            btnFinishMeal.setOnClickListener { finishMeal() }
        }

        private fun captureFood() {
            HardwareClient.getWeightFromArduino("$ESP32_ARDUINO_URL/weight") { weight, exception ->
                if (exception != null) {
                    runOnUiThread {
                        Toast.makeText(this, "Error getting weight: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@getWeightFromArduino
                }

                runOnUiThread {
                    tvWeight.text = "Weight: ${weight}g"
                    // Here you would typically use machine learning to identify the food and estimate calories
                    // For this example, we'll just use dummy data
                    val foodName = "Apple"
                    val calories = 95
                    tvFoodName.text = "Food: $foodName"
                    tvCalories.text = "Calories: $calories"
//
//                    mealItems.add(Pair(foodName, calories))
//                    updateUI()
                }
            }
        }

        private fun addAnotherItem() {
            tvWeight.text = ""
            tvFoodName.text = ""
            tvCalories.text = ""
        }

        private fun finishMeal() {
            FirebaseManager.getCurrentUser { user ->
                if (user == null) {
                    Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
                    return@getCurrentUser
                }

                val mealName = "Meal ${System.currentTimeMillis()}"
                val totalCalories = mealItems.sumOf { it.second }

                val meal = Meal(
                    userId = user.uid,
                    name = mealName,
                    calories = totalCalories
                )

                FirebaseManager.addMeal(meal) { success, message ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, "Meal added successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            Toast.makeText(this, "Failed to add meal: $message", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        private fun updateUI() {
            btnAddAnotherItem.isEnabled = mealItems.isNotEmpty()
            btnFinishMeal.isEnabled = mealItems.isNotEmpty()
        }
    }