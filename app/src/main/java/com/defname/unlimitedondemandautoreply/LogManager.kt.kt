package com.defname.unlimitedondemandautoreply

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object LogManager {
    private const val PREFS_NAME = "runtime_logs"
    private const val PREFS_KEY_ENTRIES = "entries_json"
    private const val MAX_LOGS = 250
    private const val MAX_MESSAGE_LENGTH = 500

    private var appContext: Context? = null
    private var loaded = false

    val logs = mutableStateListOf<LogEntry>()

    data class LogEntry(
        val timestamp: String,
        val message: String
    )

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        if (!loaded) {
            loadPersistentLogs()
            loaded = true
        }
    }

    @Synchronized
    fun addLog(message: String) {
        if (!loaded) {
            loadPersistentLogs()
            loaded = true
        }

        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add(0, LogEntry(time, sanitizeLogMessage(message)))

        trimLogs()
        persistLogs()
    }

    @Synchronized
    fun clearLogs() {
        logs.clear()
        loaded = true
        persistLogs()
    }

    @Synchronized
    private fun loadPersistentLogs() {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(PREFS_KEY_ENTRIES, null) ?: return

        logs.clear()

        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                if (logs.size >= MAX_LOGS) break
                val item = array.optJSONObject(i) ?: continue
                val timestamp = item.optString("timestamp", "")
                val message = item.optString("message", "")
                if (timestamp.isNotBlank() && message.isNotBlank()) {
                    logs.add(LogEntry(timestamp, sanitizeLogMessage(message)))
                }
            }
        }.onFailure {
            logs.clear()
        }

        trimLogs()
    }

    private fun trimLogs() {
        while (logs.size > MAX_LOGS) {
            logs.removeAt(logs.lastIndex)
        }
    }

    @Synchronized
    private fun persistLogs() {
        val context = appContext ?: return
        val array = JSONArray()

        logs.take(MAX_LOGS).forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("message", sanitizeLogMessage(entry.message))
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY_ENTRIES, array.toString())
            .apply()
    }

    private fun sanitizeLogMessage(message: String): String {
        return message
            .replace("\n", " ")
            .replace("\r", " ")
            .take(MAX_MESSAGE_LENGTH)
    }
}
