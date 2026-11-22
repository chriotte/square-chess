package com.dataespresso.squarechess

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class PkceTest {
    @Test fun challengeMatchesTheRfc7636Example() {
        // RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun verifiersAreLongEnoughUnreservedAndDifferent() {
        val verifiers = List(50) { Pkce.newVerifier() }
        verifiers.forEach {
            assertTrue(it, it.length in 43..128)
            assertTrue(it, it.matches(Regex("^[A-Za-z0-9._~-]+$")))
            // The server's code_challenge check: base64url without padding, 43 or more characters.
            assertTrue(Pkce.challenge(it).matches(Regex("^[A-Za-z0-9_-]{43,128}$")))
        }
        assertEquals(verifiers.size, verifiers.toSet().size)
        assertNotEquals(Pkce.newState(), Pkce.newState())
    }

    @Test fun authorizeUrlCarriesEveryParameterTheServerChecks() {
        val pending = PendingSignIn("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "st4te", 0)
        val uri = URI(authorizeUrl(pending))
        assertEquals("https", uri.scheme)
        assertEquals("squarechess.org", uri.host)
        assertEquals("/oauth/authorize", uri.path)
        val query = uri.rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("code", query["response_type"])
        assertEquals("square-chess-android", query["client_id"])
        assertEquals("org.squarechess.app:/oauth", query["redirect_uri"])
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", query["code_challenge"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("st4te", query["state"])
    }
}

class RedirectTest {
    private val pending = PendingSignIn("v".repeat(43), "abc123", 1_000)
    private val code = "0123456789abcdef".repeat(4)

    @Test fun theRightStateGivesTheCode() {
        assertEquals(RedirectResult.Code(code), parseRedirect("org.squarechess.app:/oauth?code=$code&state=abc123", pending, 2_000))
    }

    @Test fun aWrongOrMissingStateIsRejected() {
        assertEquals(RedirectResult.Invalid, parseRedirect("org.squarechess.app:/oauth?code=$code&state=other", pending, 2_000))
        assertEquals(RedirectResult.Invalid, parseRedirect("org.squarechess.app:/oauth?code=$code", pending, 2_000))
    }

    @Test fun nothingWasStartedOrItIsTooOld() {
        assertEquals(RedirectResult.Invalid, parseRedirect("org.squarechess.app:/oauth?code=$code&state=abc123", null, 2_000))
        assertEquals(RedirectResult.Invalid, parseRedirect("org.squarechess.app:/oauth?code=$code&state=abc123", pending,
            1_000 + SignInConfig.pendingLifetimeMs + 1))
    }

    @Test fun cancelOnTheWebsite() {
        assertEquals(RedirectResult.Denied, parseRedirect("org.squarechess.app:/oauth?error=access_denied&state=abc123", pending, 2_000))
    }

    @Test fun malformedAnswersAreRejected() {
        assertEquals(RedirectResult.Invalid, parseRedirect("org.squarechess.app:/oauth?code=notacode&state=abc123", pending, 2_000))
        assertEquals(RedirectResult.Invalid, parseRedirect("https://evil.example/oauth?code=$code&state=abc123", pending, 2_000))
        assertEquals(RedirectResult.Invalid, parseRedirect("::not a uri::", pending, 2_000))
    }
}

class AccountStoreTest {
    @Test fun tokenIsKeptUntilSignOut() {
        val prefs = MemoryPrefs()
        val account = AccountStore(prefs)
        assertFalse(account.signedIn)
        account.signIn("a".repeat(64), "tester")
        account.cursor = 42
        account.lastSync = 7
        account.tombstones = mapOf("game-1" to 5L)
        // A new store over the same preferences, as after the app was restarted.
        val again = AccountStore(prefs)
        assertTrue(again.signedIn)
        assertEquals("a".repeat(64), again.token)
        assertEquals("tester", again.username)
        assertEquals(42L, again.cursor)
        assertEquals(mapOf("game-1" to 5L), again.tombstones)
        again.signOut()
        assertFalse(again.signedIn)
        assertNull(again.token)
        assertNull(again.username)
        assertEquals(0L, again.cursor)
        assertEquals(emptyMap<String, Long>(), again.tombstones)
        assertTrue(prefs.values.isEmpty())
    }

    @Test fun signingInStartsFromCursorZero() {
        val account = AccountStore(MemoryPrefs())
        account.cursor = 99
        account.signIn("b".repeat(64), "other")
        assertEquals(0L, account.cursor)
    }

    @Test fun pendingSignInSurvivesAndClears() {
        val account = AccountStore(MemoryPrefs())
        account.pending = PendingSignIn("verifier", "state", 123)
        assertEquals(PendingSignIn("verifier", "state", 123), account.pending)
        account.pending = null
        assertNull(account.pending)
    }
}
