package com.example.calonote.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.calonote.R
import com.example.calonote.model.Meal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

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
        FirebaseFirestore.getInstance().collection("meals")
            .whereEqualTo("userId", FirebaseAuth.getInstance().currentUser?.uid)
            .get()
            .addOnSuccessListener { documents ->
                meals.clear()
                for (document in documents) {
                    val meal = document.toObject(Meal::class.java)
                    meals.add(meal)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load meal history", Toast.LENGTH_SHORT).show()
            }
    }

    private inner class MealAdapter(private val meals: List<Meal>) : RecyclerView.Adapter<MealViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal, parent, false)
            return MealViewHolder(view)
        }

        override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
            val meal = meals[position]
            holder.bind(meal)
        }

        override fun getItemCount(): Int {
            return meals.size
        }
    }

    private inner class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMealName: TextView = itemView.findViewById(R.id.tvMealName)
        private val tvCalories: TextView = itemView.findViewById(R.id.tvCalories)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)

        fun bind(meal: Meal) {
            tvMealName.text = meal.name
            tvCalories.text = meal.calories.toString()
            tvTimestamp.text = Date(meal.timestamp).toString()
        }
    }
}