package com.spiderhub.webverse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiderhub.webverse.game.Dimension
import com.spiderhub.webverse.game.GameHudState
import kotlin.math.hypot

@Composable
fun GameHud(
    hud: GameHudState,
    onMoveStick: (Float, Float) -> Unit,
    onLookDrag: (Float, Float) -> Unit,
    onSwingChanged: (Boolean) -> Unit,
    onJump: () -> Unit,
    onZip: () -> Unit,
    onStrike: () -> Unit,
    onShiftDimension: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBloom: () -> Unit,
    onOpenAssets: () -> Unit
) {
    val accent = Color(hud.dimension.accentHex)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactWidth = maxWidth < 600.dp

        // Full-screen camera look drag surface behind HUD controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onLookDrag(dragAmount.x, dragAmount.y)
                    }
                }
        )

        // Subtle Center Grapple Target Crosshair
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ringColor = when {
                    hud.isSwinging -> Color(0xFF00FFB3)
                    hud.hasGrappleTarget -> accent
                    else -> Color(0x44FFFFFF)
                }
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension * if (hud.hasGrappleTarget) 0.42f else 0.28f,
                    style = Stroke(width = if (hud.hasGrappleTarget) 3.5f else 2f)
                )
                drawCircle(
                    color = ringColor,
                    radius = 3.5f
                )
            }
        }

        // Safe-area container for landscape HUD elements
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Top Tactical HUD Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    TelemetryCard(hud = hud, accent = accent)

                    if (!isCompactWidth) {
                        ObjectiveBanner(hud = hud, accent = accent)
                    }

                    ScoreAndButtonsCard(
                        hud = hud,
                        accent = accent,
                        onOpenAssets = onOpenAssets,
                        onToggleMute = onToggleMute,
                        onToggleBloom = onToggleBloom
                    )
                }

                if (isCompactWidth) {
                    ObjectiveBanner(
                        hud = hud,
                        accent = accent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Bottom-Left: Virtual Analog Thumbstick
            VirtualThumbstick(
                accent = accent,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp)
                    .testTag("virtual_thumbstick"),
                onMove = onMoveStick
            )

            // Bottom-Right: Ergonomic Landscape Action Cluster
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        label = "SHIFT",
                        color = when (hud.dimension) {
                            Dimension.SUNSET_NEXUS -> Color(0xFF00F0FF)
                            Dimension.NEON_NOIR -> Color(0xFFFF2E63)
                            Dimension.INKVERSE -> Color(0xFFFF9100)
                        },
                        sizeDp = 52,
                        tag = "shift_button",
                        onClick = onShiftDimension
                    )
                    ActionButton(
                        label = "ZIP",
                        color = Color(0xFF00E5FF),
                        sizeDp = 52,
                        tag = "zip_button",
                        onClick = onZip
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    ActionButton(
                        label = "STRIKE",
                        color = Color(0xFFFF5252),
                        sizeDp = 58,
                        tag = "strike_button",
                        onClick = onStrike
                    )
                    ActionButton(
                        label = if (hud.isWallRunning) "WALL" else "JUMP",
                        color = Color(0xFF00FFB3),
                        sizeDp = 60,
                        tag = "jump_button",
                        onClick = onJump
                    )
                    SwingHoldButton(
                        isSwinging = hud.isSwinging,
                        hasTarget = hud.hasGrappleTarget,
                        accent = accent,
                        onSwingChanged = onSwingChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(hud: GameHudState, accent: Color) {
    Column(
        modifier = Modifier
            .background(
                Brush.verticalGradient(listOf(Color(0xCC090E1C), Color(0xAA050811))),
                RoundedCornerShape(10.dp)
            )
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = hud.dimension.label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        HudBar(
            label = "SUIT ${hud.health}%",
            fraction = hud.health / hud.maxHealth.toFloat().coerceAtLeast(1f),
            barColor = Color(0xFFFF2E63)
        )
        Spacer(modifier = Modifier.height(4.dp))
        HudBar(
            label = "WEB ${hud.stamina.toInt()}%",
            fraction = hud.stamina / 100f,
            barColor = Color(0xFF00F0FF)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${hud.speedMps.toInt()} m/s • ALT ${hud.altitudeM.toInt()}m",
            color = Color(0xFFD5E1F5),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ObjectiveBanner(hud: GameHudState, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xBD070B16), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hud.storyTitle,
                color = Color(0xFF00FFB3),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = if (hud.portalActive) "PORTAL READY • ${hud.portalDistanceM}m" else "RIFT ${hud.portalDistanceM}m",
                color = if (hud.portalActive) Color(0xFF00FFB3) else accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = hud.storyObjective,
            color = Color.White,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ScoreAndButtonsCard(
    hud: GameHudState,
    accent: Color,
    onOpenAssets: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBloom: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier
                .background(Color(0xBD070B16), RoundedCornerShape(10.dp))
                .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SCORE ${hud.score}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SHARDS ${hud.shardsCollected}/${hud.totalShards} • HUSKS ${hud.husksDefeated}",
                    color = Color(0xFF9EB0D0),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (hud.combo > 1) {
                Text(
                    text = "x${hud.combo}",
                    color = Color(0xFFFFD54F),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallHudChip(
                text = "${hud.fps} FPS • " + if (hud.ultraBloomEnabled) "ULTRA" else "MAX FPS",
                borderColor = if (hud.ultraBloomEnabled) Color(0xFF00FFB3) else Color(0xFFFFD54F),
                tag = "bloom_toggle_chip",
                onClick = onToggleBloom
            )
            SmallHudChip(
                text = if (hud.assetsReady) "3D RIGS (8)" else "LOADING...",
                borderColor = accent,
                tag = "assets_chip",
                onClick = onOpenAssets
            )
            SmallHudChip(
                text = if (hud.muted) "SFX: OFF" else "SFX: ON",
                borderColor = Color(0xFF9EB0D0),
                tag = "sfx_chip",
                onClick = onToggleMute
            )
        }
    }
}

@Composable
private fun HudBar(label: String, fraction: Float, barColor: Color) {
    Column {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .width(126.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x44FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun SmallHudChip(text: String, borderColor: Color, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .background(Color(0xCC0B1020), RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun VirtualThumbstick(
    accent: Color,
    modifier: Modifier = Modifier,
    onMove: (Float, Float) -> Unit
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(116.dp)
            .pointerInput(Unit) {
                val maxRadius = (size.width * 0.36f).coerceAtLeast(1f)
                detectDragGestures(
                    onDragEnd = {
                        knobX = 0f; knobY = 0f
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobX = 0f; knobY = 0f
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val nx = knobX + dragAmount.x
                    val ny = knobY + dragAmount.y
                    val dist = hypot(nx, ny)
                    if (dist > maxRadius) {
                        knobX = (nx / dist) * maxRadius
                        knobY = (ny / dist) * maxRadius
                    } else {
                        knobX = nx
                        knobY = ny
                    }
                    onMove(knobX / maxRadius, knobY / maxRadius)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color(0x66080D1C),
                radius = size.minDimension * 0.48f,
                center = center
            )
            drawCircle(
                color = accent.copy(alpha = 0.65f),
                radius = size.minDimension * 0.48f,
                center = center,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = accent,
                radius = size.minDimension * 0.19f,
                center = Offset(center.x + knobX, center.y + knobY)
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    sizeDp: Int,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xC8090E1D))
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SwingHoldButton(
    isSwinging: Boolean,
    hasTarget: Boolean,
    accent: Color,
    onSwingChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val ringColor = if (isSwinging || hasTarget) Color(0xFF00FFB3) else accent

    Box(
        modifier = Modifier
            .testTag("swing_button")
            .size(72.dp)
            .clip(CircleShape)
            .background(if (isSwinging || pressed) ringColor.copy(alpha = 0.35f) else Color(0xD00A1022))
            .border(2.5.dp, ringColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onSwingChanged(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                            onSwingChanged(false)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSwinging) "WEB\nON" else if (hasTarget) "LOCK\nSWING" else "SWING",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
