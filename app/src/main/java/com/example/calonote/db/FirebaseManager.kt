package com.example.calonote.db

import com.example.calonote.model.Meal
import com.example.calonote.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun registerUser(email: String, password: String, username: String, callback: (Boolean, String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = User(auth.uid!!, email, username, false)
                    db.collection("users").document(auth.uid!!).set(user)
                        .addOnSuccessListener {
                            auth.currentUser?.sendEmailVerification()
                            callback(true, "Verification email sent")
                        }
                        .addOnFailureListener { callback(false, it.message ?: "Error saving user") }
                } else {
                    callback(false, task.exception?.message ?: "Registration failed")
                }
            }
    }

    fun loginUser(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (auth.currentUser?.isEmailVerified == true) {
                        callback(true, "Login successful")
                    } else {
                        callback(false, "Please verify your email first")
                    }
                } else {
                    callback(false, task.exception?.message ?: "Login failed")
                }
            }
    }

    fun getCurrentUser(callback: (User?) -> Unit) {
        val uid = auth.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val user = doc.toObject(User::class.java)
                    callback(user)
                }
                .addOnFailureListener { callback(null) }
        } else {
            callback(null)
        }
    }

    fun addMeal(meal: Meal, callback: (Boolean, String) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("meals")
            .add(meal)
            .addOnSuccessListener {
                callback(true, "Meal added successfully")
            }
            .addOnFailureListener { e ->
                callback(false, e.message ?: "Failed to add meal")
            }
    }
}