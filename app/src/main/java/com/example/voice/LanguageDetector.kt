package com.example.voice

import com.example.data.prefs.ZoyaLanguageMode
import java.util.Locale

enum class DetectedLanguage(val locale: Locale, val tag: String, val label: String) {
    ENGLISH(Locale("en", "IN"), "en-IN", "English"),
    HINDI(Locale("hi", "IN"), "hi-IN", "Hindi"),
    HINGLISH(Locale("en", "IN"), "en-IN", "Hinglish")
}

object LanguageDetector {

    private val hinglishKeywords = setOf(
        "kholo", "khol", "chalao", "karo", "kar", "batao", "bata", "kitni", "kitna",
        "hai", "hain", "mera", "meri", "mere", "baad", "dena", "lagao", "laga",
        "band", "kardo", "bhejo", "bhej", "padho", "padh", "namaste", "kaise",
        "kaisa", "kya", "kyun", "kahan", "kab", "mujhe", "hum", "tum", "aap",
        "shukriya", "dhanyawad", "theek", "accha", "baje", "ghante", "minat", "suno"
    )

    private val wakeWordRegex = Regex(
        pattern = """^(hey\s+zoya|hi\s+zoya|hello\s+zoya|ok\s+zoya|okay\s+zoya|zoya|हे\s+जोया|हाय\s+जोया|जोया)[\s,.:!?-]*""",
        option = RegexOption.IGNORE_CASE
    )

    fun containsWakeWord(text: String): Boolean {
        val trimmed = text.trim()
        return wakeWordRegex.containsMatchIn(trimmed) ||
            trimmed.lowercase().contains("hey zoya") ||
            trimmed.lowercase().contains("ok zoya") ||
            trimmed.contains("हे जोया")
    }

    fun stripWakeWord(text: String): String {
        return text.trim().replace(wakeWordRegex, "").trim()
    }

    fun detect(text: String, preferredMode: ZoyaLanguageMode = ZoyaLanguageMode.AUTO): DetectedLanguage {
        if (preferredMode == ZoyaLanguageMode.HINDI) return DetectedLanguage.HINDI
        if (preferredMode == ZoyaLanguageMode.ENGLISH) return DetectedLanguage.ENGLISH
        if (preferredMode == ZoyaLanguageMode.HINGLISH) return DetectedLanguage.HINGLISH

        // Check Devanagari Unicode block (U+0900..U+097F)
        val hasDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return DetectedLanguage.HINDI

        val tokens = text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val hinglishHits = tokens.count { it in hinglishKeywords }
        return if (hinglishHits >= 1) DetectedLanguage.HINGLISH else DetectedLanguage.ENGLISH
    }

    fun recognitionLocaleTag(mode: ZoyaLanguageMode): String = when (mode) {
        ZoyaLanguageMode.HINDI -> "hi-IN"
        ZoyaLanguageMode.ENGLISH -> "en-US"
        ZoyaLanguageMode.HINGLISH -> "en-IN"
        ZoyaLanguageMode.AUTO -> "en-IN"
    }
}
