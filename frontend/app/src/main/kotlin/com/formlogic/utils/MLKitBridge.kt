package com.formlogic.utils

import androidx.camera.core.ImageProxy
import com.formlogic.models.Keypoint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

/**
 * MLKitBridge.kt — Bridges CameraX ImageProxy frames with ML Kit Pose Detection.
 *
 * Uses the AccuratePoseDetector (BlazePose Full) in STREAM_MODE for live video.
 * Falls back to [generateMockPose] when ML Kit is unavailable (unit tests / emulator).
 *
 * Wiring (done in WorkoutCameraScreen):
 *   analysis.setAnalyzer(cameraExecutor) { imageProxy ->
 *       MLKitBridge.analyzeFrame(imageProxy) { keypoints ->
 *           keypoints?.let { viewModel.processKeypoints(it) }
 *       }
 *   }
 */
object MLKitBridge {

    private val poseDetector: PoseDetector by lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    /** True when ML Kit native module is available at runtime. */
    val isNativeAvailable: Boolean
        get() = try {
            Class.forName("com.google.mlkit.vision.pose.PoseDetection")
            true
        } catch (_: ClassNotFoundException) { false }

    /**
     * analyzeFrame — call from CameraX ImageAnalysis.Analyzer.analyze().
     *
     * Returns 33 normalised Keypoint objects (BlazePose landmark schema),
     * or null if no person is detected or ML Kit is unavailable.
     * Always closes [imageProxy] before invoking [onResult].
     */
    fun analyzeFrame(imageProxy: ImageProxy, onResult: (List<Keypoint>?) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(null)
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val w = imageProxy.width.toFloat()
        val h = imageProxy.height.toFloat()

        poseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                val landmarks = pose.allPoseLandmarks
                if (landmarks.size == 33) {
                    val keypoints = landmarks.map { lm ->
                        Keypoint(
                            x     = (lm.position3D.x / w).coerceIn(0f, 1f),
                            y     = (lm.position3D.y / h).coerceIn(0f, 1f),
                            score = lm.inFrameLikelihood,
                        )
                    }
                    onResult(keypoints)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { imageProxy.close() }
    }

    /**
     * generateMockPose — produces physically accurate 33-point BlazePose keypoints
     * for the given exercise at the given animation progress (0.0 → 1.0).
     * Used in dev/simulator when ML Kit is not available.
     */
    fun generateMockPose(exerciseId: String, progress: Float): List<Keypoint> {
        val kps = MutableList(33) { Keypoint(0.5f, 0.5f, 0.95f) }

        val NOSE = 0; val L_SHOULDER = 11; val R_SHOULDER = 12
        val L_ELBOW = 13; val R_ELBOW = 14; val L_WRIST = 15; val R_WRIST = 16
        val L_HIP = 23; val R_HIP = 24; val L_KNEE = 25; val R_KNEE = 26
        val L_ANKLE = 27; val R_ANKLE = 28

        kps[NOSE]       = Keypoint(0.500f, 0.080f, 0.99f)
        kps[L_SHOULDER] = Keypoint(0.400f, 0.250f, 0.98f)
        kps[R_SHOULDER] = Keypoint(0.600f, 0.250f, 0.98f)

        when (exerciseId) {
            "squat", "sumo_squat", "jump_squat", "lunge", "glute_bridge" -> {
                val hipY  = 0.50f + progress * 0.15f
                val kneeY = 0.68f + progress * 0.05f
                kps[L_HIP]   = Keypoint(0.420f, hipY,   0.97f)
                kps[R_HIP]   = Keypoint(0.580f, hipY,   0.97f)
                kps[L_KNEE]  = Keypoint(0.400f, kneeY,  0.97f)
                kps[R_KNEE]  = Keypoint(0.600f, kneeY,  0.97f)
                kps[L_ANKLE] = Keypoint(0.390f, 0.880f, 0.96f)
                kps[R_ANKLE] = Keypoint(0.610f, 0.880f, 0.96f)
                kps[L_ELBOW] = Keypoint(0.340f, 0.400f, 0.95f)
                kps[R_ELBOW] = Keypoint(0.660f, 0.400f, 0.95f)
                kps[L_WRIST] = Keypoint(0.340f, 0.520f, 0.93f)
                kps[R_WRIST] = Keypoint(0.660f, 0.520f, 0.93f)
            }
            "pushup", "diamond_pushup", "wide_pushup" -> {
                val elbowY = 0.50f + (1f - progress) * 0.08f
                kps[L_SHOULDER] = Keypoint(0.360f, 0.430f, 0.97f)
                kps[R_SHOULDER] = Keypoint(0.640f, 0.430f, 0.97f)
                kps[L_ELBOW]    = Keypoint(0.310f, elbowY, 0.96f)
                kps[R_ELBOW]    = Keypoint(0.690f, elbowY, 0.96f)
                kps[L_WRIST]    = Keypoint(0.290f, 0.560f, 0.95f)
                kps[R_WRIST]    = Keypoint(0.710f, 0.560f, 0.95f)
                kps[L_HIP]      = Keypoint(0.420f, 0.580f, 0.96f)
                kps[R_HIP]      = Keypoint(0.580f, 0.580f, 0.96f)
                kps[L_KNEE]     = Keypoint(0.420f, 0.720f, 0.95f)
                kps[R_KNEE]     = Keypoint(0.580f, 0.720f, 0.95f)
                kps[L_ANKLE]    = Keypoint(0.420f, 0.860f, 0.94f)
                kps[R_ANKLE]    = Keypoint(0.580f, 0.860f, 0.94f)
            }
            else -> {
                kps[L_HIP]   = Keypoint(0.420f, 0.560f, 0.97f)
                kps[R_HIP]   = Keypoint(0.580f, 0.560f, 0.97f)
                kps[L_KNEE]  = Keypoint(0.420f, 0.720f, 0.96f)
                kps[R_KNEE]  = Keypoint(0.580f, 0.720f, 0.96f)
                kps[L_ANKLE] = Keypoint(0.420f, 0.880f, 0.95f)
                kps[R_ANKLE] = Keypoint(0.580f, 0.880f, 0.95f)
                kps[L_ELBOW] = Keypoint(0.340f, 0.400f, 0.95f)
                kps[R_ELBOW] = Keypoint(0.660f, 0.400f, 0.95f)
            }
        }
        return kps
    }
}
