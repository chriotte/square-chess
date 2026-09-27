package com.chriotte.squarechess

enum class ClockSide { WHITE, BLACK;
    fun other() = if(this==WHITE) BLACK else WHITE
}
enum class ClockPhase { READY, RUNNING, PAUSED, FLAGGED, FINISHED }
data class ClockConfig(val baseMs: Long, val incrementMs: Long, val delayMs: Long = 0) {
    init {
        require(baseMs in 1..86_400_000L)
        require(incrementMs in 0..3_600_000L)
        require(delayMs in 0..3_600_000L)
    }
}

data class ClockPreset(val label: String, val config: ClockConfig?)

val CLOCK_PRESETS = listOf(
    ClockPreset("Untimed", null),
    ClockPreset("1+0", ClockConfig(60_000, 0)),
    ClockPreset("3+0", ClockConfig(180_000, 0)),
    ClockPreset("3+2", ClockConfig(180_000, 2_000)),
    ClockPreset("5+0", ClockConfig(300_000, 0)),
    ClockPreset("5+3", ClockConfig(300_000, 3_000)),
    ClockPreset("10+0", ClockConfig(600_000, 0)),
    ClockPreset("10+5", ClockConfig(600_000, 5_000)),
    ClockPreset("15+10", ClockConfig(900_000, 10_000))
)

/** All event methods receive one monotonic timestamp. UI ticks never drive time. */
data class ClockState(
    val config: ClockConfig,
    val whiteMs: Long = config.baseMs,
    val blackMs: Long = config.baseMs,
    val active: ClockSide = ClockSide.WHITE,
    val phase: ClockPhase = ClockPhase.READY,
    val anchorMs: Long = 0,
    val interrupted: Boolean = false,
    val delayRemainingMs: Long = config.delayMs
) {
    init { require(delayRemainingMs in 0..config.delayMs) }

    fun settled(now: Long): ClockState {
        if(phase!=ClockPhase.RUNNING) return this
        val elapsed=(now-anchorMs).coerceAtLeast(0)
        val delayConsumed=minOf(delayRemainingMs,elapsed)
        val delayLeft=delayRemainingMs-delayConsumed
        val left=((if(active==ClockSide.WHITE) whiteMs else blackMs)-(elapsed-delayConsumed)).coerceAtLeast(0)
        return copy(
            whiteMs=if(active==ClockSide.WHITE) left else whiteMs,
            blackMs=if(active==ClockSide.BLACK) left else blackMs,
            anchorMs=now,
            phase=if(left==0L) ClockPhase.FLAGGED else ClockPhase.RUNNING,
            delayRemainingMs=delayLeft
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
            active=mover.other(), anchorMs=now, delayRemainingMs=config.delayMs
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

fun SavedGame.clockState(): ClockState? {
    val values = listOf(clockBaseMs, clockIncrementMs, clockWhiteMs, clockBlackMs, clockActive, clockPhase, clockInterrupted)
    if (values.all { it == null }) return null
    require(values.none { it == null }) { "Saved clock state is incomplete" }
    val config = ClockConfig(requireNotNull(clockBaseMs), requireNotNull(clockIncrementMs), clockDelayMs ?: 0)
    return ClockState(
        config = config,
        whiteMs = requireNotNull(clockWhiteMs),
        blackMs = requireNotNull(clockBlackMs),
        active = ClockSide.valueOf(requireNotNull(clockActive)),
        phase = ClockPhase.valueOf(requireNotNull(clockPhase)),
        interrupted = requireNotNull(clockInterrupted),
        delayRemainingMs = clockDelayRemainingMs ?: config.delayMs
    )
}

fun SavedGame.withClock(clock: ClockState?, now: Long): SavedGame {
    if (clock == null) return copy(
        clockBaseMs = null,
        clockIncrementMs = null,
        clockWhiteMs = null,
        clockBlackMs = null,
        clockActive = null,
        clockPhase = null,
        clockInterrupted = null,
        clockDelayMs = null,
        clockDelayRemainingMs = null
    )
    val settled = clock.settled(now)
    return copy(
        clockBaseMs = settled.config.baseMs,
        clockIncrementMs = settled.config.incrementMs,
        clockWhiteMs = settled.whiteMs,
        clockBlackMs = settled.blackMs,
        clockActive = settled.active.name,
        clockPhase = settled.phase.name,
        clockInterrupted = settled.interrupted,
        clockDelayMs = settled.config.delayMs,
        clockDelayRemainingMs = settled.delayRemainingMs
    )
}

fun clockText(milliseconds: Long): String {
    val seconds=(milliseconds.coerceAtLeast(0)+999)/1000
    return "%d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)
}

/** Wake only when the displayed whole second changes, including the final flag. */
fun clockTickDelayMs(remainingMs: Long): Long =
    if(remainingMs<=0) 1L else ((remainingMs-1)%1_000)+1
