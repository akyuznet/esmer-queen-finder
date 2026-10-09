package com.esmer.queenfinder.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.esmer.queenfinder.R
import com.esmer.queenfinder.detection.Backend
import com.esmer.queenfinder.ui.theme.QueenBox
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(
    onOpenSettings: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(stringResource(R.string.camera_permission_needed), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.grant_camera))
            }
        }
        return
    }

    CameraContent(viewModel, onOpenSettings)
}

@Composable
private fun CameraContent(viewModel: CameraViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by viewModel.detectionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    LaunchedEffect(previewView) {
        val cam = bindCamera(context, lifecycleOwner, previewView, viewModel)
        camera = cam
        cam.cameraInfo.zoomState.observe(lifecycleOwner) { z ->
            zoomRatio = z.zoomRatio
            maxZoom = z.maxZoomRatio
        }
        // Pinch to zoom on the preview.
        val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val max = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                cam.cameraControl.setZoomRatio((current * d.scaleFactor).coerceIn(1f, max))
                return true
            }
        })
        previewView.setOnTouchListener { v, event ->
            detector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        DetectionOverlay(state = state, showDrones = settings.showDrones, modifier = Modifier.fillMaxSize())

        // Top bar: status + settings.
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val queen = state.stableQueen != null
            Text(
                text = when {
                    state.error != null -> stringResource(R.string.model_failed)
                    queen -> stringResource(R.string.queen_found)
                    else -> stringResource(R.string.searching)
                },
                color = if (queen) QueenBox else Color.White,
                style = if (queen) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = Color.White)
            }
        }

        // Bottom bar: stats + actions.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (settings.showStats && state.error == null) {
                val backend = when (state.backend) {
                    Backend.GPU -> stringResource(R.string.backend_gpu)
                    Backend.CPU -> stringResource(R.string.backend_cpu)
                    Backend.NONE -> stringResource(R.string.backend_none)
                }
                Text(
                    text = stringResource(R.string.stats_format, state.fps.toInt(), state.inferenceMs, state.modelMs, backend),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            state.error?.let {
                Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall, maxLines = 3)
                Spacer(Modifier.height(8.dp))
            }
            if (maxZoom > 1.01f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    Slider(
                        value = zoomRatio.coerceIn(1f, maxZoom),
                        onValueChange = { v -> camera?.cameraControl?.setZoomRatio(v) },
                        valueRange = 1f..maxZoom,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", zoomRatio),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }) { Text(stringResource(R.string.torch) + if (torchOn) " ●" else "") }
                Button(onClick = {
                    scope.launch {
                        val ok = viewModel.saveSnapshot()
                        Toast.makeText(
                            context,
                            if (ok) R.string.snapshot_saved else R.string.snapshot_failed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(R.string.snapshot)) }
            }
        }
    }
}

private suspend fun bindCamera(
    context: Context,
    owner: LifecycleOwner,
    previewView: PreviewView,
    viewModel: CameraViewModel,
): Camera {
    val provider = awaitProvider(context)
    val ratio = AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)

    val preview = Preview.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
        .build()
        .also { it.surfaceProvider = previewView.surfaceProvider }

    val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(ratio)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build()
        .also { it.setAnalyzer(viewModel.pipeline.executor, viewModel.pipeline) }

    provider.unbindAll()
    return provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
}

private suspend fun awaitProvider(context: Context): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        try {
            cont.resume(future.get())
        } catch (t: Throwable) {
            cont.resumeWithException(t)
        }
    }, ContextCompat.getMainExecutor(context))
}
