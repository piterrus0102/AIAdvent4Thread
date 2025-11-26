// =============================================================================
// IMPORTS
// =============================================================================
import dotenv from 'dotenv';
import { 
    createSystemMessage,
    createToolResultMessage,
    createToolNotFoundMessage,
    createRetryMessage
} from './ServerPrompts.js';
import ReminderManager from './ReminderManager.js';
import HuggingFaceClient from './HuggingFaceClient.js';
import RAGService from '../../rag-proxy/RAGService.js';

// Загружаем переменные окружения
dotenv.config();

// =============================================================================
// MainServer - Главный сервер (Orchestrator)
// =============================================================================
// Управляет всей бизнес-логикой приложения
// Архитектура:
//   Android App → MainServer → {MCPClient, GitHubMCPClient, AppDatabase, YandexGPT}
//   (Activity → ViewModel → {Repositories, Database, API})
// =============================================================================
class MainServer {
    /**
     * @param {MCPClient} mcpClient - Локальный MCP клиент
     * @param {MCPServer} mcpServer - Локальный MCP сервер
     * @param {AppDatabase} database - База данных
     * @param {GitHubMCPClient} githubMCPClient - GitHub MCP клиент
     * @param {WardrobeMCPClient} wardrobeMCPClient - Wardrobe MCP клиент
     * @param {WeatherMCPClient} weatherMCPClient - Weather MCP клиент
     */
    constructor(mcpClient, mcpServer, database, githubMCPClient, wardrobeMCPClient, weatherMCPClient) {
        this.mcpClient = mcpClient;
        this.mcpServer = mcpServer;
        this.database = database;
        this.githubMCPClient = githubMCPClient;
        this.wardrobeMCPClient = wardrobeMCPClient;
        this.weatherMCPClient = weatherMCPClient;
        
        // Режим работы MCP (включен/выключен)
        this.useMCP = false; // По умолчанию ВЫКЛЮЧЕН (как RAG)
        
        // Режим работы GitHub MCP (включен/выключен)
        this.useGitHubMCP = false;
        this.githubCredentials = null;
        
        // Режим работы RAG (включен/выключен)
        this.useRAG = false;
        
        // Базовые MCP клиенты (local, wardrobe, weather)
        this.mcpClientsPool = {
            local: { name: 'local', client: mcpClient },
            wardrobe: { name: 'wardrobe', client: wardrobeMCPClient },
            weather: { name: 'weather', client: weatherMCPClient }
        };
        
        // HuggingFace клиент (Qwen/Qwen2.5-7B-Instruct)
        this.huggingFaceClient = new HuggingFaceClient();
        
        // Планировщик напоминаний
        this.reminderManager = new ReminderManager(this);
        
        // RAG сервис для поиска по курсу
        this.ragService = new RAGService();
    }

    // =========================================================================
    // MCP MODE - Управление MCP серверами (оркестрация)
    // =========================================================================
    
    /**
     * Установить режим работы базовых MCP серверов (local, wardrobe, weather)
     * 
     * @param {boolean} useMCP - Использовать базовые MCP инструменты?
     */
    setMCPMode(useMCP) {
        this.useMCP = useMCP;
        console.log(`\n[Server] ====== РЕЖИМ ИЗМЕНЕН ======`);
        console.log(`[Server] MCP: ${useMCP ? '✅ ВКЛЮЧЕН (инструменты доступны)' : '❌ ВЫКЛЮЧЕН (только прямой LLM)'}`);
        console.log(`[Server] ====================================\n`);
    }

    /**
     * Установить режим работы GitHub MCP
     * Теперь GitHub MCP дополняет инструменты, а не заменяет их
     *
     * @param {boolean} useGitHub - Использовать GitHub MCP?
     * @param {string} githubToken - GitHub токен (если useGitHub = true)
     */
    setGitHubMCPMode(useGitHub, githubToken = null) {
        this.useGitHubMCP = useGitHub;
        if (useGitHub && githubToken) {
            this.githubCredentials = { token: githubToken };
        }
        console.log(`[Server] GitHub MCP: ${useGitHub ? 'Включен (дополняет инструменты)' : 'Выключен'}`);
    }

