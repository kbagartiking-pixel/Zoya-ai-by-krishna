package com.example.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional, user-enabled Android AccessibilityService for Zoya AI.
 * Performs screen reading, requested clicks, and scrolling strictly when initiated by the user.
 */
class ZoyaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener; Zoya only inspects active window nodes when user explicitly requests it.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        super.onDestroy()
    }

    fun readVisibleScreenText(maxItems: Int = 20): String {
        val root = rootInActiveWindow ?: return ""
        val texts = mutableListOf<String>()
        collectVisibleTexts(root, texts, maxItems)
        return texts.distinct().joinToString(" • ")
    }

    fun clickNodeByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(query)
        for (node in matches) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                current = current.parent
            }
        }
        return false
    }

    fun scrollScreen(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return findScrollableAndPerform(root, action)
    }

    private fun findScrollableAndPerform(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findScrollableAndPerform(child, action)) return true
        }
        return false
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        maxItems: Int
    ) {
        if (out.size >= maxItems) return
        val label = node.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (label != null) {
            out.add(label)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= maxItems) return
            val child = node.getChild(i) ?: continue
            collectVisibleTexts(child, out, maxItems)
        }
    }

    companion object {
        @Volatile
        var instance: ZoyaAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
