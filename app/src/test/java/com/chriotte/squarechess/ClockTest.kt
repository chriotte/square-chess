package com.chriotte.squarechess

import org.junit.Assert.*
import org.junit.Test

class ClockTest {
    @Test fun displaySleepsUntilNextWholeSecondWithoutDelayingFlagfall() {
        assertEquals(1_000L,clockTickDelayMs(180_000))
        assertEquals(350L,clockTickDelayMs(179_350))
        assertEquals(1L,clockTickDelayMs(1))
    }
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
    @Test fun flagAtZeroPrecedesMoveIncrement() {
        val expired=ClockState(ClockConfig(1_000,3_000)).start(50).switch(ClockSide.WHITE,1_050)
        assertEquals(ClockPhase.FLAGGED,expired.phase)
        assertEquals(0L,expired.whiteMs)
        assertEquals(1_000L,expired.blackMs)
        assertEquals(ClockSide.WHITE,expired.active)
    }
    @Test fun pauseAndResumeDoNotAddIncrementOrChargePause() {
        val paused=ClockState(config).start(100).pause(10_100)
        assertEquals(290_000L,paused.whiteMs)
        assertEquals(paused,paused.settled(800_000))
        val resumed=paused.resume(900_000).settled(901_000)
        assertEquals(289_000L,resumed.whiteMs)
        assertEquals(ClockSide.WHITE,resumed.active)
    }
    @Test fun delayIsConsumedBeforeClockTimeAndResetsForTheNextTurn() {
        val config=ClockConfig(10_000,0,3_000)
        val afterTwoSeconds=ClockState(config).start(0).settled(2_000)
        assertEquals(10_000L,afterTwoSeconds.whiteMs)
        assertEquals(1_000L,afterTwoSeconds.delayRemainingMs)

        val afterDelay=afterTwoSeconds.settled(3_000)
        assertEquals(10_000L,afterDelay.whiteMs)
        assertEquals(0L,afterDelay.delayRemainingMs)
        val afterOneClockSecond=afterDelay.settled(4_000)
        assertEquals(9_000L,afterOneClockSecond.whiteMs)

        val blackTurn=afterOneClockSecond.switch(ClockSide.WHITE,4_000)
        assertEquals(3_000L,blackTurn.delayRemainingMs)
    }
    @Test fun pausingPreservesTheUnusedDelay() {
        val config=ClockConfig(10_000,0,3_000)
        val paused=ClockState(config).start(0).pause(1_000)
        assertEquals(10_000L,paused.whiteMs)
        assertEquals(2_000L,paused.delayRemainingMs)
        val resumed=paused.resume(5_000).settled(6_000)
        assertEquals(10_000L,resumed.whiteMs)
        assertEquals(1_000L,resumed.delayRemainingMs)
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
    @Test fun configuredGamesPersistRemainingTimeButRecoverPaused() {
        val now=50_000L
        val running=ClockState(config).start(10_000).switch(ClockSide.WHITE,20_000)
        val saved=SavedGame("clock-save",GameMode.LOCAL_TWO_PLAYER.name).withClock(running,now)
        val recovered=saved.clockState()!!
        assertEquals(ClockSide.BLACK,recovered.active)
        assertEquals(ClockPhase.RUNNING,recovered.phase)
        assertEquals(293_000L,recovered.whiteMs)
        val paused=recovered.recoverySnapshot(0)
        assertEquals(ClockPhase.PAUSED,paused.phase)
        assertTrue(paused.interrupted)
        assertEquals(ClockSide.BLACK,paused.active)
    }
    @Test fun recoverySnapshotCarriesCheckpointedTimeAndNeverChargesDowntime() {
        val running=ClockState(config).start(10_000)
        val saved=SavedGame("checkpoint","LOCAL_TWO_PLAYER").withClock(running,10_000)
        val checkpoint=saved.withClock(running.settled(15_000),15_000)
        assertEquals(295_000L,checkpoint.clockWhiteMs)
        val recovered=checkpoint.clockState()!!.recoverySnapshot(0)
        assertEquals(ClockPhase.PAUSED,recovered.phase)
        assertTrue(recovered.interrupted)
        assertEquals(295_000L,recovered.whiteMs)
        assertEquals(295_000L,recovered.settled(900_000).whiteMs)
    }
    @Test fun clockPersistenceRetainsConfiguredAndRemainingDelay() {
        val config=ClockConfig(300_000,2_000,5_000)
        val current=ClockState(config).start(10_000).settled(12_000)
        val saved=SavedGame("delay-save",GameMode.LOCAL_TWO_PLAYER.name).withClock(current,12_000)
        val restored=saved.clockState()!!
        assertEquals(5_000L,saved.clockDelayMs)
        assertEquals(3_000L,saved.clockDelayRemainingMs)
        assertEquals(config,restored.config)
        assertEquals(3_000L,restored.delayRemainingMs)
    }
    @Test fun untimedPresetLeavesGameClockDisabled() {
        val untimed=CLOCK_PRESETS.single { it.label=="Untimed" }
        assertNull(untimed.config)
        assertTrue(CLOCK_PRESETS.any { it.label=="15+10" && it.config==ClockConfig(900_000,10_000) })
    }
}
