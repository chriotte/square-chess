package com.dataespresso.squarechess

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket

/** The API client against a fake server in this JVM: no real accounts, no network. */
class SyncApiTest {
    private val server = FakeSyncServer()
    private val api = SyncApi(server.url, timeoutMs = 3_000)
    @After fun stop() = server.close()

    @Test fun codeAndVerifierGiveAToken() {
        val verifier = Pkce.newVerifier()
        val token = api.token(server.issueCode(Pkce.challenge(verifier)), verifier, "Test Phone")
        assertTrue(token in server.tokens)
        assertEquals("Test Phone", server.lastTokenForm["device"])
        assertEquals("authorization_code", server.lastTokenForm["grant_type"])
        assertEquals("tester", api.username(token))
    }

    @Test fun wrongVerifierExpiredOrReusedCodeIsAnInvalidGrant() {
        val verifier = Pkce.newVerifier()
        assertThrows(SyncError.InvalidGrant::class.java) { api.token(server.issueCode(Pkce.challenge(verifier)), Pkce.newVerifier(), "d") }
        val expired = server.issueCode(Pkce.challenge(verifier), lifetimeMs = -1)
        assertThrows(SyncError.InvalidGrant::class.java) { api.token(expired, verifier, "d") }
        val code = server.issueCode(Pkce.challenge(verifier))
        api.token(code, verifier, "d")
        assertThrows(SyncError.InvalidGrant::class.java) { api.token(code, verifier, "d") }
    }

    @Test fun anUnknownTokenIsSignedOut() {
        assertThrows(SyncError.SignedOut::class.java) { api.sync("f".repeat(64), 0, emptyList()) }
        assertThrows(SyncError.SignedOut::class.java) { api.username("f".repeat(64)) }
    }

    @Test fun logoutRevokesTheToken() {
        val token = server.newToken()
        api.sync(token, 0, emptyList())
        api.logout(token)
        assertThrows(SyncError.SignedOut::class.java) { api.sync(token, 0, emptyList()) }
    }

    @Test fun deleteNeedsTheRightPassword() {
        val token = server.newToken()
        server.put(SyncRecord.GAME, "g1", 1, syncGame().toSyncData())
        assertThrows(SyncError.WrongPassword::class.java) { api.deleteAccount(token, "wrong") }
        assertEquals(1, server.records.size)
        api.deleteAccount(token, server.password)
        assertTrue(server.records.isEmpty())
        assertThrows(SyncError.SignedOut::class.java) { api.sync(token, 0, emptyList()) }
    }

    @Test fun anUnreachableServerIsOffline() {
        val port = ServerSocket(0).use { it.localPort } // free, and nothing listens there now
        assertThrows(SyncError.Offline::class.java) { SyncApi("http://127.0.0.1:$port", 1_000).sync("a".repeat(64), 0, emptyList()) }
    }

    @Test fun aPageThatIsNotTheApiIsAServerError() {
        // For example a preview password page or a captive portal: an HTML 401 must not sign anyone out.
        MiniHttpServer { MiniHttpServer.Response(401, "<html>Authorization required</html>", "text/html") }.use { html ->
            val e = assertThrows(SyncError.Server::class.java) { SyncApi(html.url, 3_000).sync("a".repeat(64), 0, emptyList()) }
            assertEquals(401, e.status)
        }
    }
}

class SyncEngineTest {
    private val server = FakeSyncServer(pageSize = 3)
    private val api = SyncApi(server.url, timeoutMs = 3_000)
    @After fun stop() = server.close()

    private fun session(token: String) = object : SyncSession {
        override val token = token
        override var cursor = 0L
    }

