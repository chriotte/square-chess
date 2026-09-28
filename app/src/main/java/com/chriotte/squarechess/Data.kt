package com.chriotte.squarechess

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
    @ColumnInfo(defaultValue="NULL") val clockDelayRemainingMs: Long? = null
)
@Dao interface GameDao {
    @Query("SELECT * FROM games ORDER BY updated DESC") fun observeGames(): Flow<List<SavedGame>>
    @Query("SELECT * FROM games ORDER BY updated DESC LIMIT 1") suspend fun latest(): SavedGame?
    @Query("SELECT * FROM games ORDER BY updated ASC") suspend fun allOldestFirst(): List<SavedGame>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(game: SavedGame)
    @Query("DELETE FROM games WHERE id = :id") suspend fun delete(id: String)
}
@Database(entities=[SavedGame::class], version=4, exportSchema=true)
abstract class ChessDatabase : RoomDatabase() { abstract fun games(): GameDao }

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
fun openChessDatabase(context: Context, name: String="square-chess.db") =
    Room.databaseBuilder(context,ChessDatabase::class.java,name)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

fun defaultFlipFor(game: SavedGame?): Boolean = game?.orientationFlipped
    ?: (game?.mode==GameMode.COMPUTER.name && !game.humanWhite)
