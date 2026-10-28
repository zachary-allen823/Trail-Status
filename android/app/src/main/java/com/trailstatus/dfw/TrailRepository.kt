package com.trailstatus.dfw

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object TrailRepository {
    /** The same Trailforks table dorba.org shows on its home page. */
    const val SOURCE_URL =
        "https://www.trailforks.com/widgets/region_list/?rids=35629,3719,13783,3726,5016,22007,13746,14710,22005,22065,5025,5022,3723,19612,24208,33774,33812,25637,19031,5018,13750,13751,34284,5020,24408,5021,17590,23481,3717,13827,13829,28103,24406,45471,38370,50089,54704,61494,71523,73024,31507&rows=50&cols=status,title,last_report_ts"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    private const val PREFS = "trail_cache"

    suspend fun refresh(context: Context): Result<TrailCache> = withContext(Dispatchers.IO) {
        runCatching {
            val trails = TrailforksParser.parse(download())
            if (trails.isEmpty()) throw IOException("No trails found in page")
            TrailCache(System.currentTimeMillis(), trails, stale = false).also { save(context, it) }
        }.onFailure { markStale(context) }
    }

    fun cached(context: Context): TrailCache? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString("data", null) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            val arr = obj.getJSONArray("trails")
            val trails = (0 until arr.length()).map { i ->
                val t = arr.getJSONObject(i)
                Trail(
                    name = t.getString("name"),
                    url = t.optString("url").ifEmpty { null },
                    level = StatusLevel.valueOf(t.getString("level")),
                    statusText = t.getString("statusText"),
                    updatedEpochSec = if (t.has("updated")) t.getLong("updated") else null,
                )
            }
            TrailCache(obj.getLong("fetchedAt"), trails, stale = prefs.getBoolean("stale", false))
        }.getOrNull()
    }

    private fun download(): String {
        val conn = URL(SOURCE_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html")
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} from Trailforks")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun save(context: Context, cache: TrailCache) {
        val arr = JSONArray()
        cache.trails.forEach { t ->
            arr.put(JSONObject().apply {
                put("name", t.name)
                put("url", t.url ?: "")
                put("level", t.level.name)
                put("statusText", t.statusText)
                t.updatedEpochSec?.let { put("updated", it) }
            })
        }
        val json = JSONObject().put("fetchedAt", cache.fetchedAtMillis).put("trails", arr)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("data", json.toString())
            .putBoolean("stale", false)
            .apply()
    }

    private fun markStale(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("stale", true).apply()
    }
}

/** Which trail each placed widget shows. */
object WidgetPrefs {
    const val DEFAULT_TRAIL = "North Shore"
    private const val PREFS = "widget_prefs"

    fun trailFor(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("trail_$appWidgetId", null)
            ?: DEFAULT_TRAIL

    fun setTrail(context: Context, appWidgetId: Int, trailName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("trail_$appWidgetId", trailName).apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("trail_$appWidgetId").apply()
    }
}
