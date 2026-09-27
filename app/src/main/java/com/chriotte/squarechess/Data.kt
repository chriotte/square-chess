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
    @ColumnInfo(defaultValue="''") val resultReason: String = ""
)
@Dao interface GameDao {
    @Query("SELECT * FROM games ORDER BY updated DESC") fun observeGames(): Flow<List<SavedGame>>
    @Query("SELECT * FROM games ORDER BY updated DESC LIMIT 1") suspend fun latest(): SavedGame?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(game: SavedGame)
}
@Database(entities=[SavedGame::class], version=2, exportSchema=true)
abstract class ChessDatabase : RoomDatabase() { abstract fun games(): GameDao }

val MIGRATION_1_2 = object: Migration(1,2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN orientationFlipped INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE games ADD COLUMN resultReason TEXT NOT NULL DEFAULT ''")
    }
}
fun openChessDatabase(context: Context, name: String="square-chess.db") =
    Room.databaseBuilder(context,ChessDatabase::class.java,name).addMigrations(MIGRATION_1_2).build()

fun defaultFlipFor(game: SavedGame?): Boolean = game?.orientationFlipped
    ?: (game?.mode==GameMode.COMPUTER.name && !game.humanWhite)
