package com.example.calonote.controller

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.calonote.R
import com.example.calonote.client.HardwareClient
import com.example.calonote.model.Meal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.Properties

class AddMealActivity : AppCompatActivity() {
    private lateinit var statusBar: TextView
    private lateinit var btnConnectScale: Button
    private lateinit var btnChangeWifi: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnCaptureFoodItem: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoading: TextView
    private lateinit var tvStatus: TextView


    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val client = OkHttpClient()
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var ESP32_ARDUINO_URL: String? = null
    private var isConnected = false
    private var originalNetworkSSID: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val UDP_PORT = 4210
    private var discoveryThread: Thread? = null
    private var isDiscovering = false
    private var ESP32_AP_SSID: String? = null
    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var multicastLock: WifiManager.MulticastLock
    private var ESP32_AP_PASSWORD: String? = null
    private var userSSID: String? = null
    private var userPassword: String? = null
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private var socket: DatagramSocket? = null
    private lateinit var sharedPreferences: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_meal)
        sharedPreferences = getSharedPreferences("AddMealPrefs", Context.MODE_PRIVATE)
        isConnected = sharedPreferences.getBoolean("isConnected", false)
        ESP32_ARDUINO_URL = sharedPreferences.getString("ESP32_ARDUINO_URL", null)
        nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager


        initializeViews()
        setListeners()
        checkArduinoReachability()
        loadConfig()
        initializeWifiManager()
        multicastLock = wifiManager.createMulticastLock("multicastLock")
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyWifiLock")

        wifiLock.acquire()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        setUIEnabled(false)
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
        runOnUiThread {
            tvStatus.text = text
        }
    }

    private fun setListeners() {
        btnConnectScale.setOnClickListener {
        if (checkPermissions()) {
            configureESP32()
        } else {
            requestPermissions()
        }}
        btnChangeWifi.setOnClickListener { changeWifiCredentials() }
        btnCalibrate.setOnClickListener { calibrateScale() }
        btnCaptureFoodItem.setOnClickListener { startCaptureFoodItemActivity() }
    }

    private fun configureESP32() {
        updateStatusText("Scanning for ESP32 access point...")
        originalNetworkSSID = wifiManager.connectionInfo.ssid.replace("\"", "")
        wifiManager.startScan()

        if (isEsp32ApAvailable()) {
            updateStatusText("ESP32 access point found. Connecting...")
            connectToEsp32Ap()
        } else {
            updateStatusText("ESP32 access point not found. Starting discovery...")
            startDiscovery()
        }
    }

    private fun checkArduinoReachability() {
        val savedUrl = sharedPreferences.getString("ESP32_ARDUINO_URL", null)
        if (savedUrl != null) {
            HardwareClient.sendRequest("$savedUrl/ping") { response, exception ->
                if (exception == null && response == "pong") {
                    runOnUiThread {
                        isConnected = true
                        ESP32_ARDUINO_URL = savedUrl
                        updateConnectionStatus()
                    }
                } else {
                    runOnUiThread {
                        isConnected = false
                        updateConnectionStatus()
                    }
                }
            }
        } else {
            isConnected = false
            updateConnectionStatus()
        }
    }
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun isEsp32ApAvailable(): Boolean {
        val scanResults = wifiManager.scanResults
        for (result in scanResults) {
            if (result.SSID == ESP32_AP_SSID) {
                return true
            }
        }
        return false
    }

    @SuppressLint("NewApi")
    private fun connectToEsp32Ap() {
        wifiManager.removeNetworkSuggestions(wifiManager.networkSuggestions)

        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ESP32_AP_SSID!!)
            .setWpa2Passphrase(ESP32_AP_PASSWORD!!)
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                Log.d("Wifi", "Network available: $network")
                checkConnectionStateAndProceed()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d("Wifi", "Network lost: $network")
            }
        }
        updateStatusText("Requesting network connection...")
        connectivityManager.requestNetwork(networkRequest, networkCallback!!)
    }

    private fun checkConnectionStateAndProceed() {
        updateStatusText("Checking connection state...")
        val filter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        val wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val ssid = wifiManager.connectionInfo.ssid.replace("\"", "")
                if (ssid == ESP32_AP_SSID) {
                    Log.d("Wifi", "Connected to ESP32 AP: $ssid")
                    unregisterReceiver(this)
                    CoroutineScope(Dispatchers.Main).launch {
                        sendConfigurationToESP32()
                        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
                    }
                } else {
                    Log.d("Wifi", "Currently connected to: $ssid")
                }
            }
        }
        registerReceiver(wifiReceiver, filter)
    }

    private suspend fun sendConfigurationToESP32() {
        updateStatusText("Sending configuration to ESP32...")
        withContext(Dispatchers.IO) {
            val userWifiSSID = userSSID
            val userWifiPassword = userPassword
            if (userWifiSSID != null && userWifiPassword != null) {
                sendWifiConfigToESP32(userWifiSSID, userWifiPassword)
            }
        }
        reconnectToUserNetwork()
    }

    private fun sendWifiConfigToESP32(ssid: String, password: String) {
        updateStatusText("Sending WiFi credentials to ESP32...")
        val esp32ApIp = "192.168.4.1:8080"
        val url = "http://$esp32ApIp/configwifi?ssid=$ssid&pass=$password"

        val request = okhttp3.Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread {
                    Toast.makeText(this@AddMealActivity, "Failed to send configuration: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.w("fail:", e.message!!)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@AddMealActivity, "Configuration sent successfully", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({
                            reconnectToUserNetwork()
                        }, 10000)
                    } else {
                        Toast.makeText(this@AddMealActivity, "Failed to send configuration: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    @SuppressLint("NewApi")
    private fun reconnectToUserNetwork() {
        updateStatusText("Reconnecting to original network...")
        wifiManager.removeNetworkSuggestions(wifiManager.networkSuggestions)
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(originalNetworkSSID!!)
            .build()

        val suggestions = listOf(suggestion)

        val status = wifiManager.addNetworkSuggestions(suggestions)
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Toast.makeText(this, "Failed to reconnect to original network", Toast.LENGTH_SHORT).show()
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if(!isDiscovering)
            startDiscovery()
        }, 5000)
    }


    private fun updateConnectionStatus() {
        if (isConnected) {
            statusBar.text = "Connected"
            statusBar.setBackgroundColor(resources.getColor(R.color.green))
            btnConnectScale.visibility = View.GONE
            btnChangeWifi.visibility = View.VISIBLE
            btnCalibrate.visibility = View.VISIBLE
        } else {
            statusBar.text = "Disconnected"
            statusBar.setBackgroundColor(resources.getColor(R.color.red))
            btnConnectScale.visibility = View.VISIBLE
            btnChangeWifi.visibility = View.GONE
            btnCalibrate.visibility = View.GONE
        }
        with(sharedPreferences.edit()) {
            putBoolean("isConnected", isConnected)
            putString("ESP32_ARDUINO_URL", ESP32_ARDUINO_URL)
            apply()
        }
    }

    private fun startDiscovery() {
        updateStatusText("")
        showLoading(true)
        isDiscovering = true
        val serviceType = "_http._tcp."

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d("mDNS", "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d("mDNS", "Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceName.startsWith("esp32-scale2")) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("mDNS", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host
                            val port = serviceInfo.port
                            ESP32_ARDUINO_URL = "http://${host.hostAddress}:$port"
                            runOnUiThread {
                                showLoading(false)
                                Toast.makeText(this@AddMealActivity, "ESP32 discovered at $ESP32_ARDUINO_URL", Toast.LENGTH_LONG).show()
                                setUIEnabled(true)
                                isConnected = true
                                updateConnectionStatus()
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.e("mDNS", "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("mDNS", "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("mDNS", "Start discovery failed: $errorCode")
                runOnUiThread {
                    showLoading(false)
                    Toast.makeText(this@AddMealActivity, "Failed to start discovery", Toast.LENGTH_LONG).show()
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("mDNS", "Stop discovery failed: $errorCode")
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
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
                isConnected = false;
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendNewWifiCredentials(ssid: String, password: String) {
        HardwareClient.sendRequest("$ESP32_ARDUINO_URL/change_wifi?ssid=$ssid&password=$password") { response, exception ->
            if (exception != null) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to change WiFi credentials", Toast.LENGTH_SHORT).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "WiFi credentials updated successfully", Toast.LENGTH_SHORT).show()
                    isConnected = false;
                    updateConnectionStatus()
                }
            }
        }
    }

    private fun calibrateScale() {
        showCalibrationPrompt { shouldCalibrate ->
            if (shouldCalibrate) {
                HardwareClient.sendRequest("$ESP32_ARDUINO_URL/calibrate") { response, exception ->
                    if (exception != null) {
                        runOnUiThread {
                            Toast.makeText(this, "Calibration failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                        return@sendRequest
                    }

                    showConfirmationPrompt { confirmed ->
                        if (confirmed) {
                            HardwareClient.sendRequest("$ESP32_ARDUINO_URL/calibrate?confirm=true") { response, exception ->
                                if (exception != null) {
                                    runOnUiThread {
                                        Toast.makeText(this, "Calibration confirmation failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    return@sendRequest
                                }
                                runOnUiThread {
                                    Toast.makeText(this, "Calibration completed successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this, "Calibration cancelled", Toast.LENGTH_SHORT).show()
                            }
                        }
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
        intent.putExtra("ESP32_ARDUINO_URL", ESP32_ARDUINO_URL)
        intent.putExtra("isConnected", isConnected)
        startActivity(intent)
    }



    private fun loadConfig() {
        val properties = Properties()
        assets.open("config.properties").use { properties.load(it) }
        ESP32_AP_SSID = properties.getProperty("ESP32_AP_SSID")
        ESP32_AP_PASSWORD = properties.getProperty("ESP32_AP_PASSWORD")
        userSSID = properties.getProperty("USER_SSID")
        userPassword = properties.getProperty("USER_PASSWORD")
    }

    private fun initializeWifiManager() {
        if (!::wifiManager.isInitialized) {
            wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }

    }

    private fun showLoading(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            tvLoading.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        initializeWifiManager()
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyWifiLock")
        wifiLock.acquire()
    }

    override fun onPause() {
        super.onPause()
        stopDiscovery()
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        if(multicastLock.isHeld){
            multicastLock.release()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        stopDiscovery()
        if (::multicastLock.isInitialized && multicastLock.isHeld) {
            multicastLock.release()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        stopDiscovery()
        if (::multicastLock.isInitialized && multicastLock.isHeld) {
            multicastLock.release()
        }
    }

    private fun stopDiscovery() {
        if(!isDiscovering) return
        isDiscovering = false
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                Log.w("NSD", "Listener was not registered or already unregistered", e)
            } catch (e: Exception) {
                Log.e("NSD", "Error stopping discovery", e)
            } finally {
                discoveryListener = null
            }
        }
    }


    companion object {
        const val CAPTURE_FOOD_ITEM_REQUEST = 1
    }
}