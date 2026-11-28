package com.dataespresso.squarechess

import androidx.annotation.StringRes

/* Strings for the course and single themes, named by id. Explicit maps, so resource shrinking keeps them. */

@StringRes fun chapterName(id: String): Int = when(id) {
    "mate1" -> R.string.chapter_mate1
    "mate1b" -> R.string.chapter_mate1b
    "backrank" -> R.string.chapter_backrank
    "patterns" -> R.string.chapter_patterns
    "mate2" -> R.string.chapter_mate2
    "mate2b" -> R.string.chapter_mate2b
    "mate3" -> R.string.chapter_mate3
    "hanging" -> R.string.chapter_hanging
    "fork" -> R.string.chapter_fork
    "forkb" -> R.string.chapter_forkb
    "pin" -> R.string.chapter_pin
    "skewer" -> R.string.chapter_skewer
    "discovered" -> R.string.chapter_discovered
    "trapped" -> R.string.chapter_trapped
    "deflection" -> R.string.chapter_deflection
    "attraction" -> R.string.chapter_attraction
    "sacrifice" -> R.string.chapter_sacrifice
    "clearance" -> R.string.chapter_clearance
    "intermezzo" -> R.string.chapter_intermezzo
    "quiet" -> R.string.chapter_quiet
    "defence" -> R.string.chapter_defence
    "promotion" -> R.string.chapter_promotion
    "pawn" -> R.string.chapter_pawn
    "rook" -> R.string.chapter_rook
    "minor" -> R.string.chapter_minor
    "queen" -> R.string.chapter_queen
    "zugzwang" -> R.string.chapter_zugzwang
    else -> R.string.course_title
}

@StringRes fun chapterTip(id: String): Int = when(id) {
    "mate1" -> R.string.chapter_mate1_tip
    "mate1b" -> R.string.chapter_mate1b_tip
    "backrank" -> R.string.chapter_backrank_tip
    "patterns" -> R.string.chapter_patterns_tip
    "mate2" -> R.string.chapter_mate2_tip
    "mate2b" -> R.string.chapter_mate2b_tip
    "mate3" -> R.string.chapter_mate3_tip
    "hanging" -> R.string.chapter_hanging_tip
    "fork" -> R.string.chapter_fork_tip
    "forkb" -> R.string.chapter_forkb_tip
    "pin" -> R.string.chapter_pin_tip
    "skewer" -> R.string.chapter_skewer_tip
    "discovered" -> R.string.chapter_discovered_tip
    "trapped" -> R.string.chapter_trapped_tip
    "deflection" -> R.string.chapter_deflection_tip
    "attraction" -> R.string.chapter_attraction_tip
    "sacrifice" -> R.string.chapter_sacrifice_tip
    "clearance" -> R.string.chapter_clearance_tip
    "intermezzo" -> R.string.chapter_intermezzo_tip
    "quiet" -> R.string.chapter_quiet_tip
    "defence" -> R.string.chapter_defence_tip
    "promotion" -> R.string.chapter_promotion_tip
    "pawn" -> R.string.chapter_pawn_tip
    "rook" -> R.string.chapter_rook_tip
    "minor" -> R.string.chapter_minor_tip
    "queen" -> R.string.chapter_queen_tip
    "zugzwang" -> R.string.chapter_zugzwang_tip
    else -> R.string.course_help
}

@StringRes fun sectionName(id: String): Int = when(id) {
    "mate" -> R.string.course_sec_mate
    "material" -> R.string.course_sec_material
    "ideas" -> R.string.course_sec_ideas
    "endgames" -> R.string.course_sec_endgames
    else -> R.string.course_title
}

/** A single theme's label; the tag itself for a tag without one. */
@StringRes fun tagName(tag: String): Int? = when(tag) {
    "mateIn1" -> R.string.tag_mateIn1
    "mateIn2" -> R.string.tag_mateIn2
    "mateIn3" -> R.string.tag_mateIn3
    "backRankMate" -> R.string.tag_backRankMate
    "fork" -> R.string.tag_fork
    "pin" -> R.string.tag_pin
    "skewer" -> R.string.tag_skewer
    "discoveredAttack" -> R.string.tag_discoveredAttack
    "doubleCheck" -> R.string.tag_doubleCheck
    "hangingPiece" -> R.string.tag_hangingPiece
    "trappedPiece" -> R.string.tag_trappedPiece
    "capturingDefender" -> R.string.tag_capturingDefender
    "deflection" -> R.string.tag_deflection
    "attraction" -> R.string.tag_attraction
    "sacrifice" -> R.string.tag_sacrifice
    "clearance" -> R.string.tag_clearance
    "intermezzo" -> R.string.tag_intermezzo
    "quietMove" -> R.string.tag_quietMove
    "defensiveMove" -> R.string.tag_defensiveMove
    "kingsideAttack" -> R.string.tag_kingsideAttack
    "exposedKing" -> R.string.tag_exposedKing
    "promotion" -> R.string.tag_promotion
    "advancedPawn" -> R.string.tag_advancedPawn
    "pawnEndgame" -> R.string.tag_pawnEndgame
    "rookEndgame" -> R.string.tag_rookEndgame
    "bishopEndgame" -> R.string.tag_bishopEndgame
    "knightEndgame" -> R.string.tag_knightEndgame
    "queenEndgame" -> R.string.tag_queenEndgame
    "zugzwang" -> R.string.tag_zugzwang
    else -> null
}
