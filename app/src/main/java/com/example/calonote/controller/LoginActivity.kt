package com.example.calonote.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.calonote.R
import com.example.calonote.db.FirebaseManager

class LoginActivity : AppCompatActivity() {
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvRegister: TextView
    private lateinit var tvForgotPassword: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)


        btnLogin.setOnClickListener { login() }
        tvRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }

        FirebaseManager.getCurrentUser { user ->
            if (user != null && user.isEmailVerified) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_forgot_password, null)
        val etEmail = dialogLayout.findViewById<EditText>(R.id.etEmailForgotPassword)

        with(builder) {
            setTitle("Forgot Password")
            setPositiveButton("Reset") { dialog, which ->
                val email = etEmail.text.toString()
                if (email.isNotEmpty()) {
                    FirebaseManager.resetPassword(email) { success, message ->
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@LoginActivity, "Please enter your email", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
            }
            setView(dialogLayout)
            show()
        }
    }

    private fun login() {
        val email = etEmail.text.toString()
        val password = etPassword.text.toString()
        if (email.isNotEmpty() && password.isNotEmpty()) {
            FirebaseManager.loginUser(email, password) { success, message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                if (success) {
                    FirebaseManager.getCurrentUser { user ->
                        if (user != null) {
                            if (user.height == 0.0 || user.weight == 0.0) {
                                startActivity(Intent(this, InitialQuestionnaireActivity::class.java))
                            } else {
                                startActivity(Intent(this, MainActivity::class.java))
                            }
                            finish()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
        }
    }
}