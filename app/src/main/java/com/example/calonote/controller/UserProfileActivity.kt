package com.example.calonote.controller

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.calonote.R
import com.example.calonote.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserProfileActivity : AppCompatActivity() {
    private lateinit var etHeight: EditText
    private lateinit var etWeight: EditText
    private lateinit var etAge: EditText
    private lateinit var etDesiredWeight: EditText
    private lateinit var etActivityLevel: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        etHeight = findViewById(R.id.etHeight)
        etWeight = findViewById(R.id.etWeight)
        etAge = findViewById(R.id.etAge)
        etDesiredWeight = findViewById(R.id.etDesiredWeight)
        etActivityLevel = findViewById(R.id.etActivityLevel)
        btnSave = findViewById(R.id.btnSave)

        btnSave.setOnClickListener {
            saveUserProfile()
        }

        loadUserProfile()
    }

    private fun saveUserProfile() {
        val height = etHeight.text.toString().toDoubleOrNull() ?: 0.0
        val weight = etWeight.text.toString().toDoubleOrNull() ?: 0.0
        val age = etAge.text.toString().toIntOrNull() ?: 0
        val desiredWeight = etDesiredWeight.text.toString().toDoubleOrNull() ?: 0.0
        val activityLevel = etActivityLevel.text.toString().toIntOrNull() ?: 0

        val user = User(
            FirebaseAuth.getInstance().currentUser?.uid ?: "",
            FirebaseAuth.getInstance().currentUser?.email ?: "",
            FirebaseAuth.getInstance().currentUser?.displayName ?: "",
            FirebaseAuth.getInstance().currentUser?.isEmailVerified ?: false,
            height, weight, age, desiredWeight, activityLevel
        )

        FirebaseFirestore.getInstance().collection("users")
            .document(user.uid)
            .set(user)
            .addOnSuccessListener {
                Toast.makeText(this, "User profile saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save user profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserProfile() {
        FirebaseFirestore.getInstance().collection("users")
            .document(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            .get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                etHeight.setText(user?.height.toString())
                etWeight.setText(user?.weight.toString())
                etAge.setText(user?.age.toString())
                etDesiredWeight.setText(user?.desiredWeight.toString())
                etActivityLevel.setText(user?.activityLevel.toString())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user profile", Toast.LENGTH_SHORT).show()
            }
    }
}