// =============================================================================
// ReminderManager - Планировщик напоминаний
// =============================================================================
// Управляет периодическими напоминаниями:
// - Создание напоминаний с интервалом
// - Выполнение запросов к LLM по расписанию
// - Логирование результатов
// - Остановка напоминаний
// =============================================================================

class ReminderManager {
    constructor(mainServer) {
        this.mainServer = mainServer;
        
        // Map: reminderId -> { interval, intervalId, config }
        this.reminders = new Map();
        
        // Счетчик для ID
        this.nextId = 1;
        
        console.log('[ReminderManager] Инициализирован');
    }

    /**
     * Создать новое напоминание
     * 
     * @param {string} userRequest - Запрос пользователя (например: "оповещай меня каждые 10 секунд об issues")
     * @returns {Promise<{reminderId, interval, topic, message}>}
     */
    async createReminder(userRequest) {
        try {
            console.log('[ReminderManager] ========================================');
            console.log('[ReminderManager] Создание нового напоминания');
            console.log('[ReminderManager] Запрос:', userRequest);
            
            // ===== ШАГ 1: Извлекаем параметры через LLM =====
            const parsedParams = await this.parseReminderRequest(userRequest);
            console.log('[ReminderManager] Извлечены параметры:', JSON.stringify(parsedParams));
            
            if (!parsedParams.interval || !parsedParams.topic) {
                throw new Error('Не удалось извлечь время или тему из запроса');
            }
            
            // ===== ШАГ 2: Создаем напоминание =====
            const reminderId = this.nextId++;
            const intervalMs = parsedParams.interval * 1000; // секунды -> миллисекунды
            
            const config = {
                id: reminderId,
                interval: parsedParams.interval,
                intervalMs: intervalMs,
                topic: parsedParams.topic,
                query: parsedParams.query,
                owner: parsedParams.owner || null,
                repo: parsedParams.repo || null,
                createdAt: new Date().toISOString(),
                executionCount: 0
            };
            
            // ===== ШАГ 3: Запускаем периодический таймер =====
            const intervalId = setInterval(async () => {
                await this.executeReminder(reminderId);
            }, intervalMs);
            
            this.reminders.set(reminderId, {
                config,
                intervalId
            });
            
            console.log('[ReminderManager] ✅ Напоминание создано');
            console.log('[ReminderManager] ID:', reminderId);
            console.log('[ReminderManager] Интервал:', parsedParams.interval, 'секунд');
            console.log('[ReminderManager] Тема:', parsedParams.topic);
            console.log('[ReminderManager] ========================================');
            
            // Выполняем первый раз сразу
            setImmediate(() => this.executeReminder(reminderId));
            
            return {
                success: true,
                reminderId,
                interval: parsedParams.interval,
                topic: parsedParams.topic,
                message: `Напоминание создано! Буду проверять ${parsedParams.topic} каждые ${parsedParams.interval} секунд.`
            };
            
        } catch (error) {
            console.error('[ReminderManager] ❌ Ошибка создания напоминания:', error);
            throw error;
        }
    }

