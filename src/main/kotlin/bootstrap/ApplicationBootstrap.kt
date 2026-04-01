package bootstrap

import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import llm.core.LanguageModel
import llm.core.LanguageModelFactory

private const val DEFAULT_CONFIG_FILE = "config/app.properties"
private const val DEFAULT_TEMPERATURE = 0.7

/**
 * Собирает базовое runtime-окружение приложения независимо от конкретного UI-адаптера.
 */
object ApplicationBootstrap {
    /**
     * Создаёт runtime-контекст приложения с загруженной конфигурацией, HTTP-клиентом и моделью.
     */
    fun createRuntime(
        modelId: String? = null,
        temperature: Double = DEFAULT_TEMPERATURE
    ): ApplicationRuntime {
        val config = loadConfig()
        val httpClient = HttpClient.newHttpClient()
        val selectedModelId = modelId ?: defaultModelId(config)
        val languageModel = LanguageModelFactory.create(
            modelId = selectedModelId,
            config = config,
            httpClient = httpClient,
            temperature = temperature
        )

        return ApplicationRuntime(
            config = config,
            httpClient = httpClient,
            selectedModelId = selectedModelId,
            languageModel = languageModel
        )
    }

    /**
     * Возвращает идентификатор первой доступной и настроенной модели.
     */
    fun defaultModelId(config: Properties): String =
        LanguageModelFactory.availableModels(config)
            .firstOrNull { it.isConfigured }
            ?.id
            ?: error("Не найдена ни одна доступная модель. Проверьте токены в config/app.properties.")

    /**
     * Принудительно прогревает локальный токенизатор до начала работы приложения.
     */
    fun warmUpTokenCounter(
        languageModel: LanguageModel,
        onStarted: () -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        onStarted()
        try {
            languageModel.tokenCounter?.countText("")
        } finally {
            onFinished()
        }
    }

    /**
     * Загружает свойства приложения из локального файла конфигурации.
     */
    fun loadConfig(configPath: Path = Path.of(DEFAULT_CONFIG_FILE)): Properties {
        require(Files.exists(configPath)) {
            "Файл конфигурации $configPath не найден. Создайте его на основе config/app.properties.example."
        }

        return Properties().apply {
            Files.newInputStream(configPath).use(::load)
        }
    }
}

/**
 * Базовый runtime-контекст приложения без привязки к конкретному UI-слою.
 *
 * @property config загруженная конфигурация приложения.
 * @property httpClient общий HTTP-клиент для инфраструктурных адаптеров.
 * @property selectedModelId идентификатор выбранной модели.
 * @property languageModel созданный экземпляр модели.
 */
data class ApplicationRuntime(
    val config: Properties,
    val httpClient: HttpClient,
    val selectedModelId: String,
    val languageModel: LanguageModel
)
