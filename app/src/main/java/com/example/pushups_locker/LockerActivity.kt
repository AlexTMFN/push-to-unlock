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
    
    private enum class PushupState {
        NEEDS_ALIGNMENT,
        READY_UP,
        DOWN_POSITION
    }
    
    private var currentState = PushupState.NEEDS_ALIGNMENT
    private val minConfidence = 0.5f 

    private var lastStatus = ""
    private var lastStatusTime = 0L

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
                    analyzePose(pose)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun analyzePose(pose: Pose) {
        val landmarks = pose.getAllPoseLandmarks()
        if (landmarks.isEmpty()) {
            stableUpdateStatus("Step into camera view")
            currentState = PushupState.NEEDS_ALIGNMENT
            return
        }

        val lS = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val lE = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lW = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rS = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val rE = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rW = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val lH = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)

        if (!isConfident(lS, rS, lE, rE)) {
            stableUpdateStatus("Make sure your upper body is visible")
            currentState = PushupState.NEEDS_ALIGNMENT
            return
        }

        val leftShoulder = lS!!
        val rightShoulder = rS!!
        
        if (lH != null && lH.inFrameLikelihood > minConfidence) {
            val torsoDx = abs(leftShoulder.position.x - lH.position.x)
            val torsoDy = abs(leftShoulder.position.y - lH.position.y)
            val torsoAngle = abs(atan2(torsoDy.toDouble(), torsoDx.toDouble()) * 180 / Math.PI)
            
            if (torsoAngle > 65.0) { 
                stableUpdateStatus("Get closer to a horizontal position")
                currentState = PushupState.NEEDS_ALIGNMENT
                return
            }
        }

        val leftAngle = if (isConfident(lS, lE, lW)) calculateAngle(leftShoulder, lE!!, lW!!) else null
        val rightAngle = if (isConfident(rS, rE, rW)) calculateAngle(rightShoulder, rE!!, rW!!) else null
        
        val currentAngle = when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> {
                stableUpdateStatus("Show your arms to the camera")
                return
            }
        }

        when (currentState) {
            PushupState.NEEDS_ALIGNMENT -> {
                if (currentAngle > 145) { 
                    currentState = PushupState.READY_UP
                    stableUpdateStatus("Ready! Go down")
                } else {
                    stableUpdateStatus("Straighten your arms a bit")
                }
            }
            PushupState.READY_UP -> {
                if (currentAngle < 115) { 
                    currentState = PushupState.DOWN_POSITION
                    stableUpdateStatus("Good! Now push up")
                } else {
                    stableUpdateStatus("Lower your chest...")
                }
            }
            PushupState.DOWN_POSITION -> {
                if (currentAngle > 145) { 
                    runOnUiThread { countPushup() }
                    currentState = PushupState.READY_UP
                    stableUpdateStatus("Rep counted!")
                }
            }
        }
    }

    private fun dist(a: PoseLandmark, b: PoseLandmark): Float {
        val dx = a.position.x - b.position.x
        val dy = a.position.y - b.position.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun isConfident(vararg landmarks: PoseLandmark?): Boolean {
        for (landmark in landmarks) {
            if (landmark == null || landmark.inFrameLikelihood < minConfidence) return false
        }
        return true
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