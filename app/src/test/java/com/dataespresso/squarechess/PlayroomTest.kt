package com.dataespresso.squarechess

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

private fun game(
    moves: String, result: String = "*", reason: String = "", mode: GameMode = GameMode.COMPUTER, humanWhite: Boolean = true,
    level: Int = 3, fen: String = START_FEN, updated: Long = 1L, id: String = "g$updated",
    clockBase: Long? = null, whiteMs: Long? = null, blackMs: Long? = null, evaluations: String = ""
) = SavedGame(id = id, mode = mode.name, initialFen = fen, moves = moves, humanWhite = humanWhite, level = level,
    result = result, resultReason = reason, updated = updated, clockBaseMs = clockBase, clockWhiteMs = whiteMs,
    clockBlackMs = blackMs, evaluations = evaluations)

private const val SCHOLARS_MATE = "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7"
private const val FOOLS_MATE = "f2f3 e7e5 g2g4 d8h4"

private fun progress(id: String, result: AttemptResult, at: Long, hints: Int = 0) = PuzzleProgress(
    puzzleId = id, attempts = 1, solved = if (result == AttemptResult.ABANDONED) 0 else 1,
    firstTrySolved = if (result == AttemptResult.FIRST_TRY) 1 else 0, mistakes = 0, hints = hints,
    lastResult = result.name, lastAttemptAt = at, reviewStep = null, nextReviewAt = null)

class StickerTest {
    @Test fun nothingIsEarnedWithoutGamesOrPuzzles() {
        assertEquals(emptySet<Sticker>(), earnedStickers(emptyList(), emptyList(), emptyMap()))
    }

    @Test fun aQuickCheckmateAgainstTheComputer() {
        val earned = earnedStickers(listOf(game(SCHOLARS_MATE, "1-0", "Checkmate", level = 3)), emptyList(), null)
        assertTrue(Sticker.FIRST_GAME in earned)
        assertTrue(Sticker.FIRST_CHECKMATE in earned)
        assertTrue(Sticker.JUNGLE_HOP in earned)
        assertTrue(Sticker.LEVEL_2 in earned)
        assertFalse(Sticker.LEVEL_4 in earned)
        // The queen gave the mate, not a knight.
        assertFalse(Sticker.KNIGHT_RIDER in earned)
    }

    @Test fun losingToTheComputerEarnsNoWinsButARematchCounts() {
        val lost = game(FOOLS_MATE, "0-1", "Checkmate", updated = 1)
        assertFalse(Sticker.FIRST_CHECKMATE in earnedStickers(listOf(lost), emptyList(), null))
        assertFalse(Sticker.REMATCH in earnedStickers(listOf(lost), emptyList(), null))
        val next = game("e2e4", updated = 2)
        assertTrue(Sticker.REMATCH in earnedStickers(listOf(lost, next), emptyList(), null))
    }

    @Test fun eitherPlayerWinsAGameBetweenTwoPeople() {
        val smothered = game("g5f7", "1-0", "Checkmate", mode = GameMode.LOCAL_TWO_PLAYER, fen = "6rk/6pp/8/6N1/8/8/8/6K1 w - - 0 1")
        val earned = earnedStickers(listOf(smothered), emptyList(), null)
        assertTrue(Sticker.KNIGHT_RIDER in earned)
        assertTrue(Sticker.FIRST_CHECKMATE in earned)
    }

