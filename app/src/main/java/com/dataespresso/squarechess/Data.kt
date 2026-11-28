package com.dataespresso.squarechess

import androidx.room.*
import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="games")
data class SavedGame(
    @PrimaryKey val id: String,
    val mode: String,
    val initialFen: String = START_FEN,
    val moves: String = "",
    val white: String = "White",
    val black: String = "Black",
    val humanWhite: Boolean = true,
    val level: Int = 4,
    val result: String = "*",
    val updated: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue="NULL") val orientationFlipped: Boolean? = null,
    @ColumnInfo(defaultValue="''") val resultReason: String = "",
    @ColumnInfo(defaultValue="NULL") val clockBaseMs: Long? = null,
    @ColumnInfo(defaultValue="NULL") val clockIncrementMs: Long? = null,
    @ColumnInfo(defaultValue="NULL") val clockWhiteMs: Long? = null,
    @ColumnInfo(defaultValue="NULL") val clockBlackMs: Long? = null,
    @ColumnInfo(defaultValue="NULL") val clockActive: String? = null,
    @ColumnInfo(defaultValue="NULL") val clockPhase: String? = null,
    @ColumnInfo(defaultValue="NULL") val clockInterrupted: Boolean? = null,
    @ColumnInfo(defaultValue="NULL") val clockDelayMs: Long? = null,
    @ColumnInfo(defaultValue="NULL") val clockDelayRemainingMs: Long? = null,
    /** Chosen when a computer game starts; an app setting, not PGN metadata. */
    @ColumnInfo(defaultValue="0") val hintsEnabled: Boolean = false,
    /** Chosen when a game starts: the engine rates each move and shows who is ahead. */
    @ColumnInfo(defaultValue="0") val evaluationEnabled: Boolean = false,
    /** Engine evaluations by position; see encodeEvaluations in Evaluation.kt. */
    @ColumnInfo(defaultValue="''") val evaluations: String = "",
    /** Set when a hint was used or a move taken back: the game no longer counts for the game rating. */
    @ColumnInfo(defaultValue="0") val unrated: Boolean = false,
    /** What the game did to the game rating, once it was rated (see Rating.kt); all null before. */
    @ColumnInfo(defaultValue="NULL") val ratingBefore: Int? = null,
    @ColumnInfo(defaultValue="NULL") val ratingAfter: Int? = null,
    @ColumnInfo(defaultValue="NULL") val ratingChange: Int? = null,
    @ColumnInfo(defaultValue="NULL") val ratingOpponent: Int? = null,
    @ColumnInfo(defaultValue="NULL") val ratingProvisional: Boolean? = null
) {
    val rating: GameRatingChange? get() =
        if(ratingBefore==null || ratingAfter==null || ratingChange==null || ratingOpponent==null) null
        else GameRatingChange(ratingBefore,ratingAfter,ratingChange,ratingOpponent,ratingProvisional ?: false)
    fun withRating(r: GameRatingChange?) = copy(ratingBefore=r?.before,ratingAfter=r?.after,ratingChange=r?.change,
        ratingOpponent=r?.opponent,ratingProvisional=r?.provisional)
}
@Dao interface GameDao {
    @Query("SELECT * FROM games ORDER BY updated DESC") fun observeGames(): Flow<List<SavedGame>>
    @Query("SELECT * FROM games ORDER BY updated DESC LIMIT 1") suspend fun latest(): SavedGame?
    @Query("SELECT * FROM games ORDER BY updated ASC") suspend fun allOldestFirst(): List<SavedGame>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(game: SavedGame)
    /** Changes only the evaluations, so a running clock's saved state is not touched. */
    @Query("UPDATE games SET evaluations = :evaluations WHERE id = :id") suspend fun setEvaluations(id: String, evaluations: String)
    @Query("DELETE FROM games WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM games WHERE id = :id") suspend fun get(id: String): SavedGame?
    /** Each game's last change, for sync; cheaper than loading every game. */
    @Query("SELECT id, updated FROM games") suspend fun stamps(): List<RecordStamp>
}
data class RecordStamp(val id: String, val updated: Long)
/**
 * The server's last copy of each synced record (see SyncEngine.kt), only while signed in. [data]
 * is the record's JSON as the server sent it, so fields this version does not know are kept.
 */
