import agent.capability.AgentCapability
import agent.capability.BranchingCapability
import agent.core.Agent
import agent.core.AgentInfo
import agent.core.AgentResponse
import agent.core.AgentTokenStats
import agent.core.BranchCheckpointInfo
import agent.core.BranchInfo
import agent.core.BranchingStatus
import agent.format.ResponseFormat
import agent.format.TextResponseFormat
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.NoOpAgentLifecycleListener
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import app.output.AppEvent
import app.output.AppEventSink
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
import ui.cli.CliSessionController
import ui.cli.CliSessionControllerResult
import ui.cli.CliSessionState

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
        val sink = RecordingAppEventSink()
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("clear")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(1, agent.clearCalls)
        assertEquals(listOf<AppEvent>(AppEvent.ContextCleared), sink.events)
    }

    @Test
    fun `shows available models using current session model id`() {
        val sink = RecordingAppEventSink()
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
            listOf<AppEvent>(
                AppEvent.ModelsAvailable(
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
        val sink = RecordingAppEventSink()
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
                AppEvent.ModelChanged,
                AppEvent.AgentInfoAvailable(
                    info = recreatedAgent.info,
                    strategy = slidingWindowOption
                )
            ),
            sink.events
        )
    }

    @Test
    fun `returns exit for exit command`() {
        val sink = RecordingAppEventSink()
        val controller = createController(sink = sink)

        val result = controller.handle("exit")

        assertEquals(CliSessionControllerResult.ExitRequested, result)
        assertEquals(listOf<AppEvent>(AppEvent.SessionFinished), sink.events)
    }

    @Test
    fun `emits model switch failure for unknown model`() {
        val sink = RecordingAppEventSink()
        val controller = createController(
            sink = sink,
            createLanguageModel = { _, _, _ ->
                error("Неизвестная модель")
            }
        )

        val result = controller.handle("use unknown")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(AppEvent.ModelSwitchFailed("Неизвестная модель")),
            sink.events
        )
    }

    @Test
    fun `creates checkpoint through current agent`() {
        val sink = RecordingAppEventSink()
        val agent = FakeAgent(
            model = "initial-model",
            checkpointInfo = BranchCheckpointInfo(
                name = "checkpoint-1",
                sourceBranchName = "main"
            )
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("checkpoint")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(
                AppEvent.CheckpointCreated(
                    BranchCheckpointInfo(name = "checkpoint-1", sourceBranchName = "main")
                )
            ),
            sink.events
        )
    }

    @Test
    fun `shows branch status through current agent`() {
        val sink = RecordingAppEventSink()
        val status = BranchingStatus(
            activeBranchName = "main",
            latestCheckpointName = "checkpoint-1",
            branches = listOf(
                BranchInfo(name = "main", isActive = true),
                BranchInfo(name = "option-a", sourceCheckpointName = "checkpoint-1")
            )
        )
        val agent = FakeAgent(
            model = "initial-model",
            branchingStatus = status
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("branches")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(AppEvent.BranchStatusAvailable(status)),
            sink.events
        )
    }

    @Test
    fun `creates branch through current agent`() {
        val sink = RecordingAppEventSink()
        val branchInfo = BranchInfo(
            name = "option-a",
            sourceCheckpointName = "checkpoint-1",
            isActive = false
        )
        val agent = FakeAgent(
            model = "initial-model",
            createdBranchInfo = branchInfo
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("branch create option-a")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(AppEvent.BranchCreated(branchInfo)),
            sink.events
        )
    }

    @Test
    fun `switches branch through current agent`() {
        val sink = RecordingAppEventSink()
        val branchInfo = BranchInfo(
            name = "option-b",
            sourceCheckpointName = "checkpoint-1",
            isActive = true
        )
        val agent = FakeAgent(
            model = "initial-model",
            switchedBranchInfo = branchInfo
        )
        val controller = createController(
            sink = sink,
            initialState = initialState(agent = agent)
        )

        val result = controller.handle("branch use option-b")

        assertEquals(CliSessionControllerResult.Continue, result)
        assertEquals(
            listOf<AppEvent>(AppEvent.BranchSwitched(branchInfo)),
            sink.events
        )
    }

    @Test
    fun `handles regular prompt through current agent`() {
        val sink = RecordingAppEventSink()
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
                AppEvent.TokenPreviewAvailable(AgentTokenStats(historyTokens = 10)),
                AppEvent.AssistantResponseAvailable(
                    role = llm.core.model.ChatRole.ASSISTANT,
                    content = "Ответ",
                    tokenStats = AgentTokenStats(promptTokensLocal = 15)
                )
            ),
            sink.events
        )
    }

    private fun createController(
        sink: RecordingAppEventSink = RecordingAppEventSink(),
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
            appEventSink = sink,
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

private class RecordingAppEventSink : AppEventSink {
    val events = mutableListOf<AppEvent>()

    override fun emit(event: AppEvent) {
        events += event
    }
}

private class FakeAgent(
    model: String,
    private val previewTokenStats: AgentTokenStats = AgentTokenStats(),
    private val response: AgentResponse<String> = AgentResponse(
        content = "ok",
        tokenStats = AgentTokenStats()
    ),
    private val checkpointInfo: BranchCheckpointInfo = BranchCheckpointInfo(
        name = "checkpoint-1",
        sourceBranchName = "main"
    ),
    private val createdBranchInfo: BranchInfo = BranchInfo(
        name = "option-a",
        sourceCheckpointName = "checkpoint-1",
        isActive = false
    ),
    private val switchedBranchInfo: BranchInfo = BranchInfo(
        name = "option-a",
        sourceCheckpointName = "checkpoint-1",
        isActive = true
    ),
    private val branchingStatus: BranchingStatus = BranchingStatus(
        activeBranchName = "main",
        latestCheckpointName = null,
        branches = listOf(BranchInfo(name = "main", isActive = true))
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

    override fun <TCapability : AgentCapability> capability(capabilityType: Class<TCapability>): TCapability? {
        val branchingCapability = object : BranchingCapability {
            override fun createCheckpoint(name: String?): BranchCheckpointInfo = checkpointInfo

            override fun createBranch(name: String): BranchInfo = createdBranchInfo

            override fun switchBranch(name: String): BranchInfo = switchedBranchInfo

            override fun branchStatus(): BranchingStatus = branchingStatus
        }

        return capabilityType
            .takeIf { it.isInstance(branchingCapability) }
            ?.cast(branchingCapability)
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
