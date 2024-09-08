import base64
import tensorflow as tf
import time
import numpy as np
import os
from PIL import Image
import io
import json
import paho.mqtt.client as mqtt
import logging
from dotenv import load_dotenv
import io
from PIL import Image

chunks = []
expected_chunks = 0
total_image_size = 0


load_dotenv()

logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

model = tf.keras.models.load_model('retrained_inception_v3.h5')

with open('labels.txt', 'r') as f:
    labels = [line.strip() for line in f.readlines()]

mqtt_client = mqtt.Client()
mqtt_client.username_pw_set(os.getenv('MQTT_USERNAME'), os.getenv('MQTT_PASSWORD'))
mqtt_client.tls_set()  # Enable TLS
mqtt_client.connect(os.getenv('MQTT_BROKER'), int(os.getenv('MQTT_PORT')), 60)

def process_image(image_data):
    try:
        logger.debug(f"Received image data of length: {len(image_data)} bytes")
        image = Image.open(io.BytesIO(image_data))
        
        logger.debug(f"Image format: {image.format}, Size: {image.size}, Mode: {image.mode}")
        
        image = image.resize((299, 299))  # Adjust size as needed
        image = np.array(image) / 255.0
        image = np.expand_dims(image, axis=0)
        
        predictions = model.predict(image)
        top_prediction = np.argmax(predictions[0])
        
        buffered = io.BytesIO()
        Image.fromarray((image[0] * 255).astype(np.uint8)).save(buffered, format="JPEG")
        img_str = base64.b64encode(buffered.getvalue()).decode()
        
        result = {
            'predicted_class': labels[top_prediction],
            'confidence': float(predictions[0][top_prediction]),
            'image_data': img_str
        }
        
        logger.debug(f"Prediction result: {result['predicted_class']}, Confidence: {result['confidence']}")
        
        mqtt_client.publish("model/prediction", json.dumps(result))
    except Exception as e:
        logger.error(f"Error processing image: {str(e)}", exc_info=True)
        # Optionally, publish an error message
        mqtt_client.publish("model/error", json.dumps({"error": str(e)}))

def on_message(client, userdata, message):
    global chunks, expected_chunks, total_image_size
    
    if message.topic == "esp32cam/image/info":
        # Clear previous data
        chunks = []
        expected_chunks = 0
        total_image_size = 0

        total_image_size, expected_chunks = map(int, message.payload.decode().split(','))
        chunks = [None] * expected_chunks
        logger.debug(f"Expecting image of size {total_image_size} in {expected_chunks} chunks")
    
    elif message.topic == "esp32cam/image/chunk":
        chunk_index = int.from_bytes(message.payload[:4], byteorder='little')
        chunk_data = message.payload[4:]
        chunks[chunk_index] = chunk_data
        
        if all(chunk is not None for chunk in chunks):
            full_image = b''.join(chunks)
            if len(full_image) == total_image_size:
                process_image(full_image)
            else:
                logger.error(f"Received image size ({len(full_image)}) doesn't match expected size ({total_image_size})")
            chunks = []
            expected_chunks = 0
            total_image_size = 0

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        logger.info("Successfully connected to MQTT broker")
        client.subscribe("esp32cam/image")
    else:
        logger.error(f"Failed to connect, return code {rc}")

mqtt_client.on_connect = on_connect

def on_subscribe(client, userdata, mid, granted_qos):
    logger.info(f"Subscribed to topic: {mid}")


mqtt_client.on_subscribe = on_subscribe

mqtt_client.on_message = on_message
mqtt_client.subscribe("esp32cam/image/info")
mqtt_client.subscribe("esp32cam/image/chunk")
mqtt_client.subscribe("esp32cam/image", qos=0)

mqtt_client.loop_start()

# Keep the script running
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    logger.info("Disconnecting from MQTT broker")
    mqtt_client.loop_stop()
    mqtt_client.disconnect()