    @Test fun pushesPullsAndAdvancesTheCursorAcrossPages() = runBlocking {
        val token = server.newToken()
        for (i in 1..5) server.put(SyncRecord.GAME, "remote$i", 100L + i, syncGame("remote$i", 100L + i).toSyncData())
        val store = MemorySyncStore()
        store.games["mine"] = syncGame("mine", 50)
        store.puzzles["p1"] = syncProgress("p1", 60)
        val s = session(token)
        val changes = SyncEngine(api, store, s).run()
        // Five remote games arrived over pages of three; this device's game and puzzle went up.
        assertEquals((1..5).map { "remote$it" }.toSet(), changes.games)
        assertEquals(6, store.games.size)
        assertTrue("game:mine" in server.records && "puzzle:p1" in server.records)
        assertEquals(server.records.values.maxOf { it.seq }, s.cursor)
        // Nothing new on either side: an empty sync changes nothing and sends nothing.
        val before = s.cursor
        assertFalse(SyncEngine(api, store, s).run().any)
        assertEquals(before, s.cursor)
        assertTrue(SyncMerge.localChanges(store).isEmpty())
    }

    @Test fun twoDevicesConvergeNewestWins() = runBlocking {
        val token = server.newToken()
        val phone = MemorySyncStore().apply { games["g1"] = syncGame("g1", 1_000) }
        val tablet = MemorySyncStore()
        val phoneSession = session(token); val tabletSession = session(token)
        SyncEngine(api, phone, phoneSession).run()
        SyncEngine(api, tablet, tabletSession).run()
        assertEquals("e2e4 e7e5", tablet.games["g1"]!!.moves)
        // The tablet plays on; the phone deletes a different game; both sync twice.
        tablet.games["g1"] = tablet.games["g1"]!!.copy(moves = "e2e4 e7e5 g1f3", updated = 2_000)
        phone.games["g2"] = syncGame("g2", 1_500)
        SyncEngine(api, phone, phoneSession).run()
        phone.games.remove("g2"); phone.tombs = mapOf("g2" to 1_600L)
        SyncEngine(api, tablet, tabletSession).run()
        SyncEngine(api, phone, phoneSession).run()
        SyncEngine(api, tablet, tabletSession).run()
        assertEquals("e2e4 e7e5 g1f3", phone.games["g1"]!!.moves)
        assertNull(tablet.games["g2"])
        assertTrue(server.records["game:g2"]!!.deleted)
        assertEquals(emptyMap<String, Long>(), phone.tombs)
    }

    @Test fun moreThanOneBatchIsSentInPieces() = runBlocking {
        val token = server.newToken()
        val store = MemorySyncStore()
        repeat(SyncEngine.BATCH + 20) { store.puzzles["p$it"] = syncProgress("p$it", 10L + it) }
        SyncEngine(api, store, session(token)).run()
        assertEquals(SyncEngine.BATCH + 20, server.records.size)
        assertTrue(server.requests.count { it == "POST sync" } >= 2)
    }

    @Test fun theAccountsPlayroomWinsAtSignInOtherwiseThisDevicesGoesUp() = runBlocking {
        val token = server.newToken()
        val choices = PlayroomChoices(World.SPACE, Buddy.MAGNUS, Outfit.decode("CROWN,skin:MEDIUM_DARK,hair:BLACK"), false, emptySet())
        // An account without a Playroom: this device's choices go up after the first sync.
        val store = MemorySyncStore().apply { this.choices = choices; level = 1250 }
        val engine = SyncEngine(api, store, session(token))
        engine.run()
        assertTrue(engine.keepLocalValuesIfAccountHasNone(9_000))
        engine.run()
        assertEquals("CROWN,skin:MEDIUM_DARK,hair:BLACK",
            server.records["kv:playroom"]!!.data!!.getJSONObject("value").getString("outfit"))
        assertEquals(1250, server.records["kv:trainingLevel"]!!.data!!.getInt("value"))
        // A second device signing in takes the account's choices.
        val other = MemorySyncStore()
        val otherEngine = SyncEngine(api, other, session(token))
        otherEngine.run()
        assertFalse(otherEngine.keepLocalValuesIfAccountHasNone(10_000))
        assertEquals(choices, other.choices)
        assertEquals(1250, other.level)
    }
}

class SyncManagerTest {
    private val server = FakeSyncServer()
    private val prefs = MemoryPrefs()
    private val account = AccountStore(prefs)
    private val store = MemorySyncStore()
    private var cleared = 0
    private fun manager(api: SyncApi = SyncApi(server.url, 3_000)) =
        SyncManager(account, store, api, CoroutineScope(SupervisorJob() + Dispatchers.IO), debounceMs = 0).also {
            it.clearBases = { cleared++; store.baseRecords.clear() }
        }
    @After fun stop() = server.close()

