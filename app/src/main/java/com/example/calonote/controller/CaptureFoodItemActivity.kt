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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

class CaptureFoodItemActivity : AppCompatActivity() {
    private lateinit var btnCapture: Button
    private lateinit var tvWeight: TextView
    private lateinit var tvFoodName: TextView
    private lateinit var tvCalories: TextView
    private lateinit var btnAddAnotherItem: Button
    private lateinit var btnFinishMeal: Button
    private var isConnected: Boolean = false
    private val mealItems = mutableListOf<Pair<String, Int>>()
    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var mqttConfig: JSONObject
    private lateinit var mqttManager: MqttManager


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
                subscribeToWeightTopic()
            } else {
                Toast.makeText(this@CaptureFoodItemActivity, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun subscribeToWeightTopic() {
        mqttManager.subscribe("esp32/weight", 0) { topic, message ->
            when (topic) {
                "esp32/weight" -> handleWeightMessage(message.toString())
            }
        }
    }

    private fun captureFood() {
        mqttManager.publish("esp32/weight_request", "request")
    }
    private fun handleWeightMessage(weightStr: String?) {
        runOnUiThread {
            tvWeight.text = "Weight: ${weightStr}g"
            // Here you would typically use machine learning to identify the food and estimate calories
            // For this example, we'll just use dummy data
            val foodName = "Apple"
            val calories = 95
            tvFoodName.text = "Food: $foodName"
            tvCalories.text = "Calories: $calories"

            mealItems.add(Pair(foodName, calories))
            updateUI()
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

    override fun onDestroy() {
        super.onDestroy()
    }
}