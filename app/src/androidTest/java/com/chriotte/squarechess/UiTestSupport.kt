package com.chriotte.squarechess

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

internal fun dismissImmersiveModePrompt() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val root = automation.rootInActiveWindow ?: return

    fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == "Got it" || node.contentDescription?.toString() == "Got it") {
            return node
        }
        for (index in 0 until node.childCount) {
            find(node.getChild(index))?.let { return it }
        }
        return null
    }

    var button = find(root) ?: return
    while (!button.isClickable && button.parent != null) button = button.parent
    if (button.isClickable && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
