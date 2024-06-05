package com.example.calonote.model

data class User(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val isEmailVerified: Boolean = false
)