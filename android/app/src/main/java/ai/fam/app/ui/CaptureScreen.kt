package ai.fam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.fam.app.R
import ai.fam.app.data.CROP_ORDER
import ai.fam.app.data.ModelDownload
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

@Composable
fun CaptureScreen(padding: PaddingValues, onOpenResult: (Long) -> Unit) {
    val viewModel: CaptureViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.openId) {
        val id = state.openId ?: return@LaunchedEffect
        onOpenResult(id)
        viewModel.consumeOpen()
    }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.onGallery(uri, state.crop)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(if (state.piConfigured) R.string.settings_pi else R.string.settings_local),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        ModelBanner(onRetry = { ModelDownload.retry(context) })
        Text(stringResource(R.string.capture_crop), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.crop == null,
                onClick = { viewModel.setCrop(null) },
                label = { Text(stringResource(R.string.crop_any)) },
            )
            CROP_ORDER.forEach { crop ->
                FilterChip(
                    selected = state.crop == crop,
                    onClick = { viewModel.setCrop(crop) },
                    label = { Text(cropName(crop)) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (granted) {
                CameraPane(enabled = !state.working, onSaved = { file -> viewModel.onFile(file, state.crop) })
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.capture_permission))
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.capture_allow))
                    }
                }
            }
            if (state.working) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.capture_working),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        OutlinedButton(
            onClick = { viewModel.onSample(state.crop) },
            enabled = !state.working,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "sample-leaf" },
        ) {
            Text(stringResource(R.string.capture_sample))
        }
        OutlinedButton(
            onClick = {
                gallery.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.capture_gallery))
        }
    }
}

@Composable
private fun CameraPane(enabled: Boolean, onSaved: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var missingCamera by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = try {
                future.get()
            } catch (_: Exception) {
                missingCamera = true
                return@Runnable
            }
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val selectors = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA)
            var bound = false
            for (selector in selectors) {
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    imageCapture = capture
                    bound = true
                    break
                } catch (_: Exception) {
                    provider.unbindAll()
                }
            }
            missingCamera = !bound
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            if (future.isDone) {
                try {
                    future.get().unbindAll()
                } catch (_: Exception) {
                    // Camera already released.
                }
            }
            executor.shutdown()
        }
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(factory = { previewView }, modifier = Modifier.weight(1f).fillMaxWidth())
        if (missingCamera) {
            Text(stringResource(R.string.capture_no_camera))
        }
        Button(
            onClick = {
                val capture = imageCapture ?: return@Button
                val dir = File(context.cacheDir, "capture").apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    options,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onSaved(file)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            file.delete()
                        }
                    },
                )
            },
            enabled = enabled && imageCapture != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.capture_take))
        }
    }
}

@Composable
fun cropName(crop: String): String {
    return when (crop) {
        "tomato" -> stringResource(R.string.crop_tomato)
        "potato" -> stringResource(R.string.crop_potato)
        "cassava" -> stringResource(R.string.crop_cassava)
        "maize" -> stringResource(R.string.crop_maize)
        "apple" -> stringResource(R.string.crop_apple)
        else -> crop
    }
}
