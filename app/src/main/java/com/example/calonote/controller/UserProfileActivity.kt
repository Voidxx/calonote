package com.example.calonote.controller

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.calonote.R
import com.example.calonote.db.FirebaseManager
import com.example.calonote.model.User
import com.google.android.material.textfield.TextInputEditText

class UserProfileActivity : AppCompatActivity() {
    private lateinit var etHeight: TextInputEditText
    private lateinit var etWeight: TextInputEditText
    private lateinit var etAge: TextInputEditText
    private lateinit var etDesiredWeight: TextInputEditText
    private lateinit var rgActivityLevel: RadioGroup
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        initializeViews()
        loadUserProfile()

        btnSave.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun initializeViews() {
        etHeight = findViewById(R.id.etHeight)
        etWeight = findViewById(R.id.etWeight)
        etAge = findViewById(R.id.etAge)
        etDesiredWeight = findViewById(R.id.etDesiredWeight)
        rgActivityLevel = findViewById(R.id.rgActivityLevel)
        btnSave = findViewById(R.id.btnSave)
    }

    private fun loadUserProfile() {
        FirebaseManager.getCurrentUser { user ->
            if (user != null) {
                etHeight.setText(user.height.toString())
                etWeight.setText(user.weight.toString())
                etAge.setText(user.age.toString())
                etDesiredWeight.setText(user.desiredWeight.toString())
                when (user.activityLevel) {
                    1 -> rgActivityLevel.check(R.id.rbSedentary)
                    2 -> rgActivityLevel.check(R.id.rbLightlyActive)
                    3 -> rgActivityLevel.check(R.id.rbModeratelyActive)
                    4 -> rgActivityLevel.check(R.id.rbVeryActive)
                    5 -> rgActivityLevel.check(R.id.rbExtremelyActive)
                }
            }
        }
    }

    private fun saveUserProfile() {
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

        FirebaseManager.getCurrentUser { currentUser ->
            if (currentUser != null) {
                val updatedUser = currentUser.copy(
                    height = height,
                    weight = weight,
                    age = age,
                    desiredWeight = desiredWeight,
                    activityLevel = activityLevel
                )
                FirebaseManager.updateUser(updatedUser) { success ->
                    if (success) {
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}