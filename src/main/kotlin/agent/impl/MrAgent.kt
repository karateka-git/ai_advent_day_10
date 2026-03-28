package agent.impl

import agent.core.Agent
import agent.core.AgentInfo
import agent.core.AgentResponse
import agent.core.AgentTokenStats
import agent.format.ResponseFormat
import agent.format.TextResponseFormat
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.DefaultMemoryManager
import agent.memory.MemoryManager
import agent.memory.MemoryStrategy
import agent.memory.SummaryCompressionMemoryStrategy
import agent.memory.summarizer.LlmConversationSummarizer
import java.nio.file.Path
import llm.core.LanguageModel

class MrAgent(
    private val languageModel: LanguageModel,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener,
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
        val modelResponse = languageModel.complete(conversation)
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

    companion object {
        private fun buildSystemPrompt(systemPrompt: String, responseFormatInstruction: String): String =
            "$systemPrompt\n\nТребования к формату ответа:\n$responseFormatInstruction"

        private const val DEFAULT_SYSTEM_PROMPT =
            "Ты полезный ассистент. Отвечай кратко, если пользователь не просит подробнее."
    }
}
