package com.dataespresso.squarechess

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Fixture tests clear or overwrite saved games. They may only run in the separate
 * dev build (-Pdev=true) or the Stockfish baseline, never in the user's app.
 */
internal fun isIsolatedTestPackage(name: String) =
    name=="com.dataespresso.squarechess.dev" || name=="com.dataespresso.squarechess.stockfishbaseline"

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
