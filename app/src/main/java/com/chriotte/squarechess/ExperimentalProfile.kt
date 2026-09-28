package com.chriotte.squarechess

data class ExperimentalProfile(val id: Int,val label: String,val skill: Int)
val FAIRY_PROFILES=listOf(
    ExperimentalProfile(1,"A",-18),ExperimentalProfile(2,"B",-14),
    ExperimentalProfile(3,"C",-10),ExperimentalProfile(4,"D",-6),
    // Preserve old save IDs; 5 remains the test-only full-strength control.
    ExperimentalProfile(6,"E",0),ExperimentalProfile(7,"F",4),
    ExperimentalProfile(8,"G",8),ExperimentalProfile(9,"H",12)
)
fun difficultyLabel(level: Int) = if(BuildConfig.FAIRY_ENGINE)
    "Profile ${FAIRY_PROFILES.firstOrNull { it.id==level }?.label ?: "?"}" else "Level $level"
