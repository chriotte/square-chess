package com.chriotte.squarechess

import org.junit.Assert.*
import org.junit.Test

class ClockTest {
    private val config=ClockConfig(300_000,3_000)
    @Test fun fivePlusThreeSwitchesExactlyOnce() {
        val running=ClockState(config).start(100)
        val switched=running.switch(ClockSide.WHITE,10_100)
        assertEquals(293_000L,switched.whiteMs)
        assertEquals(300_000L,switched.blackMs)
        assertEquals(ClockSide.BLACK,switched.active)
        assertEquals(switched,switched.switch(ClockSide.WHITE,10_101))
    }
    @Test fun inactivePressAndReadyTimeDoNothing() {
        val ready=ClockState(config)
        assertEquals(ready,ready.settled(900_000))
        val running=ready.start(10)
        assertEquals(running,running.switch(ClockSide.BLACK,100))
    }
    @Test fun expiredClockCannotBeRescuedByIncrement() {
        val expired=ClockState(ClockConfig(1_000,3_000)).start(0).switch(ClockSide.WHITE,1_001)
        assertEquals(ClockPhase.FLAGGED,expired.phase)
        assertEquals(0L,expired.whiteMs)
        assertEquals(1_000L,expired.blackMs)
        assertEquals(expired,expired.resume(9_000))
    }
    @Test fun pauseAndResumeDoNotAddIncrementOrChargePause() {
        val paused=ClockState(config).start(100).pause(10_100)
        assertEquals(290_000L,paused.whiteMs)
        assertEquals(paused,paused.settled(800_000))
        val resumed=paused.resume(900_000).settled(901_000)
        assertEquals(289_000L,resumed.whiteMs)
        assertEquals(ClockSide.WHITE,resumed.active)
    }
    @Test fun presentationFrequencyDoesNotChangeClock() {
        val running=ClockState(config).start(12)
        repeat(10_000) { running.settled(it.toLong()) }
        assertEquals(290_000L,running.settled(10_012).whiteMs)
        assertEquals(running.settled(10_012),running.settled(1_012).settled(10_012))
    }
    @Test fun recoveryAcrossRebootRequiresExplicitResume() {
        val restored=ClockState(config).start(80_000).recoverySnapshot(90_000)
        assertEquals(ClockPhase.PAUSED,restored.phase)
        assertTrue(restored.interrupted)
        assertEquals(290_000L,restored.settled(4).whiteMs)
        assertEquals(289_000L,restored.resume(4).settled(1_004).whiteMs)
    }
    @Test fun terminalStateFreezesBothSides() {
        val finished=ClockState(config).start(0).finish(1_000)
        assertEquals(ClockPhase.FINISHED,finished.phase)
        assertEquals(finished,finished.switch(ClockSide.WHITE,20_000))
        assertEquals(finished,finished.settled(90_000))
    }
    @Test fun controllersCanOwnIndependentStates() {
        val integrated=ClockState(config).start(0)
        val standalone=ClockState(config).start(10)
        standalone.switch(ClockSide.WHITE,1_000)
        assertEquals(300_000L,integrated.whiteMs)
    }
}
