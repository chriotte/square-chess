package com.dataespresso.squarechess

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Every translation loads, formats its arguments and differs from English where it should. */
@RunWith(AndroidJUnit4::class)
class LocalizationDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun resources(tag: String): Resources {
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }
        return context.createConfigurationContext(config).resources
    }

    @Test fun englishTextsMatchTheScreensTheTestsUse() {
        val res = resources("en")
        assertEquals("The file contains no games.", importSummaryText(res, 0, 0, emptyList()))
        assertEquals("Imported 2 games.\nSkipped 1 game already in your history.", importSummaryText(res, 2, 1, emptyList()))
        assertEquals("Imported 1 game.\nCould not read 1 game:\n• Game 2 (A – B): move 3 (Qh5) is not legal",
            importSummaryText(res, 1, 0, listOf("Game 2 (A – B): move 3 (Qh5) is not legal")))
        assertEquals("e4, white pawn", res.getString(R.string.square_description, "e4", res.getString(squarePieceName(com.github.bhlangonijr.chesslib.Piece.WHITE_PAWN))))
        assertEquals("Computer (Level 3)", displayName(res, "Computer (Level 3)"))
        assertEquals("Checkmate", reasonText(res, "Checkmate"))
        assertEquals("Hint: Knight from g1 to f3, Nf3", hintText(res, hintMove(ChessPosition(), "g1f3")!!).spoken)
        assertEquals("Castle kingside · e1 → g1",
            hintText(res, hintMove(ChessPosition("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"), "e1g1")!!).detail)
    }

    @Test fun everyLanguageFormatsEveryString() {
        val english = resources("en")
        val fields = R.string::class.java.fields.map { it.getInt(null) }
        val plurals = R.plurals::class.java.fields.map { it.getInt(null) }
        for (language in APP_LANGUAGES) {
            val res = resources(language.tag)
            for (id in fields) {
                val name = english.getResourceEntryName(id)
                // Formatting with sample arguments throws if a translation's placeholders are wrong.
                val template = res.getString(id)
                val count = Regex("%(\\d)\\$").findAll(english.getString(id)).map { it.groupValues[1].toInt() }.maxOrNull() ?: 0
                val args = Array<Any>(count) { if (english.getString(id).contains("%${it + 1}\$d")) 7 else "x" }
                val text = if (count > 0) res.getString(id, *args) else template
                assertTrue("${language.tag}: $name is empty", text.isNotBlank())
            }
            for (id in plurals) for (n in listOf(1, 2, 5, 21)) {
                assertTrue("${language.tag}: plural ${english.getResourceEntryName(id)}",
                    res.getQuantityString(id, n, n).contains(n.toString()))
            }
            if (language.tag != "en") {
                assertNotEquals("${language.tag} has its own Settings title",
                    english.getString(R.string.settings_title), res.getString(R.string.settings_title))
                assertNotEquals("${language.tag} has its own help text",
                    english.getString(R.string.howto_move_body), res.getString(R.string.howto_move_body))
            }
        }
    }

    @Test fun unknownNamesAndReasonsStayAsSaved() {
        val res = resources("de")
        assertEquals("Magnus", displayName(res, "Magnus"))
        assertEquals("Adjudication", reasonText(res, "Adjudication"))
        assertNotEquals("White", displayName(res, "White"))
    }
}
