package com.dataespresso.squarechess

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Sign-in with the website: OAuth 2.0 authorization code with PKCE (S256), in the browser.
 * The app never sees the password. See docs/development/sign-in-sync.md.
 */
object SignInConfig {
    const val server = "https://squarechess.org"
    const val clientId = "square-chess-android"
    /** Registered on the server (SC_OAUTH_CLIENTS in site/api/index.php). */
    const val redirectUri = "org.squarechess.app:/oauth"
    const val redirectScheme = "org.squarechess.app"
    const val accountDeleteUrl = "https://squarechess.org/account/delete"
    /** A sign-in left open in the browser longer than this is started again. */
    const val pendingLifetimeMs = 30 * 60 * 1000L
}

object Pkce {
    private val random = SecureRandom()
    private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** 43 characters from the unreserved set (32 random bytes, base64url). */
    fun newVerifier(): String = ByteArray(32).also(random::nextBytes).let(::base64Url)
    fun newState(): String = ByteArray(16).also(random::nextBytes).let(::base64Url)
    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

/** A sign-in started in the browser: kept until the browser returns, since Android may end the app meanwhile. */
data class PendingSignIn(val verifier: String, val state: String, val started: Long)

private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

fun authorizeUrl(pending: PendingSignIn, server: String = SignInConfig.server): String =
    "$server/oauth/authorize?response_type=code&client_id=${enc(SignInConfig.clientId)}" +
        "&redirect_uri=${enc(SignInConfig.redirectUri)}&code_challenge=${enc(Pkce.challenge(pending.verifier))}" +
        "&code_challenge_method=S256&state=${enc(pending.state)}"

/** What the browser brought back to the redirect address. */
sealed interface RedirectResult {
    data class Code(val code: String) : RedirectResult
    /** The player pressed Cancel on the website. */
    data object Denied : RedirectResult
    /** Not the sign-in this app started (wrong or missing state), or nothing usable. */
    data object Invalid : RedirectResult
}

/** Reads `org.squarechess.app:/oauth?code=…&state=…` against the sign-in that was started. */
fun parseRedirect(uri: String, pending: PendingSignIn?, now: Long): RedirectResult {
    if (pending == null || now - pending.started > SignInConfig.pendingLifetimeMs) return RedirectResult.Invalid
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return RedirectResult.Invalid
    if (parsed.scheme != SignInConfig.redirectScheme) return RedirectResult.Invalid
    val query = parsed.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
        val (k, v) = (it.split('=', limit = 2) + "").take(2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
    // A constant-time comparison: the state is what ties this answer to this app's request.
    val state = query["state"] ?: return RedirectResult.Invalid
    if (!MessageDigest.isEqual(state.toByteArray(), pending.state.toByteArray())) return RedirectResult.Invalid
    if (query["error"] == "access_denied") return RedirectResult.Denied
    val code = query["code"]?.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) } ?: return RedirectResult.Invalid
    return RedirectResult.Code(code)
}
