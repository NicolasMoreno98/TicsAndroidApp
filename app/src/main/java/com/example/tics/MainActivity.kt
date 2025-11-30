package com.example.tics

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import com.example.tics.ui.theme.TICSTheme
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class MainActivity : ComponentActivity() {

    companion object {
        var gasAlert by mutableStateOf(false)
        var smokeAlert by mutableStateOf(false)
        var braceletConnected by mutableStateOf(true)
        var distanceM by mutableStateOf(0.0)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("APP", "Permiso de notificaciones CONCEDIDO")
        } else {
            Log.d("APP", "Permiso de notificaciones DENEGADO")
        }
    }

    private val uiUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MQTTService.ACTION_UPDATE_UI) {
                val alarmSmoke = intent.getIntExtra(MQTTService.EXTRA_ALARM_SMOKE, -1)
                val braceletNear = intent.getIntExtra(MQTTService.EXTRA_BRACELET_NEAR, -1)
                val rssi = intent.getIntExtra(MQTTService.EXTRA_RSSI, -1)

                Log.d("APP", "📱 Recibiendo datos - Humo: $alarmSmoke, Pulsera: $braceletNear, RSSI: $rssi")

                if (alarmSmoke != -1) {
                    smokeAlert = alarmSmoke == 1
                    gasAlert = alarmSmoke == 1 // Mismo valor por ahora
                }

                if (braceletNear != -1) {
                    braceletConnected = braceletNear == 1
                }

                if (rssi != -1) {
                    distanceM = convertRssiToMeters(rssi)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permiso de notificaciones en Android 13+
        askNotificationPermission()

        // Iniciar servicio MQTT en primer plano
        startMqttService()

        // Registrar BroadcastReceiver para actualizaciones de la UI
        val filter = IntentFilter(MQTTService.ACTION_UPDATE_UI)
        ContextCompat.registerReceiver(this, uiUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            TICSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF575757)
                ) {
                    StatusScreen()
                }
            }
        }
    }

    private fun startMqttService() {
        val intent = Intent(this, MQTTService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d("APP", "Iniciando servicio MQTT...")
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(uiUpdateReceiver)
    }

    private fun convertRssiToMeters(rssi: Int): Double {
        return when {
            rssi >= -50 -> 1.0
            rssi >= -60 -> 3.0
            rssi >= -70 -> 7.0
            rssi >= -80 -> 15.0
            else -> 30.0
        }
    }
}

@Composable
fun StatusScreen() {
    val gasAlert = MainActivity.gasAlert
    val smokeAlert = MainActivity.smokeAlert
    val braceletConnected = MainActivity.braceletConnected
    val distanceM = MainActivity.distanceM

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF575757))
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Monitoreo",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            AlertCard(title = "Gas", alert = gasAlert)
            AlertCard(title = "Humo", alert = smokeAlert)
            BraceletCard(connected = braceletConnected, distanceMeters = distanceM)
        }
    }
}

@Composable
fun AlertCard(title: String, alert: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2E2E2E),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val label = if (alert) "ALERTA" else "OK"
            val color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Text(label, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BraceletCard(connected: Boolean, distanceMeters: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2E2E2E),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pulsera", fontWeight = FontWeight.SemiBold)
                    Text(
                        "estado de conexión",
                        fontSize = 12.sp,
                        color = Color(0xFFBDBDBD)
                    )
                }
                Text(
                    if (connected) "CONECTADA" else "DESCONECTADA",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Distancia:",
                    fontSize = 14.sp,
                    color = Color(0xFFBDBDBD)
                )
                Text(
                    String.format(Locale.US, "%.2f m", distanceMeters),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
