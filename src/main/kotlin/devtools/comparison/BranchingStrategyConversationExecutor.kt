package devtools.comparison

import agent.capability.BranchingCapability
import agent.capability.capability
import agent.format.TextResponseFormat
import agent.impl.MrAgent
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.core.DefaultMemoryManager
import agent.memory.strategy.MemoryStrategyFactory
import agent.memory.strategy.MemoryStrategyOption
import agent.storage.JsonConversationStore
import java.nio.file.Files
import java.nio.file.Path
import llm.core.LanguageModel

/**
 * Выполняет branch-сценарий для стратегии ветвления и собирает общий execution report.
 */
class BranchingStrategyConversationExecutor(
    private val baseLanguageModel: LanguageModel,
    private val stateDirectory: Path,
    private val onStepStarted: (phaseLabel: String, stepNumber: Int, totalSteps: Int) -> Unit = { _, _, _ -> },
    private val onStepFinished: (phaseLabel: String, step: StrategyComparisonStepReport, totalSteps: Int) -> Unit = { _, _, _ -> }
) {
    fun execute(
        option: MemoryStrategyOption,
        scenario: BranchingComparisonScenario
    ): StrategyExecutionReport {
        Files.createDirectories(stateDirectory)

        val tracingLanguageModel = TracingLanguageModel(baseLanguageModel)
        val strategy = MemoryStrategyFactory.create(option.type, tracingLanguageModel)
        val storagePath = stateDirectory.resolve("${option.id}.json")
        Files.deleteIfExists(storagePath)

        val memoryManager = DefaultMemoryManager(
            languageModel = tracingLanguageModel,
            systemPrompt = buildSystemPrompt(),
            conversationStore = JsonConversationStore(storagePath),
            memoryStrategy = strategy,
            lifecycleListener = NoOpAgentLifecycleListener
        )
        val agent = MrAgent(
            languageModel = tracingLanguageModel,
            lifecycleListener = NoOpAgentLifecycleListener,
            memoryStrategy = strategy,
            memoryManager = memoryManager
        )

        val branchingCapability = agent.capability<BranchingCapability>()
            ?: error("Стратегия ${option.id} не предоставляет branching capability.")

        val totalSteps = scenario.sharedPrompts.size + scenario.firstBranchPrompts.size + scenario.secondBranchPrompts.size
        var currentStepNumber = 0

        val sharedSteps = scenario.sharedPrompts.map { prompt ->
            currentStepNumber += 1
            executePrompt(
                agent = agent,
                tracingLanguageModel = tracingLanguageModel,
                prompt = prompt,
                phaseLabel = "shared",
                stepNumber = currentStepNumber,
                totalSteps = totalSteps
            )
        }

        val checkpoint = branchingCapability.createCheckpoint(scenario.checkpointName)
        branchingCapability.createBranch(scenario.firstBranchName)
        branchingCapability.createBranch(scenario.secondBranchName)

        branchingCapability.switchBranch(scenario.firstBranchName)
        val firstBranchSteps = scenario.firstBranchPrompts.map { prompt ->
            currentStepNumber += 1
            executePrompt(
                agent = agent,
                tracingLanguageModel = tracingLanguageModel,
                prompt = prompt,
                phaseLabel = scenario.firstBranchName,
                stepNumber = currentStepNumber,
                totalSteps = totalSteps
            )
        }

        branchingCapability.switchBranch(scenario.secondBranchName)
        val secondBranchSteps = scenario.secondBranchPrompts.map { prompt ->
            currentStepNumber += 1
            executePrompt(
                agent = agent,
                tracingLanguageModel = tracingLanguageModel,
                prompt = prompt,
                phaseLabel = scenario.secondBranchName,
                stepNumber = currentStepNumber,
                totalSteps = totalSteps
            )
        }

        return StrategyExecutionReport(
            strategyId = option.id,
            strategyDisplayName = option.displayName,
            strategyDescription = option.description,
            providerPromptTokensNote = option.specificPromptDescription
                ?.takeIf { (sharedSteps + firstBranchSteps + secondBranchSteps).any { step -> step.internalModelCallCount > 0 } },
            steps = sharedSteps + firstBranchSteps + secondBranchSteps,
            branchExecutions = listOf(
                BranchExecutionReport(
                    branchName = scenario.firstBranchName,
                    sourceCheckpointName = checkpoint.name,
                    steps = firstBranchSteps
                ),
                BranchExecutionReport(
                    branchName = scenario.secondBranchName,
                    sourceCheckpointName = checkpoint.name,
                    steps = secondBranchSteps
                )
            )
        )
    }

    private fun executePrompt(
        agent: MrAgent,
        tracingLanguageModel: TracingLanguageModel,
        prompt: String,
        phaseLabel: String,
        stepNumber: Int,
        totalSteps: Int
    ): StrategyComparisonStepReport {
        onStepStarted(phaseLabel, stepNumber, totalSteps)

        val preview = agent.previewTokenStats(prompt)
        val response = agent.ask(prompt)
        val tracedRequests = tracingLanguageModel.drainRequests()
        check(tracedRequests.isNotEmpty()) {
            "Не удалось получить трассировку запросов к модели."
        }
        val finalRequest = tracedRequests.last()

        return StrategyComparisonStepReport(
            stepNumber = stepNumber,
            userPrompt = prompt,
            assistantResponse = response.content,
            historyTokens = preview.historyTokens,
            promptTokensLocal = preview.promptTokensLocal,
            userPromptTokens = preview.userPromptTokens,
            providerPromptTokens = sumUsage(tracedRequests) { it.promptTokens },
            providerCompletionTokens = sumUsage(tracedRequests) { it.completionTokens },
            providerTotalTokens = sumUsage(tracedRequests) { it.totalTokens },
            modelCallCount = tracedRequests.size,
            internalModelCallCount = tracedRequests.size - 1,
            requestMessageCount = finalRequest.messages.size,
            requestCharacterCount = finalRequest.messages.sumOf { it.content.length },
            requestRoles = finalRequest.messages.map { it.role.name }
        ).also { step ->
            onStepFinished(phaseLabel, step, totalSteps)
        }
    }

    private fun buildSystemPrompt(): String =
        "$defaultSystemPrompt\n\nТребования к формату ответа:\n${TextResponseFormat.formatInstruction}"

    private companion object {
        private const val defaultSystemPrompt =
            "Ты полезный ассистент. Отвечай кратко, если пользователь не просит подробнее."
    }
}