    @Test fun promotionForkAndCastlingCountInUnfinishedGames() {
        val promotion = game("a7a8q", fen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1", updated = 1)
        val fork = game("e4f6", fen = "4k3/8/8/3q4/4N3/8/8/4K3 w - - 0 1", updated = 2)
        val earned = earnedStickers(listOf(promotion, fork), emptyList(), null)
        assertTrue(Sticker.PROMOTION in earned)
        assertTrue(Sticker.ROYAL_FORK in earned)
        assertFalse(Sticker.ROYAL_COURT in earned)
        val castles = (1..5).map { game("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 e1g1", updated = it.toLong()) }
        assertTrue(Sticker.SAFE_KING in earnedStickers(castles, emptyList(), null))
        assertFalse(Sticker.SAFE_KING in earnedStickers(castles.take(4), emptyList(), null))
    }

    @Test fun theComputersMovesNeverCountForThePlayer() {
        // Black (the computer) promotes; the human plays White.
        val computerPromotes = game("h1g1 a2a1q", fen = "4k3/8/8/8/8/8/p7/4K2R w - - 0 1", humanWhite = true)
        assertFalse(Sticker.PROMOTION in earnedStickers(listOf(computerPromotes), emptyList(), null))
    }

    @Test fun stalemateClockAndComeback() {
        val stalemate = game("", "1/2-1/2", "Stalemate", fen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", updated = 1)
        val onTime = game(SCHOLARS_MATE, "1-0", "Checkmate", clockBase = 180_000, whiteMs = 5_000, blackMs = 90_000, updated = 2)
        val comeback = game(SCHOLARS_MATE, "1-0", "Checkmate", evaluations = "2=c-350/ 4=c-100/", updated = 3)
        val earned = earnedStickers(listOf(stalemate, onTime, comeback), emptyList(), null)
        assertTrue(Sticker.STALEMATE in earned)
        assertTrue(Sticker.COOL_HEAD in earned)
        assertTrue(Sticker.ICE_COLD in earned)
        assertTrue(Sticker.MAGIC_PAINTBRUSH in earned)
        val plenty = game(SCHOLARS_MATE, "1-0", "Checkmate", clockBase = 180_000, whiteMs = 60_000)
        assertFalse(Sticker.ICE_COLD in earnedStickers(listOf(plenty), emptyList(), null))
    }

    @Test fun theKingMarchesIntoTheOtherHalf() {
        val march = game("d3d4 a8b8 d4d5", "1-0", "Result recorded by the players", mode = GameMode.LOCAL_TWO_PLAYER,
            fen = "k7/8/8/8/8/3K4/8/7R w - - 0 1")
        assertTrue(Sticker.KING_OF_BOLSENA in earnedStickers(listOf(march), emptyList(), null))
        val short = game("d3d4", "1-0", "Result recorded by the players", mode = GameMode.LOCAL_TWO_PLAYER, fen = "k7/8/8/8/8/3K4/8/7R w - - 0 1")
        assertFalse(Sticker.KING_OF_BOLSENA in earnedStickers(listOf(short), emptyList(), null))
    }

    @Test fun levelsAndTheClimber() {
        val wins = (1..5).map { game(SCHOLARS_MATE, "1-0", "Checkmate", level = it, updated = it.toLong()) }
        val earned = earnedStickers(wins, emptyList(), null)
        assertTrue(Sticker.CLIMBER in earned)
        assertTrue(Sticker.PANCAKE_CHEF in earned)
        assertTrue(Sticker.LEVEL_4 in earned)
        assertFalse(Sticker.LEVEL_6 in earned)
        assertFalse(Sticker.CLIMBER in earnedStickers(wins.drop(1), emptyList(), null))
    }

    @Test fun aDamagedGameIsSkipped() {
        val broken = game("e2e5", "1-0", "Checkmate")
        val good = game(SCHOLARS_MATE, "1-0", "Checkmate", updated = 2)
        assertEquals(earnedStickers(listOf(good), emptyList(), null), earnedStickers(listOf(broken, good), emptyList(), null))
    }

    @Test fun puzzleStickers() {
        val firsts = (1..3).map { progress("p$it", AttemptResult.FIRST_TRY, it.toLong()) }
        assertTrue(Sticker.ONE_TWO_THREE in earnedStickers(emptyList(), firsts, null))
        val broken = listOf(firsts[0], progress("x", AttemptResult.WITH_HELP, 2), firsts[1].copy(lastAttemptAt = 3), firsts[2].copy(lastAttemptAt = 4))
        assertFalse(Sticker.ONE_TWO_THREE in earnedStickers(emptyList(), broken, null))

        val ten = (1..10).map { progress("q$it", AttemptResult.WITH_HELP, it.toLong(), hints = 1) }
        val earned = earnedStickers(emptyList(), ten, emptyMap())
        assertTrue(Sticker.PUZZLES_10 in earned)
        assertFalse(Sticker.SPELLBOUND in earned)

        val themes = mapOf("q1" to setOf("mate", "mateIn3"))
        assertTrue(Sticker.FAR_SIGHT in earnedStickers(emptyList(), ten, themes))
        // Without the pack loaded, the theme stickers wait.
        assertFalse(Sticker.FAR_SIGHT in earnedStickers(emptyList(), ten, null))
        val mates = (1..5).associate { "q$it" to setOf("mateIn2") }
        assertTrue(Sticker.HOT_STREAK in earnedStickers(emptyList(), ten, mates))
    }

    @Test fun recentGamesAreTheLastFinishedOnes() {
        val games = listOf(
            game(SCHOLARS_MATE, "1-0", updated = 1), game("e2e4", "*", updated = 9), game(FOOLS_MATE, "0-1", updated = 5),
            game(SCHOLARS_MATE, "1-0", updated = 3), game(SCHOLARS_MATE, "1-0", updated = 4), game("", "1/2-1/2", updated = 8))
        assertEquals(listOf(5L, 4L, 3L), recentFinishedGames(games).map { it.updated })
    }
}

class OutfitTest {
    @Test fun outfitsSurviveSaving() {
        val outfit = Outfit().with(Slot.HEAD, Item.ADMIRAL_HAT).with(Slot.HAND, Item.KNIGHT_SWORD).with(Slot.PET, Item.LAVA_MONSTER)
        assertEquals(outfit, Outfit.decode(outfit.encode()))
        assertEquals(Outfit(), Outfit.decode(null))
        assertEquals(Outfit(), Outfit.decode("NOT_AN_ITEM,,"))
    }

    @Test fun oneItemPerSlot() {
        val outfit = Outfit().with(Slot.HEAD, Item.CROWN).with(Slot.HEAD, Item.WIZARD_HAT)
        assertEquals(Item.WIZARD_HAT, outfit[Slot.HEAD])
        assertNull(outfit.with(Slot.HEAD, null)[Slot.HEAD])
    }

    @Test fun lockedItemsAreTakenOff() {
        val outfit = Outfit().with(Slot.HEAD, Item.ADMIRAL_HAT).with(Slot.PET, Item.PET_PAWN)
        val allowed = outfit.allowed(setOf(Sticker.FIRST_GAME))
        assertNull(allowed[Slot.HEAD])
        assertEquals(Item.PET_PAWN, allowed[Slot.PET])
    }

    @Test fun everyItemHasASticker() {
        Item.entries.forEach { assertNotNull("$it", it.sticker) }
        assertEquals(Sticker.entries.toSet(), decodeStickers(encodeStickers(Sticker.entries.toSet())))
    }

    @Test fun littleRobotHandsOutPancakes() {
        assertEquals(BuddyPose.PANCAKES, buddyPose(Buddy.ROBOT, BuddyMoment.WIN))
        assertEquals(BuddyPose.PANCAKES, buddyPose(Buddy.ROBOT, BuddyMoment.RIGHT))
        assertEquals(BuddyPose.CHEER, buddyPose(Buddy.SLOTH, BuddyMoment.RIGHT))
        Buddy.entries.forEach { assertEquals(BuddyPose.CRY, buddyPose(it, BuddyMoment.WRONG)) }
    }
}

class WorldPaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f; val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test fun textIsReadableOnEveryWorldsCards() {
        World.entries.forEach { world ->
            val p = worldPalette(world)
            val card = p.card.compositeOver(p.background)
            assertTrue("$world text", contrast(p.text, card) >= 4.5f)
            assertTrue("$world muted", contrast(p.muted, card) >= 4.5f)
            assertTrue("$world accent", contrast(p.accent, card) >= 3f)
            assertTrue("$world button", contrast(p.onAccent, p.accent) >= 4.5f)
        }
    }

    @Test fun boardSquaresAreDistinct() {
        World.entries.forEach { world -> assertTrue("$world", contrast(world.light, world.dark) >= 2f) }
    }
}

class GameRemarkTest {
    private fun ui(moves: String, humanWhite: Boolean = true, result: String = "*", mode: GameMode = GameMode.COMPUTER, evals: Map<Int, Eval> = emptyMap()): GameUi {
        val list = moves.split(' ').filter(String::isNotBlank)
        return GameUi(game = game(moves, result, mode = mode, humanWhite = humanWhite), position = ChessPosition(START_FEN, list), evals = evals)
    }

