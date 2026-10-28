package com.trailstatus.dfw

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Opened from the launcher: lists every DORBA trail with its status (tap opens Trailforks).
 * Opened by the widget host when placing/reconfiguring a widget: tap a trail to show it on that widget.
 */
class TrailPickerActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val configuring = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        if (configuring) {
            // Backing out of the picker should cancel placing the widget.
            setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }
        RefreshWorker.schedule(this)

        setContent {
            MaterialTheme {
                PickerScreen(configuring = configuring, onPick = ::onTrailPicked)
            }
        }
    }

    private fun onTrailPicked(trail: Trail) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            trail.url?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            return
        }
        WidgetPrefs.setTrail(this, appWidgetId, trail.name)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        lifecycleScope.launch {
            TrailWidget().updateAll(this@TrailPickerActivity)
            finish()
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun PickerScreen(configuring: Boolean, onPick: (Trail) -> Unit) {
        var cache by remember { mutableStateOf(TrailRepository.cached(this)) }
        var loading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            TrailRepository.refresh(this@TrailPickerActivity)
                .onSuccess { cache = it; error = null }
                .onFailure { error = it.message }
            loading = false
            TrailWidget().updateAll(this@TrailPickerActivity)
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text(if (configuring) "Pick a trail for this widget" else "DFW Trail Status") }) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (error != null) {
                    Text(
                        "Couldn't refresh: $error" + if (cache != null) " (showing cached)" else "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                val trails = cache?.trails.orEmpty()
                if (loading && trails.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                LazyColumn {
                    items(trails, key = { it.name }) { trail ->
                        TrailRow(trail) { onPick(trail) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    @Composable
    private fun TrailRow(trail: Trail, onClick: () -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(14.dp).background(Color(trail.level.argb), CircleShape))
            Column {
                Text(trail.name, style = MaterialTheme.typography.bodyLarge)
                Text(trail.statusText, style = MaterialTheme.typography.bodySmall, color = Color(trail.level.argb))
            }
        }
    }
}
