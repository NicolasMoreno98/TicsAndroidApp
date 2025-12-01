package com.example.tics

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.example.tics.ui.theme.TICSTheme
import java.util.Locale
import kotlin.math.pow

class MainActivity : ComponentActivity() {

    // State is now held by the Activity instance, not a companion object.
    private var gasAlert by mutableStateOf(false)
    private var smokeAlert by mutableStateOf(false)
    private var braceletConnected by mutableStateOf(true)
    private var distanceM by mutableStateOf(0.0)

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

                // We still want to calculate the distance even if rssi is -1 initially
                // The conversion function will handle the invalid value.
                distanceM = convertRssiToMeters(rssi)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        // FIX: Register the receiver BEFORE starting the service to avoid race conditions.
        val filter = IntentFilter(MQTTService.ACTION_UPDATE_UI)
        ContextCompat.registerReceiver(this, uiUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        startMqttService()

        setContent {
            TICSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF575757)
                ) {
                    StatusScreen(
                        gasAlert = gasAlert,
                        smokeAlert = smokeAlert,
                        braceletConnected = braceletConnected,
                        distanceM = distanceM
                    )
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
        // RSSI values are negative. A value of -1 indicates no data yet.
        // A value of 0 or positive is an error or at point-blank range.
        if (rssi >= -1) return 0.0

        val txPower = -52 // Assumed signal strength at 1 meter (in dBm).
        val n = 5.65       // Environmental factor.

        return 10.0.pow((txPower - rssi) / (10 * n))
    }
}

@Composable
fun StatusScreen(
    gasAlert: Boolean,
    smokeAlert: Boolean,
    braceletConnected: Boolean,
    distanceM: Double
) {
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

                val isNa = distanceMeters <= 0.01
                val statusText: String
                val statusColor: Color

                if (isNa) {
                    statusText = "DESCONECTADA"
                    statusColor = MaterialTheme.colorScheme.error
                } else if (connected) {
                    statusText = "CONECTADA"
                    statusColor = MaterialTheme.colorScheme.primary
                } else {
                    statusText = "FUERA DE ZONA SEGURA"
                    statusColor = MaterialTheme.colorScheme.error
                }

                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
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

                // If distance is effectively 0, it means no valid RSSI has been received yet.
                val distanceText = if (distanceMeters > 0.01) {
                    String.format(Locale.US, "%.2f m", distanceMeters)
                } else {
                    "N/A"
                }

                Text(
                    text = distanceText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
