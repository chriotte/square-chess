package com.dataespresso.squarechess

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Receives the website's answer at the redirect address (org.squarechess.app:/oauth), hands it to
 * [SyncManager] and returns to the app's own screen. It shows nothing itself.
 */
class SignInRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { SyncManager.get(this).completeSignIn(it, SyncManager.deviceName()) }
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
