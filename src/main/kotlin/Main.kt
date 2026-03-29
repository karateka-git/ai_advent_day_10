import agent.core.Agent
import agent.impl.MrAgent
import agent.lifecycle.AgentLifecycleListener
import agent.lifecycle.UiEventLifecycleListener
import agent.memory.MemoryStrategyFactory
import agent.memory.MemoryStrategyOption
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.http.HttpClient
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory
import llm.core.model.ChatRole
import ui.UiEvent
import ui.UiEventSink
import ui.cli.CliRenderer

private const val CONFIG_FILE = "config/app.properties"

private val consoleReader = BufferedReader(
    InputStreamReader(System.`in`, detectConsoleCharset())
)
private val systemConsole = System.console()

/**
 * Точка входа CLI-приложения.
 *
 * Приложение загружает конфигурацию, выбирает LLM-провайдер, предлагает пользователю выбрать
 * стратегию памяти и затем запускает интерактивный цикл чата.
 */
fun main() {
    val config = loadConfig()
    val httpClient = HttpClient.newHttpClient()
    val uiEventSink: UiEventSink = CliRenderer()
    val lifecycleListener: AgentLifecycleListener = UiEventLifecycleListener(uiEventSink)

    val languageModel: LanguageModel = LanguageModelFactory.createDefault(
        config = config,
        httpClient = httpClient
    )
    warmUpTokenCounter(languageModel, lifecycleListener)

    val selectedMemoryStrategyOption = selectMemoryStrategyOption(uiEventSink)
    val agent: Agent<String> = createAgent(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        strategyId = selectedMemoryStrategyOption.id
    )
    val sessionController = CliSessionController(
        initialState = CliSessionState(
            modelId = defaultModelId(config),
            languageModel = languageModel,
            agent = agent,
            memoryStrategyOption = selectedMemoryStrategyOption
        ),
        config = config,
        httpClient = httpClient,
        lifecycleListener = lifecycleListener,
        uiEventSink = uiEventSink,
        createLanguageModel = LanguageModelFactory::create,
        createAgent = ::createAgent,
        warmUpLanguageModel = ::warmUpTokenCounter
    )

    uiEventSink.emit(
        UiEvent.SessionStarted(
            modelsCommand = CliCommands.MODELS,
            useCommand = CliCommands.USE
        )
    )
    uiEventSink.emit(
        UiEvent.AgentInfoAvailable(
            info = sessionController.state.agent.info,
            strategy = selectedMemoryStrategyOption
        )
    )

    while (true) {
        uiEventSink.emit(UiEvent.UserInputPrompt(ChatRole.USER))
        val prompt = readConsoleLine()?.trim() ?: break

        when (sessionController.handle(prompt)) {
            CliSessionControllerResult.Continue -> continue
            CliSessionControllerResult.ExitRequested -> break
        }
    }
}

/**
 * Возвращает идентификатор модели, которая будет выбрана по умолчанию при старте приложения.
 */
private fun defaultModelId(config: Properties): String =
    LanguageModelFactory.availableModels(config)
        .firstOrNull { it.isConfigured }
        ?.id
        ?: error("Не найдена ни одна доступная модель. Проверьте токены в config/app.properties.")

/**
 * Создаёт новый экземпляр агента для выбранной модели и стратегии памяти.
 */
private fun createAgent(
    languageModel: LanguageModel,
    lifecycleListener: AgentLifecycleListener,
    strategyId: String
): Agent<String> =
    MrAgent(
        languageModel = languageModel,
        lifecycleListener = lifecycleListener,
        memoryStrategy = MemoryStrategyFactory.create(
            strategyId = strategyId,
            languageModel = languageModel
        )
    )

/**
 * Предлагает пользователю выбрать одну из доступных стратегий памяти перед стартом чата.
 */
private fun selectMemoryStrategyOption(uiEventSink: UiEventSink): MemoryStrategyOption {
    val options = MemoryStrategyFactory.availableOptions()
    uiEventSink.emit(UiEvent.MemoryStrategySelectionRequested(options))

    while (true) {
        uiEventSink.emit(UiEvent.MemoryStrategySelectionPromptRequested(options.size))
        val selection = readConsoleLine()?.trim().orEmpty()
        val index = selection.toIntOrNull()

        if (index != null && index in 1..options.size) {
            val option = options[index - 1]
            uiEventSink.emit(UiEvent.MemoryStrategySelected(option))
            return option
        }

        uiEventSink.emit(UiEvent.MemoryStrategySelectionRejected)
    }
}

/**
 * Принудительно прогревает лениво создаваемый токенизатор перед стартом чата, чтобы первая
 * оценка токенов не была слишком долгой.
 */
private fun warmUpTokenCounter(
    languageModel: LanguageModel,
    lifecycleListener: AgentLifecycleListener
) {
    lifecycleListener.onModelWarmupStarted()
    try {
        languageModel.tokenCounter?.countText("")
    } finally {
        lifecycleListener.onModelWarmupFinished()
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

/**
 * Загружает свойства приложения из локального файла конфигурации.
 */
private fun loadConfig(): Properties {
    val configPath = Path.of(CONFIG_FILE)
    require(Files.exists(configPath)) {
        "Файл конфигурации $CONFIG_FILE не найден. Создайте его на основе config/app.properties.example."
    }

    return Properties().apply {
        Files.newInputStream(configPath).use(::load)
    }
}
