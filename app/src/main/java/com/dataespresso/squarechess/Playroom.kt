package com.dataespresso.squarechess

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

/**
 * The Playroom: optional worlds (a drawn background with matching board colours) and an optional
 * buddy with things to wear, earned with stickers. Classic with Playful extras off is the app as
 * it always was; e-ink mode ignores both the world and the colours.
 */
enum class World(@StringRes val label: Int, val light: Color, val dark: Color) {
    CLASSIC(R.string.world_classic, Color(0xFFF2E7CF), Color(0xFF526D62)),
    FOREST(R.string.world_forest, Color(0xFFEEE6C6), Color(0xFF6F9150)),
    JUNGLE(R.string.world_jungle, Color(0xFFF6E9A8), Color(0xFF3F7F5A)),
    OCEAN(R.string.world_ocean, Color(0xFFE4F0F3), Color(0xFF4F8BA5)),
    SPACE(R.string.world_space, Color(0xFFE6E3F2), Color(0xFF5B5A9A))
}

/** Square Home's five characters plus two from the Pancakeverse stories. */
enum class Buddy(@StringRes val label: Int, @StringRes val intro: Int) {
    ROBOT(R.string.buddy_robot, R.string.buddy_robot_intro),
    MAGNUS(R.string.buddy_magnus, R.string.buddy_magnus_intro),
    GIRL(R.string.buddy_girl, R.string.buddy_girl_intro),
    PRINCESS(R.string.buddy_princess, R.string.buddy_princess_intro),
    MONKEY(R.string.buddy_monkey, R.string.buddy_monkey_intro),
    SLOTH(R.string.buddy_sloth, R.string.buddy_sloth_intro),
    BOB(R.string.buddy_bob, R.string.buddy_bob_intro)
}

enum class Slot(@StringRes val label: Int) {
    HEAD(R.string.slot_head), FACE(R.string.slot_face), NECK(R.string.slot_neck),
    BACK(R.string.slot_back), HAND(R.string.slot_hand), PET(R.string.slot_pet)
}

/**
 * Something to earn. Each sticker marks a chess moment; [Stickers.kt] works them out from saved
 * games and puzzle progress, so nothing extra is stored about how they were earned.
 */
