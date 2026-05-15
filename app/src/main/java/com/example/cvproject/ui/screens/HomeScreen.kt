package com.example.cvproject.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cvproject.navigation.Screen
import com.example.cvproject.domain.speed.START_ANGLE
import com.example.cvproject.domain.speed.getAngle
import com.example.cvproject.ui.theme.F1Colors
import com.example.cvproject.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.nativeCanvas

 
private const val SETTINGS_GEAR_PADDING_TOP_DP = 48     
private const val SETTINGS_GEAR_PADDING_END_DP = 24    
private const val BUTTON_BOTTOM_PADDING_DP = 48         
private const val STATS_SHEET_PADDING_TOP_DP = 16       

 
private const val IGNITION_BUTTON_SIZE_DP = 140         
private const val NEON_SHADOW_ELEVATION_DP = 80        
private const val NEON_SHADOW_ALPHA = 0.9f             
private const val IGNITION_INNER_RIM_SIZE_DP = 136     
private const val IGNITION_INNER_RIM_BORDER_WIDTH_DP = 1.5f 
private const val IGNITION_TEXT_CONTAINER_SIZE_DP = 120
    
private const val TEXT_PULSE_DURATION_MS = 750         
private const val TEXT_PULSE_ALPHA_MIN = 0.3f          
private const val TEXT_PULSE_ALPHA_MAX = 1.0f          
private const val IGNITION_TEXT_FLASH_DURATION_MS = 200L
private const val IDLE_PULSE_SCALE_FACTOR = 0.02f
private const val IDLE_RETURN_DURATION_MS = 700

 
private const val SPEEDOMETER_LABEL_OFFSET_DP = 72     
private const val MAX_SPEED = 300f
private const val IDLE_SPEED = 0f
private const val TOTAL_SWEEP = 260f
private const val TICK_COUNT = 10
private const val TICK_INTERVAL = TOTAL_SWEEP / TICK_COUNT.toFloat()
 