    @Test fun onlyInComputerGames() {
        assertNull(gameRemark(ui("e2e4", mode = GameMode.LOCAL_TWO_PLAYER)))
        assertEquals(GameRemark.YOUR_MOVE, gameRemark(ui("")))
        assertEquals(GameRemark.THINKING, gameRemark(ui("", humanWhite = false)))
    }

    @Test fun thePlayersMoveThenTheComputersReply() {
        assertEquals(GameRemark.THINKING, gameRemark(ui("e2e4")))
        assertEquals(GameRemark.COMPUTER_MOVED, gameRemark(ui("e2e4 e7e5")))
        // 3. Bxf7+ is a capture with check: check wins.
        assertEquals(GameRemark.CHECK_GIVEN, gameRemark(ui("e2e4 e7e5 f1c4 b8c6 c4f7")))
        assertEquals(GameRemark.CAPTURE, gameRemark(ui("e2e4 d7d5 e4d5")))
    }

    @Test fun checkAgainstThePlayer() {
        assertEquals(GameRemark.IN_CHECK, gameRemark(ui("f2f3 e7e6 e2e3 d8h4")))
    }

    @Test fun theEvaluationPraisesOrWarns() {
        val good = mapOf(0 to Eval(20), 1 to Eval(30))
        assertEquals(GameRemark.NICE_MOVE, gameRemark(ui("e2e4", evals = good)))
        val bad = mapOf(0 to Eval(20), 1 to Eval(-600))
        assertEquals(GameRemark.MISTAKE, gameRemark(ui("g2g4", evals = bad)))
    }

    @Test fun theEnd() {
        assertEquals(GameRemark.WON, gameRemark(ui(SCHOLARS_MATE, result = "1-0")))
        assertEquals(GameRemark.GOOD_GAME, gameRemark(ui(FOOLS_MATE, result = "0-1")))
    }
}
