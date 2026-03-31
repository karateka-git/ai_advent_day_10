package ui

import agent.core.AgentInfo
import agent.core.AgentTokenStats
import agent.lifecycle.ContextCompressionStats
import agent.memory.MemoryStrategyOption
import llm.core.model.ChatRole
import llm.core.model.LanguageModelOption

/**
 * Типизированные события presentation-слоя, которые описывают, что должно быть показано
 * пользователю, не привязывая приложение к конкретному UI.
 */
sealed interface UiEvent {
    /**
     * Приложение готово показать стартовое сообщение с подсказками по командам.
     */
    data class SessionStarted(
        val modelsCommand: String,
        val useCommand: String
    ) : UiEvent

    /**
     * Нужно показать доступные стратегии памяти перед пересозданием агента.
     */
    data class MemoryStrategySelectionRequested(
        val options: List<MemoryStrategyOption>
    ) : UiEvent

    /**
     * Нужно показать prompt выбора стратегии памяти.
     */
    data class MemoryStrategySelectionPromptRequested(
        val optionsCount: Int
    ) : UiEvent

    /**
     * Пользователь успешно выбрал стратегию памяти.
     */
    data class MemoryStrategySelected(
        val option: MemoryStrategyOption
    ) : UiEvent

    /**
     * Пользователь ввёл некорректный номер стратегии памяти.
     */
    data object MemoryStrategySelectionRejected : UiEvent

    /**
     * Нужно показать сведения о текущем агенте и активной стратегии памяти.
     */
    data class AgentInfoAvailable(
        val info: AgentInfo,
        val strategy: MemoryStrategyOption
    ) : UiEvent

    /**
     * Нужно показать список доступных моделей и пометить активную.
     */
    data class ModelsAvailable(
        val options: List<LanguageModelOption>,
        val currentModelId: String
    ) : UiEvent

    /**
     * Нужно вывести приглашение ко вводу очередного сообщения пользователя.
     */
    data class UserInputPrompt(
        val role: ChatRole
    ) : UiEvent

    /**
     * Нужно показать ответ ассистента и связанную статистику токенов.
     */
    data class AssistantResponseAvailable(
        val role: ChatRole,
        val content: String,
        val tokenStats: AgentTokenStats
    ) : UiEvent

    /**
     * Нужно показать предварительную оценку токенов перед запросом.
     */
    data class TokenPreviewAvailable(
        val tokenStats: AgentTokenStats
    ) : UiEvent

    /**
     * Контекст диалога очищен.
     */
    data object ContextCleared : UiEvent

    /**
     * Активная модель успешно изменена.
     */
    data object ModelChanged : UiEvent

    /**
     * Не удалось переключить модель.
     */
    data class ModelSwitchFailed(
        val details: String?
    ) : UiEvent

    /**
     * Не удалось выполнить пользовательский запрос.
     */
    data class RequestFailed(
        val details: String?
    ) : UiEvent

    /**
     * Пользователь завершил сессию.
     */
    data object SessionFinished : UiEvent

    /**
     * Начат прогрев локального токенизатора.
     */
    data object ModelWarmupStarted : UiEvent

    /**
     * Прогрев локального токенизатора завершён.
     */
    data object ModelWarmupFinished : UiEvent

    /**
     * Начата отправка основного запроса в модель.
     */
    data object ModelRequestStarted : UiEvent

    /**
     * Основной запрос в модель завершён.
     */
    data object ModelRequestFinished : UiEvent

    /**
     * Начато сжатие контекста.
     */
    data object ContextCompressionStarted : UiEvent

    /**
     * Сжатие контекста завершено.
     */
    data class ContextCompressionFinished(
        val stats: ContextCompressionStats
    ) : UiEvent
}