    /**
     * Установить режим работы RAG
     * 
     * @param {boolean} useRAG - Использовать RAG режим?
     */
    setRAGMode(useRAG) {
        this.useRAG = useRAG;
        console.log(`\n[Server] ====== РЕЖИМ ИЗМЕНЕН ======`);
        console.log(`[Server] RAG: ${useRAG ? '✅ ВКЛЮЧЕН (Векторный поиск + LLM)' : '❌ ВЫКЛЮЧЕН (Прямой запрос к LLM)'}`);
        console.log(`[Server] ====================================\n`);
    }

    /**
     * Получить текущий режим MCP
     * 
     * @returns {boolean}
     */
    getMCPMode() {
        return this.useMCP;
    }

    /**
     * Получить текущий режим RAG
     * 
     * @returns {boolean}
     */
    getRAGMode() {
        return this.useRAG;
    }

    /**
     * Получить все активные MCP клиенты для оркестрации
     */
    getActiveMCPClients() {
        const clients = [];
        
        // Добавляем базовые MCP клиенты если MCP включен
        if (this.useMCP) {
            clients.push(
                this.mcpClientsPool.local,
                this.mcpClientsPool.wardrobe,
                this.mcpClientsPool.weather
            );
        }
        
        // Добавляем GitHub MCP если включен
        if (this.useGitHubMCP) {
            clients.push({ name: 'github', client: this.githubMCPClient });
        }
        
        return clients;
    }
    
    /**
     * Найти MCP клиент по имени инструмента
     */
    async findMCPClientForTool(toolName) {
        const clients = this.getActiveMCPClients();
        
        for (const { name, client } of clients) {
            try {
                const toolsResponse = await client.listTools();
                const hasTool = toolsResponse.tools.some(t => t.name === toolName);
                
                if (hasTool) {
                    console.log(`[Server] Инструмент '${toolName}' найден в MCP '${name}'`);
                    return client;
                }
            } catch (error) {
                console.error(`[Server] Ошибка проверки инструментов в '${name}':`, error);
            }
        }
        
        return null;
    }

    // =========================================================================
    // TOOLS - Получение инструментов для LLM (оркестрация)
    // =========================================================================
    
    /**
     * Получить список инструментов для передачи в LLM
     * Собирает инструменты от всех активных MCP серверов
     *
     * @returns {Promise<Array>} - Список инструментов в формате YandexGPT
     */
    async getToolsForLLM() {
        // console.log('[Server] ====== ОРКЕСТРАЦИЯ: Сбор инструментов ======');
        
        const clients = this.getActiveMCPClients();
        // console.log(`[Server] Активных MCP серверов: ${clients.length}`);
        // clients.forEach(({ name }) => console.log(`[Server]   - ${name}`));
        
        const allTools = [];
        
        // Собираем инструменты от всех клиентов
        for (const { name, client } of clients) {
            try {
                // Для GitHub MCP подключаемся если еще не подключены
                if (name === 'github' && !this.githubMCPClient.isConnected) {
                    console.log('[Server] Подключение к GitHub MCP...');
                    if (!this.githubCredentials) {
                        console.error('[Server] GitHub credentials не предоставлены');
                        continue;
                    }
                    await this.githubMCPClient.connect(this.githubCredentials.token);
                }
                
                // Получаем инструменты
                const toolsResponse = await client.listTools();
                // console.log(`[Server] MCP '${name}': ${toolsResponse.tools.length} инструментов`);
                
                // Добавляем префикс к имени инструмента для отладки (опционально)
                const tools = toolsResponse.tools.map(tool => ({
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.inputSchema,
                    _mcpSource: name // Метаданные для отладки
                }));
                
                allTools.push(...tools);
                
            } catch (error) {
                console.error(`[Server] Ошибка получения инструментов от '${name}':`, error);
            }
        }
        
        // console.log(`[Server] ====== Всего инструментов: ${allTools.length} ======`);
        // console.log(`[Server] Список: ${allTools.map(t => t.name).join(', ')}`);
        
        return allTools;
    }

