    package com.example.calonote.controller

    import android.animation.ObjectAnimator
    import android.content.Intent
    import android.os.Bundle
    import android.view.View
    import android.view.animation.DecelerateInterpolator
    import android.widget.TextView
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.content.ContextCompat
    import com.example.calonote.R
    import com.example.calonote.db.FirebaseManager
    import com.example.calonote.model.User
    import com.google.android.material.progressindicator.CircularProgressIndicator

    class MainActivity : AppCompatActivity() {
        private lateinit var tvWelcome: TextView
        private lateinit var caloriesProgressIndicator: CircularProgressIndicator
        private lateinit var proteinProgressIndicator: CircularProgressIndicator
        private lateinit var fatProgressIndicator: CircularProgressIndicator
        private lateinit var carbsProgressIndicator: CircularProgressIndicator
        private lateinit var vitaminsProgressIndicator: CircularProgressIndicator
        private lateinit var tvCaloriesValue: TextView
        private lateinit var tvProteinValue: TextView
        private lateinit var tvFatValue: TextView
        private lateinit var tvCarbsValue: TextView
        private lateinit var tvVitaminsValue: TextView
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            initializeViews()

            FirebaseManager.getCurrentUser { user ->
                if (user != null) {
                    tvWelcome.text = "Welcome, ${user.username}"
                    updateNutritionProgress(user)
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }

        private fun initializeViews() {
            tvWelcome = findViewById(R.id.tvWelcome)

            val caloriesLayout = findViewById<View>(R.id.caloriesLayout)
            val proteinLayout = findViewById<View>(R.id.proteinLayout)
            val fatLayout = findViewById<View>(R.id.fatLayout)
            val carbsLayout = findViewById<View>(R.id.carbsLayout)
            val vitaminsLayout = findViewById<View>(R.id.vitaminsLayout)

            caloriesProgressIndicator = caloriesLayout.findViewById(R.id.progressIndicator)
            proteinProgressIndicator = proteinLayout.findViewById(R.id.progressIndicator)
            fatProgressIndicator = fatLayout.findViewById(R.id.progressIndicator)
            carbsProgressIndicator = carbsLayout.findViewById(R.id.progressIndicator)
            vitaminsProgressIndicator = vitaminsLayout.findViewById(R.id.progressIndicator)

            tvCaloriesValue = caloriesLayout.findViewById(R.id.tvValue)
            tvProteinValue = proteinLayout.findViewById(R.id.tvValue)
            tvFatValue = fatLayout.findViewById(R.id.tvValue)
            tvCarbsValue = carbsLayout.findViewById(R.id.tvValue)
            tvVitaminsValue = vitaminsLayout.findViewById(R.id.tvValue)

            caloriesLayout.findViewById<TextView>(R.id.tvLabel).text = "Calories"
            proteinLayout.findViewById<TextView>(R.id.tvLabel).text = "Protein"
            fatLayout.findViewById<TextView>(R.id.tvLabel).text = "Fat"
            carbsLayout.findViewById<TextView>(R.id.tvLabel).text = "Carbs"
            vitaminsLayout.findViewById<TextView>(R.id.tvLabel).text = "Vitamins"

            caloriesProgressIndicator.progress = 0
            proteinProgressIndicator.progress = 0
            fatProgressIndicator.progress = 0
            carbsProgressIndicator.progress = 0
            vitaminsProgressIndicator.progress = 0
        }

        private fun updateNutritionProgress(user: User) {
            FirebaseManager.getDailyNutrition(user.uid) { dailyNutrition ->
                updateProgressIndicator(caloriesProgressIndicator, tvCaloriesValue, dailyNutrition.calories, user.dailyCalories, "kcal", R.color.colorCalories)
                updateProgressIndicator(proteinProgressIndicator, tvProteinValue, dailyNutrition.totalNutritiveValues.protein, user.dailyProtein, "g", R.color.colorProtein)
                updateProgressIndicator(fatProgressIndicator, tvFatValue, dailyNutrition.totalNutritiveValues.fat, user.dailyFat, "g", R.color.colorFat)
                updateProgressIndicator(carbsProgressIndicator, tvCarbsValue, dailyNutrition.totalNutritiveValues.carbohydrates, user.dailyCarbs, "g", R.color.colorCarbs)
                updateProgressIndicator(vitaminsProgressIndicator, tvVitaminsValue, dailyNutrition.totalNutritiveValues.vitamins, user.dailyVitamins, "mg", R.color.colorVitamins)
            }
        }

        private fun updateProgressIndicator(indicator: CircularProgressIndicator, textView: TextView, value: Float, maxValue: Float, unit: String, colorResId: Int) {
            val progress = ((value / maxValue) * 100).toInt().coerceIn(0, 100)
            ObjectAnimator.ofInt(indicator, "progress", 0, progress).apply {
                duration = 1000 // 1 second
                interpolator = DecelerateInterpolator()
                start()
            }
            indicator.setIndicatorColor(ContextCompat.getColor(this, colorResId))
            textView.text = "${value.toInt()} / ${maxValue.toInt()} $unit"
        }

        fun openAddMealActivity(view: View) {
            startActivity(Intent(this, AddMealActivity::class.java))
        }

        fun openCalorieHistoryActivity(view: View) {
            startActivity(Intent(this, CalorieHistoryActivity::class.java))
        }
        fun openUserProfileActivity(view: View) {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }
    }