package com.example.automation

import com.example.voice.DetectedLanguage
import com.example.voice.LanguageDetector

sealed class ZoyaIntent {
    data class OpenApp(val appQuery: String) : ZoyaIntent()
    data class OpenSetting(val settingKey: String) : ZoyaIntent()
    data object ReadBattery : ZoyaIntent()
    data object ShowDeviceInfo : ZoyaIntent()
    data class CreateReminder(val title: String, val delayMinutes: Int) : ZoyaIntent()
    data class SetTimer(val durationSeconds: Int, val label: String) : ZoyaIntent()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : ZoyaIntent()
    data class PrepareCall(val target: String) : ZoyaIntent()
    data class PrepareSms(val target: String, val message: String) : ZoyaIntent()
    data object ReadNotifications : ZoyaIntent()
    data class AccessibilityAction(val action: String, val targetText: String = "") : ZoyaIntent()
    data class AiConversation(val cleanedPrompt: String) : ZoyaIntent()
}

/**
 * Bilingual English / Hindi / Hinglish command classifier.
 * Detects real mobile commands so Zoya executes actual Android actions immediately
 * instead of merely talking about what it would do.
 */
object CommandRouter {

    private val openAppRegex = Regex(
        pattern = """^(?:open|launch|start|run)\s+(.+)$|^(.+?)\s+(?:kholo|khol\s+do|chalao|chala\s+do|open\s+karo|खोलो|चलाओ)$""",
        option = RegexOption.IGNORE_CASE
    )

    private val minutesRegex = Regex(
        pattern = """(\d+)\s*(?:min|mins|minute|minutes|minat|मिनट)""",
        option = RegexOption.IGNORE_CASE
    )

    private val hoursRegex = Regex(
        pattern = """(\d+)\s*(?:hr|hrs|hour|hours|ghante|ghanta|घंटे)""",
        option = RegexOption.IGNORE_CASE
    )

    private val secondsRegex = Regex(
        pattern = """(\d+)\s*(?:sec|secs|second|seconds|सेकंड)""",
        option = RegexOption.IGNORE_CASE
    )

    private val timeRegex = Regex(
        pattern = """(\d{1,2})(?::(\d{2}))?\s*(am|pm|baje|बजे)?""",
        option = RegexOption.IGNORE_CASE
    )

    fun route(rawInput: String): ZoyaIntent {
        val cleaned = LanguageDetector.stripWakeWord(rawInput).trim()
        if (cleaned.isEmpty()) {
            return ZoyaIntent.AiConversation("Hello Zoya")
        }
        val lower = cleaned.lowercase()

        // 1. Battery status queries (e.g. "Zoya battery kitni hai?", "check battery")
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            if (lower.contains("setting") || lower.contains("settings") || lower.contains("saver")) {
                return ZoyaIntent.OpenSetting("battery")
            }
            if (lower.contains("kitni") || lower.contains("kitna") || lower.contains("percent") ||
                lower.contains("status") || lower.contains("level") || lower.contains("check") ||
                lower.contains("how much") || lower.contains("charging") || lower.contains("कितनी")
            ) {
                return ZoyaIntent.ReadBattery
            }
        }

        // 2. Device info queries
        if (lower.contains("device info") || lower.contains("phone info") ||
            lower.contains("system info") || lower.contains("ram kitni") ||
            lower.contains("storage kitna") || lower.contains("phone details")
        ) {
            return ZoyaIntent.ShowDeviceInfo
        }

        // 3. Notifications reader
        if ((lower.contains("notification") || lower.contains("notifications") || lower.contains("नोटिफिकेशन")) &&
            (lower.contains("read") || lower.contains("padh") || lower.contains("check") ||
                lower.contains("show") || lower.contains("bata") || lower.contains("पढ़ो"))
        ) {
            return ZoyaIntent.ReadNotifications
        }

        // 4. Phone settings navigation
        parseSettingIntent(lower)?.let { return it }

        // 5. Reminders (e.g., "20 minute baad reminder dena", "remind me in 15 minutes to call mom")
        if (lower.contains("remind") || lower.contains("reminder") || lower.contains("yaad dilana") ||
            lower.contains("रिमाइंडर") || lower.contains("याद दिलाना")
        ) {
            val mins = minutesRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val hrs = hoursRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull()
            val totalMinutes = ((hrs ?: 0) * 60 + (mins ?: 0)).let { if (it <= 0) 10 else it }
            val title = extractReminderTitle(cleaned)
            return ZoyaIntent.CreateReminder(title = title, delayMinutes = totalMinutes)
        }

        // 6. Timers
        if (lower.contains("timer") || lower.contains("टाइमर")) {
            val secs = secondsRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val mins = minutesRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val hrs = hoursRegex.find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val totalSeconds = (hrs * 3600 + mins * 60 + secs).let { if (it <= 0) 60 else it }
            return ZoyaIntent.SetTimer(durationSeconds = totalSeconds, label = "Zoya Timer")
        }

