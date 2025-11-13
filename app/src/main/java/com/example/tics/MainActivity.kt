package com.example.tics

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.util.Locale
import com.example.tics.ui.theme.TICSTheme  // ← ¡IMPORTANTE! Agregar este import

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // INICIAR MQTT
        Log.d("APP", "Iniciando servicio MQTT...")
        startService(Intent(this, MQTTService::class.java))

        setContent {
            // USA EL NOMBRE CORRECTO: TICSTheme (sin "Theme." delante)
            TICSTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF575757)),
                    color = Color(0xFF575757)
                ) {
                    StatusScreen()
                }
            }
        }
    }
}

@Composable
fun StatusScreen() {
    // Estados ORIGINALES pero conectados a MQTT después
    var gasAlert by remember { mutableStateOf(false) }    // false = normal
    var smokeAlert by remember { mutableStateOf(false) }  // false = normal
    var braceletConnected by remember { mutableStateOf(true) } // true = conectada
    var distanceM by remember { mutableDoubleStateOf(0.0) }     // 0 = esperando datos

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
        onClick = {
            // Tu código original de notificaciones
        },
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
        onClick = {
            // Tu código original de notificaciones
        },
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