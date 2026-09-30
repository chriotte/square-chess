package com.dataespresso.squarechess
import org.junit.Assert.*
import org.junit.Test
class ToolbarTest {
    @Test fun orientationDefaultsToHumanButExplicitChoiceWins() {
        val black=SavedGame("test",GameMode.COMPUTER.name,humanWhite=false)
        assertTrue(defaultFlipFor(black))
        assertFalse(defaultFlipFor(black.copy(orientationFlipped=false)))
        assertFalse(defaultFlipFor(black.copy(mode=GameMode.LOCAL_TWO_PLAYER.name)))
        assertTrue(defaultFlipFor(black.copy(mode=GameMode.LOCAL_TWO_PLAYER.name,orientationFlipped=true)))
    }
    @Test fun secondaryActionsCollapseBeforeReview() {
        assertEquals(ToolbarLayout(true,true,true),toolbarLayout(500f,1f))
        assertEquals(ToolbarLayout(true,false,false),toolbarLayout(360f,1f))
        assertEquals(ToolbarLayout(false,false,false),toolbarLayout(360f,2f))
    }
    @Test fun statusAccountsForModeThinkingAndResults() {
        val game=SavedGame("test",GameMode.COMPUTER.name)
        val state=GameUi(game=game)
        assertEquals(GameStatus.YOUR_MOVE,gameStatus(state))
        assertEquals(GameStatus.COMPUTER_THINKING,gameStatus(state.copy(busy=true)))
        assertEquals(GameStatus.GAME_OVER,gameStatus(state.copy(game=game.copy(result="1-0"))))
        assertEquals(GameStatus.WHITE_TO_MOVE,gameStatus(state.copy(game=game.copy(mode=GameMode.PHYSICAL_BOARD_RECORDING.name))))
        assertEquals(GameStatus.COMPUTER_TO_MOVE,gameStatus(state.copy(game=game.copy(humanWhite=false))))
    }
}
