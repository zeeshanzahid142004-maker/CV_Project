package com.example.cvproject.ui.screens

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.concurrent.futures.await
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.cvproject.domain.camera.TrackedVehicle
import com.example.cvproject.ui.components.DetectionHud
import com.example.cvproject.ui.components.SpeedOverlayView
import com.example.cvproject.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private const val MAX_SHEET_ITEMS = 25

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DetectionScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val scaffoldState = rememberBottomSheetScaffoldState()

    val modelReady by viewModel.modelReady.collectAsState()
    val trackedVehicles by viewModel.trackedVehicles.collectAsState()
    val vehicleSpeeds by viewModel.vehicleSpeeds.collectAsState()
    val detectedBoxes by viewModel.detectedBoxes.collectAsState()
    val sessionMaxSpeed by viewModel.sessionMaxSpeed.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val frameWidth by viewModel.frameWidth.collectAsState()
    val frameHeight by viewModel.frameHeight.collectAsState()
  
    val entryTripwireY = uiState.entryTripwireFraction
    val exitTripwireY = uiState.exitTripwireFraction
    val configuration = LocalConfiguration.current

    var hasCameraError by remember { mutableStateOf(false) }
    var sessionSeconds by remember { mutableIntStateOf(0) }
    var previousVehicleCount by remember { mutableIntStateOf(0) }
    var hasInitializedVehicleCount by remember { mutableStateOf(false) }
    var activeVehiclesSnapshot by remember { mutableStateOf<List<TrackedVehicle>>(emptyList()) }
    var serverFps by remember { mutableFloatStateOf(0f) }
    var lastFrameTimeMs by remember { mutableStateOf(0L) }

    LaunchedEffect(trackedVehicles) {
        activeVehiclesSnapshot = trackedVehicles.values.sortedBy { it.id }
    }

     
    LaunchedEffect(detectedBoxes) {
        val now = System.currentTimeMillis()
        if (lastFrameTimeMs > 0L) {
            val deltaMs = now - lastFrameTimeMs
            if (deltaMs > 0) {
                serverFps = (1000f / deltaMs).coerceAtMost(30f)
            }
        }
        lastFrameTimeMs = now
    }

     
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            sessionSeconds++
        }
    }
    val sessionTime = "%02d:%02d".format(sessionSeconds / 60, sessionSeconds % 60)

    if (!cameraPermissionState.status.isGranted) {
        LaunchedEffect(Unit) { cameraPermissionState.launchPermissionRequest() }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Camera permission required", color = Color.White)
        }
        return
    }

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            runCatching {
                val cameraProvider = cameraProviderFuture.await()
                val preview = Preview.Builder()
                    .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val bitmap = imageProxy.toBitmap()
                                val rotation = imageProxy.imageInfo.rotationDegrees
                                if (bitmap != null) {
                                    viewModel.sendFrameToServer(bitmap, rotation)
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
                hasCameraError = false
            }.onFailure { error ->
                hasCameraError = true
                Log.e("DetectionScreen", "Camera binding failed", error)
            }
        }
    }

    LaunchedEffect(activeVehiclesSnapshot.size) {
        if (hasInitializedVehicleCount && activeVehiclesSnapshot.size > previousVehicleCount) {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40L)
            }
        }
        previousVehicleCount = activeVehiclesSnapshot.size
        hasInitializedVehicleCount = true
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearSession() }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
            analysisExecutor.shutdown()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 120.dp,
        sheetMaxWidth = configuration.screenWidthDp.dp,
        sheetContainerColor = Color(0xEE0A0A0F),
        sheetContent = {
              
            val displayVehicles = activeVehiclesSnapshot
                .filter { vehicle -> vehicleSpeeds[vehicle.id]?.let { it > 0f } == true }
                .takeLast(MAX_SHEET_ITEMS)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 460.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .background(Color(0xFF333333), RoundedCornerShape(50))
                        .align(Alignment.CenterHorizontally),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DETECTED VEHICLES",
                    color = Color(0xFFE10600),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (displayVehicles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = configuration.screenHeightDp.dp * 0.45f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Waiting for vehicles to cross tripwire...",
                                color = Color(0xFF555555),
                                fontSize = 12.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                
                                .heightIn(max = configuration.screenHeightDp.dp * 0.45f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                items = displayVehicles,
                                key = { vehicle -> vehicle.id },
                            ) { vehicle ->
                                val id = vehicle.id
                                val borderColor = when (id % 4) {
                                    0 -> Color(0xFFE10600)
                                    1 -> Color(0xFF378ADD)
                                    2 -> Color(0xFF639922)
                                    else -> Color(0xFFEF9F27)
                                }
                                val speedKmh = vehicleSpeeds[id] ?: return@items
                                val isMph = uiState.speedUnit == "mph"
                                val displaySpeed = if (isMph) speedKmh * 0.621371f else speedKmh
                                val speedText = "${displaySpeed.toInt()} ${uiState.speedUnit}"
                                val isOverLimit = displaySpeed > uiState.speedLimitThreshold
                                
                                Surface(
                                    color = Color(0xFF161616),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.5.dp, if (isOverLimit) Color.Red else Color(0xFF2A2A2A)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .background(borderColor),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "CAR #$id",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = speedText,
                                                    color = if (isOverLimit) Color.Red else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 20.sp,
                                                )
                                                if (isOverLimit) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("⚠️", fontSize = 16.sp)
                                                }
                                            }
                                        }
                                        Text(
                                            text = "→",
                                            color = Color(0xFF666666),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(end = 10.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Max: ${sessionMaxSpeed.toInt()} km/h",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "Speeds: ${vehicleSpeeds.size}",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "Tracked: ${activeVehiclesSnapshot.size}",
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "%.1f fps".format(serverFps),
                        color = if (serverFps < 0.5f) Color(0xFFE10600) else Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F))) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )

            SpeedOverlayView(
                trackedVehicles = trackedVehicles,
                vehicleSpeeds = vehicleSpeeds,
                detectedBoxes = detectedBoxes,
                imageWidth = frameWidth,
                imageHeight = frameHeight,
                entryTripwireY = entryTripwireY,
                exitTripwireY = exitTripwireY,
                speedUnit = uiState.speedUnit,
                modifier = Modifier.fillMaxSize(),
            )

            DetectionHud(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
                isLive = !hasCameraError,
                sessionTime = sessionTime,
                onStop = { navController.popBackStack() },
                modelReady = modelReady,
            )

            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Tip: Align the red tripwire with a fixed horizontal reference on the road (like a shadow or road marking) for accurate speed calculations.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