enum class Sticker(@StringRes val label: Int, @StringRes val how: Int) {
    FIRST_GAME(R.string.sticker_first_game, R.string.sticker_first_game_how),
    FIRST_CHECKMATE(R.string.sticker_first_checkmate, R.string.sticker_first_checkmate_how),
    KNIGHT_RIDER(R.string.sticker_knight_rider, R.string.sticker_knight_rider_how),
    BISHOP_MATE(R.string.sticker_bishop_mate, R.string.sticker_bishop_mate_how),
    ROOK_MATE(R.string.sticker_rook_mate, R.string.sticker_rook_mate_how),
    PROMOTION(R.string.sticker_promotion, R.string.sticker_promotion_how),
    ROYAL_COURT(R.string.sticker_royal_court, R.string.sticker_royal_court_how),
    SAFE_KING(R.string.sticker_safe_king, R.string.sticker_safe_king_how),
    ROYAL_FORK(R.string.sticker_royal_fork, R.string.sticker_royal_fork_how),
    KING_OF_BOLSENA(R.string.sticker_king_of_bolsena, R.string.sticker_king_of_bolsena_how),
    STALEMATE(R.string.sticker_stalemate, R.string.sticker_stalemate_how),
    JUNGLE_HOP(R.string.sticker_jungle_hop, R.string.sticker_jungle_hop_how),
    LONG_GAME(R.string.sticker_long_game, R.string.sticker_long_game_how),
    MAGIC_PAINTBRUSH(R.string.sticker_magic_paintbrush, R.string.sticker_magic_paintbrush_how),
    LEVEL_2(R.string.sticker_level_2, R.string.sticker_level_2_how),
    LEVEL_4(R.string.sticker_level_4, R.string.sticker_level_4_how),
    LEVEL_6(R.string.sticker_level_6, R.string.sticker_level_6_how),
    ADMIRAL(R.string.sticker_admiral, R.string.sticker_admiral_how),
    TOP_LEVEL(R.string.sticker_top_level, R.string.sticker_top_level_how),
    CLIMBER(R.string.sticker_climber, R.string.sticker_climber_how),
    PANCAKE_CHEF(R.string.sticker_pancake_chef, R.string.sticker_pancake_chef_how),
    REMATCH(R.string.sticker_rematch, R.string.sticker_rematch_how),
    COOL_HEAD(R.string.sticker_cool_head, R.string.sticker_cool_head_how),
    ICE_COLD(R.string.sticker_ice_cold, R.string.sticker_ice_cold_how),
    REAL_BOARD(R.string.sticker_real_board, R.string.sticker_real_board_how),
    GOOD_COMPANY(R.string.sticker_good_company, R.string.sticker_good_company_how),
    GOOD_GAME(R.string.sticker_good_game, R.string.sticker_good_game_how),
    PUZZLES_10(R.string.sticker_puzzles_10, R.string.sticker_puzzles_10_how),
    PUZZLES_50(R.string.sticker_puzzles_50, R.string.sticker_puzzles_50_how),
    PUZZLES_200(R.string.sticker_puzzles_200, R.string.sticker_puzzles_200_how),
    SPELLBOUND(R.string.sticker_spellbound, R.string.sticker_spellbound_how),
    ONE_TWO_THREE(R.string.sticker_one_two_three, R.string.sticker_one_two_three_how),
    HOT_STREAK(R.string.sticker_hot_streak, R.string.sticker_hot_streak_how),
    FAR_SIGHT(R.string.sticker_far_sight, R.string.sticker_far_sight_how)
}

/** Things to wear or hold. [sticker] null means it is free from the start. */
enum class Item(val slot: Slot, @StringRes val label: Int, val sticker: Sticker?) {
    BEANIE(Slot.HEAD, R.string.item_beanie, Sticker.GOOD_GAME),
    PARTY_HAT(Slot.HEAD, R.string.item_party_hat, Sticker.FIRST_CHECKMATE),
    KNIGHT_HELMET(Slot.HEAD, R.string.item_knight_helmet, Sticker.KNIGHT_RIDER),
    CROWN(Slot.HEAD, R.string.item_crown, Sticker.PROMOTION),
    WIZARD_HAT(Slot.HEAD, R.string.item_wizard_hat, Sticker.LEVEL_6),
    ADMIRAL_HAT(Slot.HEAD, R.string.item_admiral_hat, Sticker.ADMIRAL),
    SUNGLASSES(Slot.FACE, R.string.item_sunglasses, Sticker.PUZZLES_10),
    SCARF(Slot.NECK, R.string.item_scarf, Sticker.REAL_BOARD),
    AMULET(Slot.NECK, R.string.item_amulet, Sticker.KING_OF_BOLSENA),
    CAPE(Slot.BACK, R.string.item_cape, Sticker.SAFE_KING),
    PANCAKE_SHIELD(Slot.HAND, R.string.item_pancake_shield, Sticker.STALEMATE),
    PAINTBRUSH(Slot.HAND, R.string.item_paintbrush, Sticker.MAGIC_PAINTBRUSH),
    GOLDEN_BANANA(Slot.HAND, R.string.item_golden_banana, Sticker.ONE_TWO_THREE),
    KNIGHT_SWORD(Slot.HAND, R.string.item_knight_sword, Sticker.ROYAL_FORK),
    MAGIC_WAND(Slot.HAND, R.string.item_magic_wand, Sticker.SPELLBOUND),
    BANANA(Slot.HAND, R.string.item_banana, Sticker.REMATCH),
    PANCAKE(Slot.HAND, R.string.item_pancake, Sticker.PANCAKE_CHEF),
    FROST_PANCAKE(Slot.HAND, R.string.item_frost_pancake, Sticker.ICE_COLD),
    TROPHY(Slot.HAND, R.string.item_trophy, Sticker.CLIMBER),
    TELESCOPE(Slot.HAND, R.string.item_telescope, Sticker.FAR_SIGHT),
    LANTERN(Slot.HAND, R.string.item_lantern, Sticker.GOOD_COMPANY),
    PET_PAWN(Slot.PET, R.string.item_pet_pawn, Sticker.FIRST_GAME),
    PET_KNIGHT(Slot.PET, R.string.item_pet_knight, Sticker.PUZZLES_200),
    PET_BISHOP(Slot.PET, R.string.item_pet_bishop, Sticker.BISHOP_MATE),
    PET_ROOK(Slot.PET, R.string.item_pet_rook, Sticker.ROOK_MATE),
    PET_QUEEN(Slot.PET, R.string.item_pet_queen, Sticker.ROYAL_COURT),
    PET_KING(Slot.PET, R.string.item_pet_king, Sticker.TOP_LEVEL),
    LAVA_MONSTER(Slot.PET, R.string.item_lava_monster, Sticker.HOT_STREAK),
    LITTLE_FROSTY(Slot.PET, R.string.item_little_frosty, Sticker.COOL_HEAD),
    MOON_MOUSE(Slot.PET, R.string.item_moon_mouse, Sticker.LONG_GAME),
    FROG(Slot.PET, R.string.item_frog, Sticker.JUNGLE_HOP)
}

