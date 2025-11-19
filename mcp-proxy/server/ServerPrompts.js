// =============================================================================
// ServerPrompts - System Prompts для LLM
// =============================================================================
// Содержит промпты для YandexGPT с text-based tool calling (regex parsing)
// 
// Аналог Android: strings.xml или Constants.kt с текстами
// =============================================================================

/**
 * Создать system message для LLM с инструкциями по использованию инструментов
 * 
 * @param {Array<{name: string, description: string}>} tools - Список доступных инструментов
 * @returns {{role: string, text: string}} - System message для YandexGPT
 */
export function createSystemMessage(tools) {
    // Формируем список инструментов
    const toolsDescription = tools
        .map(tool => `- ${tool.name}: ${tool.description}`)
        .join('\n');

    return {
        role: 'system',
        text: `Ты — ассистент который ВСЕГДА использует инструменты для получения информации.

ДОСТУПНЫЕ ИНСТРУМЕНТЫ:
${toolsDescription}

ОБЯЗАТЕЛЬНО:
- Для ЛЮБОГО запроса информации ВСЕГДА используй соответствующий инструмент
- НИКОГДА не говори "У меня нет доступа" - просто используй инструмент!
- НЕ отвечай без вызова инструмента, если вопрос требует данных

ФОРМАТ ВЫЗОВА ИНСТРУМЕНТА:
USE_TOOL: {"name": "имя_инструмента", "args": {параметры}}

ПРИМЕРЫ:
Вопрос: "Сколько у меня репозиториев?"
Ответ: USE_TOOL: {"name": "get_me", "args": {}}

Вопрос: "Есть ли pull request к репозиторию AIAdvent4Thread?"
Ответ: USE_TOOL: {"name": "list_pull_requests", "args": {"owner": "piterrus0102", "repo": "AIAdvent4Thread"}}

ВАЖНО:
- Сначала ВСЕГДА вызывай инструмент (USE_TOOL)
- Только после получения результата отвечай пользователю
- Если нужно несколько инструментов - вызывай последовательно
- ЗАПРЕЩЕНО отвечать "у меня нет доступа" - инструменты ВСЕГДА доступны!`
    };
}

/**
 * Сообщение с результатом выполнения инструмента
 * 
 * @param {string} toolName - Название инструмента
 * @param {string} result - Результат выполнения
 * @returns {string} - Текст сообщения для LLM
 */
export function createToolResultMessage(toolName, result) {
    return `Результат выполнения инструмента ${toolName}:\n${result}\n\nЕсли данных достаточно - предоставь понятный ответ пользователю.`;
}

/**
 * Сообщение об ошибке с несуществующим инструментом
 * 
 * @param {string} invalidToolName - Название несуществующего инструмента
 * @param {Array<string>} availableToolNames - Список доступных инструментов
 * @returns {string} - Текст сообщения об ошибке
 */
export function createToolNotFoundMessage(invalidToolName, availableToolNames) {
    const toolsList = availableToolNames.slice(0, 10).join(', ');
    return `ERROR: Инструмент '${invalidToolName}' не существует. Доступные: ${toolsList}`;
}

/**
 * Напоминание использовать только существующие инструменты
 * 
 * @param {string} errorMessage - Сообщение об ошибке
 * @returns {string} - Текст напоминания
 */
export function createRetryMessage(errorMessage) {
    return `${errorMessage}\n\nИспользуй ТОЛЬКО существующие инструменты!`;
}

