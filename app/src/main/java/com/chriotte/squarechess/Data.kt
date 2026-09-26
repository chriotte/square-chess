package com.chriotte.squarechess

import androidx.room.*
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
    val updated: Long = System.currentTimeMillis()
)
@Dao interface GameDao {
    @Query("SELECT * FROM games ORDER BY updated DESC") fun observeGames(): Flow<List<SavedGame>>
    @Query("SELECT * FROM games ORDER BY updated DESC LIMIT 1") suspend fun latest(): SavedGame?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(game: SavedGame)
}
@Database(entities=[SavedGame::class], version=1, exportSchema=false)
abstract class ChessDatabase : RoomDatabase() { abstract fun games(): GameDao }
