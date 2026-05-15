package com.example.pushups_locker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class LockerActivity : AppCompatActivity() {

    private var pushupCount = 0
    private var targetCount = 10
    private lateinit var counterText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private var lockedPackage: String? = null
    
    private lateinit var cameraExecutor: ExecutorService
    
    // Advanced State machine based on industry standards (durareApp & valericaplesu)
    private enum class PushupState {
        IDLE,           // Looking for person
        ALIGNING,       // Person found, checking posture
        READY_UP,       // Arms straight, waiting for descent
        GOING_DOWN,     // Mid-way down
        AT_BOTTOM,      // Reached bottom threshold
        GOING_UP        // Mid-way up
    }
    
    private var currentState = PushupState.IDLE
    private val minConfidence = 0.55f 

    // Stability & Smoothing
    private var lastStatus = ""
    private var lastStatusTime = 0L
    private val angleHistory = mutableListOf<Double>()
    private val HISTORY_SIZE = 3 // For moving average smoothing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locker)

        counterText = findViewById(R.id.pushupCounterText)
        statusText = findViewById(R.id.pushupStatusText)
        progressBar = findViewById(R.id.pushupProgress)
        
        targetCount = intent.getIntExtra("target_pushups", 10)
        lockedPackage = intent.getStringExtra("package_name")
        
        updateUI()

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = findViewById<PreviewView>(R.id.viewFinder).surfaceProvider
            }

            val poseDetectorOptions = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                .setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU)
                .build()
            val poseDetector = PoseDetection.getClient(poseDetectorOptions)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(poseDetector, imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("LockerActivity", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(detector: PoseDetector, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { pose ->
                    analyzePoseAdvanced(pose)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun analyzePoseAdvanced(pose: Pose) {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            stableUpdateStatus("Find a clear space")
            currentState = PushupState.IDLE
            return
        }

        // Key Landmarks
        val lS = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lE = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lW = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rS = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rE = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rW = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val lH = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)

        // 1. Confidence Weighting (Technique from durareApp)
        // Ensure at least one side is visible with high likelihood
        val leftConfident = isSideConfident(lS, lE, lW)
        val rightConfident = isSideConfident(rS, rE, rW)

        if (!leftConfident && !rightConfident) {
            stableUpdateStatus("Show your arms & torso")
            currentState = PushupState.ALIGNING
            return
        }

        // 2. Dynamic Posture Check (Very permissive verticality for beginners)
        if (lS != null && lH != null && lS.inFrameLikelihood > 0.5 && lH.inFrameLikelihood > 0.5) {
            val dy = abs(lS.position.y - lH.position.y)
            val dx = abs(lS.position.x - lH.position.x)
            val angle = abs(atan2(dy.toDouble(), dx.toDouble()) * 180 / Math.PI)
            
            if (angle > 70.0) { 
                stableUpdateStatus("Lean more forward")
                return
            }
        }

        // 3. Angle Calculation with Smoothing
        val leftAngle = if (leftConfident) calculateAngle(lS!!, lE!!, lW!!) else null
        val rightAngle = if (rightConfident) calculateAngle(rS!!, rE!!, rW!!) else null
        
        val rawAngle = when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2
            leftAngle != null -> leftAngle
            else -> rightAngle!!
        }

        // Moving Average Smoothing (Standard CV technique)
        angleHistory.add(rawAngle)
        if (angleHistory.size > HISTORY_SIZE) angleHistory.removeAt(0)
        val smoothAngle = angleHistory.average()

        // 4. Advanced State Machine Transitions
        processStateMachine(smoothAngle)
    }

    private fun processStateMachine(angle: Double) {
        when (currentState) {
            PushupState.IDLE, PushupState.ALIGNING -> {
                if (angle > 145) {
                    currentState = PushupState.READY_UP
                    stableUpdateStatus("READY! Lower your body")
                } else {
                    stableUpdateStatus("Straighten your arms")
                }
            }
            PushupState.READY_UP -> {
                if (angle < 135) { // Started descent
                    currentState = PushupState.GOING_DOWN
                }
            }
            PushupState.GOING_DOWN -> {
                if (angle < 115) { // Reached bottom threshold
                    currentState = PushupState.AT_BOTTOM
                    stableUpdateStatus("Great! Now push back UP")
                } else if (angle > 150) { // Aborted
                    currentState = PushupState.READY_UP
                }
            }
            PushupState.AT_BOTTOM -> {
                if (angle > 125) { // Started ascent
                    currentState = PushupState.GOING_UP
                }
            }
            PushupState.GOING_UP -> {
                if (angle > 145) { // Completed rep
                    runOnUiThread { countPushup() }
                    currentState = PushupState.READY_UP
                    stableUpdateStatus("Perfect rep!")
                } else if (angle < 110) { // Went back down
                    currentState = PushupState.AT_BOTTOM
                }
            }
        }
    }

    private fun isSideConfident(s: PoseLandmark?, e: PoseLandmark?, w: PoseLandmark?): Boolean {
        return s != null && e != null && w != null && 
               s.inFrameLikelihood > minConfidence && 
               e.inFrameLikelihood > minConfidence && 
               w.inFrameLikelihood > minConfidence
    }

    private fun dist(a: PoseLandmark, b: PoseLandmark): Float {
        val dx = a.position.x - b.position.x
        val dy = a.position.y - b.position.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun stableUpdateStatus(text: String) {
        if (text == lastStatus) return
        val now = System.currentTimeMillis()
        if (now - lastStatusTime > 400) {
            runOnUiThread { statusText.text = text }
            lastStatus = text
            lastStatusTime = now
        }
    }

    private fun calculateAngle(first: PoseLandmark, second: PoseLandmark, third: PoseLandmark): Double {
        var angle = Math.toDegrees(
            (atan2(third.position.y.toDouble() - second.position.y.toDouble(), third.position.x.toDouble() - second.position.x.toDouble()) -
            atan2(first.position.y.toDouble() - second.position.y.toDouble(), first.position.x.toDouble() - second.position.x.toDouble()))
        )
        angle = abs(angle)
        if (angle > 180) angle = 360 - angle
        return angle
    }

    private fun countPushup() {
        pushupCount++
        vibrate()
        updateUI()
        if (pushupCount >= targetCount) {
            val intent = packageManager.getLaunchIntentForPackage(lockedPackage ?: packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)

            val unlockIntent = Intent("com.example.pushups_locker.UNLOCKED")
            unlockIntent.putExtra("package_name", lockedPackage)
            unlockIntent.setPackage(packageName)
            sendBroadcast(unlockIntent)
            finish()
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vibrator.vibrate(150)
        }
    }

    private fun updateUI() {
        counterText.text = getString(R.string.pushup_counter_format, pushupCount, targetCount)
        val progress = (pushupCount.toFloat() / targetCount.toFloat() * 100).toInt()
        progressBar.setProgress(progress, true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onBackPressed() {}
}