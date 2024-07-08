package com.example.calonote.controller

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.calonote.R
import com.example.calonote.client.MqttManager
import com.example.calonote.db.FirebaseManager
import com.example.calonote.model.Meal
import com.example.calonote.model.MealItem
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

class CaptureFoodItemActivity : AppCompatActivity() {
    private lateinit var btnCapture: Button
    private lateinit var tvWeight: TextView
    private lateinit var tvFoodName: TextView
    private lateinit var tvCalories: TextView
    private lateinit var btnAddAnotherItem: Button
    private lateinit var btnFinishMeal: Button
    private lateinit var mqttManager: MqttManager
    private val mealItems = mutableListOf<MealItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_food_item)

        initializeViews()
        setListeners()
        mqttManager = MqttManager(this)
        connectMqtt()
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

    private fun connectMqtt() {
        lifecycleScope.launch {
            val connected = mqttManager.connect()
            if (connected) {
                Toast.makeText(this@CaptureFoodItemActivity, "Connected to MQTT broker", Toast.LENGTH_SHORT).show()
                subscribeToTopics()
            } else {
                Toast.makeText(this@CaptureFoodItemActivity, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun subscribeToTopics() {
        mqttManager.subscribe("esp32/weight", 0) { _, message ->
            handleWeightMessage(message.toString())
        }
        mqttManager.subscribe("model/prediction", 0) { _, message ->
            handlePredictionMessage(message.toString())
        }
    }

    private fun captureFood() {
        mqttManager.publish("esp32/weight_request", "request")
        mqttManager.publish("esp32cam/capture", "capture")
    }

    private fun handleWeightMessage(weightStr: String?) {
        runOnUiThread {
            tvWeight.text = "Weight: ${weightStr}g"
        }
    }

    private fun handlePredictionMessage(predictionStr: String) {
        runOnUiThread {
            try {
                val json = JSONObject(predictionStr)
                val predictedClass = json.getString("predicted_class")
                val confidence = json.getDouble("confidence")

                tvFoodName.text = "Food: $predictedClass"
                tvCalories.text = "Confidence: ${String.format("%.2f", confidence * 100)}%"

                // Parse weight from tvWeight
                val weightStr = tvWeight.text.toString()
                val weight = weightStr.substringBefore("g").substringAfter(": ").toFloatOrNull() ?: 0f

                mealItems.add(MealItem(predictedClass, weight))
                updateUI()
            } catch (e: Exception) {
                Toast.makeText(this, "Error parsing prediction: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finishMeal() {
        FirebaseManager.getCurrentUser { user ->
            if (user == null) {
                Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
                return@getCurrentUser
            }

            val mealName = "Meal ${System.currentTimeMillis()}"
            // Calorie calculation left out for now
            val totalCalories = 0 // This will be calculated later

            val meal = Meal(
                userId = user.uid,
                name = mealName,
                calories = totalCalories,
                items = mealItems.toList() // Convert mutableList to List
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

    private fun addAnotherItem() {
        tvWeight.text = ""
        tvFoodName.text = ""
        tvCalories.text = ""
    }


    private fun updateUI() {
        btnAddAnotherItem.isEnabled = mealItems.isNotEmpty()
        btnFinishMeal.isEnabled = mealItems.isNotEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttManager.disconnect()
    }
}