package com.chriotte.squarechess
import org.junit.Assert.*
import org.junit.Test
class ExperimentalProfileTest {
    @Test fun profilesPreserveSavedIdsAndAddNonnegativeCalibrationCandidates() {
        assertEquals(listOf(-18,-14,-10,-6,0,4,8,12),FAIRY_PROFILES.map {it.skill})
        assertEquals(listOf("A","B","C","D","E","F","G","H"),FAIRY_PROFILES.map {it.label})
        assertEquals(listOf(1,2,3,4,6,7,8,9),FAIRY_PROFILES.map {it.id})
    }
}
