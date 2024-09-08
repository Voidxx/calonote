#include <WiFi.h>
#include <PubSubClient.h>
#include "HX711.h"
#include <Preferences.h>
#include <ESPmDNS.h>
#include <WiFiClientSecure.h>
#include <WebServer.h>

WebServer server(80);


Preferences preferences;
String ssid;
String password;

const int LOADCELL_DOUT_PIN = 14;
const int LOADCELL_SCK_PIN = 4;

HX711 scale;
const char* root_ca PROGMEM = R"EOF(
-----BEGIN CERTIFICATE-----
MIIDrzCCApegAwIBAgIQCDvgVpBCRrGhdWrJWZHHSjANBgkqhkiG9w0BAQUFADBh
MQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3
d3cuZGlnaWNlcnQuY29tMSAwHgYDVQQDExdEaWdpQ2VydCBHbG9iYWwgUm9vdCBD
QTAeFw0wNjExMTAwMDAwMDBaFw0zMTExMTAwMDAwMDBaMGExCzAJBgNVBAYTAlVT
MRUwEwYDVQQKEwxEaWdpQ2VydCBJbmMxGTAXBgNVBAsTEHd3dy5kaWdpY2VydC5j
b20xIDAeBgNVBAMTF0RpZ2lDZXJ0IEdsb2JhbCBSb290IENBMIIBIjANBgkqhkiG
9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4jvhEXLeqKTTo1eqUKKPC3eQyaKl7hLOllsB
CSDMAZOnTjC3U/dDxGkAV53ijSLdhwZAAIEJzs4bg7/fzTtxRuLWZscFs3YnFo97
nh6Vfe63SKMI2tavegw5BmV/Sl0fvBf4q77uKNd0f3p4mVmFaG5cIzJLv07A6Fpt
43C/dxC//AH2hdmoRBBYMql1GNXRor5H4idq9Joz+EkIYIvUX7Q6hL+hqkpMfT7P
T19sdl6gSzeRntwi5m3OFBqOasv+zbMUZBfHWymeMr/y7vrTC0LUq7dBMtoM1O/4
gdW7jVg/tRvoSSiicNoxBN33shbyTApOB6jtSj1etX+jkMOvJwIDAQABo2MwYTAO
BgNVHQ8BAf8EBAMCAYYwDwYDVR0TAQH/BAUwAwEB/zAdBgNVHQ4EFgQUA95QNVbR
TLtm8KPiGxvDl7I90VUwHwYDVR0jBBgwFoAUA95QNVbRTLtm8KPiGxvDl7I90VUw
DQYJKoZIhvcNAQEFBQADggEBAMucN6pIExIK+t1EnE9SsPTfrgT1eXkIoyQY/Esr
hMAtudXH/vTBH1jLuG2cenTnmCmrEbXjcKChzUyImZOMkXDiqw8cvpOp/2PV5Adg
06O/nVsJ8dWO41P0jmP6P6fbtGbfYmbW0W5BjfIttep3Sp+dWOIrWcBAI+0tKIJF
PnlUkiaY4IBIqDfv8NZ5YBberOgOzW6sRBc4L0na4UU+Krk2U886UAb3LujEV0ls
YSEY1QSteDwsOoBrp+uvFRTp2InBuThs4pFsiv9kuXclVzDAGySj4dzp30d8tbQk
CAUw7C29C79Fv1C5qfPrmAESrciIxpg0X40KPMbp1ZWVbd4=
-----END CERTIFICATE-----
)EOF";

bool wifiConfigured = false;
const char* mqtt_server = "wf40744f.ala.eu-central-1.emqxsl.com";
const int mqtt_port = 8883;
const char* mqtt_username = "lsedlanic";
const char* mqtt_password = "ghosts";

WiFiClientSecure espClient;

PubSubClient client(espClient);

void startAP() {
    WiFi.mode(WIFI_AP);
    WiFi.softAP("ESP32_AP", "12345678");

    server.on("/", HTTP_GET, handleRoot);
    server.on("/configure", HTTP_POST, handleConfigure);
    server.begin();

    Serial.print("Access Point IP address: ");
    Serial.println(WiFi.softAPIP());
}

void setup() {
    Serial.begin(115200);
    espClient.setCACert(root_ca);
    String savedSSID;
    String savedPassword;

    delay(2000);
    Serial.println("Starting up...");

    if (loadWiFiCredentials(savedSSID, savedPassword)) {
        ssid = savedSSID;
        password = savedPassword;
        wifiConfigured = true;
        connectToWiFi();
    }

    if (!wifiConfigured) {
        startAP();
    }


    scale.begin(LOADCELL_DOUT_PIN, LOADCELL_SCK_PIN);
    scale.set_scale(590);
    scale.tare();
    
    client.setServer(mqtt_server, mqtt_port);
    client.setCallback(callback);
}

