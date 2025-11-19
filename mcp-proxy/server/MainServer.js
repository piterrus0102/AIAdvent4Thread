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

// Загружаем переменные окружения
dotenv.config();

// YandexGPT credentials
const YANDEX_API_KEY = process.env.YANDEX_API_KEY;
const YANDEX_FOLDER_ID = process.env.YANDEX_FOLDER_ID;

if (!YANDEX_API_KEY || !YANDEX_FOLDER_ID) {
    console.error('❌ Не заданы YANDEX_API_KEY или YANDEX_FOLDER_ID');
    console.error('Создайте файл .env в папке mcp-proxy');
    process.exit(1);
}

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
     */
    constructor(mcpClient, mcpServer, database, githubMCPClient) {
        this.mcpClient = mcpClient;
        this.mcpServer = mcpServer;
        this.database = database;
        this.githubMCPClient = githubMCPClient;
        
        // Режим работы MCP (локальный или GitHub)
        this.useGitHubMCP = false;
        this.githubCredentials = null;
        
        // Планировщик напоминаний
        this.reminderManager = new ReminderManager(this);
    }

    // =========================================================================
    // MCP MODE - Переключение между локальным и GitHub MCP
    // =========================================================================
    
    /**
     * Установить режим работы MCP
     *
     * @param {boolean} useGitHub - Использовать GitHub MCP?
     * @param {string} githubToken - GitHub токен (если useGitHub = true)
     */
    setMCPMode(useGitHub, githubToken = null) {
        this.useGitHubMCP = useGitHub;
        if (useGitHub && githubToken) {
            this.githubCredentials = { token: githubToken };
        }
        console.log(`[Server] Режим MCP: ${useGitHub ? 'GitHub' : 'Локальный'}`);
    }

    /**
     * Получить активный MCP клиент (локальный или GitHub)
     */
    getActiveMCPClient() {
        return this.useGitHubMCP ? this.githubMCPClient : this.mcpClient;
    }

    // =========================================================================
    // TOOLS - Получение инструментов для LLM
    // =========================================================================
    
    /**
     * Получить список инструментов для передачи в LLM
     *
     * @returns {Promise<Array>} - Список инструментов в формате YandexGPT
     */
    async getToolsForLLM() {
        // console.log('[Server] Получение инструментов...');
        // console.log(`[Server] Режим: ${this.useGitHubMCP ? 'GitHub' : 'Локальный'}`);
        
        const activeMCPClient = this.getActiveMCPClient();
        
        // Если используется GitHub MCP, сначала подключаемся
        if (this.useGitHubMCP && !this.githubMCPClient.isConnected) {
            console.log('[Server] Подключение к GitHub MCP...');
            if (!this.githubCredentials) {
                throw new Error('GitHub credentials not provided');
            }
            await this.githubMCPClient.connect(this.githubCredentials.token);
        }
        
        // Получаем инструменты от активного MCP клиента
        const toolsResponse = await activeMCPClient.listTools();
        // console.log(`[Server] Получено инструментов: ${toolsResponse.tools.length}`);
        
        // Преобразуем формат MCP в формат YandexGPT
        return toolsResponse.tools.map(tool => ({
            name: tool.name,
            description: tool.description,
            parameters: tool.inputSchema
        }));
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
     * 4. Повторяет до получения финального ответа (до MAX_TOOL_CALLS раз)
     * 
     * @param {Array} messages - История сообщений
     * @param {Array} tools - Доступные инструменты
     * @param {Object} customSystemMessage - Опциональный кастомный system message
     * @returns {Promise<{text, toolUsed, toolResult}>} - Ответ LLM
     */
    async callLLM(messages, tools, customSystemMessage = null) {
        // console.log('[Server] Вызов YandexGPT');
        // console.log(`[Server] Сообщений в истории: ${messages.length}`);
        // console.log(`[Server] Инструментов: ${tools.map(t => t.name).join(', ')}`);

        // Создаем system message с инструкциями для LLM
        const systemMessage = customSystemMessage || createSystemMessage(tools);

        const requestBody = {
            modelUri: `gpt://${YANDEX_FOLDER_ID}/yandexgpt/latest`,
            completionOptions: {
                stream: false,
                temperature: 0.6,
                maxTokens: 2000
            },
            messages: [systemMessage, ...messages]
        };

        try {
            // Первый запрос к YandexGPT
            const response = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Api-Key ${YANDEX_API_KEY}`,
                    'x-folder-id': YANDEX_FOLDER_ID
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorText = await response.text();
                console.error('[Server] Ошибка YandexGPT:', response.status, errorText);
                throw new Error(`YandexGPT API error: ${response.status}`);
            }

            const data = await response.json();
            let currentMessage = data.result.alternatives[0].message.text;
            
            // console.log('[Server] ============================================');
            // console.log('[Server] Ответ YandexGPT получен:');
            // console.log('[Server]', currentMessage);
            // console.log('[Server] ============================================');
            // console.log(`[Server] Содержит USE_TOOL?: ${currentMessage.includes('USE_TOOL:')}`);

            // ===== TOOL CHAINING: Цикл вызовов инструментов =====
            const MAX_TOOL_CALLS = 10;  // Максимальное количество вызовов инструментов
            let toolCallCount = 0;
            let conversationHistory = [...messages];
            const usedTools = [];
            
            // console.log('[Server] Начинаем цикл обработки инструментов...');
            
            while (currentMessage.includes('USE_TOOL:') && toolCallCount < MAX_TOOL_CALLS) {
                toolCallCount++;
                // console.log(`\n[Server] === Вызов инструмента #${toolCallCount} ===`);
                // console.log(`[Server] Сообщение содержит: ${currentMessage.substring(0, 200)}`);
                
                let toolName, toolArgs = {};
                
                // Улучшенный парсинг JSON формата USE_TOOL
                // Ищем начало JSON после USE_TOOL:
                const useToolIndex = currentMessage.indexOf('USE_TOOL:');
                if (useToolIndex === -1) {
                    // console.log('[Server] USE_TOOL: не найден в сообщении');
                    break;
                }
                
                const afterUseTool = currentMessage.substring(useToolIndex + 9).trim(); // 9 = длина 'USE_TOOL:'
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
                            // console.log(`[Server] JSON строка: ${jsonStr}`);
                            const toolCall = JSON.parse(jsonStr);
                            toolName = toolCall.name;
                            toolArgs = toolCall.args || {};
                            
                            // console.log(`[Server] ✓ LLM запросила: ${toolName}`);
                            // console.log(`[Server] ✓ Аргументы:`, JSON.stringify(toolArgs));
                        } catch (parseError) {
                            console.error('[Server] ❌ Ошибка парсинга JSON:', parseError.message);
                            // console.error('[Server] JSON строка была:', afterUseTool.substring(jsonStartIndex, jsonEndIndex));
                        }
                    }
                }
                
                if (!toolName) {
                    // console.log('[Server] ⚠️ Не удалось извлечь имя инструмента, прерываем цикл');
                    break;
                }
                
                // Валидация: проверяем что инструмент существует
                const availableToolNames = tools.map(t => t.name);
                if (!availableToolNames.includes(toolName)) {
                    console.error(`[Server] ⚠️ Несуществующий инструмент: ${toolName}`);
                    
                    const toolResultText = createToolNotFoundMessage(toolName, availableToolNames);
                    usedTools.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                    
                    conversationHistory.push({ role: 'assistant', text: currentMessage });
                    conversationHistory.push({ 
                        role: 'user', 
                        text: createRetryMessage(toolResultText)
                    });
                    
                    // Повторный запрос к LLM
                    const retryResponse = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Authorization': `Api-Key ${YANDEX_API_KEY}`,
                            'x-folder-id': YANDEX_FOLDER_ID
                        },
                        body: JSON.stringify({
                            ...requestBody,
                            messages: [systemMessage, ...conversationHistory]
                        })
                    });
                    
                    const retryData = await retryResponse.json();
                    currentMessage = retryData.result.alternatives[0].message.text;
                    continue;
                }
                
                // Вызываем инструмент через активный MCP клиент
                const activeMCPClient = this.getActiveMCPClient();
                
                let toolResultText;
                try {
                    const toolResult = await activeMCPClient.callTool(toolName, toolArgs);
                    toolResultText = toolResult.content[0].text;
                    
                    // console.log('[Server] ✅ Результат:', toolResultText.substring(0, 200));
                    usedTools.push({ name: toolName, args: toolArgs, result: toolResultText });
                } catch (toolError) {
                    const errorMessage = toolError.message || String(toolError);
                    toolResultText = `ERROR: ${errorMessage}`;
                    
                    console.error(`[Server] ❌ Ошибка ${toolName}:`, errorMessage);
                    usedTools.push({ name: toolName, args: toolArgs, result: toolResultText, error: true });
                }
                
                // Добавляем в историю и отправляем обратно в LLM
                conversationHistory.push({ role: 'assistant', text: currentMessage });
                conversationHistory.push({ 
                    role: 'user', 
                    text: createToolResultMessage(toolName, toolResultText)
                });
                
                const followUpResponse = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Api-Key ${YANDEX_API_KEY}`,
                        'x-folder-id': YANDEX_FOLDER_ID
                    },
                    body: JSON.stringify({
                        ...requestBody,
                        messages: [systemMessage, ...conversationHistory]
                    })
                });
                
                const followUpData = await followUpResponse.json();
                currentMessage = followUpData.result.alternatives[0].message.text;
                
                // console.log('[Server] ============================================');
                // console.log('[Server] Следующий ответ YandexGPT:');
                // console.log('[Server]', currentMessage);
                // console.log('[Server] ============================================');
                // console.log(`[Server] Содержит USE_TOOL?: ${currentMessage.includes('USE_TOOL:')}`);
            }
            
            // console.log('[Server] Цикл обработки инструментов завершен');
            // console.log(`[Server] Всего вызовов: ${toolCallCount}`);
            // console.log(`[Server] Использовано инструментов: ${usedTools.length}`);
            
            if (toolCallCount >= MAX_TOOL_CALLS) {
                console.log(`[Server] ⚠️ Достигнут лимит вызовов (${MAX_TOOL_CALLS})`);
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
            
            // Возвращаем результат
            if (usedTools.length > 0) {
                // console.log(`[Server] ✅ Цепочка из ${usedTools.length} инструментов выполнена`);
                // console.log(`[Server] Финальный ответ: ${cleanedMessage.substring(0, 100)}...`);
                
                return {
                    text: cleanedMessage,
                    toolUsed: usedTools.map(t => t.name).join(' → '),
                    toolResult: usedTools.map(t => `${t.name}: ${t.result.substring(0, 100)}...`).join('\n')
                };
            }
            
            // console.log('[Server] Прямой ответ без инструментов');
            // console.log(`[Server] Ответ: ${cleanedMessage.substring(0, 100)}...`);
            
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
            
            // Получаем инструменты от активного MCP
            const tools = await this.getToolsForLLM();
            console.log(`[Server] Получено инструментов: ${tools.length}`);
            
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
                toolResult: response.toolResult || null
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

