package com.example.tics

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MQTTService : Service() {

    private var mqttClient: MqttClient? = null
    // Initialize with values that indicate "no data yet"
    private var lastAlarmSmoke = -1
    private var lastBraceletNear = -1
    private var lastRssi = -1

    companion object {
        const val ACTION_UPDATE_UI = "com.example.tics.UPDATE_UI"
        const val EXTRA_ALARM_SMOKE = "alarm_smoke"
        const val EXTRA_BRACELET_NEAR = "bracelet_near"
        const val EXTRA_RSSI = "rssi"
        private const val NOTIFICATION_ID = 1
        private const val RSSI_THRESHOLD = -60 // As requested: alert if RSSI is -60 or lower
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MQTT", "Servicio MQTT iniciado...")

        NotificationUtils.createChannel(this)

        val notification = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID)
            .setContentTitle("TICS App")
            .setContentText("Protección en segundo plano activada.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(NOTIFICATION_ID, notification)

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
        // We only subscribe to topics that provide raw data.
        // `braceletNear` is now calculated locally.
        val topics = arrayOf(
            "tics/grupo1/esp32/tele/alarmSmoke",
            "tics/grupo1/esp32/tele/rssi"
        )

        mqttClient?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.d("MQTT", "Conexión perdida: ${cause?.message}")
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = String(message.payload)
                Log.d("MQTT", "📍 Mensaje recibido: $topic -> $payload")

                when (topic) {
                    "tics/grupo1/esp32/tele/alarmSmoke" -> {
                        val alarmValue = payload.toIntOrNull() ?: 0
                        // Notify only when changing to alert state
                        if (alarmValue == 1 && lastAlarmSmoke != 1) {
                            NotificationUtils.notify(this@MQTTService, "¡Alerta de Humo!", "Se ha detectado humo o gas en el ambiente.", 2)
                            Log.d("MQTT", "▲ ¡ALERTA CRÍTICA! Humo o gas detectado")
                        }
                        lastAlarmSmoke = alarmValue
                    }
                    "tics/grupo1/esp32/tele/rssi" -> {
                        val rssiValue = payload.toIntOrNull() ?: -1
                        lastRssi = rssiValue

                        // --- LOCAL BRACELET LOGIC ---
                        // Determine status based on RSSI threshold
                        val newBraceletStatus = if (rssiValue > RSSI_THRESHOLD) 1 else 0

                        // Notify only when changing to disconnected state (1 -> 0)
                        if (newBraceletStatus == 0 && lastBraceletNear != 0) {
                            NotificationUtils.notify(this@MQTTService, "¡Alerta de Pulsera!", "Se ha perdido la conexión con la pulsera.", 3)
                            Log.d("MQTT", "▲ ¡PULSERA DESCONECTADA! Persona alejada (RSSI: $rssiValue)")
                        }
                        lastBraceletNear = newBraceletStatus
                    }
                }

                // After any message, send a complete update to the UI
                sendUpdateToUI(lastAlarmSmoke, lastBraceletNear, lastRssi)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        topics.forEach { topic ->
            mqttClient?.subscribe(topic, 0) // Subscribe with QoS 0
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
        super.onDestroy()
        mqttClient?.disconnect()
        Log.d("MQTT", "Servicio MQTT detenido.")
    }
}
