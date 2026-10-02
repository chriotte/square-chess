package com.dataespresso.squarechess

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No saved-game fixtures or writes; safe to run against the main app. Never sends email. */
@RunWith(AndroidJUnit4::class)
class FeedbackDeviceTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
        if(node==null || !node.refresh()) return null
        if(predicate(node)) return node
        for(i in 0 until node.childCount) find(node.getChild(i),predicate)?.let { return it }
        return null
    }
    private fun node(text: String): AccessibilityNodeInfo {
        repeat(35) { i ->
            dismissImmersiveModePrompt()
            val root=activeRoot()
            (find(root) { it.contentDescription?.toString()==text }
                ?: find(root) { it.text?.toString()==text })?.let { return it }
            if(i%5==4) find(root) { it.isScrollable && !it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(100)
        }
        error("Missing node $text")
    }
    private fun tap(text: String) {
        var n=node(text)
        n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        while(!n.isClickable && n.parent!=null) n=n.parent
        assertTrue("Click $text",n.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun field(label: String): AccessibilityNodeInfo {
        var n=node(label)
        while(!n.isEditable && n.parent!=null) n=n.parent
        return n
    }
    private fun fill(label: String,text: String) {
        val n=field(label)
        assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)
        }))
        instrumentation.waitForIdleSync()
    }
    @Test fun homeLabelHelpNavigationAndFeatureDraftSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            tap("Play against computer"); node("Start game");tap("Cancel")
            tap("Help & About")
            node("Chess designed for square-screen phones.")
            tap("About Square Chess");node(instrumentation.targetContext.getString(R.string.about_story));tap("Back")
            tap("How to play");node("Keyboard moves");tap("Back")
            tap("App information");node("Copy support address");tap("Back")
            tap("Privacy");node("Open privacy policy");tap("Back")
            tap("Third-party licences");tap("Fairy-Stockfish · GNU GPL v3")
            val deadline=SystemClock.uptimeMillis()+5_000
            while(find(activeRoot()) { it.text?.contains("GNU GENERAL PUBLIC LICENSE")==true }==null && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(100)
            assertNotNull(find(activeRoot()) { it.text?.contains("GNU GENERAL PUBLIC LICENSE")==true })
            tap("Back");tap("Back")
            tap("Suggest a feature")
            fill("What would you like to see? (required)","Export æøå 中文\nKeep e2e4 as text")
            scenario.recreate()
            assertTrue(field("What would you like to see? (required)").text.toString().contains("Keep e2e4 as text"))
            tap("Review message")
            val body=field("Message").text.toString()
            assertTrue(body.contains("Export æøå 中文"))
            assertFalse(body.contains("Diagnostic details"))
            tap("Back");tap("Back");tap("Back")
            node("Play against computer")
        }
    }
    @Test fun noClientShowsFallbackAndCopiesEditedReport() {
        val noClient=FeedbackEmailHandoff { throw ActivityNotFoundException() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme { HelpAboutScreen(null,{},noClient) } } }
            tap("Report a bug")
            fill("What happened? (required)","Test report — do not send")
            tap("Include basic device and app details")
            tap("Review message")
            assertFalse(field("Message").text.toString().contains("Diagnostic details"))
            fill("Message","Edited report æøå\nSecond line")
            tap("Send using email app")
            node(instrumentation.targetContext.getString(R.string.email_unavailable))
            tap("Copy report")
            scenario.onActivity { activity ->
                val text=activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
                assertTrue(text.contains("support@dataespresso.com"));assertTrue(text.contains("Edited report æøå\nSecond line"))
            }
            tap("Copy support address")
            scenario.onActivity { activity ->
                assertEquals(SupportConfig.email,activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString())
            }
        }
    }
    @Test fun mailtoAndExtrasMatchAndLaunchIsGuarded() {
        for(report in listOf(FeedbackReportBuilder.bug("Problem æøå & ?","1\n2","Expected"),FeedbackReportBuilder.feature("Feature 中文","Useful"))) {
            val intents=mutableListOf<Intent>()
            val handoff=FeedbackEmailHandoff {intents.add(it)}
            assertTrue(handoff.open(report));assertTrue(handoff.open(report))
            assertEquals(1,intents.size)
            val intent=intents.single()
            assertEquals(Intent.ACTION_SENDTO,intent.action)
            assertEquals("mailto",intent.data!!.scheme)
            assertArrayEquals(arrayOf(SupportConfig.email),intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
            assertEquals(report.body,intent.getStringExtra(Intent.EXTRA_TEXT))
            assertEquals(report.subject,intent.getStringExtra(Intent.EXTRA_SUBJECT))
            val query=android.net.Uri.parse("https://local.invalid/?"+intent.data.toString().substringAfter('?'))
            assertEquals(report.body,query.getQueryParameter("body"))
            assertEquals(report.subject,query.getQueryParameter("subject"))
            handoff.returned();handoff.open(report);assertEquals(2,intents.size)
        }
    }
}
