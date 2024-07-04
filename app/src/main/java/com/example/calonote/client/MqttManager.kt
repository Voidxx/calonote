package com.example.calonote.client

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

class MqttManager(private val context: Context) {
    private lateinit var mqttClient: MqttAndroidClient
    private lateinit var mqttConfig: JSONObject

    suspend fun connect(): Boolean {
        loadMqttConfig()
        return connectMqtt()
    }

    private fun loadMqttConfig() {
        try {
            val inputStream = context.assets.open("mqtt_config.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)
            mqttConfig = JSONObject(json)
        } catch (e: Exception) {
            e.printStackTrace()
            throw IllegalStateException("Error loading MQTT config")
        }
    }

    private suspend fun connectMqtt(): Boolean = withContext(Dispatchers.IO) {
        try {
            val serverUri = "ssl://${mqttConfig.getString("server")}:${mqttConfig.getInt("port")}"
            val clientId = "AndroidApp_${System.currentTimeMillis()}"

            mqttClient = MqttAndroidClient(context, serverUri, clientId)
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                userName = mqttConfig.getString("username")
                password = mqttConfig.getString("password").toCharArray()
                connectionTimeout = 30
                keepAliveInterval = 60
                socketFactory = getSSLSocketFactory()
            }

            val token = mqttClient.connect(options)
            token.waitForCompletion()
            true
        } catch (e: Exception) {
            Log.e("MqttManager", "Error in MQTT connection: ${e.message}")
            false
        }
    }

    private fun getSSLSocketFactory(): SSLSocketFactory? {
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val caInput: InputStream = context.assets.open("ca.crt")
            val ca = caInput.use { cf.generateCertificate(it) }

            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, null)
            keyStore.setCertificateEntry("ca", ca)

            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
            tmf.init(keyStore)

            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)

            sslContext.socketFactory
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun subscribe(topic: String, qos: Int, callback: (String, MqttMessage) -> Unit) {
        try {
            mqttClient.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttManager", "Subscribed to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MqttManager", "Failed to subscribe to $topic", exception)
                }
            })

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.e("MqttManager", "Connection lost", cause)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        callback(topic, message)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, message: String) {
        try {
            val mqttMessage = MqttMessage(message.toByteArray())
            mqttClient.publish(topic, mqttMessage)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            if (mqttClient.isConnected) {
                mqttClient.disconnect()
                mqttClient.unregisterResources()
                mqttClient.close()
            }
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}