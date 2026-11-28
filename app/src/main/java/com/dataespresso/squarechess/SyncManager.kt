package com.dataespresso.squarechess

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.room.InvalidationTracker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The account on this device: the app token, the server cursor and games deleted since the last
 * sync. Private SharedPreferences (the app has backups turned off); see the design document for
 * why the token is not in EncryptedSharedPreferences.
 */
class AccountStore(private val prefs: SharedPreferences) : SyncSession {
    override val token: String? get() = prefs.getString(TOKEN, null)
    val username: String? get() = prefs.getString(USERNAME, null)
    val signedIn: Boolean get() = token != null
    override var cursor: Long
        get() = prefs.getLong(CURSOR, 0)
        set(value) { prefs.edit().putLong(CURSOR, value).apply() }
    var lastSync: Long
        get() = prefs.getLong(LAST_SYNC, 0)
        set(value) { prefs.edit().putLong(LAST_SYNC, value).apply() }

    fun signIn(token: String, username: String) {
        prefs.edit().putString(TOKEN, token).putString(USERNAME, username).putLong(CURSOR, 0).putLong(LAST_SYNC, 0)
            .remove(TOMBSTONES).remove(PENDING).apply()
    }
    fun setUsername(username: String) { prefs.edit().putString(USERNAME, username).apply() }
    /** Forgets the token and the sync position. Games, puzzles and settings on the device stay. */
    fun signOut() { prefs.edit().clear().apply() }

    var pending: PendingSignIn?
        get() {
            val verifier = prefs.getString(PENDING_VERIFIER, null) ?: return null
            val state = prefs.getString(PENDING_STATE, null) ?: return null
            return PendingSignIn(verifier, state, prefs.getLong(PENDING, 0))
        }
        set(value) {
            if (value == null) prefs.edit().remove(PENDING).remove(PENDING_VERIFIER).remove(PENDING_STATE).apply()
            else prefs.edit().putLong(PENDING, value.started).putString(PENDING_VERIFIER, value.verifier)
                .putString(PENDING_STATE, value.state).apply()
        }

    var tombstones: Map<String, Long>
        get() = runCatching {
            val json = JSONObject(prefs.getString(TOMBSTONES, null) ?: return emptyMap())
            json.keys().asSequence().associateWith { json.getLong(it) }
        }.getOrDefault(emptyMap())
        set(value) { prefs.edit().putString(TOMBSTONES, JSONObject(value as Map<*, *>).toString()).apply() }

    private companion object {
        const val TOKEN = "token"
        const val USERNAME = "username"
        const val CURSOR = "cursor"
        const val LAST_SYNC = "lastSync"
        const val TOMBSTONES = "tombstones"
        const val PENDING = "pending.started"
        const val PENDING_VERIFIER = "pending.verifier"
        const val PENDING_STATE = "pending.state"
    }
}

