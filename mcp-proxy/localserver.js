import express from 'express';
import cors from 'cors';
import { EventEmitter } from 'events';
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Загружаем переменные окружения из .env файла
dotenv.config();

const app = express();
const PORT = process.env.PORT || 3001;

// YandexGPT credentials (загружаются из .env файла)
const YANDEX_API_KEY = process.env.YANDEX_API_KEY;
const YANDEX_FOLDER_ID = process.env.YANDEX_FOLDER_ID;

if (!YANDEX_API_KEY || !YANDEX_FOLDER_ID) {
    console.error('❌ ОШИБКА: Не заданы переменные окружения YANDEX_API_KEY или YANDEX_FOLDER_ID');
    console.error('Создайте файл .env в папке mcp-proxy с содержимым:');
    console.error('  YANDEX_API_KEY=ваш_ключ');
    console.error('  YANDEX_FOLDER_ID=ваш_folder_id');
    console.error('\nИли скопируйте .env.example в .env и заполните значения.');
    process.exit(1);
}

// Middleware
app.use(cors());
app.use(express.json());

// =============================================================================
// MCP-СЕРВЕР: Работает только с инструментами и MCP-клиентом
// =============================================================================
class MCPServer {
    constructor() {
        this.tools = [
            {
                name: 'get_message_count',
                description: 'Получить текущее количество сообщений в чате с моделями. Если указано model_name, вернет счетчик для этой модели. Если не указано - вернет счетчики для всех моделей.',
                inputSchema: {
                    type: 'object',
                    properties: {
                        model_name: {
                            type: 'string',
                            description: 'Название модели. Если не указано, вернутся счетчики для всех моделей. Чтобы узнать доступные модели, используй инструмент get_available_models.'
                        }
                    },
                    required: []
                }
            },
            {
                name: 'get_available_models',
                description: 'Получить список доступных моделей для общения. Используй этот инструмент, когда пользователь спрашивает о моделях или их названиях.',
                inputSchema: {
                    type: 'object',
                    properties: {},
                    required: []
                }
            }
        ];
        this.dataFile = path.join(__dirname, 'count.json');
        this.availableModels = ['L3-8B-Stheno', 'MiniMax-M2', 'Qwen2.5-7B-Instruct'];
        this.initializeDataFile();
    }

    async initializeDataFile() {
        try {
            await fs.access(this.dataFile);
            // Проверяем формат файла
            const data = await fs.readFile(this.dataFile, 'utf-8');
            const parsed = JSON.parse(data);
            
            // Если старый формат (просто count), мигрируем на новый
            if (parsed.count !== undefined && !parsed.models) {
                const newData = {
                    models: {
                        'L3-8B-Stheno': 0,
                        'MiniMax-M2': 0,
                        'Qwen2.5-7B-Instruct': 0
                    }
                };
                await fs.writeFile(this.dataFile, JSON.stringify(newData, null, 2));
                console.log('[MCP-Server] Мигрирован count.json на новый формат с моделями');
            }
        } catch {
            // Файл не существует, создаем с начальным значением
                const initialData = {
                    models: {
                        'L3-8B-Stheno': 0,
                        'MiniMax-M2': 0,
                        'Qwen2.5-7B-Instruct': 0
                    }
                };
            await fs.writeFile(this.dataFile, JSON.stringify(initialData, null, 2));
            console.log('[MCP-Server] Инициализирован файл count.json с моделями');
        }
    }

    // Получить список доступных инструментов
    listTools() {
        console.log('[MCP-Server] Запрошен список инструментов');
        return {
            tools: this.tools
        };
    }

    // Вызвать инструмент
    async callTool(toolName, args) {
        console.log(`[MCP-Server] Вызов инструмента: ${toolName}`, args);
        
        switch (toolName) {
            case 'get_message_count':
                return await this.getMessageCount(args);
            case 'get_available_models':
                return await this.getAvailableModels();
            default:
                throw new Error(`Unknown tool: ${toolName}`);
        }
    }

