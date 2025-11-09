package com.example.tics

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.tics.ui.theme.TICSTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Crear canal de notificaciones
        NotificationUtils.createChannel(this)

        // Pedir permiso en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PermissionChecker.PERMISSION_GRANTED
            if (!granted) {
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
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
    // Estados mock (luego vendrán del ESP32)
    var gasAlert by remember { mutableStateOf(true) }    // true = alerta
    var smokeAlert by remember { mutableStateOf(true) }   // true = alerta
    var distanceM by remember { mutableStateOf(320.0) }   // metros
    var distanceHistory by remember {
        mutableStateOf(listOf(280f, 295f, 310f, 330f, 315f, 340f, 325f, 318f, 300f, 305f))
    }

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
            DistanceCard(title = "Distancia", distanceMeters = distanceM, history = distanceHistory)
        }
    }
}

@Composable
fun AlertCard(title: String, alert: Boolean) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    Card(
        onClick = {
            // Haptic
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            // Notificación según estado
            val text = if (alert)
                "¡Alerta con los niveles de ${title.lowercase()}!"
            else
                "Estado de ${title.lowercase()}: OK."

            NotificationUtils.notify(
                context = context,
                title = "Estado ${title}",
                text = text,
                notificationId = if (title == "Gas") 1001 else 1002
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
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
fun DistanceCard(title: String, distanceMeters: Double, history: List<Float>) {
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    Card(
        onClick = {
            // Haptic
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            // Notificación según distancia
            val text = if (distanceMeters > 300.0)
                "¡La persona se encuentra a más de 300 metros de distancia!"
            else
                "Distancia actual: %.2f m".format(distanceMeters)

            NotificationUtils.notify(
                context = context,
                title = "Estado distancia",
                text = text,
                notificationId = 1003
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
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
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "distancia de la pulsera",
                        fontSize = 12.sp,
                        color = Color(0xFFBDBDBD)
                    )
                }
                Text(
                    String.format("%.2f m", distanceMeters),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF2E2E2E))
            ) {
                DistanceSparkline(
                    data = history,
                    lineColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DistanceSparkline(data: List<Float>, lineColor: Color) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        if (data.isEmpty()) return@Canvas

        val max = (data.maxOrNull() ?: 1f)
        val min = (data.minOrNull() ?: 0f)
        val range = (max - min).takeIf { it > 0f } ?: 1f

        val stepX = size.width / (data.size - 1).coerceAtLeast(1)
        val path = Path()

        data.forEachIndexed { i, v ->
            val x = i * stepX
            val norm = (v - min) / range
            val y = size.height - (norm * size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF575757)
@Composable
fun PreviewStatus() {
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
