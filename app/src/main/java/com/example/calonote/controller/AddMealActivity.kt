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
    private lateinit var btnConnectDevices: Button
    private lateinit var btnChangeWifi: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnCaptureFoodItem: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    private lateinit var tvStatus: TextView
    private lateinit var mqttManager: MqttManager

    private var isCameraConnected = false
    private var isScaleConnected = false
    private lateinit var wifiManager: WifiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_meal)
        initializeViews()
        setUIEnabled(false)
        setListeners()
        mqttManager = MqttManager(this)
    }

    private fun initializeViews() {
        statusBar = findViewById(R.id.statusBar)
        btnConnectDevices = findViewById(R.id.btnConnectScale)
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
            runOnUiThread { tvStatus.text = text }
        }
    }

    private fun setListeners() {
        btnConnectDevices.setOnClickListener { connectToDevices() }
        btnChangeWifi.setOnClickListener { changeWifiCredentials() }
        btnCalibrate.setOnClickListener { calibrateScale() }
        btnCaptureFoodItem.setOnClickListener { startCaptureFoodItemActivity() }
    }

    private fun connectToDevices() {
        isCameraConnected = false
        isScaleConnected = false
        lifecycleScope.launch {
            if (mqttManager.connect()) {
                updateStatusText("Connected to MQTT broker")
                subscribeToEsp32()
            } else {
                updateStatusText("Failed to connect to MQTT broker")
            }
        }
    }

    private fun subscribeToEsp32() {
        isScaleConnected = false

        mqttManager.subscribe("esp32/ping", 1) { _, message ->
            handleEsp32PingResponse(message.toString())
        }
        mqttManager.publish("esp32/ping", "ping")
        updateStatusText("Checking ESP32 reachability...")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isScaleConnected) {
                handleDeviceUnreachable("esp32")
            } else {
                isCameraConnected = false
                mqttManager.unsubscribe("esp32/ping")
                subscribeToEsp32Cam()
            }
        }, 5000)
    }

    private fun subscribeToEsp32Cam() {

        mqttManager.subscribe("esp32cam/ping", 1) { _, message ->
            handleEsp32CamPingResponse(message.toString())
        }
        mqttManager.publish("esp32cam/ping", "ping")
        updateStatusText("Checking ESP32Cam reachability...")

        Handler(Looper.getMainLooper()).postDelayed({
            if (!isCameraConnected) {
                handleDeviceUnreachable("esp32cam")
            } else {
                mqttManager.unsubscribe("esp32cam/ping")
                setUIEnabled(true)
            }
        }, 10000) // 10 second timeout
    }

    private fun handleEsp32PingResponse(message: String) {
        if (message == "pong") {
            isScaleConnected = true
            updateStatusText("Connected to ESP32, Trying to connect to Cam...")
        }
    }

    private fun handleEsp32CamPingResponse(message: String) {
        if (message == "pong") {
            isCameraConnected = true
            updateStatusText("Connected to ESP32Cam")
            updateConnectionStatus()
        }
    }
    private fun handleDeviceUnreachable(device: String) {
        updateStatusText("$device not reachable. Setting up Wi-Fi...")
        when (device) {
            "esp32cam" -> configureDevice(getProperty("ESP32CAM_AP_SSID"), getProperty("ESP32CAM_AP_PASSWORD"), getProperty("ESP32_AP_URL"), CAMERA_CONFIG_REQUEST)
            "esp32" -> configureDevice(getProperty("ESP32_AP_SSID"), getProperty("ESP32_AP_PASSWORD"), getProperty("ESP32_AP_URL"), ESP32_CONFIG_REQUEST)
        }
    }

    private fun updateConnectionStatus() {
        if (isScaleConnected && isCameraConnected) {
            runOnUiThread {
                statusBar.text = "Connected"
                statusBar.setBackgroundColor(resources.getColor(R.color.green))
                btnConnectDevices.visibility = View.GONE
                btnChangeWifi.visibility = View.VISIBLE
                btnCalibrate.visibility = View.VISIBLE
            }
        } else {
            runOnUiThread {
                statusBar.text = "Disconnected"
                statusBar.setBackgroundColor(resources.getColor(R.color.red))
                btnConnectDevices.visibility = View.VISIBLE
                btnChangeWifi.visibility = View.GONE
                btnCalibrate.visibility = View.GONE
            }
        }
    }
    private fun configureDevice(ssid: String, password: String, url: String, configRequestCode: Int) {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            promptManualWifiConnection(ssid, password)
        } else {
            connectToAP(ssid, password) {
                openDeviceConfigurationWebsite(url, configRequestCode)
            }
        }
    }

    private fun connectToAP(apSsid: String, apPassword: String, onSuccess: () -> Unit) {
        val conf = WifiConfiguration().apply {
            SSID = "\"$apSsid\""
            preSharedKey = "\"$apPassword\""
        }
        val networkId = wifiManager.addNetwork(conf)
        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()

        Handler(Looper.getMainLooper()).postDelayed({
            if (wifiManager.connectionInfo.ssid == "\"$apSsid\"") {
                onSuccess()
            } else {
                promptManualWifiConnection(apSsid, apPassword)
            }
        }, 5000) // Wait 5 seconds for connection
    }

    private fun promptManualWifiConnection(ssid: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Manual Wi-Fi Connection Required")
            .setMessage("Please connect to the '$ssid' Wi-Fi network manually. The password is '$password'. After connecting, return to this app.")
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
            connectToAP(userSsid, userPassword) {
                updateStatusText("Reconnected to original network. Retrying connection...")
                connectToDevices()
            }
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
                connectToDevices()
            }
            .setCancelable(false)
            .show()
    }

    private fun openDeviceConfigurationWebsite(url: String, requestCode: Int) {
        val intent = Intent(this, WebViewActivity::class.java).apply {
            putExtra("url", url)
        }
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            WIFI_SETTINGS_REQUEST_CODE -> {
                if (isConnectedToDeviceAp()) {
                    openDeviceConfigurationWebsite(getProperty("ESP32_AP_URL"), ESP32_CONFIG_REQUEST)
                } else {
                    promptManualWifiConnection(getProperty("ESP32_AP_SSID"), getProperty("ESP32_AP_PASSWORD"))
                }
            }
            ESP32_CONFIG_REQUEST, CAMERA_CONFIG_REQUEST -> {
                if (resultCode == Activity.RESULT_OK) {
                    reconnectToOriginalNetwork()
                } else {
                    updateStatusText("Configuration failed. Retrying...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        reconnectToOriginalNetwork()
                    }, 2000) // Retry after 2 seconds
                }
            }
        }
    }

    private fun isConnectedToDeviceAp(): Boolean {
        val ssid = wifiManager.connectionInfo.ssid
        return ssid == "\"${getProperty("ESP32_AP_SSID")}\"" || ssid == "\"${getProperty("ESP32CAM_AP_SSID")}\""
    }

    private fun startCaptureFoodItemActivity() {
        val intent = Intent(this, CaptureFoodItemActivity::class.java)
        startActivity(intent)
    }

    private fun setUIEnabled(enabled: Boolean) {
        runOnUiThread {
            btnCalibrate.isEnabled = enabled
            btnCaptureFoodItem.isEnabled = enabled
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
        mqttManager.publish("esp32cam/wifi_config", "$ssid,$password")
        updateStatusText("Sent new WiFi credentials to ESP32")
        isScaleConnected = false
        isCameraConnected = false
        mqttManager.subscribe("esp32/wifi_config_result", 0) { topic, message ->
            val result = message.toString()
            updateStatusText(result)
            if (result.contains("Restarting")) {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateConnectionStatus()
                    connectToDevices()
                }, 5000) // Wait 10 seconds before reconnecting
            }
        }
    }
    private fun getProperty(propertyName: String): String {
        val properties = Properties().apply {
            val inputStream: InputStream = assets.open("config.properties")
            load(inputStream)
        }
        return properties.getProperty(propertyName)
    }

    companion object {
        const val WIFI_SETTINGS_REQUEST_CODE = 1001
        const val ESP32_CONFIG_REQUEST = 1002
        const val CAMERA_CONFIG_REQUEST = 1003
    }
}
