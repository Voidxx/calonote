package com.example.calonote.controller

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.calonote.R
import com.example.calonote.client.MqttManager
import com.example.calonote.db.FirebaseManager
import com.example.calonote.model.Meal
import com.example.calonote.model.MealItem
import com.example.calonote.model.NutritiveValues
import com.example.calonote.util.NutritionCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.internal.wait
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureFoodItemActivity : AppCompatActivity() {
    private lateinit var btnCapture: Button
    private lateinit var tvWeight: TextView
    private lateinit var tvFoodName: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvNutrition: TextView
    private lateinit var btnAddAnotherItem: Button
    private lateinit var btnFinishMeal: Button
    private lateinit var mqttManager: MqttManager
    private val mealItems = mutableListOf<MealItem>()
    private var weightReceived = false
    private var currentWeight: Float = 0.0f
    private lateinit var ivFoodImage: ImageView
    private var capturedImageData: String = ""
    private lateinit var etMealName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_food_item)

        initializeViews()
        setListeners()
        NutritionCalculator.initialize(applicationContext)
        mqttManager = MqttManager(this)
        connectMqtt()
    }

    private fun initializeViews() {
        btnCapture = findViewById(R.id.btnCapture)
        tvWeight = findViewById(R.id.tvWeight)
        tvFoodName = findViewById(R.id.tvFoodName)
        tvNutrition = findViewById(R.id.tvNutrition)
        tvCalories = findViewById(R.id.tvCalories)
        ivFoodImage = findViewById(R.id.ivFoodImage)
        btnAddAnotherItem = findViewById(R.id.btnAddAnotherItem)
        btnFinishMeal = findViewById(R.id.btnFinishMeal)
        etMealName = findViewById(R.id.etMealName)
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
            } else {
                Toast.makeText(this@CaptureFoodItemActivity, "Failed to connect to MQTT broker", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun captureFood() {
        weightReceived = false
        currentWeight = 0.0f
        Log.d("CaptureFoodItemActivity", "Sending weight request")
        mqttManager.subscribe("esp32/weight", 0) { _, message ->
            handleWeightMessage(message.toString())
        }
        mqttManager.publish("esp32/weight_request", "request")

        lifecycleScope.launch {
            for (i in 1..10) { // Try for 10 seconds
                delay(1000)
                if (weightReceived) {
                    Log.d("CaptureFoodItemActivity", "Weight received: $currentWeight")
                    break
                }
            }
            if (!weightReceived) {
                Log.d("CaptureFoodItemActivity", "Weight not received after 10 seconds, proceeding with capture anyway")
            }
            Log.d("CaptureFoodItemActivity", "Sending capture request")
            mqttManager.subscribe("model/prediction", 0) { _, message ->
                handlePredictionMessage(message.toString())
            }
            mqttManager.publish("esp32cam/capture", "capture")
        }
    }

    private fun handleWeightMessage(weightStr: String?) {
            if (weightStr != null) {
                currentWeight = weightStr.toFloat()
            };
            weightReceived = true
    }

    private fun handlePredictionMessage(predictionStr: String) {
        runOnUiThread {
            try {
                val json = JSONObject(predictionStr)
                val predictedClass = json.getString("predicted_class")
                val confidence = json.getDouble("confidence")
                capturedImageData = json.getString("image_data")

                tvFoodName.text = "Food: $predictedClass"
                tvWeight.text = "Weight: ${currentWeight}g"

                val (calories, nutritiveValues) = NutritionCalculator.calculateNutritiveValues(predictedClass, currentWeight)

                val imageBytes = Base64.decode(capturedImageData, Base64.DEFAULT)
                Glide.with(this).load(imageBytes).apply(RequestOptions().diskCacheStrategy(
                    DiskCacheStrategy.NONE)).into(ivFoodImage)


                val mealItem = MealItem(
                    name = predictedClass,
                    weight = currentWeight,
                    calories = calories,
                    nutritiveValues = nutritiveValues,
                    imageData = capturedImageData
                )

                mealItems.add(mealItem)
                updateNutritionInfo(nutritiveValues, calories)
                updateUI()

            } catch (e: Exception) {
                Log.e("CaptureFoodItemActivity", "Error processing prediction", e)
            }
        }
    }

    private fun finishMeal() {
        FirebaseManager.getCurrentUser { user ->
            if (user == null) {
                Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
                return@getCurrentUser
            }
            val userInputMealName = etMealName.text.toString().trim()
            val mealName = if (userInputMealName.isNotEmpty()) {
                "$userInputMealName - ${getCurrentDateTime()}"
            } else {
                "Meal ${getCurrentDateTime()}"
            }

            val totalCalories = mealItems.sumOf { it.calories.toDouble() }.toFloat()
            val totalNutritiveValues = calculateTotalNutritiveValues()

            val meal = Meal(
                userId = user.uid,
                name = mealName,
                calories = totalCalories,
                items = mealItems.toList(),
                totalNutritiveValues = totalNutritiveValues
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
    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun updateNutritionInfo(nutritiveValues: NutritiveValues, calories: Float) {
        tvNutrition.text = """
            Calories: ${String.format("%.2f", calories)}
            Protein: ${String.format("%.2f", nutritiveValues.protein)}g
            Calcium: ${String.format("%.2f", nutritiveValues.calcium)}g
            Fat: ${String.format("%.2f", nutritiveValues.fat)}g
            Carbohydrates: ${String.format("%.2f", nutritiveValues.carbohydrates)}g
            Vitamins: ${String.format("%.2f", nutritiveValues.vitamins)}g
        """.trimIndent()
    }
    private fun calculateTotalNutritiveValues(): NutritiveValues {
        return mealItems.fold(NutritiveValues()) { acc, item ->
            NutritiveValues(
                protein = acc.protein + item.nutritiveValues.protein,
                calcium = acc.calcium + item.nutritiveValues.calcium,
                fat = acc.fat + item.nutritiveValues.fat,
                carbohydrates = acc.carbohydrates + item.nutritiveValues.carbohydrates,
                vitamins = acc.vitamins + item.nutritiveValues.vitamins
            )
        }
    }

    private fun addAnotherItem() {
        tvWeight.text = ""
        tvFoodName.text = ""
        tvCalories.text = ""
        tvNutrition.text = ""
    }



    private fun updateUI() {
        btnAddAnotherItem.isEnabled = mealItems.isNotEmpty()
        btnFinishMeal.isEnabled = mealItems.isNotEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}