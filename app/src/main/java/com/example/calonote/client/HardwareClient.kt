package com.example.calonote.client

import okhttp3.*
import java.io.IOException

object HardwareClient {
    private const val ESP32_ARDUINO_URL = "http://<ARDUINO_IP_ADDRESS>" // Replace with the IP address of your Arduino board

    private val client = OkHttpClient()

    fun captureImageFromCamera(callback: (ByteArray?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url("$ESP32_ARDUINO_URL/capture")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val imageData = response.body?.bytes()
                    callback(imageData, null)
                } else {
                    callback(null, Exception("Failed to capture image: ${response.code}"))
                }
            }
        })
    }

    fun getWeightFromArduino(url: String, callback: (Double?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val weightString = response.body?.string()
                    val weight = weightString?.toDoubleOrNull()
                    callback(weight, null)
                } else {
                    callback(null, Exception("Failed to get weight: ${response.code}"))
                }
            }
        })
    }

    fun sendRequest(url: String, callback: (String?, Exception?) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    callback(responseBody, null)
                } else {
                    callback(null, Exception("Request failed: ${response.code}"))
                }
            }
        })
    }


}