    // Инструмент: получить количество сообщений
    async getMessageCount(args = {}) {
        try {
            const data = await fs.readFile(this.dataFile, 'utf-8');
            const parsed = JSON.parse(data);
            const { model_name } = args;
            
            if (model_name) {
                // Запрошен счетчик для конкретной модели
                const count = parsed.models[model_name];
                if (count === undefined) {
                    console.log(`[MCP-Server] Модель ${model_name} не найдена`);
                    return {
                        content: [
                            {
                                type: 'text',
                                text: `Модель ${model_name} не найдена. Доступные модели: ${Object.keys(parsed.models).join(', ')}`
                            }
                        ]
                    };
                }
                console.log(`[MCP-Server] Получено количество сообщений для ${model_name}: ${count}`);
                return {
                    content: [
                        {
                            type: 'text',
                            text: `Количество сообщений с моделью ${model_name}: ${count}`
                        }
                    ]
                };
            } else {
                // Запрошены счетчики для всех моделей
                console.log(`[MCP-Server] Получены счетчики для всех моделей:`, parsed.models);
                const modelsInfo = Object.entries(parsed.models)
                    .map(([model, count]) => `- ${model}: ${count}`)
                    .join('\n');
                return {
                    content: [
                        {
                            type: 'text',
                            text: `Количество сообщений по моделям:\n${modelsInfo}`
                        }
                    ]
                };
            }
        } catch (error) {
            console.error('[MCP-Server] Ошибка при чтении count.json:', error);
            throw error;
        }
    }

    // Сохранить количество сообщений для модели
    async saveMessageCount(modelName, count) {
        try {
            const data = await fs.readFile(this.dataFile, 'utf-8');
            const parsed = JSON.parse(data);
            
            if (!parsed.models) {
                parsed.models = {};
            }
            
            parsed.models[modelName] = count;
            
            await fs.writeFile(this.dataFile, JSON.stringify(parsed, null, 2));
            console.log(`[MCP-Server] Сохранено количество сообщений для ${modelName}: ${count}`);
        } catch (error) {
            console.error('[MCP-Server] Ошибка при сохранении count.json:', error);
            throw error;
        }
    }

    // Инструмент: получить список доступных моделей
    async getAvailableModels() {
        console.log(`[MCP-Server] Запрошен список доступных моделей`);
        const modelsList = this.availableModels.map((model, index) => `${index + 1}. ${model}`).join('\n');
        return {
            content: [
                {
                    type: 'text',
                    text: `Доступные модели для общения:\n${modelsList}`
                }
            ]
        };
    }
}

// =============================================================================
// MCP-КЛИЕНТ: Посредник между сервером и MCP-сервером
// =============================================================================
class MCPClient {
    constructor(mcpServer) {
        this.mcpServer = mcpServer;
    }

    // Получить список инструментов от MCP-сервера
    async listTools() {
        console.log('[MCP-Client] Запрос списка инструментов от MCP-Server');
        return this.mcpServer.listTools();
    }

    // Вызвать инструмент на MCP-сервере
    async callTool(toolName, args) {
        console.log(`[MCP-Client] Передача вызова инструмента '${toolName}' в MCP-Server`);
        return await this.mcpServer.callTool(toolName, args);
    }
}

// =============================================================================
// СЕРВЕР: Главный компонент для взаимодействия с App и LLM
// =============================================================================
class MainServer {
    constructor(mcpClient, mcpServer) {
        this.mcpClient = mcpClient;
        this.mcpServer = mcpServer;
    }

    // Получить список инструментов для передачи LLM
    async getToolsForLLM() {
        const toolsResponse = await this.mcpClient.listTools();
        
        // Преобразуем формат MCP в формат YandexGPT
        return toolsResponse.tools.map(tool => ({
            name: tool.name,
            description: tool.description,
            parameters: tool.inputSchema
        }));
    }

    // Вызвать LLM с поддержкой инструментов
    async callLLM(messages, tools) {
        console.log('[Server] Отправка запроса в YandexGPT');
        console.log('[Server] Количество сообщений в истории:', messages.length);
        console.log('[Server] Доступные инструменты:', tools.map(t => t.name).join(', '));

        // Динамически формируем system prompt на основе списка инструментов от MCP
        const toolsDescription = tools.map(tool => 
            `- ${tool.name}: ${tool.description}`
        ).join('\n');

        const systemMessage = {
            role: 'system',
            text: `Ты — полезный ассистент. У тебя есть доступ к следующим инструментам:

${toolsDescription}

Когда пользователь задает вопрос, связанный с функционалом инструмента, используй соответствующий инструмент.

Для использования инструмента ответь в следующем формате:
USE_TOOL: имя_инструмента

Например:
USE_TOOL: get_message_count

После этого я вызову инструмент и предоставлю результат, а ты сформируешь финальный ответ пользователю.`
        };

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
                console.error('[Server] Ошибка от YandexGPT:', response.status, errorText);
                throw new Error(`YandexGPT API error: ${response.status}`);
            }