fun Item.unlocked(earned: Set<Sticker>): Boolean = sticker==null || sticker in earned

/** What the buddy wears: at most one item per slot. */
data class Outfit(val items: Map<Slot,Item> = emptyMap()) {
    operator fun get(slot: Slot): Item? = items[slot]
    fun with(slot: Slot, item: Item?): Outfit = Outfit(if(item==null) items-slot else items+(slot to item))
    /** Only the items that are still unlocked, for example after saved games were deleted. */
    fun allowed(earned: Set<Sticker>): Outfit = Outfit(items.filterValues { it.unlocked(earned) })
    fun encode(): String = items.values.sortedBy { it.slot.ordinal }.joinToString(",") { it.name }
    companion object {
        fun decode(text: String?): Outfit = Outfit(text.orEmpty().split(',').mapNotNull { name ->
            Item.entries.firstOrNull { it.name==name.trim() } }.associateBy { it.slot })
    }
}

/** Stickers in the order the sticker book shows them, encoded for SharedPreferences. */
fun encodeStickers(stickers: Set<Sticker>): String = stickers.sortedBy { it.ordinal }.joinToString(",") { it.name }
fun decodeStickers(text: String?): Set<Sticker> =
    text.orEmpty().split(',').mapNotNull { name -> Sticker.entries.firstOrNull { it.name==name.trim() } }.toSet()

/** What just happened, for the buddy's pose and line. */
enum class BuddyMoment { HOME, RIGHT, WRONG, WIN, GOOD_GAME, HINT, THINK }

