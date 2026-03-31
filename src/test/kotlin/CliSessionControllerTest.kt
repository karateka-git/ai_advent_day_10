import agent.core.Agent
import agent.core.AgentInfo
import agent.core.AgentResponse
import agent.core.AgentTokenStats
import agent.format.ResponseFormat
import agent.format.TextResponseFormat
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.MemoryStrategyOption
import agent.memory.MemoryStrategyType
import java.net.http.HttpClient
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelOption
import llm.core.model.LanguageModelResponse
import ui.UiEvent
import ui.UiEventSink

class CliSessionControllerTest {
    private val config = Properties()
    private val httpClient = HttpClient.newHttpClient()
    private val lifecycleListener: AgentLifecycleListener = NoOpAgentLifecycleListener
    private val strategyOption = MemoryStrategyOption(
        type = MemoryStrategyType.SUMMARY_COMPRESSION,
        displayName = "Сжатие через summary",
        description = "Тестовая стратегия."
    )

    @Test
    fun `clears context for clear command`() {
        val agent = FakeAgent(model = "initial-model")
        val sink = RecordingUiEventSink()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("clear")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(1, agent.clearCalls)
        assertEquals(listOf<UiEvent>(UiEvent.ContextCleared), sink.events)
    }

    @Test
    fun `shows available models using current session model id`() {
        val sink = RecordingUiEventSink()
        val controller = createController(
            sink = sink,
            initialState = initialState(modelId = "timeweb"),
            availableModelsProvider = {
                listOf(
                    LanguageModelOption(
                        id = "timeweb",
                        displayName = "Timeweb",
                        isConfigured = true
                    )
                )
            }
        )

        val result = controller.handle("models")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<UiEvent>(
                UiEvent.ModelsAvailable(
                    options = listOf(
                        LanguageModelOption(
                            id = "timeweb",
                            displayName = "Timeweb",
                            isConfigured = true
                        )
                    ),
                    currentModelId = "timeweb"
                )
            ),
            sink.events
        )
    }

    @Test
    fun `switches model asks for memory strategy and updates session state`() {
        val sink = RecordingUiEventSink()
        val createdModel = FakeLanguageModel(
            name = "HuggingFaceLanguageModel",
            model = "hf-model"
        )
        val recreatedAgent = FakeAgent(model = "hf-model")
        val slidingWindowOption = MemoryStrategyOption(
            type = MemoryStrategyType.SLIDING_WINDOW,
            displayName = "Скользящее окно",
            description = "Тестовая стратегия."
        )
        var warmUpCalls = 0
        var selectionCalls = 0
        val controller = createController(
            sink = sink,
            createLanguageModel = { modelId, _, _ ->
                assertEquals("huggingface", modelId)
                createdModel
            },
            createAgent = { languageModel, _, strategyId ->
                assertSame(createdModel, languageModel)
                assertEquals(slidingWindowOption.type, strategyId)
                recreatedAgent
            },
            selectMemoryStrategy = {
                selectionCalls++
                slidingWindowOption
            },
            warmUpLanguageModel = { languageModel, _ ->
                assertSame(createdModel, languageModel)
                warmUpCalls++
            }
        )

        val result = controller.handle("use huggingface")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals("huggingface", controller.state.modelId)
        assertSame(createdModel, controller.state.languageModel)
        assertSame(recreatedAgent, controller.state.agent)
        assertEquals(slidingWindowOption, controller.state.memoryStrategyOption)
        assertEquals(1, selectionCalls)
        assertEquals(1, warmUpCalls)
        assertEquals(
            listOf(
                UiEvent.ModelChanged,
                UiEvent.AgentInfoAvailable(
                    info = recreatedAgent.info,
                    strategy = slidingWindowOption
                )
            ),
            sink.events
        )
    }

    @Test
    fun `returns exit for exit command`() {
        val sink = RecordingUiEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("exit")

        assertEquals(CliSessionControllerResult.ExitRequested, result)
        assertEquals(listOf<UiEvent>(UiEvent.SessionFinished), sink.events)
    }

    @Test
    fun `emits model switch failure for unknown model`() {
        val sink = RecordingUiEventSink()
        val controller = createController(
            sink = sink,
            createLanguageModel = { _, _, _ ->
                error("Неизвестная модель")
            }
        )

        val result = controller.handle("use unknown")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<UiEvent>(UiEvent.ModelSwitchFailed("Неизвестная модель")),
            sink.events
        )
    }

    @Test
    fun `handles regular prompt through current agent`() {
        val sink = RecordingUiEventSink()
        val agent = FakeAgent(
            model = "initial-model",
            previewTokenStats = AgentTokenStats(historyTokens = 10),
            response = AgentResponse(
                content = "Ответ",
                tokenStats = AgentTokenStats(promptTokensLocal = 15)
            )
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("Расскажи историю")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(listOf("Расскажи историю"), agent.previewInputs)
        assertEquals(listOf("Расскажи историю"), agent.askInputs)
        assertEquals(
            listOf(
                UiEvent.TokenPreviewAvailable(AgentTokenStats(historyTokens = 10)),
                UiEvent.AssistantResponseAvailable(
                    role = llm.core.model.ChatRole.ASSISTANT,
                    content = "Ответ",
                    tokenStats = AgentTokenStats(promptTokensLocal = 15)
                )
            ),
            sink.events
        )
    }

    private fun createController(
        sink: RecordingUiEventSink = RecordingUiEventSink(),
        initialState: CliSessionState = initialState(),
        createLanguageModel: (String, Properties, HttpClient) -> LanguageModel = { _, _, _ ->
            error("Не должен вызываться в этом тесте.")
        },
        availableModelsProvider: (Properties) -> List<LanguageModelOption> = { emptyList() },
        createAgent: (LanguageModel, AgentLifecycleListener, MemoryStrategyType) -> Agent<String> = { _, _, _ ->
            error("Не должен вызываться в этом тесте.")
        },
        selectMemoryStrategy: () -> MemoryStrategyOption = { strategyOption },
        warmUpLanguageModel: (LanguageModel, AgentLifecycleListener) -> Unit = { _, _ -> Unit }
    ): CliSessionController =
        CliSessionController(
            initialState = initialState,
            config = config,
            httpClient = httpClient,
            lifecycleListener = lifecycleListener,
            uiEventSink = sink,
            createLanguageModel = createLanguageModel,
            availableModelsProvider = availableModelsProvider,
            createAgent = createAgent,
            selectMemoryStrategy = selectMemoryStrategy,
            warmUpLanguageModel = warmUpLanguageModel
        )

    private fun initialState(
        modelId: String = "timeweb",
        languageModel: LanguageModel = FakeLanguageModel(
            name = "TimewebLanguageModel",
            model = "initial-model"
        ),
        agent: Agent<String> = FakeAgent(model = "initial-model")
    ): CliSessionState =
        CliSessionState(
            modelId = modelId,
            languageModel = languageModel,
            agent = agent,
            memoryStrategyOption = strategyOption
        )
}

