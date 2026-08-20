package com.demo.upimesh.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.demo.upimesh.ui.theme.UpiDarkBlue
import com.demo.upimesh.ui.theme.UpiPrimaryBlue
import com.demo.upimesh.ui.theme.UpiSuccessGreen

data class MeshNode(
    val id: String,
    val name: String,
    val initial: String,
    val x: Float, // Relative 0.0 - 1.0
    val y: Float,
    val isServer: Boolean = false,
    val isBridge: Boolean = false,
    val packetCount: Int = 0
)

@Composable
fun BleTopologyCanvas(
    modifier: Modifier = Modifier,
    activeHopIndex: Int = -1,
) {
    val nodes = listOf(
        MeshNode("tanish", "Tanish", "T", 0.15f, 0.5f),
        MeshNode("rahul", "Rahul", "R", 0.38f, 0.25f),
        MeshNode("priya", "Priya", "P", 0.38f, 0.75f),
        MeshNode("aman", "Aman", "A", 0.62f, 0.25f),
        MeshNode("riya", "Riya-B", "R", 0.78f, 0.5f, isBridge = true),
        MeshNode("bank", "Bank Server", "S", 0.92f, 0.5f, isServer = true)
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRadius"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interactive BLE Gossip Mesh Grid",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UpiSuccessGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (activeHopIndex >= 0) "Packet Active (Hop $activeHopIndex)" else "Mesh Idle",
                        color = UpiSuccessGreen,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    fun getNodeOffset(node: MeshNode) = Offset(node.x * w, node.y * h)

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    // Draw mesh topology connections
                    val connections = listOf(
                        Pair(nodes[0], nodes[1]),
                        Pair(nodes[0], nodes[2]),
                        Pair(nodes[1], nodes[3]),
                        Pair(nodes[2], nodes[4]),
                        Pair(nodes[3], nodes[4]),
                        Pair(nodes[4], nodes[5])
                    )

                    connections.forEach { (n1, n2) ->
                        drawLine(
                            color = Color(0xFF475569),
                            start = getNodeOffset(n1),
                            end = getNodeOffset(n2),
                            strokeWidth = 3f,
                            pathEffect = dashEffect
                        )
                    }

                    // Draw nodes
                    nodes.forEachIndexed { idx, node ->
                        val pos = getNodeOffset(node)
                        val radius = if (node.isServer) 28f else 24f
                        val color = when {
                            node.isServer -> UpiSuccessGreen
                            node.isBridge -> Color(0xFFA855F7)
                            idx == activeHopIndex -> Color(0xFF38BDF8)
                            else -> UpiPrimaryBlue
                        }

                        // Pulse active node
                        if (idx == activeHopIndex) {
                            drawCircle(
                                color = color.copy(alpha = 0.3f),
                                radius = radius * pulseAnim * 1.5f,
                                center = pos
                            )
                        }

                        drawCircle(
                            color = color,
                            radius = radius,
                            center = pos
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                this.color = android.graphics.Color.WHITE
                                textSize = 28f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                            }
                            drawText(node.initial, pos.x, pos.y + 10f, paint)

                            val labelPaint = android.graphics.Paint().apply {
                                this.color = android.graphics.Color.LTGRAY
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText(node.name, pos.x, pos.y + radius + 22f, labelPaint)
                        }
                    }
                }
            }
        }
    }
}
