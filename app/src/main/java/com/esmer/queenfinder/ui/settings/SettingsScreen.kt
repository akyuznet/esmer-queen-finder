package com.esmer.queenfinder.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.esmer.queenfinder.QueenFinderApp
import com.esmer.queenfinder.R
import com.esmer.queenfinder.data.Settings
import com.esmer.queenfinder.detection.LiteRtQueenDetector
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as QueenFinderApp
    val store = app.settingsStore
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.setting_sensitivity), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setting_sensitivity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.confidence,
                    onValueChange = { v -> scope.launch { store.setConfidence(v) } },
                    valueRange = 0.1f..0.95f,
                    modifier = Modifier.weight(1f),
                )
                Text("%.2f".format(settings.confidence), modifier = Modifier.padding(start = 12.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.setting_stable_frames), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.setting_stable_frames_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.stableFrames.toFloat(),
                    onValueChange = { v -> scope.launch { store.setStableFrames(v.roundToInt()) } },
                    valueRange = 1f..15f,
                    steps = 13,
                    modifier = Modifier.weight(1f),
                )
                Text(settings.stableFrames.toString(), modifier = Modifier.padding(start = 12.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SwitchRow(stringResource(R.string.setting_haptics), settings.haptics) { scope.launch { store.setHaptics(it) } }
            SwitchRow(stringResource(R.string.setting_sound), settings.sound) { scope.launch { store.setSound(it) } }
            SwitchRow(stringResource(R.string.setting_show_drones), settings.showDrones) { scope.launch { store.setShowDrones(it) } }
            SwitchRow(stringResource(R.string.setting_show_stats), settings.showStats) { scope.launch { store.setShowStats(it) } }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(stringResource(R.string.setting_model), style = MaterialTheme.typography.titleMedium)
            Text(LiteRtQueenDetector.DEFAULT_MODEL, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.setting_about), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
