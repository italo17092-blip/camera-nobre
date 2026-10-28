package com.nobre.cameranobre.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nobre.cameranobre.camera.CameraManager
import com.nobre.cameranobre.data.LocationHelper
import com.nobre.cameranobre.data.PropertyStorageHelper
import kotlin.math.roundToInt

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val storageHelper = remember { PropertyStorageHelper(context) }
    val locationHelper = remember { LocationHelper(context) }

    var activeProperty by remember { mutableStateOf(storageHelper.activeProperty) }
    var showPropertyDialog by remember { mutableStateOf(false) }

    var isVideoMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isHighRes50M by remember { mutableStateOf(false) }
    var isAspectRatio43 by remember { mutableStateOf(true) }
    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var showZoomSlider by remember { mutableStateOf(false) }
    var flashState by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    val previewView = remember { PreviewView(context) }
    val cameraManager = remember {
        CameraManager(context, lifecycleOwner, storageHelper, locationHelper)
    }

    LaunchedEffect(Unit) {
        cameraManager.initialize(previewView) {}
        locationHelper.updateLocation()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Barra Superior com statusBarsPadding (Zero conflito com notch e relógio)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selo do Imóvel
            Row(
                modifier = Modifier
                    .background(Color(0xFF222222), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFFFD4AF37), RoundedCornerShape(20.dp))
                    .clickable { showPropertyDialog = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFD4AF37),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeProperty,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Controles de Flash, Resolução e Proporção
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Flash Toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF222222), CircleShape)
                        .clickable {
                            flashState = when (flashState) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                            cameraManager.setFlashMode(flashState)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val (icon, tint) = when (flashState) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn to Color(0xFFFFD4AF37)
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto to Color.White
                        else -> Icons.Default.FlashOff to Color.Gray
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Resolução 12 MP / 50 MP
                Text(
                    text = if (isHighRes50M) "50 MP" else "12 MP",
                    color = if (isHighRes50M) Color(0xFFFFD4AF37) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                        .border(1.dp, if (isHighRes50M) Color(0xFFFFD4AF37) else Color.DarkGray, RoundedCornerShape(12.dp))
                        .clickable {
                            isHighRes50M = !isHighRes50M
                            cameraManager.setResolution(isHighRes50M)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Proporção 4:3 / 9:16
                Text(
                    text = if (isAspectRatio43) "4:3" else "9:16",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
                        .clickable {
                            isAspectRatio43 = !isAspectRatio43
                            cameraManager.setAspectRatio(isAspectRatio43)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        // 2. Visor da Câmera (Enquadramento Exato SEM cortes)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isAspectRatio43) 3f / 4f else 9f / 16f)
                    .clip(RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomFactor, _ ->
                            val newZoom = (currentZoom * zoomFactor).coerceIn(0.6f, 10.0f)
                            currentZoom = newZoom
                            cameraManager.setZoom(newZoom)
                            showZoomSlider = true
                        }
                    }
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Grade 3x3 Arquitetônica
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = Color.White.copy(alpha = 0.25f)
                    val w = size.width
                    val h = size.height
                    drawLine(stroke, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(stroke, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(stroke, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                    drawLine(stroke, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = 1.dp.toPx())
                }

                // Nível Virtual
                VirtualLeveler()
            }
        }

        // 3. Deck Inferior Samsung (Zero sobreposição)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Slider Suave de Zoom (aparece ao tocar/ajustar)
            AnimatedVisibility(
                visible = showZoomSlider,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = String.format("%.1fx", currentZoom),
                        color = Color(0xFFFFD4AF37),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Slider(
                        value = currentZoom,
                        onValueChange = { zoomVal ->
                            currentZoom = zoomVal
                            cameraManager.setZoom(zoomVal)
                        },
                        valueRange = 0.6f..10.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFD4AF37),
                            activeTrackColor = Color(0xFFFFD4AF37),
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.width(220.dp)
                    )
                }
            }

            // Seletor de Lentes (.6 Ultra-wide, 1x Principal, 2x, 5x, 10x)
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lenses = listOf(0.6f, 1.0f, 2.0f, 5.0f, 10.0f)
                lenses.forEach { zoom ->
                    val isSelected = (currentZoom * 10).roundToInt() == (zoom * 10).roundToInt()
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isSelected) Color(0xFFFFD4AF37) else Color.Transparent,
                                CircleShape
                            )
                            .clickable {
                                currentZoom = zoom
                                cameraManager.setZoom(zoom)
                                showZoomSlider = !showZoomSlider
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (zoom == 0.6f) ".6" else "${zoom.toInt()}x",
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Seletor de Modos (FOTO / VÍDEO)
            Row(
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                Text(
                    text = "FOTO",
                    color = if (!isVideoMode) Color(0xFFFFD4AF37) else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { if (!isRecording) isVideoMode = false }
                )
                Text(
                    text = "VÍDEO",
                    color = if (isVideoMode) Color(0xFFFFD4AF37) else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { isVideoMode = true }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Linha de Disparo com Atalho para Galeria à esquerda
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Atalho da Galeria
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF222222), CircleShape)
                        .border(1.5.dp, Color.DarkGray, CircleShape)
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    if (lastCapturedUri != null) {
                                        setDataAndType(lastCapturedUri, "image/jpeg")
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    } else {
                                        setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                                    }
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Abrindo Galeria...", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Galeria",
                        tint = Color(0xFFFFD4AF37),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Botão Principal de Disparo (Estilo Samsung)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .border(3.5.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .background(
                            if (isVideoMode) Color(0xFFE53935) else Color.White,
                            CircleShape
                        )
                        .clickable {
                            if (isVideoMode) {
                                if (!isRecording) {
                                    cameraManager.startRecording(
                                        onRecordingStarted = { isRecording = true },
                                        onRecordingFinished = { uri ->
                                            isRecording = false
                                            lastCapturedUri = uri
                                            Toast.makeText(context, "Take salvo no álbum $activeProperty!", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = {
                                            isRecording = false
                                            Toast.makeText(context, "Erro na gravação: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else {
                                    cameraManager.stopRecording()
                                }
                            } else {
                                cameraManager.takePhoto(
                                    onSuccess = { uri ->
                                        lastCapturedUri = uri
                                        Toast.makeText(context, "Foto salva no álbum $activeProperty!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Erro ao capturar: ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideoMode && isRecording) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color.White, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // Espaçador para equilíbrio estético
                Box(modifier = Modifier.size(52.dp))
            }
        }
    }

    if (showPropertyDialog) {
        PropertySelectorDialog(
            currentProperty = activeProperty,
            recentProperties = storageHelper.getRecentProperties(),
            onSelectProperty = { selected ->
                activeProperty = selected
                storageHelper.activeProperty = selected
            },
            onDismiss = { showPropertyDialog = false }
        )
    }
}
