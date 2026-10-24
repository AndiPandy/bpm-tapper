package com.example.bpmtapper

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TapperScreen()
            }
        }
    }
}

@Composable
fun TapperScreen() {
    val taps = remember { mutableStateListOf<Long>() }
    var bpm by remember { mutableStateOf<Double?>(null) }
    var isActiveSession by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val pulseAnim = remember { Animatable(0f) }

    // 30-color wheel with rich, deep tones for guaranteed high white text contrast
    val colorWheel = remember {
        List(30) { i ->
            Color.hsl(i * (360f / 30f), 0.80f, 0.42f)
        }
    }
    var colorIndex by remember { mutableStateOf(Random.nextInt(30)) }
    val buttonColor = colorWheel[colorIndex]

    // 5-second inactivity timeout stops ONLY the active session glowing indicator
    LaunchedEffect(taps.lastOrNull()) {
        if (taps.isNotEmpty()) {
            isActiveSession = true
            delay(5000L)
            val now = System.nanoTime() / 1_000_000
            if (taps.isNotEmpty() && now - taps.last() >= 5000) {
                isActiveSession = false
            }
        }
    }

    fun triggerPulse() {
        coroutineScope.launch {
            pulseAnim.snapTo(0f)
            pulseAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(600, easing = LinearOutSlowInEasing)
            )
        }
    }

    fun recalculateBpm() {
        bpm = if (taps.size >= 2) {
            60_000.0 / ((taps.last() - taps.first()).toDouble() / (taps.size - 1))
        } else null
    }

    fun tap() {
        val now = System.nanoTime() / 1_000_000
        if (!isActiveSession && taps.isNotEmpty()) {
            taps.clear()
        }
        isActiveSession = true
        taps.add(now)
        recalculateBpm()
        colorIndex = (colorIndex + 1) % 30
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun scaleBpm(factor: Double) {
        isActiveSession = false
        if (bpm != null) {
            bpm = bpm!! * factor
        } else if (taps.size >= 2) {
            val calc = 60_000.0 / ((taps.last() - taps.first()).toDouble() / (taps.size - 1))
            bpm = calc * factor
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Subtle, non-intrusive top branding header pinned at the very top
            Text(
                text = "BPM TAPPER • by AndiPandy",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TapIntervalVisualizer(taps = taps)

                Spacer(Modifier.height(24.dp))

                TapperButtonContainer(
                    buttonColor = buttonColor,
                    isActiveSession = isActiveSession,
                    pulseValue = pulseAnim.value,
                    bpm = bpm,
                    onTap = {
                        tap()
                        triggerPulse()
                    }
                )

                Spacer(Modifier.height(36.dp))

                ControlButtons(
                    isResetEnabled = bpm != null,
                    onDivide = { scaleBpm(0.5) },
                    onMultiply = { scaleBpm(2.0) },
                    onReset = {
                        taps.clear()
                        bpm = null
                        isActiveSession = false
                    }
                )
            }

            // Bottom spacer balance filler
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun TapperButtonContainer(
    buttonColor: Color,
    isActiveSession: Boolean,
    pulseValue: Float,
    bpm: Double?,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier.size(340.dp),
        contentAlignment = Alignment.Center
    ) {
        // Smaller, more subtle active session indicator halo
        val ringAlphaAnim by animateFloatAsState(
            targetValue = if (isActiveSession) 1.0f else 0.0f,
            animationSpec = tween(durationMillis = if (isActiveSession) 400 else 600),
            label = "ringAlphaAnim"
        )

        if (ringAlphaAnim > 0f) {
            val infiniteTransition = rememberInfiniteTransition(label = "activeSessionRing")
            val ringScale by infiniteTransition.animateFloat(
                initialValue = 1.03f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ringScale"
            )
            val ringAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ringAlpha"
            )

            val finalAlpha = ringAlpha * ringAlphaAnim

            Box(
                modifier = Modifier
                    .size(340.dp)
                    .graphicsLayer(scaleX = ringScale, scaleY = ringScale)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to buttonColor.copy(alpha = finalAlpha),
                                    0.65f to buttonColor.copy(alpha = finalAlpha),
                                    1.0f to Color.Transparent
                                ),
                                radius = size.width / 2f
                            )
                        )
                    }
            )
        }

        // Visible, smooth pulsating white click flash
        if (pulseValue > 0f) {
            val alpha = (1f - pulseValue).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.White.copy(alpha = alpha * 0.85f),
                                    0.4f to Color.White.copy(alpha = alpha * 0.45f),
                                    0.8f to Color.White.copy(alpha = alpha * 0.1f),
                                    1.0f to Color.Transparent
                                ),
                                radius = size.width / 2f
                            )
                        )
                    }
            )
        }

        TapperButton(
            buttonColor = buttonColor,
            bpm = bpm,
            onTap = onTap
        )
    }
}

