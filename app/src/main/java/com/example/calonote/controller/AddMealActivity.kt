package com.example.calonote.controller

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.calonote.R
import com.example.calonote.client.MqttManager
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.util.*


class AddMealActivity : AppCompatActivity() {
    private lateinit var statusBar: TextView
    private lateinit var btnConnectScale: Button
    private lateinit var btnChangeWifi: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnCaptureFoodItem: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    private lateinit var tvStatus: TextView
    private lateinit var mqttManager: MqttManager
    private var isCameraConnected = false


    private var isConnected = false
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var wifiManager: WifiManager
    private var userSsid: String? = null
    private var userPassword: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_meal)
        sharedPreferences = getSharedPreferences("AddMealPrefs", Context.MODE_PRIVATE)
        isConnected = sharedPreferences.getBoolean("isConnected", false)
        isCameraConnected = sharedPreferences.getBoolean("isCameraConnected", false)
        initializeViews()
        setUIEnabled(false)
        setListeners()
        mqttManager = MqttManager(this)

    }

    private fun initializeViews() {
        statusBar = findViewById(R.id.statusBar)
        btnConnectScale = findViewById(R.id.btnConnectScale)
        btnChangeWifi = findViewById(R.id.btnChangeWifi)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        btnCaptureFoodItem = findViewById(R.id.btnCaptureFoodItem)
        progressBar = findViewById(R.id.progressBar)
        tvLoading = findViewById(R.id.tvLoading)
        tvStatus = findViewById(R.id.tvStatus)
        updateStatusText("Ready to connect")
    }

    private fun updateStatusText(text: String) {
        if (!isFinishing && !isDestroyed) {
            runOnUiThread {
                tvStatus.text = text
            }
        }
    }

    private fun setListeners() {
        btnConnectScale.setOnClickListener { connectMqtt() }
        btnChangeWifi.setOnClickListener { changeWifiCredentials() }
        btnCalibrate.setOnClickListener { calibrateScale() }
        btnCaptureFoodItem.setOnClickListener { startCaptureFoodItemActivity() }
    }


    private fun configureEsp32() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        userSsid = getProperty("USER_SSID")
        userPassword = getProperty("USER_PASSWORD")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above, we can't programmatically connect to Wi-Fi networks
            promptManualWifiConnection()
        } else {
            connectToEsp32Ap()
        }
    }

    private fun connectToEsp32Ap() {
        val conf = WifiConfiguration()
        conf.SSID = "\"${getProperty("ESP32_AP_SSID")}\""
        conf.preSharedKey = "\"${getProperty("ESP32_AP_PASSWORD")}\""

        val networkId = wifiManager.addNetwork(conf)
        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()

        // Wait for connection to be established
        Handler(Looper.getMainLooper()).postDelayed({
            if (wifiManager.connectionInfo.ssid == "\"${getProperty("ESP32_AP_SSID")}\"") {
                openEsp32ConfigurationWebsite()
            } else {
                promptManualWifiConnection()
            }
        }, 5000) // Wait 5 seconds for connection
    }

    private fun promptManualWifiConnection() {
        AlertDialog.Builder(this)
            .setTitle("Manual Wi-Fi Connection Required")
            .setMessage("Please connect to the '${getProperty("ESP32_AP_SSID")}' Wi-Fi network manually. " +
                    "The password is '${getProperty("ESP32_AP_PASSWORD")}'. " +
                    "After connecting, return to this app.")
            .setPositiveButton("Open Wi-Fi Settings") { _, _ ->
                startActivityForResult(Intent(Settings.ACTION_WIFI_SETTINGS), WIFI_SETTINGS_REQUEST_CODE)
            }
            .setCancelable(false)
            .show()
    }

    private fun reconnectToOriginalNetwork() {
        val userSsid = getProperty("USER_SSID")
        val userPassword = getProperty("USER_PASSWORD")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            promptManualReconnection(userSsid)
        } else {
            // Attempt to reconnect to the original network
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val conf = WifiConfiguration()
            conf.SSID = "\"$userSsid\""
            conf.preSharedKey = "\"$userPassword\""
            val networkId = wifiManager.addNetwork(conf)
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()

            // Check if reconnection was successful
            Handler(Looper.getMainLooper()).postDelayed({
                if (wifiManager.connectionInfo.ssid == "\"$userSsid\"") {
                    updateStatusText("Reconnected to original network. Retrying connection...")
                    connectMqtt()
                } else {
                    promptManualReconnection(userSsid)
                }
            }, 5000)
        }
    }

    private fun promptManualReconnection(ssid: String) {
        AlertDialog.Builder(this)
            .setTitle("Reconnect to Original Network")
            .setMessage("Please reconnect to your original Wi-Fi network '$ssid' manually.")
            .setPositiveButton("Open Wi-Fi Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .setNegativeButton("I'm Reconnected") { _, _ ->
                updateStatusText("Retrying connection...")
                connectMqtt()
            }
            .setCancelable(false)
            .show()
    }

    private fun connectMqtt() {
        lifecycleScope.launch {
            if (mqttManager.connect()) {
                updateStatusText("Connected to MQTT broker")
                isConnected = true
                subscribeToPings()
                connectToCamera()
            } else {
                updateStatusText("Failed to connect to MQTT broker")
            }
        }
    }
    private fun connectToCamera() {
        updateStatusText("Connecting to camera...")
        mqttManager.publish("esp32cam/connect", "connect")

        var responseReceived = false

        mqttManager.subscribe("esp32cam/status", 0) { _, message ->
            responseReceived = true
            when (message.toString()) {
                "connected" -> {
                    isCameraConnected = true
                    updateConnectionStatus()
                    updateStatusText("Connected to scale and camera")
                    setUIEnabled(true)
                }
                else -> {
                    updateStatusText("Unexpected camera status: ${message.toString()}")
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!responseReceived) {
                updateStatusText("Camera in AP mode. Setting up Wi-Fi...")
                setupCameraWifi()
            }
        }, 5000) // 5 second timeout
    }

    private fun setupCameraWifi() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cameraApSsid = "ESP32CAM_AP" // This should match the SSID in the ESP32-CAM code

        if (wifiManager.connectionInfo.ssid.replace("\"", "") == cameraApSsid) {
            // Already connected to camera AP, proceed with configuration
            openCameraConfigWebView()
        } else {
            // Prompt user to connect to camera AP
            AlertDialog.Builder(this)
                .setTitle("Camera Wi-Fi Setup")
                .setMessage("Please connect to the '$cameraApSsid' Wi-Fi network, then return to this app.")
                .setPositiveButton("Open Wi-Fi Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
                .setNegativeButton("I'm Connected") { _, _ ->
                    if (wifiManager.connectionInfo.ssid.replace("\"", "") == cameraApSsid) {
                        openCameraConfigWebView()
                    } else {
                        updateStatusText("Not connected to camera AP. Please try again.")
                    }
                }
                .show()
        }
    }

    private fun openCameraConfigWebView() {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("url", "http://192.168.4.1") // This should match the IP address in the ESP32-CAM code
            putExtra("configType", "camera")
        }
        startActivityForResult(intent, CAMERA_CONFIG_REQUEST)
    }


    private fun subscribeToPings() {
        mqttManager.subscribe("esp32/ping", 0) { _, message ->
            handlePing(message.toString())
        }
        updateStatusText("Connected and listening for ESP32")
        checkArduinoReachability()
    }

    private fun handlePing(message: String?) {
        if (message == "pong") {
                isConnected = true
                updateConnectionStatus()
                updateStatusText("Connected to ESP32")
                connectToCamera()
        }
    }

    private fun checkArduinoReachability() {
        mqttManager.publish("android/ping", "ping")
        updateStatusText("Checking ESP32 reachability...")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isConnected) {
                updateStatusText("ESP32 not reachable.")
                promptEsp32Configuration()
            }
        }, 5000) // 5 second timeout
    }
    private fun promptEsp32Configuration() {
        AlertDialog.Builder(this)
            .setTitle("ESP32 Configuration Required")
            .setMessage("The ESP32 is not reachable. Would you like to configure it now?")
            .setPositiveButton("Yes") { _, _ ->
                configureEsp32()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    private fun openEsp32ConfigurationWebsite() {
        val esp32ConfigUrl = getProperty("ESP32_AP_URL")
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("url", esp32ConfigUrl)
        }
        startActivityForResult(intent, ESP32_CONFIG_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            WIFI_SETTINGS_REQUEST_CODE -> {
                if (isConnectedToEsp32Ap()) {
                    openEsp32ConfigurationWebsite()
                } else {
                    promptManualWifiConnection()
                }
            }
            ESP32_CONFIG_REQUEST -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        reconnectToOriginalNetwork()
                    }
                    Activity.RESULT_CANCELED, Activity.RESULT_FIRST_USER -> {
                        // User pressed back or exited the web activity
                        handleWebActivityExit()
                    }
                    else -> {
                        updateStatusText("ESP32 configuration cancelled or failed.")
                        handleWebActivityExit()
                    }
                }
            }
            CAMERA_CONFIG_REQUEST -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        updateStatusText("Camera Wi-Fi configured. Reconnecting...")
                        reconnectToOriginalNetwork()
                    }
                    else -> {
                        updateStatusText("Camera configuration cancelled or failed.")
                    }
                }
            }
        }
    }

    private fun handleWebActivityExit() {
        AlertDialog.Builder(this)
            .setTitle("Configuration Incomplete")
            .setMessage("Would you like to reconnect to your original Wi-Fi network?")
            .setPositiveButton("Yes") { _, _ -> reconnectToOriginalNetwork() }
            .setNegativeButton("No") { _, _ -> updateStatusText("Please connect to a Wi-Fi network manually.") }
            .setCancelable(false)
            .show()
    }
    companion object {
        private const val ESP32_CONFIG_REQUEST = 1001
        private const val WIFI_SETTINGS_REQUEST_CODE = 1002
        private const val CAMERA_CONFIG_REQUEST = 1003
    }

    private fun isConnectedToEsp32Ap(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val esp32ApSsid = getProperty("ESP32_AP_SSID")
        return wifiManager.connectionInfo.ssid.replace("\"", "") == esp32ApSsid
    }


    private fun updateConnectionStatus() {
        if (isConnected && isCameraConnected) {
            runOnUiThread {
                statusBar.text = "Connected"
                statusBar.setBackgroundColor(resources.getColor(R.color.green))
                btnConnectScale.visibility = View.GONE
                btnChangeWifi.visibility = View.VISIBLE
                btnCalibrate.visibility = View.VISIBLE
            }
        } else {
            runOnUiThread {
                statusBar.text = "Disconnected"
                statusBar.setBackgroundColor(resources.getColor(R.color.red))
                btnConnectScale.visibility = View.VISIBLE
                btnChangeWifi.visibility = View.GONE
                btnCalibrate.visibility = View.GONE
            }
        }
        with(sharedPreferences.edit()) {
            putBoolean("isConnected", isConnected && isCameraConnected)
            apply()
        }
    }
    private fun setUIEnabled(enabled: Boolean) {
        btnCaptureFoodItem.isEnabled = enabled
    }

    private fun changeWifiCredentials() {
        val ssidEditText = EditText(this)
        val passwordEditText = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("Change WiFi Credentials")
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = "SSID" })
                addView(ssidEditText)
                addView(TextView(context).apply { text = "Password" })
                addView(passwordEditText)
            })
            .setPositiveButton("Submit") { _, _ ->
                val newSsid = ssidEditText.text.toString()
                val newPassword = passwordEditText.text.toString()
                sendNewWifiCredentials(newSsid, newPassword)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendNewWifiCredentials(ssid: String, password: String) {
        mqttManager.publish("esp32/wifi_config", "$ssid,$password")
        updateStatusText("Sent new WiFi credentials to ESP32")

        mqttManager.subscribe("esp32/wifi_config_result", 0) { topic, message ->
            val result = message.toString()
            updateStatusText(result)
            if (result.contains("Restarting")) {
                Handler(Looper.getMainLooper()).postDelayed({
                    isConnected = false
                    updateConnectionStatus()
                    connectMqtt()
                }, 10000) // Wait 10 seconds before reconnecting
            }
        }
    }

    private fun calibrateScale() {
        showCalibrationPrompt { shouldCalibrate ->
            if (shouldCalibrate) {
                mqttManager.publish("esp32/calibrate", "calibrate")
                updateStatusText("Sent calibration request to ESP32")
                showConfirmationPrompt { confirmed ->
                    if (confirmed) {
                        mqttManager.publish("esp32/calibrate/confirm", "confirm")
                        updateStatusText("Sent calibration confirmation to ESP32")
                    } else {
                        updateStatusText("Calibration cancelled")
                    }
                }
            }
        }
    }

    private fun showCalibrationPrompt(callback: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Calibrate Scale")
            .setMessage("Do you want to calibrate the scale?")
            .setPositiveButton("Yes") { _, _ -> callback(true) }
            .setNegativeButton("No") { _, _ -> callback(false) }
            .show()
    }

    private fun showConfirmationPrompt(callback: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Calibration")
            .setMessage("Place the calibration object on the scale and click 'Confirm'")
            .setPositiveButton("Confirm") { _, _ -> callback(true) }
            .setNegativeButton("Cancel") { _, _ -> callback(false) }
            .show()
    }

    private fun startCaptureFoodItemActivity() {
        val intent = Intent(this, CaptureFoodItemActivity::class.java)
        intent.putExtra("isConnected", isConnected)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun getProperty(key: String): String {
        val properties = Properties()
        try {
            val inputStream: InputStream = assets.open("config.properties")
            properties.load(inputStream)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return properties.getProperty(key) ?: ""
    }
}