/** Sync's view of this device's Room tables and settings. */
class AppSyncStore(private val db: ChessDatabase, private val settings: SettingsStore,
                   private val puzzlePrefs: SharedPreferences, private val account: AccountStore) : SyncStore {
    override suspend fun gameStamps() = db.games().stamps().associate { it.id to it.updated }
    override suspend fun game(id: String) = db.games().get(id)
    override suspend fun saveGame(game: SavedGame) = db.games().save(game)
    override suspend fun deleteGame(id: String) = db.games().delete(id)
    override suspend fun puzzleStamps() = db.puzzleProgress().stamps().associate { it.id to it.updated }
    override suspend fun puzzle(id: String) = db.puzzleProgress().get(id)
    override suspend fun savePuzzle(progress: PuzzleProgress) = db.puzzleProgress().save(progress)
    override fun trainingLevel(): Int? =
        if (puzzlePrefs.contains(PuzzleViewModel.LEVEL_KEY)) puzzlePrefs.getInt(PuzzleViewModel.LEVEL_KEY, START_TRAINING_LEVEL) else null
    override fun setTrainingLevel(level: Int) { puzzlePrefs.edit().putInt(PuzzleViewModel.LEVEL_KEY, level).apply() }
    override fun playroom() = settings.load().playroom
    override fun setPlayroom(choices: PlayroomChoices) {
        settings.save(settings.load().withPlayroom(choices), playroomUpdated = settings.playroomUpdated)
    }
    // The training values are written here without a new change time: sync sets that itself.
    override fun puzzleGoal(): Int? =
        if (puzzlePrefs.contains(TrainingPrefs.GOAL_KEY)) puzzlePrefs.getInt(TrainingPrefs.GOAL_KEY, DEFAULT_PUZZLE_GOAL) else null
    override fun setPuzzleGoal(goal: Int) { puzzlePrefs.edit().putInt(TrainingPrefs.GOAL_KEY, goal).apply() }
    override fun puzzleDays(): Map<String, Int> =
        daysFromJson(puzzlePrefs.getString(TrainingPrefs.DAYS_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() })
    override fun setPuzzleDays(days: Map<String, Int>) { puzzlePrefs.edit().putString(TrainingPrefs.DAYS_KEY, daysToJson(days).toString()).apply() }
    override fun repeatSet(): JSONObject? = puzzlePrefs.getString(TrainingPrefs.REPEAT_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    override fun setRepeatSet(set: JSONObject?) {
        puzzlePrefs.edit().apply { if (set == null) remove(TrainingPrefs.REPEAT_KEY) else putString(TrainingPrefs.REPEAT_KEY, set.toString()) }.apply()
    }
    override fun gameRating(): GameRating? =
        gameRatingFromJson(puzzlePrefs.getString(TrainingPrefs.GAME_RATING_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() })
    override fun setGameRating(rating: GameRating) { puzzlePrefs.edit().putString(TrainingPrefs.GAME_RATING_KEY, rating.toJson().toString()).apply() }
    override fun ratingDays(): RatingDays =
        ratingDaysFromJson(puzzlePrefs.getString(TrainingPrefs.RATING_DAYS_KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() })
    override fun setRatingDays(days: RatingDays) { puzzlePrefs.edit().putString(TrainingPrefs.RATING_DAYS_KEY, ratingDaysToJson(days).toString()).apply() }
    override fun kvUpdated(key: String) = when (key) {
        SyncRecord.TRAINING_LEVEL -> puzzlePrefs.getLong(PuzzleViewModel.LEVEL_UPDATED_KEY, 0)
        SyncRecord.PUZZLE_GOAL, SyncRecord.PUZZLE_DAYS, SyncRecord.REPEAT_SET, SyncRecord.GAME_RATING, SyncRecord.RATING_DAYS ->
            puzzlePrefs.getLong(key + TrainingPrefs.UPDATED, 0)
        SyncRecord.PLAYROOM -> settings.playroomUpdated
        else -> 0
    }
    override fun setKvUpdated(key: String, updated: Long) {
        when (key) {
            SyncRecord.TRAINING_LEVEL -> puzzlePrefs.edit().putLong(PuzzleViewModel.LEVEL_UPDATED_KEY, updated).apply()
            SyncRecord.PLAYROOM -> settings.setPlayroomUpdated(updated)
            SyncRecord.PUZZLE_GOAL, SyncRecord.PUZZLE_DAYS, SyncRecord.REPEAT_SET, SyncRecord.GAME_RATING, SyncRecord.RATING_DAYS ->
                puzzlePrefs.edit().putLong(key + TrainingPrefs.UPDATED, updated).apply()
        }
    }
    override fun tombstones() = account.tombstones
    override fun setTombstones(tombstones: Map<String, Long>) { account.tombstones = tombstones }
    override suspend fun bases() = db.syncBase().all().associate {
        "${it.kind}:${it.id}" to SyncRecord(it.kind, it.id, it.updated, it.deleted, it.data?.let(::JSONObject))
    }
    override suspend fun saveBase(record: SyncRecord) =
        db.syncBase().save(SyncBase(record.kind, record.id, record.updated, record.deleted, record.data?.toString()))
}

enum class SyncStatus { SIGNED_OUT, SIGNING_IN, IDLE, SYNCING }
/** Why the last action did not work; the account screen says it in the player's language. */
enum class AccountProblem { OFFLINE, SESSION_ENDED, SIGN_IN_FAILED, SIGN_IN_CANCELLED, WRONG_PASSWORD, TOO_MANY_ATTEMPTS, SERVER, NO_BROWSER }

data class AccountUi(
    val status: SyncStatus = SyncStatus.SIGNED_OUT,
    val username: String? = null,
    val lastSync: Long = 0,
    val problem: AccountProblem? = null,
    /** Deleting the account: the confirmation shows progress and then closes. */
    val deleting: Boolean = false,
    val deleted: Boolean = false
) {
    val signedIn: Boolean get() = username != null && status != SyncStatus.SIGNED_OUT && status != SyncStatus.SIGNING_IN
}

private fun problemOf(e: Throwable): AccountProblem = when (e) {
    is SyncError.Offline -> AccountProblem.OFFLINE
    is SyncError.SignedOut -> AccountProblem.SESSION_ENDED
    is SyncError.InvalidGrant -> AccountProblem.SIGN_IN_FAILED
    is SyncError.WrongPassword -> AccountProblem.WRONG_PASSWORD
    is SyncError.TooManyAttempts -> AccountProblem.TOO_MANY_ATTEMPTS
    else -> AccountProblem.SERVER
}

/**
 * Optional sign-in and sync for the whole app. Nothing here touches the network until the player
 * signs in. Sync runs in the background after changes and when the app starts; a failure is only
 * shown on the account screen and never stops play.
 */
class SyncManager internal constructor(
    private val account: AccountStore,
    internal val store: SyncStore,
    private val api: SyncApi,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = 5_000,
    private val playroomReset: () -> Unit = {}
) {
    private val engine = SyncEngine(api, store, account)
    private val lock = Mutex()
    private var scheduled: Job? = null
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AccountUi> = _state
    private val _changes = MutableSharedFlow<SyncChanges>(extraBufferCapacity = 16)
    /** What a sync changed here; open screens reload. */
    val changes: SharedFlow<SyncChanges> = _changes

    private fun initialState() = if (account.signedIn) AccountUi(SyncStatus.IDLE, account.username, account.lastSync) else AccountUi()

    /** Remembers the sign-in and returns the website address to open in the browser. */
    fun startSignIn(): String {
        val pending = PendingSignIn(Pkce.newVerifier(), Pkce.newState(), now())
        account.pending = pending
        _state.value = AccountUi(SyncStatus.SIGNING_IN)
        return authorizeUrl(pending)
    }
    fun browserMissing() { account.pending = null; _state.value = AccountUi(problem = AccountProblem.NO_BROWSER) }

    /** The browser came back to the redirect address. */
    fun completeSignIn(uri: String, device: String): Job = scope.launch {
        val pending = account.pending
        account.pending = null
        when (val result = parseRedirect(uri, pending, now())) {
            RedirectResult.Denied -> { _state.value = initialState().copy(problem = AccountProblem.SIGN_IN_CANCELLED); return@launch }
            RedirectResult.Invalid -> { _state.value = initialState().copy(problem = AccountProblem.SIGN_IN_FAILED); return@launch }
            is RedirectResult.Code -> lock.withLock {
                _state.value = AccountUi(SyncStatus.SIGNING_IN)
                try {
                    val token = api.token(result.code, pending!!.verifier, device)
                    val name = api.username(token)
                    account.signIn(token, name)
                    // As on the website: the account's saved Playroom wins over this device's;
                    // with none saved, this device's goes up (keepLocalValuesIfAccountHasNone).
                    playroomReset()
                    _state.value = AccountUi(SyncStatus.SYNCING, name)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    Log.w("SquareChess", "Sign-in failed: ${e.message}")
                    account.signOut()
                    _state.value = AccountUi(problem = problemOf(e))
                    return@launch
                }
                syncLocked(first = true)
            }
        }
    }

    /** Sync now, from the account screen. */
    fun syncNow(): Job = scope.launch { lock.withLock { syncLocked() } }

    /** A game, puzzle or setting changed: sync a few seconds later, when something is waiting. */
    fun requestSync() {
        if (!account.signedIn) return
        scheduled?.cancel()
        scheduled = scope.launch {
            delay(debounceMs)
            lock.withLock { if (account.signedIn && SyncMerge.localChanges(store).isNotEmpty()) syncLocked() }
        }
    }

    /** The app came to the front: pull what other devices changed. */
    fun onAppStart() {
        if (!account.signedIn || now() - account.lastSync < MIN_START_INTERVAL_MS) return
        syncNow()
    }

    /** Records a game deleted here so the deletion reaches the account's other devices. */
    fun gameDeleted(id: String) {
        if (!account.signedIn) return
        store.setTombstones(store.tombstones() + (id to now()))
        requestSync()
    }

    private suspend fun syncLocked(first: Boolean = false) {
        if (!account.signedIn) return
        _state.value = _state.value.copy(status = SyncStatus.SYNCING, problem = null)
        try {
            var changes = engine.run()
            if (first && engine.keepLocalValuesIfAccountHasNone(now())) changes += engine.run()
            account.lastSync = now()
            _state.value = AccountUi(SyncStatus.IDLE, account.username, account.lastSync)
            if (changes.any) _changes.tryEmit(changes)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Log.w("SquareChess", "Sync failed: ${e.message}")
            if (e is SyncError.SignedOut) forget()
            _state.value = initialState().copy(problem = problemOf(e))
        }
    }

    /** Signs out here and revokes the token on the server; the data on this device stays. */
    fun signOut(): Job = scope.launch {
        val token = account.token
        scheduled?.cancel()
        lock.withLock { forget() }
        _state.value = AccountUi()
        // Best effort: an unreachable server still leaves this device signed out.
        if (token != null) runCatching { api.logout(token) }.onFailure { Log.w("SquareChess", "Logout not confirmed: ${it.message}") }
    }

    /** Deletes the account on the server (it asks for the password), then signs out here. */
    fun deleteAccount(password: String): Job = scope.launch {
        val token = account.token ?: return@launch
        _state.value = _state.value.copy(deleting = true, problem = null)
        try {
            api.deleteAccount(token, password)
            scheduled?.cancel()
            lock.withLock { forget() }
            _state.value = AccountUi(deleted = true)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is SyncError.SignedOut) { lock.withLock { forget() }; _state.value = AccountUi(problem = problemOf(e)) }
            else _state.value = _state.value.copy(deleting = false, problem = problemOf(e))
        }
    }

    /** A restored backup changed data here: open screens reload, as after a sync. */
    fun notifyChanges(changes: SyncChanges) { if (changes.any) _changes.tryEmit(changes) }

    fun clearProblem() { _state.value = _state.value.copy(problem = null, deleted = false) }

    private suspend fun forget() {
        account.signOut()
        clearBases()
    }
    internal var clearBases: suspend () -> Unit = {}

    companion object {
        private const val MIN_START_INTERVAL_MS = 60_000L
        @Volatile private var instance: SyncManager? = null

        fun get(context: Context): SyncManager = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        private fun create(context: Context): SyncManager {
            val db = appChessDatabase(context)
            val settings = SettingsStore(context)
            val puzzlePrefs = context.getSharedPreferences(PuzzleViewModel.PREFS, Context.MODE_PRIVATE)
            val account = AccountStore(context.getSharedPreferences("account", Context.MODE_PRIVATE))
            val manager = SyncManager(account, AppSyncStore(db, settings, puzzlePrefs, account), SyncApi(),
                CoroutineScope(SupervisorJob() + Dispatchers.IO), playroomReset = { settings.setPlayroomUpdated(0) })
            manager.clearBases = { db.syncBase().clear() }
            // Any saved game or puzzle result, and a new training level or Playroom choice, asks for a sync.
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("games", "puzzle_progress")) {
                override fun onInvalidated(tables: Set<String>) = manager.requestSync()
            })
            manager.prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "playroom.updated" || key == PuzzleViewModel.LEVEL_UPDATED_KEY || key?.endsWith(TrainingPrefs.UPDATED) == true) manager.requestSync()
            }.also {
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(it)
                puzzlePrefs.registerOnSharedPreferenceChangeListener(it)
            }
            return manager
        }

        /** Shown on the website's list of signed-in devices. */
        fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL).filter { !it.isNullOrBlank() }
            .joinToString(" ").take(60).ifBlank { "Android device" }
    }
    /** SharedPreferences holds listeners weakly; this keeps the one above alive. */
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
}
