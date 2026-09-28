package com.chriotte.squarechess
import org.junit.Assert.*
import org.junit.Test
class DifficultyTest {
    @Test fun tenLevelsInIncreasingStrength() {
        assertEquals((1..10).toList(),ENGINE_LEVELS.map { it.level })
        // Same skill with fewer candidate lines is stronger, so order by (skill, -multiPv).
        val order=ENGINE_LEVELS.map { it.skill*100-it.multiPv }
        assertEquals(order.sorted(),order)
        assertEquals(order.size,order.toSet().size)
    }
    @Test fun neverUsesSkillsThatPreferWorseMoves() {
        // Below -4 the pinned formula favours worse lines (the rejected A-D profiles).
        assertTrue(ENGINE_LEVELS.all { it.skill>=-4 && it.skill<=20 })
        assertTrue(ENGINE_LEVELS.all { it.multiPv in 1..8 })
    }
    @Test fun outOfRangeSavedLevelsAreClamped() {
        assertEquals(1,engineLevel(0).level)
        assertEquals(10,engineLevel(14).level)
        assertEquals("Level 10",difficultyLabel(14))
    }
}