    // =========================================================================
    // LLM - Вызов YandexGPT с поддержкой инструментов
    // =========================================================================
    
    /**
     * Вызвать LLM с кастомным system message
     * 
     * @param {Array} messages - История сообщений
     * @param {Array} tools - Доступные инструменты
     * @param {Object} customSystemMessage - Кастомный system message
     * @returns {Promise<{text, toolUsed, toolResult}>} - Ответ LLM
     */
    async callLLMWithCustomPrompt(messages, tools, customSystemMessage) {
        return await this.callLLM(messages, tools, customSystemMessage);
    }

    /**
     * Вызвать LLM с поддержкой инструментов (Tool Chaining)
     *      *
     * Это сложный метод который:
     * 1. Отправляет запрос в YandexGPT
     * 2. Если LLM хочет вызвать инструмент - вызывает его
     * 3. Передает результат обратно в LLM
     *
     * @param {Array} messages - История сообщений
     * @param {Array} tools - Доступные инструменты
     * @param {Object} customSystemMessage - Опциональный кастомный system message
     * @returns {Promise<{text, toolUsed, toolResult}>} - Ответ LLM
     */
    async callLLM(messages, tools, customSystemMessage = null) {
        // console.log('[Server] Вызов HuggingFace (Qwen/Qwen2.5-7B-Instruct)');
        // console.log(`[Server] Сообщений в истории: ${messages.length}`);
        // console.log(`[Server] Инструментов: ${tools.map(t => t.name).join(', ')}`);

        // Создаем system message с инструкциями для LLM
        const systemMessage = customSystemMessage || createSystemMessage(tools);

        // ===== ДЕТАЛЬНОЕ ЛОГИРОВАНИЕ ДЛЯ ОТЛАДКИ =====
        // console.log('\n[PITERRUS] ====================================================================================================');
        // console.log('[PITERRUS] 📤 ОТПРАВКА ЗАПРОСА В LLM');
        // console.log('[PITERRUS] ====================================================================================================');
        // console.log('[PITERRUS] 🔧 ДОСТУПНЫЕ ИНСТРУМЕНТЫ (передаются в LLM через API):');
        // tools.forEach((tool, index) => {
        //     console.log(`[PITERRUS]   ${index + 1}. ${tool.name}`);
        //     console.log(`[PITERRUS]      Описание: ${tool.description}`);
        //     if (tool.parameters && tool.parameters.properties) {
        //         console.log(`[PITERRUS]      Параметры:`);
        //         Object.entries(tool.parameters.properties).forEach(([paramName, paramInfo]) => {
        //             const required = tool.parameters.required?.includes(paramName) ? '(обязательный)' : '(опциональный)';
        //             console.log(`[PITERRUS]        - ${paramName} ${required}: ${paramInfo.description || paramInfo.type}`);
        //         });
        //     }
        // });
        // console.log('[PITERRUS] ====================================================================================================');
        // console.log('[PITERRUS] ⚠️  ВАЖНО: Qwen использует text-based tool calling с THINKING MODE!');
        // console.log('[PITERRUS] Настройки: temperature=0.6, top_p=0.95 (парсинг <think> тегов включен)');
        // console.log('[PITERRUS] Инструменты передаются через текстовый system prompt (см. выше).');
        // console.log('[PITERRUS] ====================================================================================================');
        // console.log('[PITERRUS] 💬 СИСТЕМА ПРОМПТ:');
        // console.log(`[PITERRUS] ${systemMessage.text}`);
        // console.log('[PITERRUS] ====================================================================================================');
        // console.log('[PITERRUS] 📜 ИСТОРИЯ СООБЩЕНИЙ:');
        // messages.forEach((msg, index) => {
        //     console.log(`[PITERRUS]   ${index + 1}. [${msg.role}]: ${msg.text.substring(0, 200)}${msg.text.length > 200 ? '...' : ''}`);
        // });
        // console.log('[PITERRUS] ====================================================================================================\n');

        try {
            // Первый запрос к HuggingFace
            // NOTE: Qwen использует text-based tool calling через system prompt
            // Используем thinking mode: temperature=0.6, top_p=0.95 (парсинг <think> тегов - опционально)
            const allMessages = [systemMessage, ...messages];
            let currentMessage = await this.huggingFaceClient.callModel(allMessages, 0.6, 2000);
            
            console.log('[Server] ============================================');
            console.log('[Server] Ответ LLM получен:');
            console.log('[Server]', currentMessage);
            console.log('[Server] ============================================');
            
            console.log(`[Server] Содержит USE_TOOL?: ${currentMessage.includes('USE_TOOL:')}`);

            // ===== TOOL CHAINING: Цикл вызовов инструментов =====
            let toolCallCount = 0;
            let conversationHistory = [...messages];
            const usedTools = [];

            while (currentMessage.includes('USE_TOOL:')) {
                console.log(`[Server] Сообщение содержит: ${currentMessage.substring(0, 300)}`);
                
                // ===== ПАРСИМ ВСЕ USE_TOOL: ИЗ ОТВЕТА =====
                const toolCalls = [];
                let searchPos = 0;
                
                while (true) {
                    const useToolIndex = currentMessage.indexOf('USE_TOOL:', searchPos);
                    if (useToolIndex === -1) break;
                    
                    const afterUseTool = currentMessage.substring(useToolIndex + 9).trim();
                    const jsonStartIndex = afterUseTool.indexOf('{');
                    
                    if (jsonStartIndex !== -1) {
                        // Находим соответствующую закрывающую скобку
                        let braceCount = 0;
                        let jsonEndIndex = -1;
                        
                        for (let i = jsonStartIndex; i < afterUseTool.length; i++) {
                            if (afterUseTool[i] === '{') braceCount++;
                            if (afterUseTool[i] === '}') {
                                braceCount--;
                                if (braceCount === 0) {
                                    jsonEndIndex = i + 1;
                                    break;
                                }
                            }
                        }
                        
                        if (jsonEndIndex !== -1) {
                            try {
                                const jsonStr = afterUseTool.substring(jsonStartIndex, jsonEndIndex);
                                const toolCall = JSON.parse(jsonStr);
                                toolCalls.push({
                                    name: toolCall.name,
                                    args: toolCall.args || {}
                                });
                            } catch (parseError) {
                                console.error('[Server] ❌ Ошибка парсинга USE_TOOL JSON:', parseError.message);
                            }
                        }
                    }
                    
                    // Продолжаем поиск после текущего вхождения
                    searchPos = useToolIndex + 9;
                }
                
                if (toolCalls.length === 0) {
                    console.log('[Server] ⚠️ Не удалось распарсить ни одного инструмента');
                    break;
                }
                
                console.log(`[Server] 📋 Найдено ${toolCalls.length} инструментов для выполнения:`);
                toolCalls.forEach((tc, idx) => {
                    console.log(`[Server]   ${idx + 1}. ${tc.name} ${JSON.stringify(tc.args)}`);
                });
                
                // ===== ВЫПОЛНЯЕМ ВСЕ ИНСТРУМЕНТЫ ПОСЛЕДОВАТЕЛЬНО =====
                const allResults = [];
                
                for (let i = 0; i < toolCalls.length; i++) {
                    const { name: toolName, args: toolArgs } = toolCalls[i];
                    toolCallCount++;
                    console.log(`\n[Server] === Выполнение инструмента ${i + 1}/${toolCalls.length}: ${toolName} ===`);
                
                    // Валидация: проверяем что инструмент существует
                    const availableToolNames = tools.map(t => t.name);
                    if (!availableToolNames.includes(toolName)) {
                        console.error(`[Server] ⚠️ Несуществующий инструмент: ${toolName}`);
                        const toolResultText = createToolNotFoundMessage(toolName, availableToolNames);
                        allResults.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                        usedTools.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                        continue; // Переходим к следующему инструменту
                    }
                    
                    // Находим MCP клиент для этого инструмента (оркестрация)
                    console.log('[Server] 🔍 Поиск MCP клиента для инструмента...');
                    const targetMCPClient = await this.findMCPClientForTool(toolName);
                    
                    if (!targetMCPClient) {
                        console.error(`[Server] ⚠️ MCP клиент для инструмента '${toolName}' не найден`);
                        const toolResultText = createToolNotFoundMessage(toolName, availableToolNames);
                        allResults.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                        usedTools.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                        continue; // Переходим к следующему инструменту
                    }
                    
                    // Вызываем инструмент через найденный MCP клиент
                    let toolResultText;
                    try {
                        const toolResult = await targetMCPClient.callTool(toolName, toolArgs);
                        toolResultText = toolResult.content[0].text;
                        
                        console.log('[Server] ✅ Результат:', toolResultText.substring(0, 200));
                        allResults.push({ name: toolName, args: toolArgs, result: toolResultText });
                        usedTools.push({ name: toolName, args: toolArgs, result: toolResultText });
                    } catch (toolError) {
                        const errorMessage = toolError.message || String(toolError);
                        toolResultText = `ERROR: ${errorMessage}`;
                        
                        console.error(`[Server] ❌ Ошибка ${toolName}:`, errorMessage);
                        allResults.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                        usedTools.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                    }
                }
                
                // ===== ВСЕ ИНСТРУМЕНТЫ ВЫПОЛНЕНЫ - ОТПРАВЛЯЕМ РЕЗУЛЬТАТЫ В LLM =====
                console.log(`\n[Server] ✅ Выполнено ${allResults.length} инструментов`);
                
                // Формируем сообщение со всеми результатами
                conversationHistory.push({ role: 'assistant', text: currentMessage });
                
                const allResultsText = allResults.map(r => 
                    `Результат инструмента ${r.name}:\n${r.result}`
                ).join('\n\n---\n\n');
                
                conversationHistory.push({ 
                    role: 'user', 
                    text: `${allResultsText}\n\nИспользуй эти данные для ответа. Не выдумывай!`
                });
                
                // console.log('\n[PITERRUS] ====================================================================================================');
                // console.log('[PITERRUS] 🔄 ПОВТОРНЫЙ ЗАПРОС В LLM (после выполнения ВСЕХ инструментов)');
                // console.log('[PITERRUS] ====================================================================================================');
                // console.log(`[PITERRUS] ✅ Выполнено инструментов: ${allResults.length}`);
                // allResults.forEach((r, idx) => {
                //     console.log(`[PITERRUS]   ${idx + 1}. ${r.name}: ${r.result.substring(0, 150)}${r.result.length > 150 ? '...' : ''}`);
                // });
                // console.log('[PITERRUS] ====================================================================================================');
                // console.log('[PITERRUS] 📜 ОБНОВЛЕННАЯ ИСТОРИЯ СООБЩЕНИЙ:');
                // conversationHistory.forEach((msg, index) => {
                //     console.log(`[PITERRUS]   ${index + 1}. [${msg.role}]: ${msg.text.substring(0, 200)}${msg.text.length > 200 ? '...' : ''}`);
                // });
                // console.log('[PITERRUS] ====================================================================================================\n');
                
                const followUpMessages = [systemMessage, ...conversationHistory];
                currentMessage = await this.huggingFaceClient.callModel(followUpMessages, 0.6, 2000);
                
                console.log('[Server] ============================================');
                console.log('[Server] Следующий ответ LLM:');
                console.log('[Server]', currentMessage);
                console.log('[Server] ============================================');
                console.log(`[Server] Содержит USE_TOOL?: ${currentMessage.includes('USE_TOOL:')}`);
            }
            
            // ===== ОЧИСТКА ФИНАЛЬНОГО ОТВЕТА =====
            // Убираем любые остатки USE_TOOL команд из финального ответа
            let cleanedMessage = currentMessage;
            
            // Удаляем все вхождения USE_TOOL: {...} с учетом вложенных скобок
            while (cleanedMessage.includes('USE_TOOL:')) {
                const useToolIndex = cleanedMessage.indexOf('USE_TOOL:');
                const afterUseTool = cleanedMessage.substring(useToolIndex + 9).trim();
                const jsonStartIndex = afterUseTool.indexOf('{');
                
                if (jsonStartIndex !== -1) {
                    let braceCount = 0;
                    let jsonEndIndex = -1;
                    
                    for (let i = jsonStartIndex; i < afterUseTool.length; i++) {
                        if (afterUseTool[i] === '{') braceCount++;
                        if (afterUseTool[i] === '}') {
                            braceCount--;
                            if (braceCount === 0) {
                                jsonEndIndex = i + 1;
                                break;
                            }
                        }
                    }
                    
                    if (jsonEndIndex !== -1) {
                        // Удаляем USE_TOOL: {...} из сообщения
                        const removeLength = 9 + jsonStartIndex + jsonEndIndex;
                        cleanedMessage = cleanedMessage.substring(0, useToolIndex) + 
                                       cleanedMessage.substring(useToolIndex + removeLength);
                    } else {
                        // Не нашли закрывающую скобку, удаляем просто USE_TOOL:
                        cleanedMessage = cleanedMessage.replace('USE_TOOL:', '');
                        break;
                    }
                } else {
                    // Нет открывающей скобки, удаляем просто USE_TOOL:
                    cleanedMessage = cleanedMessage.replace('USE_TOOL:', '');
                    break;
                }
            }
            
            cleanedMessage = cleanedMessage.trim();
            
            // ===== ПРОВЕРКА НА INCORRECT_RAG_ANSWER =====
            if (cleanedMessage === 'INCORRECT_RAG_ANSWER') {
                console.log('\n========================================');
                console.log('🚨 INCORRECT_RAG_ANSWER ОБНАРУЖЕН!');
                console.log('========================================');
                console.log('Пользователь сообщил о неправильном понимании вопроса');
                console.log('Требуется улучшение релевантности поиска');
                console.log('========================================\n');
                
                return {
                    text: cleanedMessage,
                    toolUsed: null,
                    toolResult: null,
                    incorrectRAG: true
                };
            }
            
            // Возвращаем результат
            if (usedTools.length > 0) {
                console.log(`[Server] ✅ Цепочка из ${usedTools.length} инструментов выполнена`);
                console.log(`[Server] Финальный ответ: ${cleanedMessage.substring(0, 100)}...`);
                
                return {
                    text: cleanedMessage,
                    toolUsed: usedTools.map(t => t.name).join(' → '),
                    toolResult: usedTools.map(t => `${t.name}: ${t.result.substring(0, 100)}...`).join('\n')
                };
            }
            
            console.log(`[Server] Ответ: ${cleanedMessage.substring(0, 100)}...`);
            
            return {
                text: cleanedMessage,
                toolUsed: null,
                toolResult: null
            };
            
        } catch (error) {
            console.error('[Server] ❌ Ошибка при вызове YandexGPT:', error);
            throw error;
        }
    }

