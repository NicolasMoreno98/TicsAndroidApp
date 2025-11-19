package com.example.tics

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MQTTService : Service() {
    private var mqttClient: MqttClient? = null

    companion object {
        const val ACTION_UPDATE_UI = "com.example.tics.UPDATE_UI"
        const val EXTRA_ALARM_SMOKE = "alarm_smoke"
        const val EXTRA_BRACELET_NEAR = "bracelet_near"
        const val EXTRA_RSSI = "rssi"
    }

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
            "tics/grupo1/esp32/tele/alarmSmoke",
            "tics/grupo1/esp32/tele/braceletNear",
            "tics/grupo1/esp32/tele/rssi"
        )

        topics.forEach { topic ->
            mqttClient?.subscribe(topic) { receivedTopic, message ->
                val payload = String(message.payload)
                Log.d("MQTT", "📍 Mensaje recibido: $topic -> $payload")

                when (receivedTopic) {
                    "tics/grupo1/esp32/tele/alarmSmoke" -> {
                        val alarmValue = payload.toIntOrNull() ?: 0
                        sendUpdateToUI(alarmValue, -1, -1)
                        if (alarmValue == 1) {
                            Log.d("MQTT", "▲ ¡ALERTA CRÍTICA! Humo o gas detectado")
                        } else {
                            Log.d("MQTT", "▼ Estado normal: Sin humo/gas")
                        }
                    }
                    "tics/grupo1/esp32/tele/braceletNear" -> {
                        val braceletValue = payload.toIntOrNull() ?: 0
                        sendUpdateToUI(-1, braceletValue, -1)
                        if (braceletValue == 0) {
                            Log.d("MQTT", "▲ ¡PULSERA DESCONECTADA! Persona alejada")
                        } else {
                            Log.d("MQTT", "▼ Pulsera conectada - Persona cerca")
                        }
                    }
                    "tics/grupo1/esp32/tele/rssi" -> {
                        val rssiValue = payload.toIntOrNull() ?: 0
                        sendUpdateToUI(-1, -1, rssiValue)
                        Log.d("MQTT", "■ Intensidad señal pulsera: $payload dBm")
                    }
                }
            }
        }
    }

    private fun sendUpdateToUI(alarmSmoke: Int, braceletNear: Int, rssi: Int) {
        val intent = Intent(ACTION_UPDATE_UI).apply {
            putExtra(EXTRA_ALARM_SMOKE, alarmSmoke)
            putExtra(EXTRA_BRACELET_NEAR, braceletNear)
            putExtra(EXTRA_RSSI, rssi)
        }
        sendBroadcast(intent)
        Log.d("MQTT", "📡 Enviando datos a UI - Humo: $alarmSmoke, Pulsera: $braceletNear, RSSI: $rssi")
    }

    override fun onDestroy() {
        mqttClient?.disconnect()
        super.onDestroy()
    }
}