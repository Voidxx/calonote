package com.example.calonote.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.calonote.R
import com.example.calonote.db.FirebaseManager
import com.google.android.material.progressindicator.CircularProgressIndicator

class MainActivity : AppCompatActivity() {
    private lateinit var tvWelcome: TextView
    private lateinit var circularProgressIndicator: CircularProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvWelcome = findViewById(R.id.tvWelcome)
        circularProgressIndicator = findViewById(R.id.circularProgressIndicator)

        FirebaseManager.getCurrentUser { user ->
            if (user != null) {
                tvWelcome.text = "Welcome, ${user.username}"
                // Set the progress value based on the user's calorie intake
                circularProgressIndicator.progress = 50 // Replace with the actual progress value
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }
    }
    fun openAddMealActivity(view: View) {
        startActivity(Intent(this, AddMealActivity::class.java))
    }

    fun openCalorieHistoryActivity(view: View) {
        startActivity(Intent(this, CalorieHistoryActivity::class.java))
    }
}