    // =========================================================================
    // MESSAGE HANDLING - Обработка сообщений от приложения
    // =========================================================================
    
    /**
     * Обработать сообщение от Android приложения
     *
     * @param {string} userMessage - Сообщение пользователя
     * @param {Array} messageHistory - История сообщений
     * @returns {Promise<{success, message, toolUsed, toolResult}>}
     */
    async handleMessage(userMessage, messageHistory) {
        try {
            console.log('[Server] Обработка сообщения от приложения...');
            console.log(`[Server] Режим RAG: ${this.useRAG ? 'ВКЛЮЧЕН' : 'ВЫКЛЮЧЕН'}`);
            
            // ===== РЕЖИМ RAG: Векторный поиск + LLM =====
            if (this.useRAG) {
                console.log('[Server] 🔍 Использую RAG режим (векторный поиск по курсу)');
                
                try {
                    const ragResult = await this.answerCourseQuestion(userMessage, 3);
                    
                    if (!ragResult.success) {
                        throw new Error(ragResult.error);
                    }
                    
                    // ===== ОБНАРУЖЕН INCORRECT_RAG_ANSWER =====
                    if (ragResult.incorrectRAG) {
                        console.log('\n========================================');
                        console.log('🚨 INCORRECT_RAG_ANSWER ОБНАРУЖЕН!');
                        console.log('========================================');
                        console.log('Пользователь недоволен ответом.');
                        console.log('Включаю РЕРАНКИНГ и повторяю поиск...');
                        console.log('========================================\n');
                        
                        // Находим оригинальный вопрос из истории (последний user message)
                        let originalQuery = userMessage;
                        
                        // Ищем в истории последний вопрос пользователя (не жалобу)
                        for (let i = messageHistory.length - 1; i >= 0; i--) {
                            const msg = messageHistory[i];
                            if (msg.role === 'user' && !msg.text.toLowerCase().includes('неправильно') 
                                && !msg.text.toLowerCase().includes('не то') 
                                && !msg.text.toLowerCase().includes('не понял')) {
                                originalQuery = msg.text;
                                console.log(`[Server] 🔍 Найден оригинальный вопрос: "${originalQuery}"`);
                                break;
                            }
                        }
                        
                        // Включаем реранкинг
                        this.ragService.setReranking(true);
                        
                        // Повторяем поиск с реранкингом
                        console.log('[Server] 🔄 Повторный запрос С РЕРАНКИНГОМ...');
                        const improvedResult = await this.answerCourseQuestion(originalQuery, 3);
                        
                        // Выключаем реранкинг обратно (только для этого запроса)
                        this.ragService.setReranking(false);
                        
                        if (!improvedResult.success) {
                            throw new Error(improvedResult.error);
                        }
                        
                        console.log('\n========================================');
                        console.log('✅ РЕРАНКИНГ ЗАВЕРШЕН');
                        console.log(`Найдено уроков: ${improvedResult.lessons.length}`);
                        console.log('========================================\n');
                        
                        return {
                            success: true,
                            message: '🔄 Я провел более тщательный поиск по курсу:\n\n' + improvedResult.answer,
                            toolUsed: 'RAG (Векторный поиск + Реранкинг)',
                            toolResult: `РЕРАНКИНГ включен!\nНайдено уроков: ${improvedResult.lessons.length}\n` +
                                       improvedResult.lessons.map(l => {
                                           const llmScore = l.llm_score ? ` [LLM: ${l.llm_score}]` : '';
                                           return `- ${l.title} (${l.relevance}${llmScore})`;
                                       }).join('\n') +
                                       (improvedResult.reranking_stats ? 
                                        `\n\nСтатистика реранкинга:\n- Начальных: ${improvedResult.reranking_stats.initial}\n- После фильтра: ${improvedResult.reranking_stats.after_llm}` 
                                        : ''),
                            ragLessons: improvedResult.lessons,
                            incorrectRAG: false,
                            rerankingUsed: true
                        };
                    }
                    
                    // ===== ОБЫЧНЫЙ ОТВЕТ (без INCORRECT_RAG_ANSWER) =====
                    return {
                        success: true,
                        message: ragResult.answer,
                        toolUsed: 'RAG (Векторный поиск)',
                        toolResult: `Найдено уроков: ${ragResult.lessons.length}\n` +
                                   ragResult.lessons.map(l => `- ${l.title} (${l.relevance})`).join('\n'),
                        ragLessons: ragResult.lessons,
                        incorrectRAG: false
                    };
                    
                } catch (ragError) {
                    console.error('[Server] ❌ Ошибка RAG режима:', ragError);
                    // Fallback на обычный режим если RAG не работает
                    console.log('[Server] ⚠️ Переключаюсь на обычный режим (fallback)');
                }
            }
            
            // ===== ОБЫЧНЫЙ РЕЖИМ: Прямой запрос к LLM =====
            console.log('[Server] 💬 Использую обычный режим (прямой запрос к LLM)');
            
            // Получаем инструменты от активного MCP
            const tools = await this.getToolsForLLM();
            // console.log(`[Server] Получено инструментов: ${tools.length}`);
            
            // Формируем историю сообщений
            const messages = [
                ...messageHistory,
                { role: 'user', text: userMessage }
            ];
            
            // Вызываем LLM
            const response = await this.callLLM(messages, tools);
            
            return {
                success: true,
                message: response.text,
                toolUsed: response.toolUsed || null,
                toolResult: response.toolResult || null,
                incorrectRAG: response.incorrectRAG || false
            };
            
        } catch (error) {
            console.error('[Server] ❌ Ошибка обработки сообщения:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    // =========================================================================
    // RAG - Retrieval-Augmented Generation
    // =========================================================================
    
    /**
     * Поиск по курсу через RAG
     * 
     * @param {string} query - Вопрос пользователя
     * @param {number} topK - Количество результатов
     * @returns {Promise<Object>} - Результаты поиска
     */
    async searchCourse(query, topK = 3) {
        console.log(`[Server] RAG поиск по запросу: "${query}"`);
        
        try {
            const result = await this.ragService.query(query, topK);
            
            console.log(`[Server] ✓ Найдено уроков: ${result.lessons.length}`);
            
            return {
                success: true,
                ...result
            };
            
        } catch (error) {
            console.error('[Server] ❌ Ошибка RAG поиска:', error.message);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Ответить на вопрос по курсу с использованием RAG + LLM
     * 
     * @param {string} query - Вопрос пользователя
     * @param {number} topK - Количество релевантных документов
     * @returns {Promise<Object>} - Ответ с контекстом и генерацией
     */
    async answerCourseQuestion(query, topK = 3) {
        console.log(`[Server] RAG + LLM для вопроса: "${query}"`);
        
        try {
            // Используем RAG с LLM через callback
            const result = await this.ragService.queryWithLLM(
                query,
                async (messages, tools) => {
                    // Вызываем LLM через существующий метод
                    return await this.callLLM(messages, tools);
                },
                topK
            );
            
            console.log(`[Server] ✓ Ответ сгенерирован`);
            
            return {
                success: true,
                ...result
            };
            
        } catch (error) {
            console.error('[Server] ❌ Ошибка RAG + LLM:', error.message);
            return {
                success: false,
                error: error.message
            };
        }
    }

    /**
     * Получить статистику векторного индекса
     * 
     * @returns {Promise<Object>} - Статистика
     */
    async getCourseIndexStats() {
        try {
            const stats = await this.ragService.getStats();
            return {
                success: true,
                stats: stats
            };
        } catch (error) {
            return {
                success: false,
                error: error.message
            };
        }
    }

    // =========================================================================
    // DATABASE OPERATIONS - Операции с базой данных
    // =========================================================================
    
    /**
     * Обновить счетчик сообщений для модели
     */
    async updateMessageCount(modelName, count) {
        console.log(`[Server] Обновление счетчика ${modelName}: ${count}`);
        await this.mcpServer.saveMessageCount(modelName, count);
        this.database.updateModelCounter(modelName, count);
    }

    /**
     * Синхронизировать сообщения из приложения в БД
     */
    async syncMessages(messages) {
        console.log(`[Server] Синхронизация ${messages.length} сообщений`);
        return this.database.syncMessages(messages);
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default MainServer;

