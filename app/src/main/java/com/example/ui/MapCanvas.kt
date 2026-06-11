package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.example.data.Friend
import kotlin.math.cos

@Composable
fun MapCanvas(
    userLat: Double,
    userLng: Double,
    friends: List<Friend>,
    selectedFriendId: String?,
    mapScale: Float,
    mapOffsetX: Float,
    mapOffsetY: Float,
    onMapTransform: (zoom: Float, panX: Float, panY: Float) -> Unit,
    onFriendClick: (String) -> Unit
) {
    // Custom Sophisticated Dark Design Colors matching Theme exactly
    val mapBgColor = Color(0xFF25232A)
    val gridColor = Color(0xFF49454F).copy(alpha = 0.35f)
    val waterColor = Color(0xFF381E72).copy(alpha = 0.6f)
    val parkColor = Color(0xFF1C1B1F)
    val roadColor = Color(0xFF2B2930)
    val roadAccentColor = Color(0xFF49454F)
    val userPinColor = Color(0xFFD0BCFF)
    val friendPinColor = Color(0xFFB1EEFF) // Sam's signature Cyan
    val selectedFriendColor = Color(0xFFEADDFF) // M3 Lavender container highlight
    val routeLineColor = Color(0xFFD0BCFF).copy(alpha = 0.7f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onMapTransform(zoom, pan.x, pan.y)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)

            clipRect {
                // 1. Draw solid background
                drawRect(color = mapBgColor)

                // Scale factor: degrees of relative lat/lng to screen pixels
                // At zoom scale 1f, we'll map 0.01 degree to 400 pixels
                val conversionFactor = 40000f * mapScale

                // Calculated map center offsets combined with manual map panning offsets
                val finalCenterX = center.x + mapOffsetX
                val finalCenterY = center.y + mapOffsetY

                // 2. Draw modern digital background coordinate grid
                val gridSpacing = 80f * mapScale
                var gridX = finalCenterX % gridSpacing
                if (gridX < 0) gridX += gridSpacing
                while (gridX < width) {
                    drawLine(
                        color = gridColor,
                        start = Offset(gridX, 0f),
                        end = Offset(gridX, height),
                        strokeWidth = 1.5f
                    )
                    gridX += gridSpacing
                }

                var gridY = finalCenterY % gridSpacing
                if (gridY < 0) gridY += gridSpacing
                while (gridY < height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gridY),
                        end = Offset(width, gridY),
                        strokeWidth = 1.5f
                    )
                    gridY += gridSpacing
                }

                // 3. Draw Waterbodies (Simulated beautiful Cyber river flowing through)
                val riverPath = Path().apply {
                    val startY = finalCenterY + 200f * mapScale
                    moveTo(-100f, startY)
                    cubicTo(
                        width * 0.25f + mapOffsetX, startY - 300f * mapScale,
                        width * 0.75f + mapOffsetX, startY + 400f * mapScale,
                        width + 100f, startY - 100f * mapScale
                    )
                }
                drawPath(
                    path = riverPath,
                    color = waterColor,
                    style = Stroke(width = 160f * mapScale)
                )

                // 4. Draw Green Spaces (Simulated Safe Zone Parks)
                drawRoundRect(
                    color = parkColor,
                    topLeft = Offset(finalCenterX - 400f * mapScale, finalCenterY - 600f * mapScale),
                    size = Size(350f * mapScale, 250f * mapScale),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * mapScale)
                )

                drawRoundRect(
                    color = parkColor,
                    topLeft = Offset(finalCenterX + 200f * mapScale, finalCenterY + 350f * mapScale),
                    size = Size(300f * mapScale, 200f * mapScale),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f * mapScale)
                )

                // 5. Draw Primary Cyber Highways (Clean, high-contrast grid streets)
                // Diagonal speedway
                drawLine(
                    color = roadColor,
                    start = Offset(finalCenterX - 1500f * mapScale, finalCenterY - 1500f * mapScale),
                    end = Offset(finalCenterX + 1500f * mapScale, finalCenterY + 1500f * mapScale),
                    strokeWidth = 32f * mapScale
                )
                drawLine(
                    color = roadAccentColor,
                    start = Offset(finalCenterX - 1500f * mapScale, finalCenterY - 1500f * mapScale),
                    end = Offset(finalCenterX + 1500f * mapScale, finalCenterY + 1500f * mapScale),
                    strokeWidth = 4f * mapScale
                )

                // Horizontal Expressway
                drawLine(
                    color = roadColor,
                    start = Offset(-2000f, finalCenterY),
                    end = Offset(width + 2000f, finalCenterY),
                    strokeWidth = 40f * mapScale
                )
                drawLine(
                    color = roadAccentColor,
                    start = Offset(-2000f, finalCenterY),
                    end = Offset(width + 2000f, finalCenterY),
                    strokeWidth = 5f * mapScale
                )

                // Vertical Boulevard
                drawLine(
                    color = roadColor,
                    start = Offset(finalCenterX, -2000f),
                    end = Offset(finalCenterX, height + 2000f),
                    strokeWidth = 40f * mapScale
                )
                drawLine(
                    color = roadAccentColor,
                    start = Offset(finalCenterX, -2000f),
                    end = Offset(finalCenterX, height + 2000f),
                    strokeWidth = 5f * mapScale
                )

                // 6. Draw Routing line from Self to Selected friend
                val selectedFriend = friends.find { it.id == selectedFriendId }
                if (selectedFriend != null && selectedFriend.trackingActive) {
                    val dLng = selectedFriend.longitude - userLng
                    val dLat = selectedFriend.latitude - userLat

                    val friendX = finalCenterX + (dLng.toFloat() * conversionFactor)
                    val friendY = finalCenterY - (dLat.toFloat() * conversionFactor)

                    drawLine(
                        color = routeLineColor,
                        start = Offset(finalCenterX, finalCenterY),
                        end = Offset(friendX, friendY),
                        strokeWidth = 6f * mapScale,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(15f, 15f), 0f
                        )
                    )
                }

                // 7. Draw User's Current Location (Pulsing blue radar pin)
                drawCircle(
                    color = userPinColor.copy(alpha = 0.25f),
                    center = Offset(finalCenterX, finalCenterY),
                    radius = 45f * mapScale
                )
                drawCircle(
                    color = userPinColor,
                    center = Offset(finalCenterX, finalCenterY),
                    radius = 12f * mapScale
                )
                drawCircle(
                    color = Color.White,
                    center = Offset(finalCenterX, finalCenterY),
                    radius = 5f * mapScale
                )

                // Draw Text Label for self
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f * mapScale.coerceAtLeast(0.5f).coerceAtMost(2f)
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val labelY = finalCenterY - 24f * mapScale
                    drawText("Secure Portal (You)", finalCenterX, labelY, paint)
                }

                // 8. Draw Friend Pins and Labels
                friends.forEach { friend ->
                    if (friend.trackingActive) {
                        val dLng = friend.longitude - userLng
                        val dLat = friend.latitude - userLat

                        val fx = finalCenterX + (dLng.toFloat() * conversionFactor)
                        val fy = finalCenterY - (dLat.toFloat() * conversionFactor)

                        val isSelected = friend.id == selectedFriendId
                        val pinColor = if (isSelected) selectedFriendColor else friendPinColor

                        // Pulsing exterior halo for active movement
                        if (friend.speedKmh > 5.0) {
                            drawCircle(
                                color = pinColor.copy(alpha = 0.15f),
                                center = Offset(fx, fy),
                                radius = 60f * mapScale
                            )
                        }

                        // Drawing primary pin node
                        drawCircle(
                            color = pinColor.copy(alpha = 0.35f),
                            center = Offset(fx, fy),
                            radius = 35f * mapScale
                        )
                        drawCircle(
                            color = pinColor,
                            center = Offset(fx, fy),
                            radius = 14f * mapScale
                        )
                        drawCircle(
                            color = Color.White,
                            center = Offset(fx, fy),
                            radius = 6f * mapScale
                        )

                        // Label details
                        drawContext.canvas.nativeCanvas.apply {
                            val paintName = android.graphics.Paint().apply {
                                color = if (isSelected) 0xFFD0BCFF.toInt() else 0xFFE6E1E5.toInt()
                                textSize = 30f * mapScale.coerceAtLeast(0.5f).coerceAtMost(2.0f)
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            val paintSub = android.graphics.Paint().apply {
                                color = 0xFFC9C5D0.toInt()
                                textSize = 22f * mapScale.coerceAtLeast(0.5f).coerceAtMost(2.0f)
                                textAlign = android.graphics.Paint.Align.CENTER
                            }

                            val labelYName = fy - 52f * mapScale
                            val labelYSub = fy - 28f * mapScale
                            val speedText = if (friend.speedKmh > 0.1) "${friend.speedKmh.toInt()} km/h" else "Static"
                            drawText(friend.name, fx, labelYName, paintName)
                            drawText("🔋${friend.batteryPercent}% • $speedText", fx, labelYSub, paintSub)
                        }
                    }
                }
            }
        }
    }
}
