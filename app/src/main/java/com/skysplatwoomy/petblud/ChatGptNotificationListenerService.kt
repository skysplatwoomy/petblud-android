package com.skysplatwoomy.petblud

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ChatGptNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker = ChatGptNotificationBridge.notifications
    private var connected = false
    private var enabled = false

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            SettingsRepository(this@ChatGptNotificationListenerService).settings
                .map { it.hoots.showChatGptNotifications }
                .distinctUntilChanged()
                .collect { value ->
                    enabled = value
                    tracker.setEnabled(value)
                    if (value && connected) reconcileActiveNotifications()
                }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        if (enabled) reconcileActiveNotifications()
    }

    override fun onListenerDisconnected() {
        connected = false
        tracker.disconnected()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (!enabled) return
        sbn?.toPayload()?.let(tracker::posted)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(tracker::removed)
    }

    override fun onDestroy() {
        connected = false
        tracker.disconnected()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun reconcileActiveNotifications() {
        val payloads = runCatching { activeNotifications.orEmpty().map { it.toPayload() } }
            .getOrDefault(emptyList())
        tracker.reconcile(payloads)
    }

    private fun StatusBarNotification.toPayload(): NotificationPayload {
        val extras = notification.extras
        return NotificationPayload(
            packageName = packageName,
            key = key,
            postTime = postTime,
            title = extras.getCharSequence(Notification.EXTRA_TITLE),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
        )
    }
}
