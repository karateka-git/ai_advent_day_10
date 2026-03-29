/**
 * Разбирает строку пользовательского ввода в типизированную CLI-команду.
 */
class CliCommandParser {
    /**
     * Преобразует пользовательский ввод в одну из встроенных команд или обычный prompt.
     */
    fun parse(input: String): CliCommand =
        when {
            input.isEmpty() -> CliCommand.Empty
            input.equals(CliCommands.EXIT, ignoreCase = true) ||
                input.equals(CliCommands.QUIT, ignoreCase = true) -> CliCommand.Exit

            input.equals(CliCommands.CLEAR, ignoreCase = true) -> CliCommand.Clear
            input.equals(CliCommands.MODELS, ignoreCase = true) -> CliCommand.ShowModels
            input.startsWith("${CliCommands.USE} ", ignoreCase = true) ->
                CliCommand.SwitchModel(input.substringAfter(' ').trim())

            else -> CliCommand.UserPrompt(input)
        }
}

/**
 * Типизированные команды CLI после разбора пользовательского ввода.
 */
sealed interface CliCommand {
    /**
     * Пустой ввод, который нужно проигнорировать.
     */
    data object Empty : CliCommand

    /**
     * Запрос на завершение сессии.
     */
    data object Exit : CliCommand

    /**
     * Очистка текущего контекста диалога.
     */
    data object Clear : CliCommand

    /**
     * Показ списка доступных моделей.
     */
    data object ShowModels : CliCommand

    /**
     * Переключение модели по её CLI-идентификатору.
     */
    data class SwitchModel(
        val modelId: String
    ) : CliCommand

    /**
     * Обычное сообщение пользователя, которое нужно отправить агенту.
     */
    data class UserPrompt(
        val value: String
    ) : CliCommand
}
