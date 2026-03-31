package devtools.comparison

import agent.memory.MemoryStrategyFactory
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlinx.serialization.json.Json
import llm.core.LanguageModel
import llm.core.LanguageModelFactory

private const val CONFIG_FILE = "config/app.properties"
private const val COMPARISON_TEMPERATURE = 0.0
private const val COMPARISON_STEPS_PROPERTY = "comparison.steps"
private const val COMPARISON_JUDGE_PROPERTY = "comparison.judge"
private val reportJson = Json {
    prettyPrint = true
}

/**
 * Точка входа dev-инструмента, который прогоняет один сценарий на нескольких
 * стратегиях памяти и сохраняет JSON-отчёт для последующего сравнения.
 */
fun main() {
    configureUtf8Console()

    val config = loadConfig()
    val httpClient = HttpClient.newHttpClient()
    val selectedModelId = defaultModelId(config)
    val languageModel = LanguageModelFactory.create(
        modelId = selectedModelId,
        config = config,
        httpClient = httpClient,
        temperature = COMPARISON_TEMPERATURE
    )

    warmUpTokenCounter(languageModel)

    val scenario = defaultTechnicalSpecificationScenario()
        .limitedToConfiguredSteps()
    val judgeEnabled = System.getProperty(COMPARISON_JUDGE_PROPERTY)?.toBooleanStrictOrNull() == true
    val reportPath = Path.of("build", "reports", "strategy-comparison", "report.json")
    val strategies = MemoryStrategyFactory.availableOptions()
        .filter { it.id in setOf("no_compression", "summary_compression", "sliding_window") }
    val service = StrategyComparisonService()
    val report = service.compare(
        selectedModelId = selectedModelId,
        providerModelName = languageModel.info.model,
        scenario = scenario,
        strategies = strategies,
        executor = DefaultStrategyConversationExecutor(
            baseLanguageModel = languageModel,
            stateDirectory = Path.of("build", "strategy-comparison", "state"),
            onStepStarted = { option, stepNumber, totalSteps ->
                println("[${option.id}] шаг $stepNumber/$totalSteps...")
            },
            onStepFinished = { option, step, totalSteps ->
                println(
                    "[${option.id}] шаг ${step.stepNumber}/$totalSteps завершён: " +
                        "вызовов модели=${step.modelCallCount}, prompt-токены=${step.promptTokensLocal ?: "н/д"}"
                )
            }
        ),
        onStrategyStarted = { option, index, total ->
            println("Стратегия $index/$total: ${option.displayName} (${option.id})")
        },
        onStrategyFinished = { option, execution, completedExecutions, _ ->
            val partialReport = service.createReport(
                selectedModelId = selectedModelId,
                providerModelName = languageModel.info.model,
                scenario = scenario,
                executions = completedExecutions
            )
            saveReport(reportPath, partialReport)
            println("Стратегия ${option.id} завершена.")
            println("Промежуточный отчёт сохранён (${completedExecutions.size} стратегий готово).")
            println("Длина финального ответа=${execution.finalResponse.length} символов.")
            println()
        }
    )

    val finalReport =
        if (judgeEnabled) {
            println()
            println("Запускаем дополнительную judge-оценку...")
            val judgeResult = StrategyComparisonJudgeService().evaluate(
                judgeModelId = selectedModelId,
                judgeInput = report.judgeInput,
                languageModel = languageModel
            )
            service.createReport(
                selectedModelId = selectedModelId,
                providerModelName = languageModel.info.model,
                scenario = scenario,
                executions = report.executions,
                judgeResult = judgeResult
            )
        } else {
            report
        }

    saveReport(reportPath, finalReport)
    println()
    println(StrategyComparisonConsoleFormatter.format(finalReport, reportPath))
}

/**
 * Принудительно настраивает UTF-8 для stdout/stderr внутри comparison runner.
 */
private fun configureUtf8Console() {
    val utf8Out = PrintStream(FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8)
    val utf8Err = PrintStream(FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8)
    System.setOut(utf8Out)
    System.setErr(utf8Err)
}

/**
 * Возвращает сценарий по умолчанию для сравнения стратегий на задаче сбора ТЗ.
 */
private fun defaultTechnicalSpecificationScenario(): StrategyComparisonScenario =
    StrategyComparisonScenario(
        name = "Собираем ТЗ на чат-бота",
        prompts = listOf(
            "Помоги собрать ТЗ на Telegram-бота для школы английского языка.",
            "Цель бота: записывать учеников на пробный урок и отвечать на частые вопросы.",
            "Бот должен работать на русском языке и без голосовых сообщений.",
            "Интеграции нужны с Google Sheets и Telegram, но без CRM на первом этапе.",
            "Бюджет на MVP до 120 тысяч рублей.",
            "Срок запуска MVP две недели.",
            "Важно, чтобы администратор мог менять FAQ без участия разработчика.",
            "Пользователи должны выбирать удобный слот из доступного расписания.",
            "Нужна аналитика: сколько заявок пришло и сколько дошло до пробного урока.",
            "Не добавляем оплату внутри бота на первом этапе.",
            "Сделай список открытых вопросов, которые ещё нужно уточнить у заказчика.",
            "Теперь собери итоговое краткое ТЗ списком: цель, функции, ограничения, интеграции, сроки и открытые вопросы."
        )
    )

/**
 * Возвращает сценарий, ограниченный числом шагов из системного свойства, если оно задано.
 */
private fun StrategyComparisonScenario.limitedToConfiguredSteps(): StrategyComparisonScenario {
    val configuredSteps = System.getProperty(COMPARISON_STEPS_PROPERTY)?.toIntOrNull()
        ?: return this
    if (configuredSteps <= 0) {
        return this
    }

    return copy(prompts = prompts.take(configuredSteps))
}

/**
 * Сохраняет сериализованный отчёт сравнения стратегий в файл.
 */
private fun saveReport(
    reportPath: Path,
    report: StrategyComparisonReport
) {
    Files.createDirectories(reportPath.parent)
    Files.writeString(
        reportPath,
        reportJson.encodeToString(report),
        StandardCharsets.UTF_8
    )
}

/**
 * Принудительно прогревает локальный токенизатор до старта серии сравнений.
 */
private fun warmUpTokenCounter(languageModel: LanguageModel) {
    languageModel.tokenCounter?.countText("")
}

/**
 * Возвращает идентификатор первой настроенной модели, доступной в конфигурации.
 */
private fun defaultModelId(config: Properties): String =
    LanguageModelFactory.availableModels(config)
        .firstOrNull { it.isConfigured }
        ?.id
        ?: error("Не найдена ни одна доступная модель. Проверьте токены в config/app.properties.")

/**
 * Загружает локальный конфиг приложения, используемый comparison runner.
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
