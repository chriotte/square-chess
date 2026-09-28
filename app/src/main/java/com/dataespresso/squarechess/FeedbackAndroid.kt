package com.dataespresso.squarechess

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

data class AppInformation(val name: String, val version: String)
fun appInformation(context: Context): AppInformation {
    val info=context.packageManager.getPackageInfo(context.packageName,0)
    return AppInformation(context.applicationInfo.loadLabel(context.packageManager).toString(),
        "${info.versionName.orEmpty()} (${info.longVersionCode})")
}
fun feedbackDiagnostics(context: Context, mode: String?): FeedbackDiagnostics = FeedbackDiagnostics(
    appInformation(context).version, "${Build.MANUFACTURER} ${Build.MODEL}",
    "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
    mode?.let { runCatching { GameMode.valueOf(it) }.getOrNull() }?.let {
        when(it) { GameMode.COMPUTER -> "Computer"; GameMode.LOCAL_TWO_PLAYER -> "Two players"; GameMode.PHYSICAL_BOARD_RECORDING -> "Physical board recording"; GameMode.POST_GAME_REVIEW -> "Game review" }
    })

fun feedbackEmailIntent(report: FeedbackReport): Intent {
    val subject=FeedbackReportBuilder.subject(report.subject)
    // Mailto query supports clients that ignore extras; extras support clients that prefer them.
    val uri=Uri.parse("mailto:${SupportConfig.email}?subject=${Uri.encode(subject)}&body=${Uri.encode(report.body)}")
    return Intent(Intent.ACTION_SENDTO,uri).apply {
        putExtra(Intent.EXTRA_EMAIL,arrayOf(SupportConfig.email))
        putExtra(Intent.EXTRA_SUBJECT,subject)
        putExtra(Intent.EXTRA_TEXT,report.body)
    }
}

/** Kept injectable so the no-client path can be tested without changing phone apps. */
class FeedbackEmailHandoff(private val launch: (Intent)->Unit) {
    var inFlight=false
        private set
    fun returned() { inFlight=false }
    fun open(report: FeedbackReport): Boolean {
        if(inFlight) return true
        inFlight=true
        return try { launch(feedbackEmailIntent(report)); true }
        catch(_: ActivityNotFoundException) { inFlight=false; false }
        catch(_: SecurityException) { inFlight=false; false }
    }
}

fun copyFeedback(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label,text))
}
