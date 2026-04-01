import agent.core.Agent
import agent.impl.MrAgent
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.AppEventLifecycleListener
import agent.memory.strategy.MemoryStrategyFactory
import agent.memory.strategy.MemoryStrategyOption
import agent.memory.strategy.MemoryStrategyType
import bootstrap.ApplicationBootstrap
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import app.output.AppEvent
import app.output.AppEventSink
import ui.cli.CliCommands
import ui.cli.CliSessionController
import ui.cli.CliSessionControllerResult
import ui.cli.CliSessionState
import ui.cli.CliRenderer

private val consoleReader = BufferedReader(
    InputStreamReader(System.`in`, detectConsoleCharset())
)
private val systemConsole = System.console()

/**
 * Точка входа CLI-приложения.
 *
 * Приложение загружает конфигурацию, выбирает LLM-провайдер, создаёт агента
 * со стратегией памяти по умолчанию и затем запускает интерактивный цикл чата.
 */
fun main() {
    val runtime = ApplicationBootstrap.createRuntime()
    val appEventSink: AppEventSink = CliRenderer()
    val lifecycleListener: AgentLifecycleListener = AppEventLifecycleListener(appEventSink)
    val languageModel = runtime.languageModel
    ApplicationBootstrap.warmUpTokenCounter(
        languageModel = languageModel,
        onStarted = lifecycleListener::onModelWarmupStarted,
        onFinished = lifecycleListener::onModelWarmupFinished
    )

    val defaultMemoryStrategyOption = MemoryStrategyFactory.defaultOption()
    val agent: Agent<String> = createAgent(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        strategyType = defaultMemoryStrategyOption.type
    )
    val sessionController = CliSessionController(
        initialState = CliSessionState(
            modelId = runtime.selectedModelId,
            languageModel = languageModel,
            agent = agent,
            memoryStrategyOption = defaultMemoryStrategyOption
        ),
        config = runtime.config,
        httpClient = runtime.httpClient,
        lifecycleListener = lifecycleListener,
        appEventSink = appEventSink,
        createLanguageModel = LanguageModelFactory::create,
        createAgent = ::createAgent,
        selectMemoryStrategy = { selectMemoryStrategyOption(appEventSink) },
        warmUpLanguageModel = { model, listener ->
            ApplicationBootstrap.warmUpTokenCounter(
                languageModel = model,
                onStarted = listener::onModelWarmupStarted,
                onFinished = listener::onModelWarmupFinished
            )
        }
    )

    appEventSink.emit(
        AppEvent.SessionStarted(
            modelsCommand = CliCommands.MODELS,
            useCommand = CliCommands.USE
        )
    )
    appEventSink.emit(
        AppEvent.AgentInfoAvailable(
            info = sessionController.state.agent.info,
            strategy = defaultMemoryStrategyOption
        )
    )

    while (true) {
        appEventSink.emit(AppEvent.UserInputPrompt(ChatRole.USER))
        val prompt = readConsoleLine()?.trim() ?: break

        when (sessionController.handle(prompt)) {
            CliSessionControllerResult.Continue -> continue
            CliSessionControllerResult.ExitRequested -> break
        }
    }
}

/**
 * Создаёт новый экземпляр агента для выбранной модели и стратегии памяти.
 */
private fun createAgent(
    languageModel: LanguageModel,
    lifecycleListener: AgentLifecycleListener,
    strategyType: MemoryStrategyType
): Agent<String> =
    MrAgent(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        memoryStrategy = MemoryStrategyFactory.create(
            strategyType = strategyType,
            languageModel = languageModel
        )
    )

/**
 * Предлагает пользователю выбрать одну из доступных стратегий памяти при смене модели.
 */
private fun selectMemoryStrategyOption(appEventSink: AppEventSink): MemoryStrategyOption {
    val options = MemoryStrategyFactory.availableOptions()
    appEventSink.emit(AppEvent.MemoryStrategySelectionRequested(options))

    while (true) {
        appEventSink.emit(AppEvent.MemoryStrategySelectionPromptRequested(options.size))
        val selection = readConsoleLine()?.trim().orEmpty()
        val index = selection.toIntOrNull()

        if (index != null && index in 1..options.size) {
            val option = options[index - 1]
            appEventSink.emit(AppEvent.MemoryStrategySelected(option))
            return option
        }

        appEventSink.emit(AppEvent.MemoryStrategySelectionRejected)
    }
}

/**
 * Определяет кодировку, используемую текущей консольной сессией.
 */
private fun detectConsoleCharset(): Charset {
    val nativeEncoding = System.getProperty("native.encoding")
    return if (nativeEncoding.isNullOrBlank()) {
        Charset.defaultCharset()
    } else {
        Charset.forName(nativeEncoding)
    }
}

/**
 * Читает одну строку из консоли, предпочитая нативный API консоли, если он доступен.
 */
private fun readConsoleLine(): String? = systemConsole?.readLine() ?: consoleReader.readLine()