@Entity(tableName="sync_base", primaryKeys=["kind","id"])
data class SyncBase(val kind: String, val id: String, val updated: Long, val deleted: Boolean, val data: String?)
@Dao interface SyncBaseDao {
    @Query("SELECT * FROM sync_base") suspend fun all(): List<SyncBase>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(base: SyncBase)
    @Query("DELETE FROM sync_base") suspend fun clear()
}
@Database(entities=[SavedGame::class, PuzzleProgress::class, SyncBase::class], version=9, exportSchema=true)
abstract class ChessDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun puzzleProgress(): PuzzleProgressDao
    abstract fun syncBase(): SyncBaseDao
}

val MIGRATION_1_2 = object: Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN orientationFlipped INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN resultReason TEXT NOT NULL DEFAULT ''")
    }
}
val MIGRATION_2_3 = object: Migration(2,3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN clockBaseMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockIncrementMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockWhiteMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockBlackMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockActive TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockPhase TEXT DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockInterrupted INTEGER DEFAULT NULL")
    }
}
val MIGRATION_3_4 = object: Migration(3,4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN clockDelayMs INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN clockDelayRemainingMs INTEGER DEFAULT NULL")
    }
}
val MIGRATION_4_5 = object: Migration(4,5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN hintsEnabled INTEGER NOT NULL DEFAULT 0")
    }
}
val MIGRATION_5_6 = object: Migration(5,6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN evaluationEnabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE games ADD COLUMN evaluations TEXT NOT NULL DEFAULT ''")
    }
}
/** Puzzle progress lives in its own table; the games table is not touched. */
val MIGRATION_6_7 = object: Migration(6,7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `puzzle_progress` (`puzzleId` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `solved` INTEGER NOT NULL, `firstTrySolved` INTEGER NOT NULL, `mistakes` INTEGER NOT NULL, `hints` INTEGER NOT NULL, `lastResult` TEXT NOT NULL, `lastAttemptAt` INTEGER NOT NULL, `reviewStep` INTEGER, `nextReviewAt` INTEGER, PRIMARY KEY(`puzzleId`))")
    }
}
/** Ratings: whether a game still counts, and what a rated game did to the game rating. */
val MIGRATION_7_8 = object: Migration(7,8) {
    override fun migrate(db: SupportSQLiteDatabase) = addRatingColumns(db)
}
/** Adds the rating columns that are missing, so it can also repair a database that has some of them. */
fun addRatingColumns(db: SupportSQLiteDatabase) {
    val existing=HashSet<String>()
    db.query("PRAGMA table_info(`games`)").use { c -> val name=c.getColumnIndex("name"); while(c.moveToNext()) existing+=c.getString(name) }
    listOf("unrated" to "INTEGER NOT NULL DEFAULT 0","ratingBefore" to "INTEGER DEFAULT NULL","ratingAfter" to "INTEGER DEFAULT NULL",
        "ratingChange" to "INTEGER DEFAULT NULL","ratingOpponent" to "INTEGER DEFAULT NULL","ratingProvisional" to "INTEGER DEFAULT NULL")
        .filter { it.first !in existing }.forEach { (name,type) -> db.execSQL("ALTER TABLE games ADD COLUMN `$name` $type") }
}
/**
 * Sign-in and sync keep the server's copies in their own table. Also adds any rating column that is
 * missing: an early test build of sign-in used version 8 for this table alone.
 */
val MIGRATION_8_9 = object: Migration(8,9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_base` (`kind` TEXT NOT NULL, `id` TEXT NOT NULL, `updated` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `data` TEXT, PRIMARY KEY(`kind`, `id`))")
        addRatingColumns(db)
    }
}
fun openChessDatabase(context: Context, name: String="square-chess.db") =
    Room.databaseBuilder(context,ChessDatabase::class.java,name)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()

/**
 * The app's one database, open for the life of the process. View models must not close it: a save
 * started just before the screen closes can still be running (seen on a slow Android 8 device).
 */
@Volatile private var appDatabase: ChessDatabase? = null
fun appChessDatabase(context: Context): ChessDatabase = appDatabase ?: synchronized(ChessDatabase::class) {
    appDatabase ?: openChessDatabase(context.applicationContext).also { appDatabase=it }
}

fun defaultFlipFor(game: SavedGame?): Boolean = game?.orientationFlipped
    ?: (game?.mode==GameMode.COMPUTER.name && !game.humanWhite)
