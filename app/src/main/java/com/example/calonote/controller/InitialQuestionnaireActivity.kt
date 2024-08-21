package com.example.calonote.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.example.calonote.R
import com.example.calonote.db.FirebaseManager

class InitialQuestionnaireActivity : AppCompatActivity() {
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var etAge: EditText
    private lateinit var etDesiredWeight: EditText
    private lateinit var rgActivityLevel: RadioGroup
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_initial_questionnaire)

        initializeViews()

        btnSubmit.setOnClickListener { submitQuestionnaire() }
    }

    private fun initializeViews() {
        etHeight = findViewById(R.id.etHeight)
        etWeight = findViewById(R.id.etWeight)
        etAge = findViewById(R.id.etAge)
        etDesiredWeight = findViewById(R.id.etDesiredWeight)
        rgActivityLevel = findViewById(R.id.rgActivityLevel)
        btnSubmit = findViewById(R.id.btnSubmit)
    }

    private fun submitQuestionnaire() {
        val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
        val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
        val age = etAge.text.toString().toIntOrNull() ?: 0
        val desiredWeight = etDesiredWeight.text.toString().toDoubleOrNull() ?: 0.0
        val activityLevel = when (rgActivityLevel.checkedRadioButtonId) {
            R.id.rbSedentary -> 1
            R.id.rbLightlyActive -> 2
            R.id.rbModeratelyActive -> 3
            R.id.rbVeryActive -> 4
            R.id.rbExtremelyActive -> 5
            else -> 1
        }

        FirebaseManager.getCurrentUser { user ->
            if (user != null) {
                val updatedUser = user.copy(
                    height = height,
                    weight = weight,
                    age = age,
                    desiredWeight = desiredWeight,
                    activityLevel = activityLevel
                )
                FirebaseManager.updateUser(updatedUser) { success ->
                    if (success) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }
}