    /**
     * Парсит запрос пользователя через LLM
     * Извлекает: время, тему, репозиторий
     * 
     * @param {string} userRequest
     * @returns {Promise<{interval: number, topic: string, query: string, owner?: string, repo?: string}>}
     */
    async parseReminderRequest(userRequest) {
        console.log('[ReminderManager] Парсинг запроса через LLM...');
        
        const systemMessage = {
            role: 'system',
            text: `Ты - парсер запросов на создание напоминаний. Извлеки из запроса пользователя:
1. Интервал времени в СЕКУНДАХ (преобразуй минуты/часы в секунды)
2. Тему напоминания (о чем напоминать)
3. Репозиторий (если указан)

Ответь СТРОГО в JSON формате:
{
  "interval": <число_секунд>,
  "topic": "<краткое_описание_темы>",
  "query": "<вопрос_который_нужно_задавать_LLM>",
  "owner": "<владелец_репо или null>",
  "repo": "<название_репо или null>"
}

Примеры:
Запрос: "оповещай меня каждые 10 секунд об открытых issues"
Ответ: {"interval": 10, "topic": "открытые issues", "query": "Сколько открытых issues?", "owner": null, "repo": null}

Запрос: "напоминай каждые 2 минуты о pull requests в AIAdvent4Thread"
Ответ: {"interval": 120, "topic": "pull requests в AIAdvent4Thread", "query": "Сколько открытых pull requests в репозитории piterrus0102/AIAdvent4Thread?", "owner": "piterrus0102", "repo": "AIAdvent4Thread"}

Запрос: "проверяй каждые 30 секунд issues в репозитории про челлендж ИИ"
Ответ: {"interval": 30, "topic": "issues в AIAdvent4Thread", "query": "Сколько открытых issues в репозитории piterrus0102/AIAdvent4Thread?", "owner": "piterrus0102", "repo": "AIAdvent4Thread"}

ВАЖНО: возвращай только JSON, без комментариев!`
        };

        const requestBody = {
            modelUri: `gpt://${process.env.YANDEX_FOLDER_ID}/yandexgpt/latest`,
            completionOptions: {
                stream: false,
                temperature: 0.3,
                maxTokens: 500
            },
            messages: [
                systemMessage,
                { role: 'user', text: userRequest }
            ]
        };

        const response = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Api-Key ${process.env.YANDEX_API_KEY}`,
                'x-folder-id': process.env.YANDEX_FOLDER_ID
            },
            body: JSON.stringify(requestBody)
        });

        if (!response.ok) {
            throw new Error(`YandexGPT API error: ${response.status}`);
        }

        const data = await response.json();
        const llmResponse = data.result.alternatives[0].message.text.trim();
        
        console.log('[ReminderManager] Ответ LLM:', llmResponse);
        
        // Парсим JSON из ответа
        const jsonMatch = llmResponse.match(/\{[\s\S]*\}/);
        if (!jsonMatch) {
            throw new Error('LLM не вернула JSON');
        }
        
        const parsed = JSON.parse(jsonMatch[0]);
        
        // Валидация
        if (!parsed.interval || parsed.interval <= 0) {
            throw new Error('Некорректный интервал времени');
        }
        
        return parsed;
    }

    /**
     * Выполнить напоминание (запрос к LLM)
     * 
     * @param {number} reminderId
     */
    async executeReminder(reminderId) {
        const reminder = this.reminders.get(reminderId);
        if (!reminder) {
            console.error(`[ReminderManager] Напоминание ${reminderId} не найдено`);
            return;
        }
        
        const { config } = reminder;
        config.executionCount++;
        
        const timestamp = new Date().toISOString();
        
        console.log('\n');
        console.log('╔════════════════════════════════════════════════════════════════╗');
        console.log(`║ 🔔 НАПОМИНАНИЕ #${reminderId} - Выполнение #${config.executionCount}`);
        console.log(`║ 📅 Время: ${timestamp}`);
        console.log(`║ 📝 Тема: ${config.topic}`);
        console.log('╚════════════════════════════════════════════════════════════════╝');
        
        try {
            // Выполняем запрос к LLM с специальным промптом для структурированного ответа
            const result = await this.executeStructuredQuery(config.query);
            
            console.log('[ReminderManager] ========================================');
            console.log('[ReminderManager] 📊 РЕЗУЛЬТАТ:');
            console.log('[ReminderManager]', result.text || result.message);
            if (result.toolUsed) {
                console.log('[ReminderManager] 🔧 Использованные инструменты:', result.toolUsed);
            }
            console.log('[ReminderManager] ========================================');
            console.log('\n');
            
        } catch (error) {
            console.error(`[ReminderManager] ❌ Ошибка выполнения напоминания #${reminderId}:`, error.message);
        }
    }

    /**
     * Выполнить структурированный запрос (для планировщика)
     * Требует от LLM стандартизированный формат ответа
     * 
     * @param {string} query
     * @returns {Promise<{message: string, toolUsed: string|null}>}
     */
    async executeStructuredQuery(query) {
        // console.log('[ReminderManager] Выполнение структурированного запроса...');
        
        // Получаем инструменты от активного MCP
        const tools = await this.mainServer.getToolsForLLM();
        
        // Специальный промпт для структурированных ответов
        const structuredSystemMessage = this.createStructuredSystemMessage(tools);
        
        const messages = [
            { role: 'user', text: query }
        ];
        
        // Вызываем LLM с модифицированным промптом
        return await this.mainServer.callLLMWithCustomPrompt(messages, tools, structuredSystemMessage);
    }

