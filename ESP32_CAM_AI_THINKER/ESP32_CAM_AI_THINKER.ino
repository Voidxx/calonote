#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include "esp_camera.h"
#include <Preferences.h>
#include <ESPmDNS.h>
#include <WebServer.h>
#include "base64.h"


WebServer server(80);
Preferences preferences;
String ssid;
String password;

const char* mqtt_server = "wf40744f.ala.eu-central-1.emqxsl.com";
const int mqtt_port = 8883;
const char* mqtt_username = "lsedlanic";
const char* mqtt_password = "ghosts";

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

#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

WiFiClientSecure espClient;
PubSubClient client(espClient);

bool wifiConfigured = false;

void startAP() {
    WiFi.mode(WIFI_AP);
    WiFi.softAP("ESP32CAM_AP", "12345678");

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

    initCamera();
    
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

void initCamera() {
    camera_config_t config;
    config.grab_mode = CAMERA_GRAB_LATEST;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sscb_sda = SIOD_GPIO_NUM;
    config.pin_sscb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;
    config.pixel_format = PIXFORMAT_JPEG;
    config.frame_size = FRAMESIZE_VGA;
    config.jpeg_quality = 10;
    config.fb_count = 2;

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("Camera init failed with error 0x%x", err);
        return;
    }
}

int checkLightingConditions() {
    camera_fb_t * fb = NULL;
    
    sensor_t * s = esp_camera_sensor_get();
    framesize_t original_resolution = s->status.framesize;
    s->set_framesize(s, FRAMESIZE_QVGA);

    fb = esp_camera_fb_get();
    if (!fb) {
        Serial.println("Camera capture failed");
        return -1;  // Return error code
    }

    long sum = 0;
    int pixelCount = fb->width * fb->height;
    uint8_t* pixels = fb->buf;

    for (int i = 0; i < pixelCount; i++) {
        sum += pixels[i];
    }

    float averageBrightness = sum / (float)pixelCount;

    esp_camera_fb_return(fb);

    s->set_framesize(s, (framesize_t)original_resolution);

    int lightLevel = map(averageBrightness, 0, 255, 0, 100);

    Serial.printf("Average brightness: %.2f, Light level: %d\n", averageBrightness, lightLevel);

    return lightLevel;
}

void adjustCameraSettings(int lightLevel) {
    sensor_t * s = esp_camera_sensor_get();

    if (lightLevel < 30) {  // Low light
        s->set_gain_ctrl(s, 1);  // Auto gain on
        s->set_exposure_ctrl(s, 1);  // Auto exposure on
        s->set_awb_gain(s, 1);  // Auto white balance gain on
    } else if (lightLevel > 70) {  // Bright light
        s->set_gain_ctrl(s, 0);  // Auto gain off
        s->set_exposure_ctrl(s, 0);  // Auto exposure off
        s->set_awb_gain(s, 0);  // Auto white balance gain off
        s->set_exposure_ctrl(s, 1);
        s->set_aec_value(s, 300);  // Set a fixed exposure time
    } else {  // Moderate light
        s->set_gain_ctrl(s, 1);  // Auto gain on
        s->set_exposure_ctrl(s, 1);  // Auto exposure on
        s->set_awb_gain(s, 1);  // Auto white balance gain on
    }

}

void connectToWiFi() {
    if (wifiConfigured) {
        Serial.print("Connecting to ");
        Serial.println(ssid);
        WiFi.mode(WIFI_STA);
        WiFi.setSleep(false);
        WiFi.begin(ssid.c_str(), password.c_str());

        unsigned long startTime = millis();
        while (WiFi.status() != WL_CONNECTED && millis() - startTime < 10000) {
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
            startAP();
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
        if (client.connect("ESP32CAMClient", mqtt_username, mqtt_password)) {
            Serial.println("connected");
            client.subscribe("esp32cam/capture");
            client.subscribe("esp32cam/wifi_config");
            client.subscribe("esp32cam/ping");
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
        
        client.publish("esp32cam/wifi_config_result", "WiFi credentials updated. Restarting...");
        delay(1000);
        ESP.restart();
    } else {
        client.publish("esp32cam/wifi_config_result", "Invalid WiFi configuration format");
    }
}

void handlePing() {
    client.publish("esp32cam/ping", "pong");
}

void callback(char* topic, byte* payload, unsigned int length) {
    String message = "";
    for (int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    
    if (String(topic) == "esp32cam/capture") {
        captureAndSendImage();
    } else if (String(topic) == "esp32cam/wifi_config") {
        handleWiFiConfig(message);
    } else if (String(topic) == "esp32cam/ping") {
        handlePing();
    }
}

void captureAndSendImage() {
    camera_fb_t * fb = esp_camera_fb_get();
    if (!fb) {
        Serial.println("Camera capture failed");
        return;
    }

    if (client.connected()) {
        const int maxChunkSize = 10000; // Slightly smaller than 13000 to be safe
        int numChunks = (fb->len + maxChunkSize - 1) / maxChunkSize;
        
        // Send image info first
        char info[50];
        snprintf(info, sizeof(info), "%d,%d", fb->len, numChunks);
        client.publish("esp32cam/image/info", info);
        
        for (int i = 0; i < numChunks; i++) {
            int start = i * maxChunkSize;
            int end = min((int)fb->len, start + maxChunkSize);
            int chunkSize = end - start;
            
            client.beginPublish("esp32cam/image/chunk", chunkSize + 4, false);
            client.write((uint8_t*)&i, 4);  // Send chunk index
            client.write(fb->buf + start, chunkSize);
            client.endPublish();
            
            delay(50);  // Small delay to avoid overwhelming the network
        }
        Serial.printf("Image sent in %d chunks\n", numChunks);
    }

    esp_camera_fb_return(fb);
}

void handleRoot() {
    String html = "<html><body>";
    html += "<h1>ESP32-CAM Configuration</h1>";
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
    server.send(200, "text/plain", "Configuration saved. ESP32-CAM will restart.");
    delay(1000);
    ESP.restart();
}