void loop() {
    if (!client.connected() && WiFi.status() == WL_CONNECTED) {
        reconnect();
    }
    client.loop();
    server.handleClient();
}

void connectToWiFi() {
    if (wifiConfigured) {
        Serial.print("Connecting to ");
        Serial.println(ssid);
        WiFi.mode(WIFI_STA);
        WiFi.setSleep(false);
        WiFi.begin(ssid.c_str(), password.c_str());

        unsigned long startTime = millis();
        while (WiFi.status() != WL_CONNECTED && millis() - startTime < 10000) {  // Timeout set to 10 seconds
            delay(500);
            Serial.print(".");
        }

        if (WiFi.status() == WL_CONNECTED) {
            Serial.println("");
            Serial.println("WiFi connected.");
        } else {
            Serial.println("");
            Serial.println("WiFi connection failed. Starting AP mode.");
            wifiConfigured = false;
            startAP();  // Start access point mode
        }
    } else {
        Serial.println("WiFi not configured");
    }
}

void saveWiFiCredentials(const String& ssid, const String& password) {
  preferences.begin("wifi-config", false);
  preferences.putString("ssid", ssid);
  preferences.putString("password", password);
  preferences.end();
}

bool loadWiFiCredentials(String& ssid, String& password) {
  preferences.begin("wifi-config", true);
  bool hasCredentials = preferences.isKey("ssid") && preferences.isKey("password");

  if (hasCredentials) {
    ssid = preferences.getString("ssid");
    password = preferences.getString("password");
  }

  preferences.end();
  return hasCredentials;
}

void reconnect() {
    while (!client.connected()) {
        Serial.print("Attempting MQTT connection...");
        if (client.connect("ESP32Client", mqtt_username, mqtt_password)) { 
            Serial.println("connected");
            client.subscribe("esp32/calibrate");
            client.subscribe("esp32/weight_request");
            client.subscribe("esp32/wifi_config");
            client.subscribe("esp32/ping");
        } else {
            Serial.print("failed, rc=");
            Serial.print(client.state());
            Serial.println(" try again in 5 seconds");
            delay(5000);
        }
    }
}

void handleWiFiConfig(String message) {
    int commaIndex = message.indexOf(',');
    if (commaIndex != -1) {
        String newSsid = message.substring(0, commaIndex);
        String newPassword = message.substring(commaIndex + 1);
        
        saveWiFiCredentials(newSsid, newPassword);
        
        client.publish("esp32/wifi_config_result", "WiFi credentials updated. Restarting...");
        delay(1000);
        ESP.restart();
    } else {
        client.publish("esp32/wifi_config_result", "Invalid WiFi configuration format");
    }
}


void handleCalibration(String message) {
    if (message == "calibrate") {
        Serial.println("Calibration requested");
        client.publish("esp32/calibration_status", "Place the calibration weight on the scale and send 'confirm' to esp32/calibrate");
    } else if (message == "confirm") {
        tare();
        client.publish("esp32/calibration_result", "Calibration completed");
    }
}

void tare() {
    Serial.println("Taring scale...");
    scale.tare();  // Reset the scale to zero
    Serial.println("Scale tared");
}

void handlePing() {
    client.publish("esp32/ping", "pong");
}

void handleWeightRequest() {
    Serial.println("Weight request received");
    float weight = scale.get_units(10);
    String weightStr = String(weight, 2);  // 2 decimal places
    Serial.println("Weight: " + weightStr);
    
    // Convert String to uint8_t array
    uint8_t* payload = (uint8_t*)weightStr.c_str();
    unsigned int length = weightStr.length();

    // Publish with QoS 0
    if (client.publish("esp32/weight", payload, length, false)) {
        Serial.println("Weight published successfully");
    } else {
        Serial.println("Failed to publish weight");
    }
}

void callback(char* topic, byte* payload, unsigned int length) {
    String message = "";
    for (int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    
    if (String(topic) == "esp32/calibrate") {
        handleCalibration(message);
    } else if (String(topic) == "esp32/weight_request") {
        handleWeightRequest();
    } else if (String(topic) == "esp32/ping") {
        handlePing();
    } else if (String(topic) == "esp32/wifi_config") {
    handleWiFiConfig(message);
    }


}


    void handleRoot() {
    String html = "<html><body>";
    html += "<h1>ESP32 Configuration</h1>";
    html += "<form method='post' action='/configure'>";
    html += "SSID: <input type='text' name='ssid'><br>";
    html += "Password: <input type='password' name='password'><br>";
    html += "<input type='submit' value='Configure'>";
    html += "</form></body></html>";
    server.send(200, "text/html", html);
}

void handleConfigure() {
    String newSsid = server.arg("ssid");    
    String newPassword = server.arg("password");
    saveWiFiCredentials(newSsid, newPassword);
    server.send(200, "text/plain", "Configuration saved. ESP32 will restart.");
    delay(1000);
    ESP.restart();
}
