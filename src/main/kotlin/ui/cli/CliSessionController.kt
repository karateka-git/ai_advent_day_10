package ui.cli

import agent.capability.BranchingCapability
import agent.capability.capability
import agent.core.Agent
import agent.lifecycle.AgentLifecycleListener
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import app.output.AppEvent
import app.output.AppEventSink
import java.net.http.HttpClient
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption

/**
 * Исполняет команды CLI и обычные пользовательские prompt в рамках одной сессии.
 */
class CliSessionController(
    initialState: CliSessionState,
    private val config: Properties,
    private val httpClient: HttpClient,
    private val lifecycleListener: AgentLifecycleListener,
    private val appEventSink: AppEventSink,
    private val commandParser: CliCommandParser = CliCommandParser(),
    private val createLanguageModel: (String, Properties, HttpClient) -> LanguageModel,
    private val availableModelsProvider: (Properties) -> List<LanguageModelOption> = LanguageModelFactory::availableModels,
    private val createAgent: (LanguageModel, AgentLifecycleListener, MemoryStrategyType) -> Agent<String>,
    private val selectMemoryStrategy: () -> MemoryStrategyOption,
    private val warmUpLanguageModel: (LanguageModel, AgentLifecycleListener) -> Unit
) {
    /**
     * Актуальное состояние текущей CLI-сессии.
     */
    var state: CliSessionState = initialState
        private set

    /**
     * Обрабатывает один ввод пользователя: встроенную команду или обычный prompt.
     */
    fun handle(input: String): CliSessionControllerResult {
        return when (val command = commandParser.parse(input)) {
            CliCommand.Empty -> CliSessionControllerResult.Continue
            CliCommand.Exit -> {
                appEventSink.emit(AppEvent.SessionFinished)
                CliSessionControllerResult.ExitRequested
            }

            CliCommand.Clear -> {
                state.agent.clearContext()
                appEventSink.emit(AppEvent.ContextCleared)
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowModels -> {
                appEventSink.emit(
                    AppEvent.ModelsAvailable(
                        options = availableModelsProvider(config),
                        currentModelId = state.modelId
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateCheckpoint -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(
                        AppEvent.CheckpointCreated(
                            branchingCapability.createCheckpoint(command.name)
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowBranches -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchStatusAvailable(branchingCapability.branchStatus()))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.CreateBranch -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchCreated(branchingCapability.createBranch(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchBranch -> {
                try {
                    val branchingCapability = state.agent.capability<BranchingCapability>()
                        ?: error("Команды ветвления доступны только для стратегии Branching.")
                    appEventSink.emit(AppEvent.BranchSwitched(branchingCapability.switchBranch(command.name)))
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchModel -> {
                switchModel(command.modelId)
                CliSessionControllerResult.Continue
            }

            is CliCommand.UserPrompt -> {
                try {
                    appEventSink.emit(AppEvent.TokenPreviewAvailable(state.agent.previewTokenStats(command.value)))
                    val response = state.agent.ask(command.value)
                    appEventSink.emit(
                        AppEvent.AssistantResponseAvailable(
                            role = ChatRole.ASSISTANT,
                            content = response.content,
                            tokenStats = response.tokenStats
                        )
                    )
                } catch (error: Exception) {
                    appEventSink.emit(AppEvent.RequestFailed(error.message))
                }

                CliSessionControllerResult.Continue
            }
        }
    }

    private fun switchModel(requestedModelId: String) {
        try {
            val languageModel = createLanguageModel(
                requestedModelId,
                config,
                httpClient
            )
            warmUpLanguageModel(languageModel, lifecycleListener)
            val selectedStrategy = selectMemoryStrategy()
            val agent = createAgent(
                languageModel,
                lifecycleListener,
                selectedStrategy.type
            )
            state = state.copy(
                modelId = requestedModelId,
                languageModel = languageModel,
                agent = agent,
                memoryStrategyOption = selectedStrategy
            )
            appEventSink.emit(AppEvent.ModelChanged)
            appEventSink.emit(
                AppEvent.AgentInfoAvailable(
                    info = agent.info,
                    strategy = selectedStrategy
                )
            )
        } catch (error: Exception) {
            appEventSink.emit(AppEvent.ModelSwitchFailed(error.message))
        }
    }
}

/**
 * Результат обработки одного шага CLI-сессии.
 */
sealed interface CliSessionControllerResult {
    /**
     * Сессия продолжается, можно читать следующий ввод.
     */
    data object Continue : CliSessionControllerResult

    /**
     * Пользователь завершил работу сессии.
     */
    data object ExitRequested : CliSessionControllerResult
}
