import agent.core.Agent
import agent.lifecycle.AgentLifecycleListener
import agent.memory.MemoryStrategyOption
import agent.memory.MemoryStrategyType
import java.net.http.HttpClient
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption
import ui.UiEvent
import ui.UiEventSink

/**
 * Исполняет команды CLI и обычные пользовательские prompt в рамках одной сессии.
 */
class CliSessionController(
    initialState: CliSessionState,
    private val config: Properties,
    private val httpClient: HttpClient,
    private val lifecycleListener: AgentLifecycleListener,
    private val uiEventSink: UiEventSink,
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
                uiEventSink.emit(UiEvent.SessionFinished)
                CliSessionControllerResult.ExitRequested
            }

            CliCommand.Clear -> {
                state.agent.clearContext()
                uiEventSink.emit(UiEvent.ContextCleared)
                CliSessionControllerResult.Continue
            }

            CliCommand.ShowModels -> {
                uiEventSink.emit(
                    UiEvent.ModelsAvailable(
                        options = availableModelsProvider(config),
                        currentModelId = state.modelId
                    )
                )
                CliSessionControllerResult.Continue
            }

            is CliCommand.SwitchModel -> {
                switchModel(command.modelId)
                CliSessionControllerResult.Continue
            }

            is CliCommand.UserPrompt -> {
                try {
                    uiEventSink.emit(UiEvent.TokenPreviewAvailable(state.agent.previewTokenStats(command.value)))
                    val response = state.agent.ask(command.value)
                    uiEventSink.emit(
                        UiEvent.AssistantResponseAvailable(
                            role = ChatRole.ASSISTANT,
                            content = response.content,
                            tokenStats = response.tokenStats
                        )
                    )
                } catch (error: Exception) {
                    uiEventSink.emit(UiEvent.RequestFailed(error.message))
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
            uiEventSink.emit(UiEvent.ModelChanged)
            uiEventSink.emit(
                UiEvent.AgentInfoAvailable(
                    info = agent.info,
                    strategy = selectedStrategy
                )
            )
        } catch (error: Exception) {
            uiEventSink.emit(UiEvent.ModelSwitchFailed(error.message))
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
