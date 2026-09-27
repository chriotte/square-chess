package com.chriotte.squarechess

import org.junit.Assert.*
import org.junit.Test

class FeedbackReportTest {
    private val diagnostic=FeedbackDiagnostics("0.1.0 (1)","Unihertz Titan", "16 / API 36", "Two players")
    @Test fun bugHasRequiredTextAndOptionalDefaults() {
        val report=FeedbackReportBuilder.bug(" Pieces disappeared ","", "  ")
        assertEquals("[Square Chess] Bug Report — Pieces disappeared",report.subject)
        assertTrue(report.body.contains("What happened:\nPieces disappeared"))
        assertTrue(report.body.contains("Steps to reproduce:\nNot provided"))
        assertTrue(report.body.contains("Expected behaviour:\nNot provided"))
        assertFalse(report.body.contains("Diagnostic details"))
    }
    @Test fun diagnosticsAreEntirelyOptIn() {
        val with=FeedbackReportBuilder.bug("Problem","steps","expected",diagnostic)
        assertTrue(with.body.endsWith("App version: 0.1.0 (1)\nDevice: Unihertz Titan\nAndroid version: 16 / API 36\nGame mode: Two players"))
        val without=FeedbackReportBuilder.bug("Problem","steps","expected")
        for(value in listOf("Diagnostic", "App version", "Device:", "Android version", "Game mode:")) assertFalse(without.body.contains(value))
    }
    @Test fun featureNeverIncludesDiagnostics() {
        val report=FeedbackReportBuilder.feature("Add export", "Share a game")
        assertEquals("[Square Chess] Feature Request",report.subject)
        assertEquals("Square Chess feature request\n\nSuggestion:\nAdd export\n\nHow it would help:\nShare a game",report.body)
        assertFalse(report.body.contains("Diagnostic"))
        assertTrue(FeedbackReportBuilder.feature("Idea","").body.endsWith("Not provided"))
    }
    @Test fun emptyRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FeedbackReportBuilder.bug(" \n", "", "") }
        assertThrows(IllegalArgumentException::class.java) { FeedbackReportBuilder.feature("\t", "why") }
    }
    @Test fun subjectsStripControlCharactersButBodiesPreserveUnicodeAndNewlines() {
        val text="Brikke æøå 中文\nAndre linje\nThird line"
        val report=FeedbackReportBuilder.bug(text,"1. Play\n2. Review","正常")
        assertFalse(report.subject.contains('\n'))
        assertTrue(report.body.contains(text))
        assertTrue(report.body.contains("1. Play\n2. Review"))
        assertEquals("A B C",FeedbackReportBuilder.subject(" A\r\nB\u0000C "))
        assertEquals(140,FeedbackReportBuilder.subject("x".repeat(200)).length)
    }
    @Test fun clipboardIncludesAddressAndEditedContent() {
        val report=FeedbackReport("Edited subject", "Edited body")
        assertEquals("To: support@dataespresso.com\nSubject: Edited subject\n\nEdited body",report.copyText())
    }
}
