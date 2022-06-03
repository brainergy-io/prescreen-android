package io.brainergy.prescreen.demo

import io.brainergy.prescreen.CardImage
import io.brainergy.prescreen.IDCardResult
import io.brainergy.prescreen.Prescreen
import io.brainergy.prescreen.face.FaceDetectionResult
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.camera.core.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity(), Prescreen.OnInitializedListener {

    lateinit var swapCameraButton: Button
    lateinit var resultText: TextView
    lateinit var confidenceText: TextView
    lateinit var faceText: TextView
    lateinit var previewView: PreviewView
    lateinit var faceDetectionSwitch: Switch
    lateinit var boundingBoxOverlay: BoundingBoxOverlay

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null

    private var previewWidth: Int = 1
    private var previewHeight: Int = 1
    private var imgProxyWidth: Int = 1
    private var imgProxyHeight: Int = 1
    private var imgProxyRotationDegree: Int = 0
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Prescreen.init(this,"ajMbRHTFPtUo9RzpSAMd", this)

        resultText = findViewById(R.id.resultTextView)
        confidenceText = findViewById(R.id.confidenceTextView)
        swapCameraButton = findViewById(R.id.swapCameraButton)
        faceText = findViewById(R.id.faceTextView)
        previewView = findViewById(R.id.previewView)
        faceDetectionSwitch = findViewById(R.id.faceDetectionSwitch)
        boundingBoxOverlay = findViewById(R.id.boundingBoxOverlay)

        swapCameraButton.setOnClickListener {
            swapCamera()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 1000)
            }
        }

    }

    override fun onCompleted() {
        Toast.makeText(this, "Init Completed", Toast.LENGTH_LONG).show()
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupCamera()
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        previewView.scaleType = PreviewView.ScaleType.FIT_START
        val rotation = previewView.display.rotation

        previewWidth = (previewView.width * previewView.scaleX).toInt()
        previewHeight = (previewView.height * previewView.scaleY).toInt()
        val screenAspectRatio = aspectRatio(previewWidth, previewHeight)

        preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetAspectRatio(screenAspectRatio)
            .build()


        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->

                    imageProxy.run {
                        val reverseDimens = imageInfo.rotationDegrees == 90 || imageInfo.rotationDegrees == 270
                        imgProxyWidth = if (reverseDimens) imageProxy.height else imageProxy.width
                        imgProxyHeight = if (reverseDimens) imageProxy.width else imageProxy.height
                        imgProxyRotationDegree = imageInfo.rotationDegrees
                        val card = CardImage(this.image!!, imageInfo.rotationDegrees)
                        Prescreen.scanIDCard(card) { result ->
//                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            if (faceDetectionSwitch.isChecked) {
                                Prescreen.checkFace(result) { faceResult ->
                                    this@MainActivity.displayResult(result, faceResult)
                                    imageProxy.close()
                                }
                            } else {
                                this@MainActivity.displayResult(result, null)
                                imageProxy.close()
                            }
                        }
                    }
                }
            }

        cameraProvider?.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
        }
    }

    private fun swapCamera() {
        cameraProvider?.unbindAll()
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCameraUseCases()
    }

    private fun displayResult(result: IDCardResult, faceResult: FaceDetectionResult?) {
        result.error?.run {
            resultText.text = errorMessage
        }
        boundingBoxOverlay.post{boundingBoxOverlay.clearBounds()}
        val bboxes: MutableList<Rect> = mutableListOf()
        val faceBBoxes: MutableList<Rect> = mutableListOf()
        var mlBBox: Rect? = null
        result.run {
            // fullImage is always available.
            val capturedImage = fullImage

            confidenceText.text = "%.3f ".format(confidence)
            if (this.detectResult != null) {
                confidenceText.text = "%.3f (%.3f) (%.3f)".format(
                    confidence,
                    this.detectResult!!.mlConfidence,
                    this.detectResult!!.boxConfidence)
                if (this.detectResult!!.cardBoundingBox != null) {
                    mlBBox = this.detectResult!!.cardBoundingBox!!.transform()
                }
            }
            if (isFrontSide != null && isFrontSide as Boolean) {
                // cropped image is only available for front side scan result.
                val cardImage = croppedImage
                confidenceText.text = "%s, Full: %s".format(confidenceText.text, isFrontCardFull)

                if (classificationResult != null && classificationResult!!.error == null) {
                    confidenceText.text = "%s (%.3f)".format(confidenceText.text, classificationResult!!.confidence)
                }
            }

            if (texts != null) {
                resultText.text = "TEXTS -> ${texts!!.joinToString("\n")}, isFrontside -> $isFrontSide"
            } else {
                resultText.text = "TEXTS -> NULL, isFrontside -> $isFrontSide"
            }
            if (idBBoxes != null) {
                bboxes.addAll(idBBoxes!!)
            }
            if (cardBox != null) {
                bboxes.add(cardBox!!)
            }
            if (faceBox != null) {
                bboxes.add(faceBox!!)
            }
            if (faceResult != null && faceResult.error == null) {
                if (faceResult.selfieFace != null) {
                    faceBBoxes.add(faceResult.selfieFace!!.bbox)
                    val rotX = faceResult.selfieFace!!.rot.rotX
                    val rotY = faceResult.selfieFace!!.rot.rotY
                    val rotZ = faceResult.selfieFace!!.rot.rotZ
                    faceText.text =
                        "Num: ${faceResult.faceScreeningResults!!.size}, Full: ${faceResult.selfieFace!!.isFullFace}, Front: ${faceResult.selfieFace!!.isFrontFacing} (%.1f, %.1f, %.1f)".format(
                            rotX, rotY, rotZ
                        )
                } else {
                    faceText.text = ""
                }
                if (faceResult.cardFace != null) {
                    faceBBoxes.add(faceResult.cardFace!!.bbox)
                }
            }
            boundingBoxOverlay.post{boundingBoxOverlay.drawBounds(
                bboxes.map{it.transform()},
                faceBBoxes.map{it.transform()},
                mlBBox,
                imgProxyRotationDegree)}
        }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }


    private fun Rect.transform(): Rect {

        var scale = previewWidth / imgProxyWidth.toFloat()
        if (imgProxyRotationDegree == 0 || imgProxyRotationDegree == 180) {

            scale = previewHeight / imgProxyHeight.toFloat()
        }


        var flippedLeft = left
        var flippedRight = right
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            flippedLeft = imgProxyWidth - right
            flippedRight = imgProxyWidth - left
        }

        // Scale all coordinates to match preview
        val scaledLeft = scale * flippedLeft
        val scaledRight = scale * flippedRight
        val scaledTop = scale * top
        val scaledBottom = scale * bottom
        return Rect(scaledLeft.toInt(), scaledTop.toInt(), scaledRight.toInt(), scaledBottom.toInt())
    }
}
