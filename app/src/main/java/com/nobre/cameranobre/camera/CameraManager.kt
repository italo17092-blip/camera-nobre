package com.nobre.cameranobre.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nobre.cameranobre.data.LocationHelper
import com.nobre.cameranobre.data.PropertyStorageHelper
import com.nobre.cameranobre.processing.RealEstateImageProcessor
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val storageHelper: PropertyStorageHelper,
    private val locationHelper: LocationHelper
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Resoluções: 0 = 12 MP (Padrão), 1 = 50 MP, 2 = 200 MP (Sensor ISOCELL HP2 S25 Ultra)
    var resolutionMode: Int = 0
        private set

    var isAspectRatio43: Boolean = true
        private set
    var currentZoomRatio: Float = 1.0f
        private set
    var flashMode: Int = ImageCapture.FLASH_MODE_AUTO
        private set

    private var previewViewRef: PreviewView? = null

    fun initialize(previewView: PreviewView, onInitialized: () -> Unit) {
        previewViewRef = previewView
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
            onInitialized()
        }, ContextCompat.getMainExecutor(context))
    }

    fun setResolutionMode(mode: Int) {
        if (resolutionMode != mode) {
            resolutionMode = mode
            bindCameraUseCases()
        }
    }

    fun setAspectRatio(ratio43: Boolean) {
        if (isAspectRatio43 != ratio43) {
            isAspectRatio43 = ratio43
            bindCameraUseCases()
        }
    }

    fun setFlashMode(mode: Int) {
        flashMode = mode
        imageCapture?.flashMode = mode
    }

    /**
     * Aplica o zoom suave de 0.6x a 10.0x de forma contínua no sensor lógico
     * SEM recriar a câmera (eliminando a tela preta e o erro de gravação de vídeo).
     */
    fun setZoom(zoom: Float) {
        currentZoomRatio = zoom
        camera?.cameraInfo?.zoomState?.value?.let { state ->
            val targetZoom = zoom.coerceIn(state.minZoomRatio, state.maxZoomRatio)
            camera?.cameraControl?.setZoomRatio(targetZoom)
        }
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val previewView = previewViewRef ?: return
        provider.unbindAll()

        // Seleção de resolução com base no sensor do S25 Ultra:
        // 0: 12 MP (4000x3000) - Rápido, ideal para interiores e luminosidade
        // 1: 50 MP (8160x6120) - Alto detalhe arquitetônico
        // 2: 200 MP (16320x12240) - Máxima resolução do sensor principal
        val targetSize = when (resolutionMode) {
            2 -> if (isAspectRatio43) Size(16320, 12240) else Size(16320, 9180)
            1 -> if (isAspectRatio43) Size(8160, 6120) else Size(8160, 4590)
            else -> if (isAspectRatio43) Size(4000, 3000) else Size(3840, 2160)
        }

        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .setAspectRatioStrategy(
                if (isAspectRatio43) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            )
            .build()

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setFlashMode(flashMode)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.UHD, Quality.FHD, Quality.HD)))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        // Câmera Traseira Lógica (que gerencia ultra-wide, 1x e teleobjetivas nativamente)
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )

            // Restaura o zoom atual sem piscar
            camera?.cameraInfo?.zoomState?.value?.let { state ->
                val targetZoom = currentZoomRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                camera?.cameraControl?.setZoomRatio(targetZoom)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun takePhoto(
        onSuccess: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val capture = imageCapture ?: return
        locationHelper.updateLocation()

        val tempRawFile = storageHelper.createTempFile("raw", ".jpg")
        val tempProcessedFile = storageHelper.createTempFile("proc", ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempRawFile).build()

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        // 1. Processamento e correção da orientação (SEMPRE em pé)
                        RealEstateImageProcessor.processImage(tempRawFile, tempProcessedFile)

                        // 2. Injeção de GPS no EXIF
                        locationHelper.injectGpsToExif(tempProcessedFile)

                        // 3. Gravação no MediaStore na pasta do Imóvel
                        val contentValues = storageHelper.createPhotoContentValues()
                        val resolver = context.contentResolver
                        val finalUri = resolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        ) ?: throw Exception("Falha ao criar registro no MediaStore")

                        resolver.openOutputStream(finalUri)?.use { outStream ->
                            FileInputStream(tempProcessedFile).use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(finalUri, contentValues, null, null)
                        }

                        tempRawFile.delete()
                        tempProcessedFile.delete()

                        ContextCompat.getMainExecutor(context).execute {
                            onSuccess(finalUri)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        ContextCompat.getMainExecutor(context).execute {
                            onError(e)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    ContextCompat.getMainExecutor(context).execute {
                        onError(exception)
                    }
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        onRecordingStarted: () -> Unit,
        onRecordingFinished: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val vc = videoCapture ?: return
        val contentValues = storageHelper.createVideoContentValues()
        val mediaStoreOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        currentRecording = vc.output
            .prepareRecording(context, mediaStoreOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onRecordingStarted()
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            onRecordingFinished(event.outputResults.outputUri)
                        } else {
                            val errorMsg = event.cause?.message ?: "Gravação finalizada"
                            onError(Exception(errorMsg))
                        }
                    }
                }
            }
    }

    fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    fun isRecording(): Boolean = currentRecording != null
}
