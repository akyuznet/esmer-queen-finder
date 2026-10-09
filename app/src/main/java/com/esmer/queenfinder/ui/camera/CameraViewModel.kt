package com.esmer.queenfinder.ui.camera

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.esmer.queenfinder.QueenFinderApp
import com.esmer.queenfinder.data.Settings
import com.esmer.queenfinder.detection.DetectionPipeline
import com.esmer.queenfinder.detection.DetectionState
import com.esmer.queenfinder.detection.LiteRtQueenDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val queenApp = app as QueenFinderApp
    private val alerter = queenApp.alerter

    val pipeline = DetectionPipeline(
        detector = LiteRtQueenDetector(app),
        onQueenStable = { alerter.queenFound() },
    )

    val detectionState: StateFlow<DetectionState> = pipeline.state

    val settings: StateFlow<Settings> = queenApp.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    init {
        viewModelScope.launch {
            settings.collect { s ->
                pipeline.confidence = s.confidence
                pipeline.stableFrames = s.stableFrames
                alerter.hapticsEnabled = s.haptics
                alerter.soundEnabled = s.sound
            }
        }
    }

    /** Saves the last analysed frame to Pictures/QueenFinder. Returns true on success. */
    suspend fun saveSnapshot(): Boolean = withContext(Dispatchers.IO) {
        val frame: Bitmap = pipeline.uprightSnapshot() ?: return@withContext false
        val resolver = getApplication<Application>().contentResolver
        val name = "queen_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QueenFinder")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                frame.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: error("no stream")
        }.isSuccess
    }

    override fun onCleared() {
        pipeline.close()
    }
}
