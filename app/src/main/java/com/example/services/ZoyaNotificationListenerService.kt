package com.example.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

/**
 * Optional, user-controlled NotificationListenerService allowing Zoya AI to summarize
 * active notifications only when the user explicitly grants Notification Access.
 */
class ZoyaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        refreshSnapshot()
    }

    override fun onListenerDisconnected() {
        _isConnected.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        val list = runCatching {
            activeNotifications.orEmpty()
                .filter { it.packageName != packageName && !it.isOngoing }
                .sortedByDescending { it.postTime }
                .take(15)
                .mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
                    val body = extras.getCharSequence("android.text")?.toString()?.trim().orEmpty()
                    if (title.isEmpty() && body.isEmpty()) return@mapNotNull null
                    val appLabel = runCatching {
                        val ai = packageManager.getApplicationInfo(sbn.packageName, 0)
                        packageManager.getApplicationLabel(ai).toString()
                    }.getOrDefault(sbn.packageName.substringAfterLast('.'))
                    CapturedNotification(
                        key = sbn.key,
                        appName = appLabel,
                        title = title,
                        text = body,
                        postTime = sbn.postTime
                    )
                }
        }.getOrDefault(emptyList())
        _notifications.value = list
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()
    }
}
