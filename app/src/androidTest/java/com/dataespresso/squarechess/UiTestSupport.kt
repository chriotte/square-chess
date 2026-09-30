package com.dataespresso.squarechess

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Fixture tests clear or overwrite saved games. They may only run in the separate
 * dev build (-Pdev=true), never in the user's app.
 */
internal fun isIsolatedTestPackage(name: String) = name=="com.dataespresso.squarechess.dev"

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

/**
 * On small screens (Light Phone III) a menu item can be below the visible part of a
 * scrolling dialog, where Compose does not report it. Scrolls every scrollable node one
 * step forward; returns false when nothing could scroll further.
 */
internal fun scrollForward(): Boolean {
    val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow ?: return false
    var scrolled = false
    fun visit(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) scrolled = true
        for (index in 0 until node.childCount) visit(node.getChild(index))
    }
    visit(root)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    return scrolled
}
