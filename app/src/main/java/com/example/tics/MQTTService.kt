package com.example.tics

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MQTTService : Service() {
    private var mqttClient: MqttClient? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connectToMQTT()
        return START_STICKY
    }

    private fun connectToMQTT() {
        try {
            val serverURI = "tcp://broker.hivemq.com:1883"
            mqttClient = MqttClient(serverURI, "android-app-${System.currentTimeMillis()}", MemoryPersistence())

            val options = MqttConnectOptions()
            options.isCleanSession = true

            mqttClient?.connect(options)
            subscribeToTopics()

            Log.d("MQTT", "Conectado al broker MQTT - Escuchando ESP32...")
        } catch (e: Exception) {
            Log.e("MQTT", "Error conectando: ${e.message}")
        }
    }

    private fun subscribeToTopics() {
        val topics = arrayOf(
            "tics/grupo1/esp32/tele/alarmSmoke",    // Alerta de humo/gas
            "tics/grupo1/esp32/tele/braceletNear",  // Pulsera cerca/lejos
            "tics/grupo1/esp32/tele/rssi"           // Intensidad de señal
        )

        topics.forEach { topic ->
            mqttClient?.subscribe(topic) { receivedTopic, message ->
                val payload = String(message.payload)
                Log.d("MQTT", "📍 Mensaje recibido: $topic -> $payload")

                // Aquí procesamos lo que dice el ESP32
                when (receivedTopic) {
                    "tics/grupo1/esp32/tele/alarmSmoke" -> {
                        if (payload == "1") {
                            Log.d("MQTT", "🚨 ¡ALERTA CRÍTICA! Humo o gas detectado")
                        } else {
                            Log.d("MQTT", "✅ Estado normal: Sin humo/gas")
                        }
                    }
                    "tics/grupo1/esp32/tele/braceletNear" -> {
                        if (payload == "0") {
                            Log.d("MQTT", "🚨 ¡PULSERA DESCONECTADA! Persona alejada")
                        } else {
                            Log.d("MQTT", "✅ Pulsera conectada - Persona cerca")
                        }
                    }
                    "tics/grupo1/esp32/tele/rssi" -> {
                        Log.d("MQTT", "📶 Intensidad señal pulsera: $payload dBm")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        mqttClient?.disconnect()
        super.onDestroy()
    }
}