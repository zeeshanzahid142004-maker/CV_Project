package com.example.cvproject.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cvproject.data.preferences.PreferencesManager
import com.example.cvproject.domain.camera.TrackedVehicle
import com.example.cvproject.domain.camera.VehicleTracker
import com.example.cvproject.domain.camera.RemoteYoloService
import com.example.cvproject.domain.camera.ShakeDetector
import com.example.cvproject.domain.camera.YoloAnalyzer
import androidx.lifecycle.ViewModelProvider
import com.example.cvproject.data.local.entity.TripSession
import com.example.cvproject.data.repository.TripRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainViewModel(private val tripRepository: TripRepository) : ViewModel() {
    private var yoloAnalyzer: YoloAnalyzer? = null
    private val analyzerLock = Any()
    private var vehicleTracker = VehicleTracker()
    private var sceneWidthSyncJob: Job? = null
    private var tripwireSyncJob: Job? = null
    private var prefsManager: PreferencesManager? = null
    private var shakeDetector: ShakeDetector? = null
 
    private val remoteYoloService = RemoteYoloService(
        serverUrl = "https://untaken-smelting-danger.ngrok-free.dev"
    )
    private val isProcessing = AtomicBoolean(false)
    private var lastFrameResultMs = 0L
    private var stalenessSweepJob: Job? = null
    private val STALE_OVERLAY_TIMEOUT_MS = 2000L
    private val STALENESS_SWEEP_INTERVAL_MS = 1000L

    private val _frameWidth = MutableStateFlow(1080)
    val frameWidth: StateFlow<Int> = _frameWidth.asStateFlow()

    private val _frameHeight = MutableStateFlow(1920)
    val frameHeight: StateFlow<Int> = _frameHeight.asStateFlow()

    val entryTripwireY: Float
        get() = _uiState.value.entryTripwireFraction

    val exitTripwireY: Float
        get() = _uiState.value.exitTripwireFraction

    init {
        Log.d("AppDebug", "ViewModel Initialized")
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val _detectedBoxes = MutableStateFlow<Map<Int, RectF>>(emptyMap())
    val detectedBoxes: StateFlow<Map<Int, RectF>> = _detectedBoxes.asStateFlow()
    private val _modelReady = MutableStateFlow(true) // Always ready — server handles inference
    val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    private val _trackedVehicles = MutableStateFlow<Map<Int, TrackedVehicle>>(emptyMap())
    val trackedVehicles: StateFlow<Map<Int, TrackedVehicle>> = _trackedVehicles.asStateFlow()

    private val _vehicleSpeeds = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val vehicleSpeeds: StateFlow<Map<Int, Float>> = _vehicleSpeeds.asStateFlow()

    private val _sessionMaxSpeed = MutableStateFlow(0f)
    val sessionMaxSpeed: StateFlow<Float> = _sessionMaxSpeed.asStateFlow()

    // Keep the old local analyzer available as fallback
    fun getOrCreateYoloAnalyzer(context: Context): YoloAnalyzer {
        synchronized(analyzerLock) {
            yoloAnalyzer?.let { return it }
            val analyzer = YoloAnalyzer(context.applicationContext) { boxes, width, height ->
                onFrameResult(boxes, emptyList(), width, height)
            }
            yoloAnalyzer = analyzer
            return analyzer
        }
    }

    
    fun sendFrameToServer(bitmap: Bitmap, rotationDegrees: Int = 0) {
        if (!isProcessing.compareAndSet(false, true)) {
            return // Skip — previous frame still processing
        }

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    remoteYoloService.detect(bitmap, rotationDegrees)
                }
                onFrameResult(result.boxes, result.trackIds, result.imageWidth, result.imageHeight)
            } catch (e: Exception) {
                Log.e("VM", "Remote detection failed: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    fun onFrameResult(boxes: List<RectF>, trackIds: List<Int> = emptyList(), width: Int, height: Int) {
        synchronized(analyzerLock) {
            _frameWidth.value = width
            _frameHeight.value = height
            val accel = shakeDetector?.currentAcceleration ?: -1f
            val moveThreshold = shakeDetector?.dynamicMovementThreshold ?: 0.005f
            Log.d("VM-SHAKE", "sensor accel=${String.format("%.3f", accel)} m/s² | threshold=${String.format("%.5f", moveThreshold)} | detector=${if (shakeDetector != null) "ACTIVE" else "NULL"}")
            val result = vehicleTracker.processFrame(boxes, trackIds, entryTripwireY, exitTripwireY, moveThreshold)
            _trackedVehicles.value = vehicleTracker.activeVehicles.mapValues { (_, vehicle) ->
                TrackedVehicle(
                    id = vehicle.id,
                    center = PointF(vehicle.center.x, vehicle.center.y),
                    firstSeenTimeMs = vehicle.firstSeenTimeMs,
                    entryTimeMs = vehicle.entryTimeMs,
                    entryLine = vehicle.entryLine,
                    hasCrossedExit = vehicle.hasCrossedExit,
                    initialY = vehicle.initialY,
                    displayId = vehicle.displayId,
                    lastSeenMs = vehicle.lastSeenMs,
                )
            }
            _detectedBoxes.value = vehicleTracker.vehicleBoxes
            _vehicleSpeeds.value = vehicleTracker.vehicleSpeeds.toMap()
            lastFrameResultMs = System.currentTimeMillis()
            Log.d(
                "VM",
                "onFrameResult: trackerResult=$result trackedVehicles=${_trackedVehicles.value.size} speeds=${_vehicleSpeeds.value.size}",
            )
            result?.let { speed ->
                if (speed > _sessionMaxSpeed.value) {
                    _sessionMaxSpeed.value = speed
                    Log.d("VM", "onFrameResult: NEW SESSION MAX=$result")
                }
                // Persist speed to DataStore — updates the HomeScreen card in real-time
                prefsManager?.let { pm ->
                    viewModelScope.launch { pm.recordSpeed(speed) }
                }
            }
        }
    }

    fun startTracking(context: Context? = null) {
        _uiState.update { it.copy(isTracking = true) }
        lastFrameResultMs = System.currentTimeMillis()
        
        if (context != null && shakeDetector == null) {
            shakeDetector = ShakeDetector(context.applicationContext)
        }
        shakeDetector?.start()
    
        stalenessSweepJob?.cancel()
        stalenessSweepJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(STALENESS_SWEEP_INTERVAL_MS)
                val elapsed = System.currentTimeMillis() - lastFrameResultMs
                if (elapsed > STALE_OVERLAY_TIMEOUT_MS && _trackedVehicles.value.isNotEmpty()) {
                    Log.d("VM", "Staleness sweep: clearing stale overlay (${elapsed}ms since last frame)")
                    synchronized(analyzerLock) {
                        vehicleTracker.clearSession()
                        _trackedVehicles.value = emptyMap()
                        _detectedBoxes.value = emptyMap()
                        _vehicleSpeeds.value = emptyMap()
                    }
                }
            }
        }
    }

    fun stopTracking() {
        stalenessSweepJob?.cancel()
        shakeDetector?.stop()
        _uiState.update { it.copy(isTracking = false) }
    }

    fun updateSpeed(speed: Float) {
        _uiState.update { current ->
            current.copy(
                currentSpeed = speed,
                maxSpeed = maxOf(current.maxSpeed, speed),
            )
        }
    }

    fun setSpeedUnit(unit: String) {
        _uiState.update { it.copy(speedUnit = unit) }
    }

    fun setSpeedLimitThreshold(threshold: Float) {
        _uiState.update { it.copy(speedLimitThreshold = threshold) }
    }

    fun updateTrackedCars(cars: List<TrackedCar>) {
        _uiState.update { it.copy(trackedCars = cars) }
    }

    fun bindPreferences(preferencesManager: PreferencesManager) {
        prefsManager = preferencesManager
        sceneWidthSyncJob?.cancel()
        sceneWidthSyncJob = viewModelScope.launch {
            preferencesManager.sceneWidthMeters.collectLatest { sceneWidthMeters ->
                vehicleTracker.assumedDistanceMeters = sceneWidthMeters
            }
        }
        tripwireSyncJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesManager.entryTripwireFraction,
                preferencesManager.exitTripwireFraction
            ) { entry, exit ->
                Pair(entry, exit)
            }.collectLatest { (entry, exit) ->
                _uiState.update { it.copy(entryTripwireFraction = entry, exitTripwireFraction = exit) }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesManager.isMph,
                preferencesManager.speedWarningThreshold
            ) { isMph, threshold ->
                Pair(isMph, threshold)
            }.collectLatest { (isMph, threshold) ->
                _uiState.update { 
                    it.copy(
                        speedUnit = if (isMph) "mph" else "km/h",
                        speedLimitThreshold = threshold.toFloat()
                    ) 
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.maxConcurrentVehicles.collectLatest { max ->
                _uiState.update { it.copy(maxConcurrentVehicles = max) }
                vehicleTracker.maxTrackedVehicles = max
            }
        }
        
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesManager.allTimeMaxSpeed,
                preferencesManager.totalSpeedSum,
                preferencesManager.totalSpeedCount
            ) { maxSpd, totalSum, totalCount ->
                Triple(maxSpd, totalSum, totalCount)
            }.collectLatest { (maxSpd, totalSum, totalCount) ->
                val avg = if (totalCount > 0) totalSum / totalCount else 0f
                _uiState.update {
                    it.copy(
                        allTimeMaxSpeed = maxSpd,
                        allTimeAvgSpeed = avg,
                        totalCarsTracked = totalCount
                    )
                }
            }
        }
    }

    fun clearSession() {
        synchronized(analyzerLock) {
            Log.d("VM", "clearSession: resetting all state")
            
            val maxSpd = _sessionMaxSpeed.value
            val speeds = _vehicleSpeeds.value.values
            val avgSpd = if (speeds.isNotEmpty()) speeds.average().toFloat() else 0f
            
            if (maxSpd > 0f) {
                viewModelScope.launch {
                    tripRepository.insertSession(
                        TripSession(
                            maxSpeed = maxSpd,
                            avgSpeed = avgSpd,
                            distance = 0f
                        )
                    )
                }
            }

            vehicleTracker.clearSession()
            _trackedVehicles.value = emptyMap()
            _detectedBoxes.value = emptyMap()
            _vehicleSpeeds.value = emptyMap()
            _sessionMaxSpeed.value = 0f
        }
    }

    override fun onCleared() {
        yoloAnalyzer?.close()
        yoloAnalyzer = null
        super.onCleared()
    }

    companion object {
        fun factory(repository: TripRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repository) as T
                }
            }
        }
    }
}