            const data = await response.json();
            const assistantMessage = data.result.alternatives[0].message.text;
            
            console.log('[Server] Получен ответ от YandexGPT');
            console.log('[Server] Ответ:', assistantMessage.substring(0, 100) + '...');

            // Проверяем, хочет ли LLM использовать инструмент
            if (assistantMessage.includes('USE_TOOL:')) {
                const toolMatch = assistantMessage.match(/USE_TOOL:\s*(\w+)/);
                if (toolMatch) {
                    const toolName = toolMatch[1];
                    console.log(`[Server] LLM запросила использование инструмента: ${toolName}`);
                    
                    // Вызываем инструмент через MCP-клиент
                    const toolResult = await this.mcpClient.callTool(toolName, {});
                    const toolResultText = toolResult.content[0].text;
                    
                    console.log('[Server] Результат инструмента:', toolResultText);
                    
                    // Отправляем результат обратно в LLM
                    const followUpMessages = [
                        ...messages,
                        { role: 'assistant', text: assistantMessage },
                        { role: 'user', text: `Результат выполнения инструмента ${toolName}:\n${toolResultText}\n\nТеперь, пожалуйста, предоставь понятный ответ пользователю на основе этих данных.` }
                    ];
                    
                    const followUpResponse = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                            'Authorization': `Api-Key ${YANDEX_API_KEY}`,
                            'x-folder-id': YANDEX_FOLDER_ID
                        },
                        body: JSON.stringify({
                            ...requestBody,
                            messages: [systemMessage, ...followUpMessages]
                        })
                    });
                    
                    const followUpData = await followUpResponse.json();
                    const finalMessage = followUpData.result.alternatives[0].message.text;
                    
                    console.log('[Server] Финальный ответ от LLM:', finalMessage.substring(0, 100) + '...');
                    
                    return {
                        text: finalMessage,
                        toolUsed: toolName,
                        toolResult: toolResultText
                    };
                }
            }
            
            // Обычный ответ без инструментов
            return {
                text: assistantMessage,
                toolUsed: null
            };
            
        } catch (error) {
            console.error('[Server] Ошибка при вызове YandexGPT:', error);
            throw error;
        }
    }

    // Обработать сообщение от App
    async handleMessage(userMessage, messageHistory) {
        try {
            console.log('[Server] Запрос списка инструментов от MCP Client...');
            
            // ВАЖНО: Получаем список инструментов динамически от MCP Client
            // MCP Client в свою очередь получит их от MCP Server
            const tools = await this.getToolsForLLM();
            
            console.log('[Server] Получено инструментов:', tools.length);
            tools.forEach(tool => {
                console.log(`[Server]   - ${tool.name}: ${tool.description}`);
            });
            
            // Формируем историю сообщений
            const messages = [
                ...messageHistory,
                { role: 'user', text: userMessage }
            ];
            
            // Вызываем LLM с динамически полученными инструментами
            const response = await this.callLLM(messages, tools);
            
            return {
                success: true,
                message: response.text,
                toolUsed: response.toolUsed || null,
                toolResult: response.toolResult || null
            };
            
        } catch (error) {
            console.error('[Server] Ошибка при обработке сообщения:', error);
            return {
                success: false,
                error: error.message
            };
        }
    }

    // Обновить счетчик сообщений для модели
    async updateMessageCount(modelName, count) {
        console.log(`[Server] Обновление счетчика для модели ${modelName}: ${count}`);
        await this.mcpServer.saveMessageCount(modelName, count);
    }
}

// =============================================================================
// Инициализация компонентов
// =============================================================================
const mcpServer = new MCPServer();
const mcpClient = new MCPClient(mcpServer);
const mainServer = new MainServer(mcpClient, mcpServer);

// =============================================================================
// API ENDPOINTS (только App общается через эти endpoints)
// =============================================================================

/**
 * POST /api/chat
 * Отправить сообщение в чат (используется YandexGPT)
 * Body: { message: string, history: Array<{role: string, text: string}> }
 */
