package com.example.calonote.controller

import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.calonote.R
import com.example.calonote.db.FirebaseManager
import com.example.calonote.model.Meal
import com.example.calonote.model.MealItem
import java.text.SimpleDateFormat
import java.util.*

class CalorieHistoryActivity : AppCompatActivity() {
    private lateinit var rvMeals: RecyclerView
    private lateinit var adapter: MealAdapter
    private val meals = mutableListOf<Meal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calorie_history)

        rvMeals = findViewById(R.id.rvMeals)
        adapter = MealAdapter(meals)
        rvMeals.adapter = adapter
        rvMeals.layoutManager = LinearLayoutManager(this)

        loadMealHistory()
    }

    private fun loadMealHistory() {
        FirebaseManager.getCurrentUser { user ->
            if (user == null) {
                Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
                return@getCurrentUser
            }

            FirebaseManager.getMeals(user.uid) { fetchedMeals ->
                runOnUiThread {
                    meals.clear()
                    meals.addAll(fetchedMeals)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private inner class MealAdapter(private val meals: List<Meal>) : RecyclerView.Adapter<MealViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal, parent, false)
            return MealViewHolder(view)
        }

        override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
            holder.bind(meals[position])
        }

        override fun getItemCount(): Int = meals.size
    }

    private inner class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMealName: TextView = itemView.findViewById(R.id.tvMealName)
        private val tvCalories: TextView = itemView.findViewById(R.id.tvCalories)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val rvMealItems: RecyclerView = itemView.findViewById(R.id.rvMealItems)
        private val tvNutrition: TextView = itemView.findViewById(R.id.tvNutrition)

        fun bind(meal: Meal) {
            tvMealName.text = meal.name
            tvCalories.text = "${meal.calories} calories"
            tvTimestamp.text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(meal.timestamp))

            val nutritionText = """
                Protein: ${meal.totalNutritiveValues.protein}g
                Carbs: ${meal.totalNutritiveValues.carbohydrates}g
                Fat: ${meal.totalNutritiveValues.fat}g
                Calcium: ${meal.totalNutritiveValues.calcium}mg
                Vitamins: ${meal.totalNutritiveValues.vitamins}mg
            """.trimIndent()
            tvNutrition.text = nutritionText

            rvMealItems.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            rvMealItems.adapter = MealItemAdapter(meal.items)
        }
    }

    private inner class MealItemAdapter(private val items: List<MealItem>) : RecyclerView.Adapter<MealItemViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealItemViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal_food, parent, false)
            return MealItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: MealItemViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }

    private inner class MealItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivFoodImage: ImageView = itemView.findViewById(R.id.ivFoodImage)
        private val tvFoodName: TextView = itemView.findViewById(R.id.tvFoodName)
        private val tvFoodWeight: TextView = itemView.findViewById(R.id.tvFoodWeight)
        private val tvFoodCalories: TextView = itemView.findViewById(R.id.tvFoodCalories)

        fun bind(item: MealItem) {
            tvFoodName.text = item.name
            tvFoodWeight.text = "${item.weight}g"
            tvFoodCalories.text = "${item.calories} cal"

            if (item.imageData.isNotEmpty()) {
                val imageBytes = Base64.decode(item.imageData, Base64.DEFAULT)
                Glide.with(itemView.context)
                    .load(imageBytes)
                    .placeholder(R.drawable.placeholder_image)
                    .error(R.drawable.error_image)
                    .into(ivFoodImage)
            } else {
                ivFoodImage.setImageResource(R.drawable.placeholder_image)
            }

            itemView.setOnClickListener {
                // You can add a click listener here to show more details about the food item
                Toast.makeText(itemView.context, "Clicked on ${item.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}