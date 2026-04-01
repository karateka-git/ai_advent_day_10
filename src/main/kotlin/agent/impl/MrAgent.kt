package agent.impl

import agent.capability.AgentCapability
import agent.core.Agent
import agent.core.AgentInfo
import agent.core.AgentResponse
import agent.core.AgentTokenStats
import agent.format.ResponseFormat
import agent.format.TextResponseFormat
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.core.DefaultMemoryManager
import agent.memory.core.MemoryManager
import agent.memory.core.MemoryStrategy
import agent.memory.strategy.SummaryCompressionMemoryStrategy
import agent.memory.strategy.summary.LlmConversationSummarizer
import java.nio.file.Path
import llm.core.LanguageModel

/**
 * Базовая реализация CLI-агента.
 *
 * Делегирует управление памятью в [MemoryManager], отправляет эффективный контекст в активную
 * [LanguageModel] и возвращает текстовые ответы для отображения в консоли.
 */
class MrAgent(
    private val languageModel: LanguageModel,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener,
    memoryStrategy: MemoryStrategy = SummaryCompressionMemoryStrategy(
        recentMessagesCount = 2,
        summaryBatchSize = 3,
        summarizer = LlmConversationSummarizer(languageModel)
    ),
    private val memoryManager: MemoryManager = DefaultMemoryManager(
        languageModel = languageModel,
        systemPrompt = buildSystemPrompt(
            systemPrompt = systemPrompt,
            responseFormatInstruction = TextResponseFormat.formatInstruction
        ),
        memoryStrategy = memoryStrategy,
        lifecycleListener = lifecycleListener
    )
) : Agent<String> {
    override val responseFormat: ResponseFormat<String> = TextResponseFormat

    override val info = AgentInfo(
        name = "MrAgent",
        description = "CLI-агент для диалога с LLM через HTTP API.",
        model = languageModel.info.model
    )

    override fun previewTokenStats(userPrompt: String): AgentTokenStats =
        memoryManager.previewTokenStats(userPrompt)

    override fun ask(userPrompt: String): AgentResponse<String> {
        val preview = previewTokenStats(userPrompt)
        val conversation = memoryManager.appendUserMessage(userPrompt)
        val modelResponse = try {
            lifecycleListener.onModelRequestStarted()
            languageModel.complete(conversation)
        } finally {
            lifecycleListener.onModelRequestFinished()
        }
        memoryManager.appendAssistantMessage(modelResponse.content)

        return AgentResponse(
            content = responseFormat.parse(modelResponse.content),
            tokenStats = AgentTokenStats(
                historyTokens = preview.historyTokens,
                promptTokensLocal = preview.promptTokensLocal,
                userPromptTokens = preview.userPromptTokens,
                apiUsage = modelResponse.usage
            )
        )
    }

    override fun clearContext() {
        memoryManager.clear()
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        memoryManager.replaceContextFromFile(sourcePath)
    }

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? =
        memoryManager.capability(capabilityType)

    companion object {
        /**
         * Собирает финальный системный prompt агента, добавляя инструкции по формату ответа.
         */
        private fun buildSystemPrompt(systemPrompt: String, responseFormatInstruction: String): String =
            "$systemPrompt\n\nТребования к формату ответа:\n$responseFormatInstruction"

        private const val DEFAULT_SYSTEM_PROMPT =
            "Ты полезный ассистент. Отвечай кратко, если пользователь не просит подробнее."
    }
}