app.post('/api/chat', async (req, res) => {
    try {
        const { message, history = [] } = req.body;
        
        if (!message) {
            return res.status(400).json({
                success: false,
                error: 'Message is required'
            });
        }
        
        console.log(`\n[API] POST /api/chat - Новое сообщение от App`);
        console.log(`[API] Сообщение: "${message.substring(0, 50)}..."`);
        console.log(`[API] Используется YandexGPT`);
        
        const result = await mainServer.handleMessage(message, history);
        res.json(result);
        
    } catch (error) {
        console.error('[API] Ошибка в /api/chat:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * POST /api/message-count
 * Обновить счетчик сообщений для модели
 * Body: { modelName: string, count: number }
 */
app.post('/api/message-count', async (req, res) => {
    try {
        const { modelName, count } = req.body;
        
        if (!modelName || typeof modelName !== 'string') {
            return res.status(400).json({
                success: false,
                error: 'modelName is required and must be a string'
            });
        }
        
        if (typeof count !== 'number') {
            return res.status(400).json({
                success: false,
                error: 'count must be a number'
            });
        }
        
        console.log(`\n[API] POST /api/message-count - Обновление счетчика для ${modelName}`);
        
        await mainServer.updateMessageCount(modelName, count);
        
        res.json({
            success: true,
            modelName,
            count
        });
        
    } catch (error) {
        console.error('[API] Ошибка в /api/message-count:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * GET /api/message-count
 * Получить текущее количество сообщений
 * Query: ?modelName=имя_модели (опционально)
 */
app.get('/api/message-count', async (req, res) => {
    try {
        const { modelName } = req.query;
        
        console.log(`\n[API] GET /api/message-count - Запрос счетчика${modelName ? ` для ${modelName}` : ' для всех моделей'}`);
        
        const args = modelName ? { model_name: modelName } : {};
        const result = await mcpClient.callTool('get_message_count', args);
        const resultText = result.content[0].text;
        
        if (modelName) {
            // Парсим счетчик для одной модели
            const countMatch = resultText.match(/(\d+)/);
            const count = countMatch ? parseInt(countMatch[1]) : 0;
            
            res.json({
                success: true,
                modelName,
                count
            });
        } else {
            // Парсим счетчики для всех моделей
            const models = {};
            const lines = resultText.split('\n');
            lines.forEach(line => {
                const match = line.match(/- (.+?): (\d+)/);
                if (match) {
                    models[match[1]] = parseInt(match[2]);
                }
            });
            
            res.json({
                success: true,
                models
            });
        }
        
    } catch (error) {
        console.error('[API] Ошибка в /api/message-count:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * GET /api/tools
 * Получить список доступных инструментов
 */
app.get('/api/tools', async (req, res) => {
    try {
        console.log('\n[API] GET /api/tools - Запрос списка инструментов');
        
        const tools = await mainServer.getToolsForLLM();
        
        res.json({
            success: true,
            tools
        });
        
    } catch (error) {
        console.error('[API] Ошибка в /api/tools:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * GET /health
 * Health check
 */
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        server: 'localserver',
        architecture: {
            app: 'Android App',
            server: 'Main Server (Express + YandexGPT)',
            mcpClient: 'MCP Client',
            mcpServer: 'MCP Server (Tools)'
        },
        timestamp: new Date().toISOString()
    });
});

// =============================================================================
// Запуск сервера
// =============================================================================
app.listen(PORT, '0.0.0.0', () => {
    console.log('\n==========================================================');
    console.log(`🚀 Local Server запущен на http://0.0.0.0:${PORT}`);
    console.log('==========================================================');
    console.log('\n📐 Архитектура:');
    console.log('  📱 App → 🖥️  Main Server → 🔌 MCP Client → 🔧 MCP Server → 🛠️  Tools');
    console.log('\n📡 Доступные endpoints:');
    console.log(`  POST   /api/chat           - Отправить сообщение в чат`);
    console.log(`  POST   /api/message-count  - Обновить счетчик сообщений`);
    console.log(`  GET    /api/message-count  - Получить счетчик сообщений`);
    console.log(`  GET    /api/tools          - Список инструментов`);
    console.log(`  GET    /health             - Health check`);
    console.log('\n🔧 Доступные инструменты MCP:');
    mcpServer.tools.forEach(tool => {
        console.log(`  - ${tool.name}: ${tool.description}`);
    });
    console.log('\n💡 Данные хранятся в: count.json');
    console.log('==========================================================\n');
});

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n🛑 Остановка сервера...');
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n🛑 Остановка сервера...');
    process.exit(0);
});