        // 7. Alarms
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            val match = timeRegex.find(lower)
            var hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 7
            val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val meridiem = match?.groupValues?.get(3)?.lowercase().orEmpty()
            if (meridiem == "pm" && hour in 1..11) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
            return ZoyaIntent.SetAlarm(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59), label = "Zoya Alarm")
        }

        // 8. Calls & SMS (Always routed to confirmation dialog!)
        if (lower.startsWith("call ") || lower.contains("dial ") || lower.contains("phone lagao") ||
            lower.contains("call lagao") || lower.contains("call karo")
        ) {
            val target = cleaned
                .replace(Regex("""^(call|dial)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""(ko\s+)?(phone|call)\s+(lagao|karo)""", RegexOption.IGNORE_CASE), "")
                .trim()
            return ZoyaIntent.PrepareCall(target = target)
        }

        if (lower.startsWith("sms ") || lower.startsWith("text ") || lower.contains("send sms") ||
            lower.contains("message bhejo") || lower.contains("sms bhejo")
        ) {
            val target = cleaned
                .replace(Regex("""^(send\s+sms\s+to|sms|text)\s+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""(ko\s+)?(message|sms)\s+bhejo""", RegexOption.IGNORE_CASE), "")
                .trim()
            return ZoyaIntent.PrepareSms(target = target, message = "")
        }

        // 9. Optional Accessibility actions
        if (lower.contains("read screen") || lower.contains("screen padho") || lower.contains("what is on screen")) {
            return ZoyaIntent.AccessibilityAction("read_screen")
        }
        if (lower.startsWith("scroll down") || lower.contains("neeche scroll")) {
            return ZoyaIntent.AccessibilityAction("scroll_down")
        }
        if (lower.startsWith("scroll up") || lower.contains("upar scroll")) {
            return ZoyaIntent.AccessibilityAction("scroll_up")
        }
        if (lower.startsWith("click ") || lower.startsWith("tap ")) {
            val target = cleaned.replace(Regex("""^(click|tap)\s+""", RegexOption.IGNORE_CASE), "").trim()
            if (target.isNotEmpty() && !target.equals("to talk", ignoreCase = true)) {
                return ZoyaIntent.AccessibilityAction("click", target)
            }
        }

        // 10. App Launching (e.g. "YouTube kholo", "Open Instagram", "WhatsApp chalao")
        val appMatch = openAppRegex.find(cleaned)
        if (appMatch != null) {
            val target = (appMatch.groupValues[1].ifBlank { appMatch.groupValues[2] })
                .replace(Regex("""\b(app|application|please|zara|plz)\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (target.isNotEmpty()) {
                parseSettingIntent("$target settings")?.let {
                    if (target.lowercase() in setOf("wifi", "wi-fi", "bluetooth", "location", "display", "sound", "battery", "accessibility", "settings")) {
                        return it
                    }
                }
                return ZoyaIntent.OpenApp(target)
            }
        }

        return ZoyaIntent.AiConversation(cleaned)
    }

    private fun parseSettingIntent(lower: String): ZoyaIntent.OpenSetting? {
        val asksSettings = lower.contains("setting") || lower.contains("open") ||
            lower.contains("kholo") || lower.contains("on karo") || lower.contains("manage")
        if (!asksSettings) return null
        return when {
            lower.contains("wi-fi") || lower.contains("wifi") -> ZoyaIntent.OpenSetting("wifi")
            lower.contains("bluetooth") -> ZoyaIntent.OpenSetting("bluetooth")
            lower.contains("location") || lower.contains("gps") -> ZoyaIntent.OpenSetting("location")
            lower.contains("display") || lower.contains("brightness") -> ZoyaIntent.OpenSetting("display")
            lower.contains("sound") || lower.contains("volume") -> ZoyaIntent.OpenSetting("sound")
            lower.contains("accessibility") -> ZoyaIntent.OpenSetting("accessibility")
            lower.contains("notification access") -> ZoyaIntent.OpenSetting("notification_access")
            lower.contains("battery") -> ZoyaIntent.OpenSetting("battery")
            else -> null
        }
    }

    private fun extractReminderTitle(cleaned: String): String {
        val stripped = cleaned
            .replace(Regex("""\b(set\s+a?\s*reminder|remind\s+me|reminder\s+dena|reminder\s+lagao|yaad\s+dilana|रिमाइंडर\s+देना)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(in|after|baad|ke\s+baad)\b""", RegexOption.IGNORE_CASE), "")
            .replace(minutesRegex, "")
            .replace(hoursRegex, "")
            .replace(Regex("""^\s*(to|for|about|ki|ke\s+liye)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return stripped.ifBlank { "Zoya scheduled reminder" }
    }

    fun localizeReply(language: DetectedLanguage, english: String, hinglish: String, hindi: String): String =
        when (language) {
            DetectedLanguage.HINDI -> hindi
            DetectedLanguage.HINGLISH -> hinglish
            DetectedLanguage.ENGLISH -> english
        }
}
