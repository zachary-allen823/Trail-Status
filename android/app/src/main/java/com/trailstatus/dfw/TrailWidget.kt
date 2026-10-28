package com.trailstatus.dfw

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrailWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val query = WidgetPrefs.trailFor(context, appWidgetId)
        val cache = TrailRepository.cached(context)
        provideContent { WidgetContent(query, cache) }
    }
}

@Composable
private fun WidgetContent(query: String, cache: TrailCache?) {
    val trail = cache?.let { TrailMatcher.find(it.trails, query) }
    val level = trail?.level ?: StatusLevel.UNKNOWN
    val white = ColorProvider(Color.White)

    val detail = when {
        cache == null -> "Tap to load"
        trail == null -> "Trail not found"
        trail.updatedEpochSec != null ->
            "${level.label} · ${SimpleDateFormat("MMM d", Locale.US).format(Date(trail.updatedEpochSec * 1000))}"
        else -> level.label
    }
    val footer = cache?.let {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.fetchedAtMillis))
        if (it.stale) "⚠ cached $time" else "checked $time"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(level.argb))
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionRunCallback<RefreshAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = trail?.let { TrailMatcher.shortName(it.name) } ?: query,
            style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(text = detail, style = TextStyle(color = white, fontSize = 13.sp), maxLines = 1)
        if (footer != null) {
            Text(text = footer, style = TextStyle(color = white, fontSize = 10.sp), maxLines = 1)
        }
    }
}

/** Tapping the widget refreshes it. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TrailRepository.refresh(context)
        TrailWidget().updateAll(context)
    }
}

class TrailWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrailWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.refreshNow(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }
}