@StringRes fun buddyLine(buddy: Buddy, moment: BuddyMoment): Int = when(buddy) {
    Buddy.ROBOT -> when(moment) {
        BuddyMoment.HOME -> R.string.line_robot_home; BuddyMoment.RIGHT -> R.string.line_robot_right
        BuddyMoment.WRONG -> R.string.line_robot_wrong; BuddyMoment.WIN -> R.string.line_robot_win
        BuddyMoment.GOOD_GAME -> R.string.line_robot_gg; BuddyMoment.HINT -> R.string.line_robot_hint
        BuddyMoment.THINK -> R.string.line_robot_think
    }
    Buddy.MAGNUS -> when(moment) {
        BuddyMoment.HOME -> R.string.line_magnus_home; BuddyMoment.RIGHT -> R.string.line_magnus_right
        BuddyMoment.WRONG -> R.string.line_magnus_wrong; BuddyMoment.WIN -> R.string.line_magnus_win
        BuddyMoment.GOOD_GAME -> R.string.line_magnus_gg; BuddyMoment.HINT -> R.string.line_magnus_hint
        BuddyMoment.THINK -> R.string.line_magnus_think
    }
    Buddy.GIRL -> when(moment) {
        BuddyMoment.HOME -> R.string.line_girl_home; BuddyMoment.RIGHT -> R.string.line_girl_right
        BuddyMoment.WRONG -> R.string.line_girl_wrong; BuddyMoment.WIN -> R.string.line_girl_win
        BuddyMoment.GOOD_GAME -> R.string.line_girl_gg; BuddyMoment.HINT -> R.string.line_girl_hint
        BuddyMoment.THINK -> R.string.line_girl_think
    }
    Buddy.PRINCESS -> when(moment) {
        BuddyMoment.HOME -> R.string.line_princess_home; BuddyMoment.RIGHT -> R.string.line_princess_right
        BuddyMoment.WRONG -> R.string.line_princess_wrong; BuddyMoment.WIN -> R.string.line_princess_win
        BuddyMoment.GOOD_GAME -> R.string.line_princess_gg; BuddyMoment.HINT -> R.string.line_princess_hint
        BuddyMoment.THINK -> R.string.line_princess_think
    }
    Buddy.MONKEY -> when(moment) {
        BuddyMoment.HOME -> R.string.line_monkey_home; BuddyMoment.RIGHT -> R.string.line_monkey_right
        BuddyMoment.WRONG -> R.string.line_monkey_wrong; BuddyMoment.WIN -> R.string.line_monkey_win
        BuddyMoment.GOOD_GAME -> R.string.line_monkey_gg; BuddyMoment.HINT -> R.string.line_monkey_hint
        BuddyMoment.THINK -> R.string.line_monkey_think
    }
    Buddy.SLOTH -> when(moment) {
        BuddyMoment.HOME -> R.string.line_sloth_home; BuddyMoment.RIGHT -> R.string.line_sloth_right
        BuddyMoment.WRONG -> R.string.line_sloth_wrong; BuddyMoment.WIN -> R.string.line_sloth_win
        BuddyMoment.GOOD_GAME -> R.string.line_sloth_gg; BuddyMoment.HINT -> R.string.line_sloth_hint
        BuddyMoment.THINK -> R.string.line_sloth_think
    }
    Buddy.BOB -> when(moment) {
        BuddyMoment.HOME -> R.string.line_bob_home; BuddyMoment.RIGHT -> R.string.line_bob_right
        BuddyMoment.WRONG -> R.string.line_bob_wrong; BuddyMoment.WIN -> R.string.line_bob_win
        BuddyMoment.GOOD_GAME -> R.string.line_bob_gg; BuddyMoment.HINT -> R.string.line_bob_hint
        BuddyMoment.THINK -> R.string.line_bob_think
    }
}

/** The pose for a moment. Little Robot hands out pancakes where the others cheer. */
fun buddyPose(buddy: Buddy, moment: BuddyMoment): BuddyPose = when(moment) {
    BuddyMoment.HOME -> BuddyPose.WAVE
    BuddyMoment.RIGHT, BuddyMoment.WIN -> if(buddy==Buddy.ROBOT) BuddyPose.PANCAKES else BuddyPose.CHEER
    BuddyMoment.WRONG -> BuddyPose.CRY
    BuddyMoment.GOOD_GAME -> BuddyPose.SHRUG
    BuddyMoment.HINT -> BuddyPose.EUREKA
    BuddyMoment.THINK -> BuddyPose.THINK
}

enum class BuddyPose { STAND, WAVE, CHEER, THINK, EUREKA, SHRUG, CRY, PANCAKES }
