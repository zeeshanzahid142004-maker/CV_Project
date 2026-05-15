package com.example.cvproject.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cvproject.ui.theme.F1Colors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private const val SPLASH_FALLBACK_NAV_DELAY_MS = 150L
private val CAR_Y_FRACTIONS = listOf(0.38f, 0.50f, 0.62f)
private val CAR_COLORS = listOf(
    F1Colors.Red,
    F1Colors.Blue,
    F1Colors.Green
)
private const val CAR_W = 140f
private const val CAR_H = 22f
private const val PARTICLE_COUNT = 20

 
private val JITTER_OFFSETS: List<Float> = List(PARTICLE_COUNT) { Random.nextFloat() * 8f - 4f }

@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    Log.d("AppDebug", "SplashScreen Composed")

    
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = remember(configuration) {
        with(density) { configuration.screenWidthDp.dp.toPx() }
    }

    val car1X = remember { Animatable(-CAR_W) }
    val car2X = remember { Animatable(-CAR_W) }
    val car3X = remember { Animatable(-CAR_W) }
    val carAnimatables = listOf(car1X, car2X, car3X)

    var showTitle by remember { mutableStateOf(false) }
    var hasStartedAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(screenWidthPx) {
        try {
            if (hasStartedAnimation || screenWidthPx <= 0f) return@LaunchedEffect
            hasStartedAnimation = true

            // 1. Launch the car animations in the background
            carAnimatables.forEachIndexed { i, anim ->
                launch {
                    delay(i * 150L) // Stagger the car launches
                    anim.animateTo(
                        targetValue = screenWidthPx + CAR_W,
                        animationSpec = tween(
                            durationMillis = (900 + i * 120),
                            easing = FastOutSlowInEasing,
                        )
                    )
                }
            }

          
            delay(300)
            showTitle = true

           
            delay(1200)
            onNavigateToHome()

        } catch (e: CancellationException) {
             
            throw e
        } catch (e: Exception) {
            Log.e("AppDebug", "Splash Animation Failed", e)
            Log.d("AppDebug", "Navigating to home screen after splash animation failure")
            delay(SPLASH_FALLBACK_NAV_DELAY_MS)
            onNavigateToHome()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(F1Colors.Background),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            carAnimatables.forEachIndexed { i, anim ->
                val x = anim.value
                val y = size.height * CAR_Y_FRACTIONS[i]
                val color = CAR_COLORS[i]
                val bounce = sin(x * 0.03f) * 3f

                // Speed ratio 0..1 used for motion-blur length
                val speedRatio = (x / (screenWidthPx + CAR_W)).coerceIn(0f, 1f)
                val exitFade = ((screenWidthPx + CAR_W - x) / CAR_W).coerceIn(0f, 1f)

                // Smoke / dust particle trail
                for (p in 0 until PARTICLE_COUNT) {
                    val spacing = CAR_W * 1.4f / PARTICLE_COUNT
                    val trailX = x - p * spacing
                    val progress = p.toFloat() / PARTICLE_COUNT
                    val alpha = (1f - progress) * 0.55f * exitFade
                    val radius = (9f - p * 0.35f).coerceAtLeast(1.5f)
                    val grey = (0.55f - progress * 0.35f).coerceIn(0.2f, 0.75f)
                    val jitter = JITTER_OFFSETS[p]
                    if (trailX + radius < 0f || trailX - radius > screenWidthPx || alpha <= 0f) continue
                    drawCircle(
                        color = Color(grey, grey, grey, alpha),
                        radius = radius,
                        center = Offset(trailX, y + CAR_H / 2 + bounce + jitter),
                    )
                }

                if (x < screenWidthPx + CAR_W && x + CAR_W > -CAR_W) {
                    drawF1Car(x, y + bounce, color, speedRatio)
                }
            }
        }

        AnimatedVisibility(
            visible = showTitle,
            enter = fadeIn(animationSpec = tween(800)),
        ) {
            Text(
                text = "SPEED TRACKER",
                color = F1Colors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

 
private fun DrawScope.drawF1Car(x: Float, y: Float, color: Color, speedRatio: Float) {
    val bodyW = CAR_W
    val bodyH = CAR_H

  
    val ghostStretch = speedRatio * bodyW * 1.2f
    if (ghostStretch > 2f) {
        drawRect(
            color = color.copy(alpha = 0.18f * speedRatio),
            topLeft = Offset(x - ghostStretch, y + bodyH * 0.25f),
            size = Size(bodyW + ghostStretch, bodyH * 0.5f),
        )
    }

 
    drawRect(
        color = color,
        topLeft = Offset(x + bodyW * 0.02f, y - 7f),
        size = Size(bodyW * 0.10f, 7f),
    )

     
    drawRect(
        color = color,
        topLeft = Offset(x, y + bodyH * 0.15f),
        size = Size(bodyW, bodyH * 0.70f),
    )

  
    drawRect(
        color = color,
        topLeft = Offset(x + bodyW * 0.88f, y + bodyH * 0.3f),
        size = Size(bodyW * 0.12f, bodyH * 0.4f),
    )

    
    drawRect(
        color = Color.White.copy(alpha = 0.20f),
        topLeft = Offset(x + bodyW * 0.45f, y + bodyH * 0.18f),
        size = Size(bodyW * 0.22f, bodyH * 0.30f),
    )
 
    drawRect(
        color = color,
        topLeft = Offset(x + bodyW * 0.82f, y + bodyH * 0.75f),
        size = Size(bodyW * 0.18f, 5f),
    )

 
    val tireW = bodyW * 0.10f
    val tireH = bodyH * 0.55f
    val tireColor = Color.Black

  
    drawRect(
        color = tireColor,
        topLeft = Offset(x + bodyW * 0.06f, y - tireH * 0.15f),
        size = Size(tireW, tireH),
    )
    drawRect(
        color = tireColor,
        topLeft = Offset(x + bodyW * 0.06f, y + bodyH - tireH * 0.85f),
        size = Size(tireW, tireH),
    )

    
    drawRect(
        color = tireColor,
        topLeft = Offset(x + bodyW * 0.78f, y - tireH * 0.15f),
        size = Size(tireW, tireH),
    )
    drawRect(
        color = tireColor,
        topLeft = Offset(x + bodyW * 0.78f, y + bodyH - tireH * 0.85f),
        size = Size(tireW, tireH),
    )
}