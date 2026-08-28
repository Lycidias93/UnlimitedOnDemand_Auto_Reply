package com.defname.unlimitedondemandautoreply

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeStatusManager {
    private const val PREFS_NAME = "listener_runtime_status"
    private const val MAX_VALUE_LENGTH = 160

    fun markAppOpened(context: Context) {
        prefs(context).edit()
            .putLong("app_opened_at", System.currentTimeMillis())
            .apply()
    }

    fun markListenerCreated(context: Context) {
        prefs(context).edit()
            .putBoolean("listener_created", true)
            .putLong("listener_created_at", System.currentTimeMillis())
            .putString("listener_state", "created")
            .apply()
    }

    fun markListenerDestroyed(context: Context) {
        prefs(context).edit()
            .putLong("listener_destroyed_at", System.currentTimeMillis())
            .putString("listener_state", "destroyed")
            .apply()
    }

    fun markNotificationCallback(context: Context) {
        prefs(context).edit()
            .putBoolean("notification_callback_seen", true)
            .putLong("last_callback_at", System.currentTimeMillis())
            .putString("listener_state", "notification_callback")
            .apply()
    }

    fun recordNotificationEvaluation(
        context: Context,
        sourcePackage: String,
        configuredPackage: String,
        titlePresent: Boolean,
        bodyPresent: Boolean,
        profileEnabled: Boolean,
        dryRun: Boolean,
        packageMatch: Boolean?,
        titleMatch: Boolean?,
        bodyMatch: Boolean?,
        decision: String
    ) {
        val now = System.currentTimeMillis()
        val sanitizedSource = sanitizeValue(sourcePackage)
        val sanitizedConfigured = sanitizeValue(configuredPackage)
        val sanitizedDecision = sanitizeValue(decision)
        val editor = prefs(context).edit()
            .putLong("last_evaluation_at", now)
            .putString("last_seen_package", sanitizedSource)
            .putString("configured_sms_app", sanitizedConfigured)
            .putBoolean("last_title_present", titlePresent)
            .putBoolean("last_body_present", bodyPresent)
            .putBoolean("profile_enabled", profileEnabled)
            .putBoolean("dry_run", dryRun)
            .putString("package_match", packageMatch.toStatusString())
            .putString("title_match", titleMatch.toStatusString())
            .putString("body_match", bodyMatch.toStatusString())
            .putString("last_decision", sanitizedDecision)

        if (packageMatch == true || decision == "matched") {
            editor
                .putLong("last_relevant_evaluation_at", now)
                .putString("last_relevant_package", sanitizedSource)
                .putString("last_relevant_decision", sanitizedDecision)
                .putBoolean("last_relevant_title_present", titlePresent)
                .putBoolean("last_relevant_body_present", bodyPresent)
                .putString("last_relevant_package_match", packageMatch.toStatusString())
                .putString("last_relevant_title_match", titleMatch.toStatusString())
                .putString("last_relevant_body_match", bodyMatch.toStatusString())
        }

        if (decision == "matched") {
            editor
                .putLong("last_match_evaluation_at", now)
                .putString("last_match_package", sanitizedSource)
                .putString("last_match_decision", sanitizedDecision)
        }

        editor.apply()
    }

    fun recordDecision(context: Context, decision: String) {
        val now = System.currentTimeMillis()
        val sanitizedDecision = sanitizeValue(decision)
        val editor = prefs(context).edit()
            .putLong("last_decision_at", now)
            .putString("last_decision", sanitizedDecision)

        if (decision in setOf("dry_run_match_no_sms", "sms_scheduled", "sms_send_requested")) {
            editor
                .putLong("last_success_at", now)
                .putString("last_success_decision", sanitizedDecision)
        }

        if (decision == "dry_run_match_no_sms") {
            editor
                .putLong("last_dry_run_match_at", now)
                .putString("last_dry_run_match_decision", sanitizedDecision)
        }

        editor.apply()
    }

    fun copyStatusToClipboard(context: Context) {
        val text = buildStatusText(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("UODA runtime status", text))
        LogManager.addLog("Runtime status copied")
    }

    fun buildStatusText(context: Context): String {
        val status = prefs(context)
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val state = context.getSharedPreferences("runtime_state", Context.MODE_PRIVATE)

        return buildString {
            appendLine("UODA runtime status")
            appendLine("generated_at=${formatTimestamp(System.currentTimeMillis())}")
            appendLine("listener_state=${status.getString("listener_state", "unknown")}")
            appendLine("listener_created=${status.getBoolean("listener_created", false)}")
            appendLine("notification_callback_seen=${status.getBoolean("notification_callback_seen", false)}")
            appendLine("last_callback_at=${formatStoredTimestamp(status, "last_callback_at")}")
            appendLine("last_evaluation_at=${formatStoredTimestamp(status, "last_evaluation_at")}")
            appendLine("last_seen_package=${status.getString("last_seen_package", "unknown")}")
            appendLine("configured_sms_app=${status.getString("configured_sms_app", settings.getString("sms_app", "unknown"))}")
            appendLine("profile_enabled=${status.getBoolean("profile_enabled", settings.getString("profile_enabled", "true") == "true")}")
            appendLine("dry_run=${status.getBoolean("dry_run", settings.getString("dry_run", "true") == "true")}")
            appendLine("title_present=${status.getBoolean("last_title_present", false)}")
            appendLine("body_present=${status.getBoolean("last_body_present", false)}")
            appendLine("package_match=${status.getString("package_match", "unknown")}")
            appendLine("title_match=${status.getString("title_match", "unknown")}")
            appendLine("body_match=${status.getString("body_match", "unknown")}")
            appendLine("last_decision=${status.getString("last_decision", "unknown")}")
            appendLine("last_decision_at=${formatStoredTimestamp(status, "last_decision_at")}")
            appendLine("last_relevant_evaluation_at=${formatStoredTimestamp(status, "last_relevant_evaluation_at")}")
            appendLine("last_relevant_package=${status.getString("last_relevant_package", "unknown")}")
            appendLine("last_relevant_decision=${status.getString("last_relevant_decision", "unknown")}")
            appendLine("last_relevant_title_present=${status.getBoolean("last_relevant_title_present", false)}")
            appendLine("last_relevant_body_present=${status.getBoolean("last_relevant_body_present", false)}")
            appendLine("last_relevant_package_match=${status.getString("last_relevant_package_match", "unknown")}")
            appendLine("last_relevant_title_match=${status.getString("last_relevant_title_match", "unknown")}")
            appendLine("last_relevant_body_match=${status.getString("last_relevant_body_match", "unknown")}")
            appendLine("last_match_evaluation_at=${formatStoredTimestamp(status, "last_match_evaluation_at")}")
            appendLine("last_match_package=${status.getString("last_match_package", "unknown")}")
            appendLine("last_match_decision=${status.getString("last_match_decision", "unknown")}")
            appendLine("last_success_at=${formatStoredTimestamp(status, "last_success_at")}")
            appendLine("last_success_decision=${status.getString("last_success_decision", "unknown")}")
            appendLine("last_dry_run_match_at=${formatStoredTimestamp(status, "last_dry_run_match_at")}")
            appendLine("last_dry_run_match_decision=${status.getString("last_dry_run_match_decision", "unknown")}")
            appendLine("last_notification_at=${formatStoredTimestamp(state, "last_notification_at")}")
            appendLine("last_send_at=${formatStoredTimestamp(state, "last_send_at")}")
            appendLine("daily_limit_date=${state.getString("daily_limit_date", "")}")
            appendLine("daily_send_count=${state.getInt("daily_send_count", 0)}")
        }.trimEnd()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Boolean?.toStatusString(): String {
        return when (this) {
            true -> "yes"
            false -> "no"
            null -> "unknown"
        }
    }

    private fun sanitizeValue(value: String): String {
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .filter { it.isLetterOrDigit() || it in " ._:-/@" }
            .take(MAX_VALUE_LENGTH)
    }

    private fun formatStoredTimestamp(
        prefs: android.content.SharedPreferences,
        key: String
    ): String {
        val value = prefs.getLong(key, 0L)
        return if (value > 0L) formatTimestamp(value) else "never"
    }

    private fun formatTimestamp(value: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
    }
}
