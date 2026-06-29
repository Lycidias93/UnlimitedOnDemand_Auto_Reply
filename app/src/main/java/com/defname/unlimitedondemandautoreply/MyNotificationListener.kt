/*
 * Copyright (C) 2025 defname
 *
 * This file is part of UnlimitedOnDemand Auto Reply.
 *
 * UnlimitedOnDemand_Auto_Reply is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * UnlimitedOnDemand Auto Reply is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with UnlimitedOnDemand_Auto_Reply. If not, see <https://www.gnu.org/licenses/>.
 */

package com.defname.unlimitedondemandautoreply

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val SETTINGS_PREFS = "settings"
private const val STATE_PREFS = "runtime_state"
private const val SMS_SENT_ACTION = "com.defname.unlimitedondemandautoreply.SMS_SENT"
private const val DEFAULT_MIN_DELAY_SECONDS = 5L
private const val DEFAULT_MAX_DELAY_SECONDS = 30L
private const val DEFAULT_SMS_APP = "com.google.android.apps.messaging"
private const val DEFAULT_TITLE_MATCH = "10118"
private const val DEFAULT_BODY_MATCH = "Vollspeed"
private const val DEFAULT_TARGET_NUMBER = "10118"
private const val DEFAULT_ANSWER = "2"
private const val DEFAULT_COOLDOWN_MS = 15L * 60L * 1000L
private const val DEFAULT_DEDUPE_WINDOW_MS = 10L * 60L * 1000L
private const val DEFAULT_DAILY_LIMIT = 3

/**
 * NotificationService that runs in the background and listens for incoming notifications.
 * If a notification matches the configured criteria, it schedules one SMS after a random delay.
 */
class MyNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.orEmpty()
        val extras = sbn.notification.extras
        val title = extras.getString("android.title").orEmpty()
        val text = notificationText(extras)

        val prefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
        val smsApp = readSetting(prefs, "sms_app", DEFAULT_SMS_APP).trim()
        val titleMatch = readSetting(prefs, "title_match", DEFAULT_TITLE_MATCH).trim()
        val bodyMatch = readSetting(prefs, "body_match", DEFAULT_BODY_MATCH).trim()
        val number = readSetting(prefs, "number", DEFAULT_TARGET_NUMBER).trim()
        val answer = readSetting(prefs, "answer", DEFAULT_ANSWER)
        val minDelay = readDelaySetting(prefs, "min_delay", DEFAULT_MIN_DELAY_SECONDS)
        val maxDelay = readDelaySetting(prefs, "max_delay", DEFAULT_MAX_DELAY_SECONDS)

        if (!isConfigComplete(smsApp, titleMatch, bodyMatch, number, answer)) {
            LogManager.addLog("Skipped: configuration incomplete")
            Log.d("NotifListener", "Skipped notification: configuration incomplete")
            return
        }

        if (!matchesConfiguredNotification(packageName, smsApp, title, titleMatch, text, bodyMatch)) {
            Log.d("NotifListener", "Notification ignored: no configured match")
            return
        }

        val now = System.currentTimeMillis()
        val statePrefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE)
        val notificationKey = stableHash(
            listOf(packageName, normalizeForMatch(title), normalizeForMatch(text), sbn.id.toString())
                .joinToString("|")
        )

        if (isDuplicate(statePrefs, notificationKey, now)) {
            LogManager.addLog("Skipped: duplicate notification within dedupe window")
            return
        }

        if (isInCooldown(statePrefs, now)) {
            LogManager.addLog("Skipped: cooldown active")
            return
        }

        if (dailyLimitReached(statePrefs, now)) {
            LogManager.addLog("Skipped: daily limit reached")
            return
        }

        val sanitizedPhone = sanitizePhoneNumber(number)
        if (sanitizedPhone.isBlank()) {
            LogManager.addLog("Skipped: target number invalid")
            return
        }

        val delay = calculateDelayMillis(minDelay, maxDelay)
        val sendId = "$now-${notificationKey.take(12)}"

        markScheduled(statePrefs, notificationKey, now)
        LogManager.addLog("Notification matched. SMS scheduled in ${delay / 1000}s.")

        Handler(Looper.getMainLooper()).postDelayed({
            sendSMS(sanitizedPhone, answer, sendId)
        }, delay)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // No-op.
    }

    private fun readSetting(
        prefs: android.content.SharedPreferences,
        key: String,
        defaultValue: String
    ): String {
        val value = prefs.getString(key, null)
        return value?.takeIf { it.isNotBlank() } ?: defaultValue
    }

    private fun readDelaySetting(
        prefs: android.content.SharedPreferences,
        key: String,
        defaultValue: Long
    ): Long {
        val value = prefs.getString(key, null)
        return value?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: defaultValue
    }

    private fun notificationText(extras: android.os.Bundle): String {
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()

        return listOf(text, bigText)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
    }

    private fun isConfigComplete(
        smsApp: String,
        titleMatch: String,
        bodyMatch: String,
        number: String,
        answer: String
    ): Boolean {
        return smsApp.isNotBlank() &&
            titleMatch.isNotBlank() &&
            bodyMatch.isNotBlank() &&
            number.isNotBlank() &&
            answer.isNotBlank()
    }

    private fun matchesConfiguredNotification(
        packageName: String,
        smsApp: String,
        title: String,
        titleMatch: String,
        text: String,
        bodyMatch: String
    ): Boolean {
        if (packageName != smsApp) return false

        val normalizedTitle = normalizeForMatch(title)
        val normalizedTitleMatch = normalizeForMatch(titleMatch)
        val normalizedText = normalizeForMatch(text)
        val normalizedBodyMatch = normalizeForMatch(bodyMatch)

        return normalizedTitle.contains(normalizedTitleMatch) &&
            normalizedText.contains(normalizedBodyMatch)
    }

    private fun calculateDelayMillis(minDelaySeconds: Long, maxDelaySeconds: Long): Long {
        val safeMinSeconds = minDelaySeconds.coerceAtLeast(0L)
        val safeMaxSeconds = maxDelaySeconds.coerceAtLeast(safeMinSeconds + 1L)
        val minMillis = safeMinSeconds * 1000L
        val maxMillis = safeMaxSeconds * 1000L

        return Random.nextLong(minMillis, maxMillis)
    }

    private fun isDuplicate(
        prefs: android.content.SharedPreferences,
        notificationKey: String,
        now: Long
    ): Boolean {
        val lastKey = prefs.getString("last_notification_key", "").orEmpty()
        val lastAt = prefs.getLong("last_notification_at", 0L)

        return lastKey == notificationKey && now - lastAt < DEFAULT_DEDUPE_WINDOW_MS
    }

    private fun isInCooldown(prefs: android.content.SharedPreferences, now: Long): Boolean {
        val lastSendAt = prefs.getLong("last_send_at", 0L)
        return lastSendAt > 0L && now - lastSendAt < DEFAULT_COOLDOWN_MS
    }

    private fun dailyLimitReached(prefs: android.content.SharedPreferences, now: Long): Boolean {
        val today = dateKey(now)
        val storedDay = prefs.getString("daily_limit_date", "").orEmpty()
        val count = if (storedDay == today) prefs.getInt("daily_send_count", 0) else 0

        return count >= DEFAULT_DAILY_LIMIT
    }

    private fun markScheduled(
        prefs: android.content.SharedPreferences,
        notificationKey: String,
        now: Long
    ) {
        val today = dateKey(now)
        val storedDay = prefs.getString("daily_limit_date", "").orEmpty()
        val previousCount = if (storedDay == today) prefs.getInt("daily_send_count", 0) else 0

        prefs.edit()
            .putString("last_notification_key", notificationKey)
            .putLong("last_notification_at", now)
            .putLong("last_send_at", now)
            .putString("daily_limit_date", today)
            .putInt("daily_send_count", previousCount + 1)
            .apply()
    }

    private fun dateKey(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    }

    private fun normalizeForMatch(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    private fun sanitizePhoneNumber(value: String): String {
        return value.filter { it.isDigit() || it == '+' }
    }

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Show a notification with the given title and message.
     */
    private fun showNotification(title: String, message: String) {
        val channelId = "unlimitedondemandautoreply_notification_channel"
        val channelName = "UnlimitedOnDemand Auto Reply"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from UnlimitedOnDemand Auto Reply"
            }

            val notificationManager: NotificationManager =
                applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    /**
     * Send an SMS to the given number with the given message.
     */
    private fun sendSMS(number: String, msg: String, sendId: String) {
        try {
            if (number.isBlank()) {
                throw IllegalArgumentException("Phone number is empty after filtering")
            }

            val smsManager = getSystemService(SmsManager::class.java)
                ?: throw IllegalStateException("SmsManager service not available")

            val intent = Intent(SMS_SENT_ACTION).apply {
                setPackage(packageName)
                putExtra("send_id", sendId)
            }

            val requestCode = sendId.hashCode()
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                flags
            )

            smsManager.sendTextMessage(number, null, msg, sentIntent, null)
            Log.d("NotifListener", "SMS process started")
            LogManager.addLog("SMS send requested")

        } catch (e: Exception) {
            Log.e("NotifListener", "Error sending SMS: ${e.message}")
            Toast.makeText(this, "SMS Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            LogManager.addLog("SMS Error: ${e.localizedMessage}")
        }
    }

    private val smsStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val code = resultCode
            val message = when (code) {
                android.app.Activity.RESULT_OK -> "SMS sent successfully"
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Error: generic SMS failure"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "Error: no mobile service"
                SmsManager.RESULT_ERROR_NULL_PDU -> "Error: empty SMS PDU"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "Error: radio off"
                else -> "SMS send failed (code $code)"
            }

            Log.d("NotifListener", "Receiver Result: $code")
            LogManager.addLog(message)
            showNotification("SMS Status", message)
        }
    }

    override fun onCreate() {
        super.onCreate()

        ContextCompat.registerReceiver(
            this,
            smsStatusReceiver,
            IntentFilter(SMS_SENT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsStatusReceiver)
    }
}
