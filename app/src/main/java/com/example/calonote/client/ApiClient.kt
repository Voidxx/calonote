package com.example.calonote.client
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

object ApiClient {
    private const val API_URL = "https://your-api-url.com"

    private val client = OkHttpClient()

    fun recognizeFood(imageData: ByteArray, callback: (String?, Exception?) -> Unit) {
        val requestBody = RequestBody.create("image/jpeg".toMediaTypeOrNull(), imageData)
        val request = Request.Builder()
            .url("$API_URL/recognize-food")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val foodName = response.body?.string()
                    callback(foodName, null)
                } else {
                    callback(null, Exception("Food recognition failed: ${response.code}"))
                }
            }
        })
    }

    fun estimateCalories(foodName: String, weight: Double, callback: (Int?, Exception?) -> Unit) {
        val requestBody = FormBody.Builder()
            .add("food_name", foodName)
            .add("weight", weight.toString())
            .build()

        val request = Request.Builder()
            .url("$API_URL/estimate-calories")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val estimatedCalories = response.body?.string()?.toIntOrNull()
                    callback(estimatedCalories, null)
                } else {
                    callback(null, Exception("Calorie estimation failed: ${response.code}"))
                }
            }
        })
    }
}