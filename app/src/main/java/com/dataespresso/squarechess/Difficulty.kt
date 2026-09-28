package com.dataespresso.squarechess

/**
 * Fairy-Stockfish options for one difficulty level. The engine makes every
 * move choice itself; the app only sets its Skill Level and MultiPV options.
 *
 * Pinned Skill::pick_best uses weakness=120-2*skill. Skill -4 (weakness 128)
 * picks uniformly at random among the MultiPV lines, so fewer lines make it
 * stronger; below -4 it prefers worse lines (the rejected A-D profiles).
 * Skill -3 and higher reliably take free material in the tactical probes.
 */
data class EngineLevel(val level: Int, val skill: Int, val multiPv: Int, val description: String)

val ENGINE_LEVELS=listOf(
    EngineLevel(1,-4,8,"Beginner · often misses captures"),
    EngineLevel(2,-4,4,"Beginner · makes clear mistakes"),
    EngineLevel(3,-3,8,"Casual · takes free pieces"),
    EngineLevel(4,-1,8,"Casual · misses some tactics"),
    EngineLevel(5,0,8,"Improving player"),
    EngineLevel(6,3,8,"Intermediate"),
    EngineLevel(7,6,8,"Club player"),
    EngineLevel(8,10,8,"Strong"),
    EngineLevel(9,15,8,"Very strong"),
    // Skill 20 disables the handicap; one line gives the engine its full search.
    EngineLevel(10,20,1,"Full strength")
)
const val DEFAULT_LEVEL=3
const val ENGINE_MOVE_TIME_MS=500

fun engineLevel(level: Int)=ENGINE_LEVELS[level.coerceIn(1,ENGINE_LEVELS.size)-1]
fun difficultyLabel(level: Int)="Level ${level.coerceIn(1,ENGINE_LEVELS.size)}"
