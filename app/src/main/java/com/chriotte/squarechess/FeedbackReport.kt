package com.chriotte.squarechess

object SupportConfig {
    const val email = "support@dataespresso.com"
    // Supply actual published URLs before enabling these links.
    val privacyPolicyUrl: String? = null
    val playListingUrl: String? = null
}

data class FeedbackDiagnostics(val version: String, val device: String, val android: String, val gameMode: String?)
data class FeedbackReport(val subject: String, val body: String) {
    fun copyText() = "To: ${SupportConfig.email}\nSubject: $subject\n\n$body"
}

/** No Android APIs, network requests, device identifiers or saved-game access. */
object FeedbackReportBuilder {
    fun subject(value: String) = value.map { if(it.isISOControl()) ' ' else it }
        .joinToString("").replace(Regex("\\s+"), " ").trim().take(140)
    private fun optional(value: String) = value.trim().ifBlank { "Not provided" }
    fun bug(what: String, steps: String, expected: String, diagnostics: FeedbackDiagnostics? = null): FeedbackReport {
        require(what.isNotBlank()) { "Describe what happened" }
        val title=subject(what.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()).take(80)
        val details=diagnostics?.let {
            "\n\n---\nDiagnostic details (included with your approval):\n" +
                "App version: ${it.version}\nDevice: ${it.device}\nAndroid version: ${it.android}" +
                (it.gameMode?.let { mode -> "\nGame mode: $mode" } ?: "")
        }.orEmpty()
        return FeedbackReport("[Square Chess] Bug Report — $title",
            "Square Chess bug report\n\nWhat happened:\n${what.trim()}\n\nSteps to reproduce:\n${optional(steps)}\n\nExpected behaviour:\n${optional(expected)}$details")
    }
    fun feature(what: String, why: String): FeedbackReport {
        require(what.isNotBlank()) { "Describe the feature" }
        return FeedbackReport("[Square Chess] Feature Request",
            "Square Chess feature request\n\nSuggestion:\n${what.trim()}\n\nHow it would help:\n${optional(why)}")
    }
}
