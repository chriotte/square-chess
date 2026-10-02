package com.dataespresso.squarechess

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Fixture tests clear or overwrite saved games. They may only run in the separate
 * dev build (-Pdev=true), never in the user's app.
 */
internal fun isIsolatedTestPackage(name: String) = name=="com.dataespresso.squarechess.dev"

/**
 * The root of the window the user sees. Before Android 10 the "active window" lags behind when a
 * popup (such as the time-control menu) opens or closes over a dialog, so there the topmost app
 * window is used. Android 10+ normally reports the active window correctly, but just after an
 * activity is recreated (seen on Android 13) it can still be the closing, empty window; then the
 * topmost window of this app is used.
 */
internal fun activeRoot(): AccessibilityNodeInfo? {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    if (Build.VERSION.SDK_INT >= 29) {
        val active = automation.rootInActiveWindow
        if (active != null && active.refresh() && active.childCount > 0) return active
        val app = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        return appWindows(automation).firstOrNull { it.packageName == app } ?: active
    }
    return appWindows(automation).firstOrNull() ?: automation.rootInActiveWindow
}

/** Roots of the application windows, topmost first. */
private fun appWindows(automation: android.app.UiAutomation): List<AccessibilityNodeInfo> {
    val info = automation.serviceInfo
    if (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS == 0) {
        automation.serviceInfo = info.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }
    return automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .sortedByDescending { it.layer }.mapNotNull { it.root }
}

internal fun dismissImmersiveModePrompt() {
    val root = activeRoot() ?: return

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
    val root = activeRoot() ?: return false
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