@Composable
fun RollingBpmDisplay(bpm: Double?) {
    val text = bpm?.let { "${it.roundToInt()}" } ?: "--"
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val isIncreasing = targetState >= initialState
                    val initialOffset = if (isIncreasing) { height: Int -> height } else { height: Int -> -height }
                    val targetOffset = if (isIncreasing) { height: Int -> -height } else { height: Int -> height }

                    (slideInVertically(animationSpec = tween(200), initialOffsetY = initialOffset) + fadeIn(animationSpec = tween(200)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(200), targetOffsetY = targetOffset) + fadeOut(animationSpec = tween(200))
                        )
                },
                label = "digit_$index"
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun TapperButton(
    buttonColor: Color,
    bpm: Double?,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(280.dp)
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                ambientColor = buttonColor,
                spotColor = buttonColor
            )
            .clip(CircleShape)
            .background(buttonColor)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Smart digit-by-digit rolling number animation
            RollingBpmDisplay(bpm = bpm)
            Spacer(Modifier.height(6.dp))
            // Smooth animated transition for label ("TAP HERE" vs "BPM")
            AnimatedContent(
                targetState = bpm != null,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(220)) + scaleOut(targetScale = 1.1f, animationSpec = tween(220)))
                },
                label = "labelTransition"
            ) { hasBpm ->
                Text(
                    text = if (hasBpm) "BPM" else "TAP HERE",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }
    }
}

@Composable
fun ControlButtons(
    isResetEnabled: Boolean,
    onDivide: () -> Unit,
    onMultiply: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val controlsAlpha by animateFloatAsState(
            targetValue = if (isResetEnabled) 1.0f else 0.4f,
            animationSpec = tween(350),
            label = "controlsAlpha"
        )

        OutlinedButton(
            onClick = onDivide,
            enabled = isResetEnabled,
            modifier = Modifier
                .height(50.dp)
                .graphicsLayer(alpha = controlsAlpha)
        ) {
            Text("÷2", fontSize = 18.sp)
        }

        Button(
            onClick = onReset,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.height(50.dp)
        ) {
            Text("Reset", fontSize = 18.sp)
        }

        OutlinedButton(
            onClick = onMultiply,
            enabled = isResetEnabled,
            modifier = Modifier
                .height(50.dp)
                .graphicsLayer(alpha = controlsAlpha)
        ) {
            Text("x2", fontSize = 18.sp)
        }
    }
}

@Composable
fun TapIntervalVisualizer(taps: List<Long>) {
    val animatedFractions = remember { mutableMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(taps.toList()) {
        if (taps.isEmpty()) {
            animatedFractions.clear()
            return@LaunchedEffect
        }

        val first = taps.first()
        val last = taps.last()
        val span = (last - first).toDouble()

        taps.forEachIndexed { index, tapTime ->
            val target = when {
                taps.size <= 1 -> 0f
                taps.size == 2 -> if (index == 0) 0f else 1f
                else -> {
                    if (span == 0.0) 0f else ((tapTime - first) / span).toFloat().coerceIn(0f, 1f)
                }
            }

            if (!animatedFractions.containsKey(tapTime)) {
                // New dot starts at 1.0 (far right) and slides left into position
                val anim = Animatable(1.0f)
                animatedFractions[tapTime] = anim
                coroutineScope.launch {
                    anim.animateTo(target, animationSpec = tween(400, easing = FastOutSlowInEasing))
                }
            } else {
                // Existing dot slides to updated target fraction as timeline span adjusts
                coroutineScope.launch {
                    animatedFractions[tapTime]?.animateTo(target, animationSpec = tween(400, easing = FastOutSlowInEasing))
                }
            }
        }
        animatedFractions.keys.retainAll(taps.toSet())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val centerY = size.height / 2f

                // Draw timeline track line
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(width, centerY),
                    strokeWidth = 3.5f
                )

                if (taps.isNotEmpty()) {
                    taps.forEachIndexed { index, tapTime ->
                        val fraction = animatedFractions[tapTime]?.value ?: 1.0f
                        val x = fraction * width
                        val radius = if (index == taps.size - 1) 7f else 5f

                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            center = androidx.compose.ui.geometry.Offset(x, centerY)
                        )
                    }
                }
            }
        }
    }
}