    /**
     * Создать system message для структурированных ответов планировщика
     */
    createStructuredSystemMessage(tools) {
        const toolsDescription = tools
            .map(tool => `- ${tool.name}: ${tool.description}`)
            .join('\n');

        return {
            role: 'system',
            text: `Ты — ассистент для периодических проверок. Твоя задача — предоставлять СТРУКТУРИРОВАННЫЕ отчеты.

ДОСТУПНЫЕ ИНСТРУМЕНТЫ:
${toolsDescription}

ВАЖНАЯ ИНФОРМАЦИЯ:
- По умолчанию owner репозитория: "piterrus0102"
- Для репозитория AIAdvent4Thread используй: {"owner": "piterrus0102", "repo": "AIAdvent4Thread"}

ФОРМАТ ВЫЗОВА ИНСТРУМЕНТА:
USE_TOOL: {"name": "имя_инструмента", "args": {параметры}}

Примеры с правильным owner:
USE_TOOL: {"name": "list_issues", "args": {"owner": "piterrus0102", "repo": "AIAdvent4Thread"}}
USE_TOOL: {"name": "list_pull_requests", "args": {"owner": "piterrus0102", "repo": "AIAdvent4Thread"}}

ОБЯЗАТЕЛЬНЫЙ ФОРМАТ ОТВЕТА:

📊 Количество: <число> issues/PR/релизов
📋 Summary: <ВОЛЬНОЕ ИЗЛОЖЕНИЕ содержания ВСЕХ issues/PR - суммаризация того, о чем они, связный текст>

ПРИМЕРЫ ПРАВИЛЬНЫХ ОТВЕТОВ:

Пример 1 (3 issues):
📊 Количество: 3 issues
📋 Summary: В репозитории идет активная работа над стабильностью и новыми возможностями. Пользователи сообщают о критическом краше приложения при определенных действиях. Также обнаружена проблема с UI - одна из кнопок отображается некорректно и выходит за границы экрана. Помимо багов, команда обсуждает амбициозные планы - рассматривается возможность интеграции с космическими проектами.

Пример 2 (0 issues):
📊 Количество: 0 issues
📋 Summary: Репозиторий находится в стабильном состоянии, активных проблем не зарегистрировано.

Пример 3 (5 PR):
📊 Количество: 5 PR
📋 Summary: Проект активно развивается - несколько команд работают параллельно. Идет большой рефакторинг системы аутентификации для улучшения архитектуры. Параллельно покрывается тестами критичный функционал. Обновляются зависимости до последних версий. Недавно были влиты изменения по исправлению утечки памяти и добавлению темной темы, которую давно просили пользователи.

Пример 4 (1 issue про краш):
📊 Количество: 1 issue
📋 Summary: Пользователь обнаружил критический краш - приложение внезапно закрывается при попытке отправить сообщение. Проблема требует срочного внимания, так как затрагивает основной функционал.

ВАЖНО:
- Summary должен быть СВЯЗНЫМ ТЕКСТОМ, а не списком
- Пиши как журналист - рассказывай историю того, что происходит в репозитории
- Объединяй схожие темы, находи общие паттерны
- Используй естественный язык, избегай перечислений через запятую или тире
- Делай суммаризацию - выдели главное, обобщи

ВАЖНО:
- ВСЕГДА используй инструменты для получения актуальных данных
- ВСЕГДА указывай owner: "piterrus0102" в аргументах
- ВСЕГДА начинай ответ с "📊 Количество:"
- ВСЕГДА добавляй "📋 Summary:"
- Summary = СУММАРИЗАЦИЯ, а не список:
  * Прочитай все issues/PR
  * Напиши связный текст о том, что в них происходит
  * Объедини схожие темы
  * Расскажи как историю - что делает команда, какие проблемы решают
  * 2-4 предложения естественного текста
- НЕ используй списки через тире или перечисления
- Пиши как аналитик, который делает обзор состояния проекта`
        };
    }

    /**
     * Остановить напоминание
     * 
     * @param {number} reminderId
     * @returns {boolean}
     */
    stopReminder(reminderId) {
        const reminder = this.reminders.get(reminderId);
        if (!reminder) {
            console.log(`[ReminderManager] Напоминание ${reminderId} не найдено`);
            return false;
        }
        
        clearInterval(reminder.intervalId);
        this.reminders.delete(reminderId);
        
        console.log('[ReminderManager] ✅ Напоминание остановлено:', reminderId);
        return true;
    }

    /**
     * Получить список активных напоминаний
     * 
     * @returns {Array}
     */
    listReminders() {
        const list = [];
        for (const [id, reminder] of this.reminders.entries()) {
            list.push({
                id,
                ...reminder.config
            });
        }
        return list;
    }

    /**
     * Остановить все напоминания
     */
    stopAll() {
        console.log('[ReminderManager] Остановка всех напоминаний...');
        for (const [id, reminder] of this.reminders.entries()) {
            clearInterval(reminder.intervalId);
        }
        this.reminders.clear();
        console.log('[ReminderManager] ✅ Все напоминания остановлены');
    }
}

export default ReminderManager;

