package agent.memory.core

import agent.capability.AgentCapability
import agent.core.AgentTokenStats
import java.nio.file.Path
import llm.core.model.ChatMessage

/**
 * Фасад, через который агент управляет сохранённым состоянием диалога и эффективным prompt.
 */
interface MemoryManager {
    /**
     * Возвращает исходный диалог, который сейчас хранится в памяти.
     */
    fun currentConversation(): List<ChatMessage>

    /**
     * Оценивает расход токенов для гипотетического следующего пользовательского сообщения без
     * изменения состояния.
     */
    fun previewTokenStats(userPrompt: String): AgentTokenStats

    /**
     * Добавляет новое пользовательское сообщение и возвращает эффективный контекст для модели.
     */
    fun appendUserMessage(userPrompt: String): List<ChatMessage>

    /**
     * Сохраняет ответ ассистента после завершения запроса к модели.
     */
    fun appendAssistantMessage(content: String)

    /**
     * Очищает видимый пользователю контекст, сохраняя базовый системный prompt.
     */
    fun clear()

    /**
     * Заменяет текущее содержимое памяти данными, загруженными из указанного файла.
     */
    fun replaceContextFromFile(sourcePath: Path)

    /**
     * Возвращает дополнительную capability текущей стратегии памяти, если она поддерживается.
     */
    fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability?
}