    /** Plays the browser's part: the player allows the app and the website redirects with a code. */
    private fun redirectFor(url: String, state: String? = null): String {
        val query = java.net.URI(url).rawQuery.split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        val code = server.issueCode(query.getValue("code_challenge"))
        return "org.squarechess.app:/oauth?code=$code&state=${state ?: query.getValue("state")}"
    }

    @Test fun nothingTouchesTheNetworkWhileSignedOut() = runBlocking {
        val m = manager()
        store.games["g1"] = syncGame()
        m.requestSync(); m.onAppStart(); m.gameDeleted("g1")
        m.syncNow().join()
        assertTrue(server.requests.isEmpty())
        assertEquals(SyncStatus.SIGNED_OUT, m.state.value.status)
    }

    @Test fun signInSyncsAndShowsTheUsername() = runBlocking {
        val m = manager()
        store.games["g1"] = syncGame()
        m.completeSignIn(redirectFor(m.startSignIn()), "Test Phone").join()
        val ui = m.state.value
        assertTrue(ui.signedIn)
        assertEquals("tester", ui.username)
        assertEquals(SyncStatus.IDLE, ui.status)
        assertTrue(ui.lastSync > 0)
        assertNull(account.pending)
        assertTrue("game:g1" in server.records)
    }

    @Test fun aWrongStateIsRefusedWithoutAToken() = runBlocking {
        val m = manager()
        m.completeSignIn(redirectFor(m.startSignIn(), state = "forged"), "d").join()
        assertFalse(account.signedIn)
        assertEquals(AccountProblem.SIGN_IN_FAILED, m.state.value.problem)
        assertFalse(server.requests.any { it == "POST oauth/token" })
    }

    @Test fun anExpiredCodeFailsCleanly() = runBlocking {
        val m = manager()
        val url = m.startSignIn()
        val redirect = redirectFor(url)
        server.codes.replaceAll { _, v -> v.first to 0L }
        m.completeSignIn(redirect, "d").join()
        assertFalse(account.signedIn)
        assertEquals(AccountProblem.SIGN_IN_FAILED, m.state.value.problem)
    }

    @Test fun cancelOnTheWebsite() = runBlocking {
        val m = manager()
        val url = m.startSignIn()
        val state = java.net.URI(url).rawQuery.split('&').first { it.startsWith("state=") }.substringAfter('=')
        m.completeSignIn("org.squarechess.app:/oauth?error=access_denied&state=$state", "d").join()
        assertEquals(AccountProblem.SIGN_IN_CANCELLED, m.state.value.problem)
        assertFalse(account.signedIn)
    }

    @Test fun a401SignsOutButKeepsTheGames() = runBlocking {
        val m = manager()
        m.completeSignIn(redirectFor(m.startSignIn()), "d").join()
        server.tokens.clear() // revoked on the website
        store.games["g1"] = syncGame()
        m.syncNow().join()
        assertFalse(account.signedIn)
        assertEquals(AccountProblem.SESSION_ENDED, m.state.value.problem)
        assertFalse(m.state.value.signedIn)
        assertNotNull(store.games["g1"])
        assertTrue(cleared > 0)
    }

    @Test fun anUnreachableServerKeepsTheAccountAndTheChanges() = runBlocking {
        val m = manager()
        m.completeSignIn(redirectFor(m.startSignIn()), "d").join()
        val token = account.token
        val port = ServerSocket(0).use { it.localPort }
        val offline = SyncManager(account, store, SyncApi("http://127.0.0.1:$port", 1_000), CoroutineScope(Dispatchers.IO), debounceMs = 0)
        store.games["g1"] = syncGame()
        offline.syncNow().join()
        assertEquals(AccountProblem.OFFLINE, offline.state.value.problem)
        assertTrue(offline.state.value.signedIn)
        assertEquals(token, account.token)
        // Back online, the change goes up.
        m.syncNow().join()
        assertTrue("game:g1" in server.records)
    }

