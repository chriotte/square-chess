package com.dataespresso.squarechess

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Why a call to the website did not work. Play is never affected; sync tries again later. */
sealed class SyncError(message: String) : Exception(message) {
    /** No connection, or the server could not be reached. */
    class Offline(cause: Throwable? = null) : SyncError("offline") { init { cause?.let(::initCause) } }
    /** The token is no longer valid (signed out on the website, expired, or the account was deleted). */
    class SignedOut : SyncError("signed_out")
    /** The sign-in code was wrong, used before or expired. */
    class InvalidGrant : SyncError("invalid_grant")
    class WrongPassword : SyncError("wrong_password")
    class TooManyAttempts : SyncError("too_many_attempts")
    /** Any other answer: [code] is the server's error code, or "http_<status>". */
    class Server(val code: String, val status: Int) : SyncError(code)
}

data class SyncReply(val cursor: Long, val records: List<SyncRecord>, val more: Boolean)

/**
 * The website's account API (site/api/index.php), with the platform's HttpURLConnection and
 * org.json, so no network library is added. Every call blocks: run it off the main thread.
 */
class SyncApi(private val server: String = SignInConfig.server, private val timeoutMs: Int = 15_000) {

    /** Exchanges the browser's one-time code for a long-lived app token. */
    fun token(code: String, verifier: String, device: String): String {
        val form = mapOf(
            "grant_type" to "authorization_code", "code" to code, "code_verifier" to verifier,
            "client_id" to SignInConfig.clientId, "redirect_uri" to SignInConfig.redirectUri, "device" to device
        ).entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
        val reply = call("POST", "oauth/token", null, form, "application/x-www-form-urlencoded")
        val token = reply.optString("access_token")
        if (!token.matches(Regex("^[0-9a-f]{64}$"))) throw SyncError.Server("bad_token", 200)
        return token
    }

    /** The account's user name; [SyncError.SignedOut] when the token is not valid. */
    fun username(token: String): String {
        val user = call("GET", "me", token).optJSONObject("user") ?: throw SyncError.SignedOut()
        return user.optString("username")
    }

    fun sync(token: String, cursor: Long, records: List<SyncRecord>): SyncReply {
        val body = JSONObject().put("cursor", cursor).put("records", JSONArray(records.map { it.toJson() }))
        val reply = call("POST", "sync", token, body.toString(), "application/json")
        val list = reply.optJSONArray("records") ?: JSONArray()
        return SyncReply(
            cursor = reply.optLong("cursor", cursor),
            records = (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(SyncRecord::fromJson) },
            more = reply.optBoolean("more", false)
        )
    }

    /** Revokes the token on the server. */
    fun logout(token: String) { call("POST", "logout", token, "{}", "application/json") }

    /** Deletes the account and everything synced to it. The server asks for the account password. */
    fun deleteAccount(token: String, password: String) {
        call("POST", "delete", token, JSONObject().put("password", password).toString(), "application/json")
    }

    private fun call(method: String, path: String, token: String?, body: String? = null, type: String? = null): JSONObject {
        val connection = try {
            (URL("${server.trimEnd('/')}/api/$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                // Bearer calls need no X-Square-Chess header; it is harmless and keeps any other call working.
                setRequestProperty("X-Square-Chess", "1")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "$type; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
        } catch (e: IOException) { throw SyncError.Offline(e) }
        try {
            val status = try { connection.responseCode } catch (e: IOException) { throw SyncError.Offline(e) }
            val text = runCatching {
                (if (status >= 400) connection.errorStream else connection.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull().orEmpty()
            val json = try { JSONObject(text) } catch (_: JSONException) { null }
            val error = json?.optString("error").orEmpty()
            when {
                // A plain 401 page without JSON is a password-protected preview, not a token problem.
                (status == 401 && json != null) || error == "signed_out" -> throw SyncError.SignedOut()
                error == "invalid_grant" -> throw SyncError.InvalidGrant()
                error == "wrong_password" -> throw SyncError.WrongPassword()
                status == 429 -> throw SyncError.TooManyAttempts()
                // A preview password, a captive portal or a proxy page: not this API.
                json == null -> throw SyncError.Server("http_$status", status)
                status !in 200..299 || json.optBoolean("ok") != true -> throw SyncError.Server(error.ifEmpty { "http_$status" }, status)
            }
            return json!!
        } finally { connection.disconnect() }
    }
}
