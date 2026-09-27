package com.chriotte.squarechess

enum class ClockSide { WHITE, BLACK;
    fun other() = if(this==WHITE) BLACK else WHITE
}
enum class ClockPhase { READY, RUNNING, PAUSED, FLAGGED, FINISHED }
data class ClockConfig(val baseMs: Long, val incrementMs: Long) {
    init { require(baseMs in 1..86_400_000L); require(incrementMs in 0..3_600_000L) }
}

/** All event methods receive one monotonic timestamp. UI ticks never drive time. */
data class ClockState(
    val config: ClockConfig,
    val whiteMs: Long = config.baseMs,
    val blackMs: Long = config.baseMs,
    val active: ClockSide = ClockSide.WHITE,
    val phase: ClockPhase = ClockPhase.READY,
    val anchorMs: Long = 0,
    val interrupted: Boolean = false
) {
    fun settled(now: Long): ClockState {
        if(phase!=ClockPhase.RUNNING) return this
        val elapsed=(now-anchorMs).coerceAtLeast(0)
        val left=((if(active==ClockSide.WHITE) whiteMs else blackMs)-elapsed).coerceAtLeast(0)
        return copy(
            whiteMs=if(active==ClockSide.WHITE) left else whiteMs,
            blackMs=if(active==ClockSide.BLACK) left else blackMs,
            anchorMs=now,
            phase=if(left==0L) ClockPhase.FLAGGED else ClockPhase.RUNNING
        )
    }
    fun start(now: Long) = if(phase==ClockPhase.READY) copy(phase=ClockPhase.RUNNING,anchorMs=now) else this
    fun switch(mover: ClockSide, now: Long): ClockState {
        if(phase!=ClockPhase.RUNNING || active!=mover) return this
        val s=settled(now)
        if(s.phase==ClockPhase.FLAGGED) return s
        return s.copy(
            whiteMs=s.whiteMs + if(mover==ClockSide.WHITE) config.incrementMs else 0,
            blackMs=s.blackMs + if(mover==ClockSide.BLACK) config.incrementMs else 0,
            active=mover.other(), anchorMs=now
        )
    }
    fun pause(now: Long, interrupted: Boolean=false): ClockState {
        val s=settled(now)
        return if(s.phase==ClockPhase.RUNNING) s.copy(phase=ClockPhase.PAUSED,interrupted=interrupted) else s
    }
    fun resume(now: Long) = if(phase==ClockPhase.PAUSED) copy(phase=ClockPhase.RUNNING,anchorMs=now,interrupted=false) else this
    fun finish(now: Long): ClockState {
        val s=settled(now)
        return if(s.phase==ClockPhase.FLAGGED) s else s.copy(phase=ClockPhase.FINISHED)
    }
    fun restart() = ClockState(config,active=ClockSide.WHITE)
    /** Persist settled remaining values; never replay an old boot's monotonic anchor. */
    fun recoverySnapshot(now: Long) = pause(now,interrupted=true).copy(anchorMs=0)
}

fun clockText(milliseconds: Long): String {
    val seconds=(milliseconds.coerceAtLeast(0)+999)/1000
    return "%d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)
}