private class RecordingUiEventSink : UiEventSink {
    val events = mutableListOf<UiEvent>()

    override fun emit(event: UiEvent) {
        events += event
    }
}

private class FakeAgent(
    model: String,
    private val previewTokenStats: AgentTokenStats = AgentTokenStats(),
    private val response: AgentResponse<String> = AgentResponse(
        content = "ok",
        tokenStats = AgentTokenStats()
    )
) : Agent<String> {
    override val info: AgentInfo = AgentInfo(
        name = "TestAgent",
        description = "Тестовый агент",
        model = model
    )
    override val responseFormat: ResponseFormat<String> = TextResponseFormat

    var clearCalls: Int = 0
    val previewInputs = mutableListOf<String>()
    val askInputs = mutableListOf<String>()

    override fun previewTokenStats(userPrompt: String): AgentTokenStats {
        previewInputs += userPrompt
        return previewTokenStats
    }

    override fun ask(userPrompt: String): AgentResponse<String> {
        askInputs += userPrompt
        return response
    }

    override fun clearContext() {
        clearCalls++
    }

    override fun replaceContextFromFile(sourcePath: Path) {
        error("Не должен вызываться в этом тесте.")
    }
}

private class FakeLanguageModel(
    name: String,
    model: String
) : LanguageModel {
    override val info = LanguageModelInfo(
        name = name,
        model = model
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}