@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val simulatedSpeed = remember { Animatable(IDLE_SPEED) }
    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f, 
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val textPulseAlpha by infiniteTransition.animateFloat(
        initialValue = TEXT_PULSE_ALPHA_MIN,
        targetValue = TEXT_PULSE_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TEXT_PULSE_DURATION_MS, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "textPulse",
    )
    var isIgnitionAnimating by rememberSaveable { mutableStateOf(false) }
    var forceTextAlphaFull by remember { mutableStateOf(false) }
    var flashTextColor by remember { mutableStateOf<Color?>(null) }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    simulatedSpeed.snapTo(IDLE_SPEED)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val textAlpha = if (forceTextAlphaFull) 1f else textPulseAlpha
    val baseTextColor = if (isIgnitionAnimating) F1Colors.Red else F1Colors.TextPrimary
    val startButtonTextColor = (flashTextColor ?: baseTextColor).copy(alpha = textAlpha)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startTracking(navController.context)
            navController.navigate(Screen.Detection.route)
        } else {
            scope.launch {
                simulatedSpeed.animateTo(
                    targetValue = IDLE_SPEED,
                    animationSpec = tween(durationMillis = IDLE_RETURN_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    LaunchedEffect(uiState.isTracking) {
        if (!uiState.isTracking && simulatedSpeed.value > IDLE_SPEED) {
            simulatedSpeed.animateTo(
                targetValue = IDLE_SPEED,
                animationSpec = tween(durationMillis = IDLE_RETURN_DURATION_MS, easing = FastOutSlowInEasing),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = SETTINGS_GEAR_PADDING_TOP_DP.dp, end = SETTINGS_GEAR_PADDING_END_DP.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚙",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                modifier = Modifier
                    .semantics { contentDescription = "Settings" }
                    .clickable { navController.navigate(Screen.Settings.route) },
            )
        }

        Text(
            text = "SPEED TRACKER",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(STATS_SHEET_PADDING_TOP_DP.dp))

        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    color = Color(0xAA111111),  
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .border(
                    width = 1.dp,
                    color = F1Colors.TextMuted.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "LAST SESSION",
                    color = F1Colors.Amber,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem(
                        label = "MAX",
                        value = "%.0f".format(uiState.allTimeMaxSpeed),
                        unit = uiState.speedUnit,
                        primaryTextColor = MaterialTheme.colorScheme.onSurface,
                        mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatItem(
                        label = "AVG",
                        value = "%.0f".format(uiState.allTimeAvgSpeed),
                        unit = uiState.speedUnit,
                        primaryTextColor = MaterialTheme.colorScheme.onSurface,
                        mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatItem(
                        label = "CARS",
                        value = "${uiState.totalCarsTracked}",
                        unit = "tracked",
                        primaryTextColor = MaterialTheme.colorScheme.onSurface,
                        mutedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        
        Canvas(
            modifier = Modifier.size(280.dp)
        ) {
            drawSpeedometer(simulatedSpeed.value)
        }

        Spacer(modifier = Modifier.height(16.dp))

         
        Box(
            modifier = Modifier
                .size(IGNITION_BUTTON_SIZE_DP.dp)
                .graphicsLayer {
                    val pulseScale = if (!uiState.isTracking) 1f + (idlePulse * IDLE_PULSE_SCALE_FACTOR) else 1f
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .clip(CircleShape)
                .shadow(
                    elevation = NEON_SHADOW_ELEVATION_DP.dp,
                    shape = CircleShape,
                    spotColor = F1Colors.Red.copy(alpha = NEON_SHADOW_ALPHA),
                )
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        colorStops = arrayOf(
                            0.0f to F1Colors.Red,
                            0.5f to F1Colors.RedDark,
                            1.0f to F1Colors.Red,
                        )
                    ),
                    shape = CircleShape,
                )
                .background(
                    brush = Brush.radialGradient(
                        listOf(
                            F1Colors.ButtonGradientLight,
                            F1Colors.ButtonGradientMid,
                        )
                    ),
                    shape = CircleShape,
                )
                .clickable(enabled = !isIgnitionAnimating && !simulatedSpeed.isRunning) {
                    isIgnitionAnimating = true
                    scope.launch {
                        try {
                            forceTextAlphaFull = true
                            flashTextColor = Color.White
                            delay(IGNITION_TEXT_FLASH_DURATION_MS)
                            flashTextColor = F1Colors.Red
                            simulatedSpeed.animateTo(
                                targetValue = MAX_SPEED,
                                animationSpec = tween(
                                    durationMillis = 1200,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } finally {
                            flashTextColor = null
                            forceTextAlphaFull = false
                            isIgnitionAnimating = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(IGNITION_INNER_RIM_SIZE_DP.dp)
                    .border(
                        width = IGNITION_INNER_RIM_BORDER_WIDTH_DP.dp,
                        color = F1Colors.InnerRimRed,
                        shape = CircleShape,
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(IGNITION_TEXT_CONTAINER_SIZE_DP.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "PUSH TO START",
                        color = startButtonTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 1.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(BUTTON_BOTTOM_PADDING_DP.dp))
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    unit: String,
    primaryTextColor: Color,
    mutedTextColor: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = mutedTextColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(text = value, color = primaryTextColor, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(text = unit, color = mutedTextColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun DrawScope.drawSpeedometer(speed: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f - 8.dp.toPx()

     
    val gridAlpha = 0.15f
    val gridStep = size.minDimension / 8f
    var gx = 0f
    while (gx <= size.width) {
        drawLine(
            color = F1Colors.TextMuted.copy(alpha = gridAlpha),
            start = Offset(gx, 0f),
            end = Offset(gx, size.height),
            strokeWidth = 1f,
        )
        gx += gridStep
    }
    var gy = 0f
    while (gy <= size.height) {
        drawLine(
            color = F1Colors.TextMuted.copy(alpha = gridAlpha),
            start = Offset(0f, gy),
            end = Offset(size.width, gy),
            strokeWidth = 1f,
        )
        gy += gridStep
    }

     
    drawArc(
        color = Color(0xFF1A1A2E),
        startAngle = START_ANGLE,
        sweepAngle = TOTAL_SWEEP,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round),
    )

  
    val progressSweep = (speed.coerceIn(0f, MAX_SPEED) / MAX_SPEED) * TOTAL_SWEEP
    if (progressSweep > 0f) {
        drawArc(
            color = F1Colors.Red,
            startAngle = START_ANGLE,
            sweepAngle = progressSweep,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Round),
        )
    }

   
    for (i in 0..TICK_COUNT) {
        val angle = Math.toRadians((START_ANGLE + i * TICK_INTERVAL).toDouble())
        val inner = if (i % 5 == 0) r - 40.dp.toPx() else r - 28.dp.toPx()
        val strokeW = if (i % 5 == 0) 3.dp.toPx() else 1.5.dp.toPx()
        drawLine(
            color = F1Colors.TextMuted,
            start = Offset(cx + inner * cos(angle).toFloat(), cy + inner * sin(angle).toFloat()),
            end = Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat()),
            strokeWidth = strokeW,
        )
    }

    
    val speedLabels = listOf(0 to "0", 300 to "300")
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#666666")  
        textSize = 32f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val markerRadius = r - SPEEDOMETER_LABEL_OFFSET_DP.dp.toPx()
    drawContext.canvas.nativeCanvas.let { nativeCanvas ->
        speedLabels.forEach { (speed, label) ->
            val angleDeg = getAngle(speed.toFloat())
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val lx = cx + markerRadius * cos(angleRad).toFloat()
            val ly = cy + markerRadius * sin(angleRad).toFloat() + paint.textSize / 3f
            nativeCanvas.drawText(label, lx, ly, paint)
        }
    }

    
    val needleLength = r - 30.dp.toPx()
    rotate(degrees = getAngle(speed) + 90f, pivot = Offset(cx, cy)) {
        drawLine(
            color = F1Colors.Red,
            start = Offset(cx, cy + 20.dp.toPx()),
            end = Offset(cx, cy - needleLength),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

        
    drawCircle(color = F1Colors.Surface, radius = 14.dp.toPx(), center = Offset(cx, cy))
    drawCircle(color = F1Colors.Red, radius = 6.dp.toPx(), center = Offset(cx, cy))
}