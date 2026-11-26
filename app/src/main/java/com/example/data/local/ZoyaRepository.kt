package com.example.data.local

import com.example.data.prefs.ZoyaLanguageMode
import com.example.data.prefs.ZoyaPreferencesRepository
import com.example.data.prefs.ZoyaSettings
import com.example.data.security.SecureApiKeyStore
import kotlinx.coroutines.flow.Flow

class ZoyaRepository(
    private val chatMessageDao: ChatMessageDao,
    private val reminderDao: ReminderDao,
    private val preferencesRepository: ZoyaPreferencesRepository,
    val secureApiKeyStore: SecureApiKeyStore
) {
    val messagesFlow: Flow<List<ChatMessageEntity>> = chatMessageDao.observeAllMessages()
    val remindersFlow: Flow<List<ReminderEntity>> = reminderDao.observeAllReminders()
    val settingsFlow: Flow<ZoyaSettings> = preferencesRepository.settingsFlow

    suspend fun getRecentConversation(limit: Int = 12): List<ChatMessageEntity> =
        chatMessageDao.getRecentMessages(limit).reversed()

    suspend fun saveMessage(
        role: String,
        content: String,
        languageTag: String = "en-IN",
        commandBadge: String? = null
    ) {
        chatMessageDao.insertMessage(
            ChatMessageEntity(
                role = role,
                content = content,
                languageTag = languageTag,
                commandBadge = commandBadge
            )
        )
    }

    suspend fun clearConversationHistory() = chatMessageDao.clearAllMessages()

    suspend fun addReminder(title: String, triggerAtMillis: Long): Long =
        reminderDao.insertReminder(ReminderEntity(title = title, triggerAtMillis = triggerAtMillis))

    suspend fun toggleReminderCompleted(id: Long, completed: Boolean) =
        reminderDao.setCompleted(id, completed)

    suspend fun deleteReminder(id: Long) = reminderDao.deleteReminder(id)

    suspend fun clearAllReminders() = reminderDao.clearAllReminders()

    suspend fun setModel(modelId: String) = preferencesRepository.setModel(modelId)
    suspend fun setLanguageMode(mode: ZoyaLanguageMode) = preferencesRepository.setLanguageMode(mode)
    suspend fun setVoiceName(voiceName: String) = preferencesRepository.setVoiceName(voiceName)
    suspend fun setSpeechRate(rate: Float) = preferencesRepository.setSpeechRate(rate)
    suspend fun setSpeechPitch(pitch: Float) = preferencesRepository.setSpeechPitch(pitch)
    suspend fun setWakeWordEnabled(enabled: Boolean) = preferencesRepository.setWakeWordEnabled(enabled)
    suspend fun setContinuousConversation(enabled: Boolean) =
        preferencesRepository.setContinuousConversation(enabled)
    suspend fun setBackgroundAssistantEnabled(enabled: Boolean) =
        preferencesRepository.setBackgroundAssistantEnabled(enabled)
    suspend fun setSetupSkippedOnce(skipped: Boolean) =
        preferencesRepository.setSetupSkippedOnce(skipped)
    suspend fun setSaveHistoryEnabled(enabled: Boolean) =
        preferencesRepository.setSaveHistoryEnabled(enabled)

    suspend fun wipeAllLocalData() {
        chatMessageDao.clearAllMessages()
        reminderDao.clearAllReminders()
        secureApiKeyStore.clearAllKeys()
        preferencesRepository.resetAll()
    }
}