    @Test fun signOutForgetsTheTokenRevokesItAndKeepsLocalData() = runBlocking {
        val m = manager()
        store.games["g1"] = syncGame()
        m.completeSignIn(redirectFor(m.startSignIn()), "d").join()
        val token = account.token!!
        m.signOut().join()
        assertNull(account.token)
        assertFalse(token in server.tokens)
        assertEquals(SyncStatus.SIGNED_OUT, m.state.value.status)
        assertNotNull(store.games["g1"])
        assertTrue(store.baseRecords.isEmpty())
    }

    @Test fun deleteAccountAsksForThePassword() = runBlocking {
        val m = manager()
        m.completeSignIn(redirectFor(m.startSignIn()), "d").join()
        m.deleteAccount("wrong").join()
        assertEquals(AccountProblem.WRONG_PASSWORD, m.state.value.problem)
        assertTrue(account.signedIn)
        m.deleteAccount(server.password).join()
        assertTrue(m.state.value.deleted)
        assertFalse(account.signedIn)
        assertTrue(server.records.isEmpty())
    }

    @Test fun deletingAGameWhileSignedInReachesTheServer() = runBlocking {
        val m = manager()
        store.games["g1"] = syncGame()
        m.completeSignIn(redirectFor(m.startSignIn()), "d").join()
        store.games.remove("g1")
        m.gameDeleted("g1")
        m.syncNow().join()
        assertTrue(server.records["game:g1"]!!.deleted)
        assertNull(server.records["game:g1"]!!.data)
    }
}

class BackupTest {
    @Test fun backupRestoresIntoAnEmptyDevice() = runBlocking {
        val from = MemorySyncStore().apply {
            games["g1"] = syncGame("g1", 1_000); puzzles["p1"] = syncProgress("p1", 2_000)
            level = 1400; kvStamps[SyncRecord.TRAINING_LEVEL] = 3_000
            choices = PlayroomChoices(World.FOREST, Buddy.GIRL, Outfit.decode("CROWN,hair:GINGER"), true, setOf(Sticker.FIRST_GAME))
            kvStamps[SyncRecord.PLAYROOM] = 4_000
        }
        val settings = AppSettings(sound = false, boardTheme = BoardTheme.SLATE, eink = true).withPlayroom(from.choices)
        val json = JSONObject(Backup.build(from, settings, 5_000).toString())
        assertEquals(Backup.FORMAT, json.getString("format"))
        val to = MemorySyncStore()
        val restored = Backup.restore(to, json, AppSettings())
        assertEquals(1, restored.games)
        assertEquals(1, restored.puzzles)
        assertEquals(from.games, to.games)
        assertEquals(from.puzzles, to.puzzles)
        assertEquals(1400, to.level)
        assertEquals(from.choices, to.choices)
        assertEquals(false, restored.settings!!.sound)
        assertEquals(BoardTheme.SLATE, restored.settings!!.boardTheme)
        assertTrue(restored.settings!!.eink)
        // Restoring never adds bases: those are only the server's copies.
        assertTrue(to.baseRecords.isEmpty())
    }

    @Test fun restoreKeepsNewerGames() = runBlocking {
        val backup = Backup.build(MemorySyncStore().apply { games["g1"] = syncGame("g1", 1_000, moves = "d2d4") }, AppSettings(), 1)
        val to = MemorySyncStore().apply { games["g1"] = syncGame("g1", 2_000) }
        assertEquals(0, Backup.restore(to, backup, AppSettings()).games)
        assertEquals("e2e4 e7e5", to.games["g1"]!!.moves)
    }

    @Test fun theWebsitesExportCanBeRestored() = runBlocking {
        val export = JSONObject().put("ok", true).put("account", JSONObject().put("username", "tester"))
            .put("records", org.json.JSONArray().put(JSONObject().put("kind", "game").put("id", "g9").put("updated", 9)
                .put("data", syncGame("g9", 9).toSyncData())))
        val to = MemorySyncStore()
        assertEquals(1, Backup.restore(to, export, AppSettings()).games)
        assertNotNull(to.games["g9"])
    }

    @Test fun otherFilesAreRefused() = runBlocking {
        try { Backup.restore(MemorySyncStore(), JSONObject().put("hello", 1), AppSettings()); fail() }
        catch (_: IllegalArgumentException) {}
    }
}
