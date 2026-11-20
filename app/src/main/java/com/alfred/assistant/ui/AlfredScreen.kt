package com.alfred.assistant.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfred.assistant.service.AlfredService

@Composable
fun AlfredScreen(
    permissionsGranted: Boolean = true,
    onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    val goldColor = Color(0xFFFFB800)

    var isServiceRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Alfred is ready") }
    var lastCommand by remember { mutableStateOf<String?>(null) }
    var lastResponse by remember { mutableStateOf<String?>(null) }

    // Listen for broadcast status updates from AlfredService
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let {
                    statusText = it.getStringExtra("status") ?: "Alfred is ready"
                    it.getStringExtra("command")?.let { cmd -> lastCommand = cmd }
                    it.getStringExtra("response")?.let { resp -> lastResponse = resp }
                    if (it.hasExtra("running")) {
                        isServiceRunning = it.getBooleanExtra("running", false)
                    }
                }
            }
        }
        val filter = IntentFilter("com.alfred.assistant.STATUS_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Animated pulsing ring
    val infiniteTransition: InfiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Animate status text color
    val statusColor by animateColorAsState(
        targetValue = when (statusText) {
            "Listening..." -> goldColor
            "Processing..." -> Color(0xFF58A6FF)
            else -> MaterialTheme.colorScheme.onBackground
        },
        animationSpec = tween(durationMillis = 400),
        label = "statusColor"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!permissionsGranted) {
                        onRequestPermissions()
                        return@FloatingActionButton
                    }
                    if (isServiceRunning) {
                        AlfredService.stop(context)
                        isServiceRunning = false
                        statusText = "Alfred is offline"
                    } else {
                        AlfredService.start(context)
                        isServiceRunning = true
                        statusText = "Alfred is ready"
                    }
                },
                containerColor = goldColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = if (isServiceRunning) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (isServiceRunning) "Stop Alfred" else "Start Alfred"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Monogram with pulsing ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                // Pulsing ring
                if (isServiceRunning) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val radius = (size.minDimension / 2f) * pulseScale
                        drawCircle(
                            color = goldColor.copy(alpha = pulseAlpha),
                            radius = radius,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }
                }

                // Static ring
                Canvas(modifier = Modifier.size(160.dp)) {
                    drawCircle(
                        color = goldColor.copy(alpha = 0.3f),
                        radius = size.minDimension / 2f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // "A" monogram
                Text(
                    text = "A",
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    color = goldColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status text
            Text(
                text = statusText,
                style = MaterialTheme.typography.headlineSmall,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Last command/response card
            if (lastCommand != null || lastResponse != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        if (lastCommand != null) {
                            Text(
                                text = "You said:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"$lastCommand\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = goldColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (lastCommand != null && lastResponse != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        if (lastResponse != null) {
                            Text(
                                text = "Alfred:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastResponse!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom hint text
            Text(
                text = "Say \"Hey Alfred\" followed by a command",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
