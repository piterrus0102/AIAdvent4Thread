// =============================================================================
// IMPORTS
// =============================================================================
import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// =============================================================================
// MCPServer - Локальный MCP сервер с инструментами
// =============================================================================
// Предоставляет доступ к локальным данным через инструменты (tools)
// =============================================================================
class MCPServer {
    constructor() {
        // Инструменты (tools) - это как API endpoints
        // Каждый инструмент = метод который можно вызвать
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
        
        // Путь к JSON файлу с данными
        this.dataFile = path.join(__dirname, '..', 'count.json');
        
        // Список доступных моделей
        this.availableModels = ['L3-8B-Stheno', 'MiniMax-M2', 'Qwen2.5-7B-Instruct'];
        
        // Инициализируем файл данных
        this.initializeDataFile();
    }

    // =========================================================================
    // INITIALIZATION - Инициализация данных
    // =========================================================================
    
    /**
     * Инициализировать файл с данными
     */
    async initializeDataFile() {
        try {
            // Проверяем существует ли файл
            await fs.access(this.dataFile);
            
            // Проверяем формат файла
            const data = await fs.readFile(this.dataFile, 'utf-8');
            const parsed = JSON.parse(data);
            
            // Миграция старого формата на новый (если нужно)
            if (parsed.count !== undefined && !parsed.models) {
                const newData = {
                    models: {
                        'L3-8B-Stheno': 0,
                        'MiniMax-M2': 0,
                        'Qwen2.5-7B-Instruct': 0
                    }
                };
                await fs.writeFile(this.dataFile, JSON.stringify(newData, null, 2));
                console.log('[MCP-Server] Мигрирован count.json на новый формат');
            }
        } catch {
            // Файл не существует - создаем с начальными значениями
            const initialData = {
                models: {
                    'L3-8B-Stheno': 0,
                    'MiniMax-M2': 0,
                    'Qwen2.5-7B-Instruct': 0
                }
            };
            await fs.writeFile(this.dataFile, JSON.stringify(initialData, null, 2));
            console.log('[MCP-Server] Инициализирован count.json');
        }
    }

    // =========================================================================
    // PUBLIC API - MCP протокол
    // =========================================================================
    
    /**
     * Получить список доступных инструментов
     *
     * @returns {{tools: Array}} - Список инструментов
     */
    listTools() {
        console.log('[MCP-Server] Запрошен список инструментов');
        return {
            tools: this.tools
        };
    }

    /**
     * Вызвать инструмент
     *
     * @param {string} toolName - Название инструмента
     * @param {object} args - Аргументы
     * @returns {Promise<{content: Array}>} - Результат выполнения
     */
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

    // =========================================================================
    // TOOLS IMPLEMENTATION - Реализация инструментов
    // =========================================================================
    
    /**
     * Инструмент: получить количество сообщений
     */
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
                console.log(`[MCP-Server] Счетчик для ${model_name}: ${count}`);
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
                console.log(`[MCP-Server] Счетчики для всех моделей:`, parsed.models);
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
            console.error('[MCP-Server] Ошибка чтения count.json:', error);
            throw error;
        }
    }

    /**
     * Сохранить количество сообщений для модели
     */
    async saveMessageCount(modelName, count) {
        try {
            const data = await fs.readFile(this.dataFile, 'utf-8');
            const parsed = JSON.parse(data);
            
            if (!parsed.models) {
                parsed.models = {};
            }
            
            parsed.models[modelName] = count;
            
            await fs.writeFile(this.dataFile, JSON.stringify(parsed, null, 2));
            console.log(`[MCP-Server] Сохранено для ${modelName}: ${count}`);
        } catch (error) {
            console.error('[MCP-Server] Ошибка сохранения:', error);
            throw error;
        }
    }

    /**
     * Инструмент: получить список доступных моделей
     */
    async getAvailableModels() {
        console.log(`[MCP-Server] Запрошен список моделей`);
        const modelsList = this.availableModels
            .map((model, index) => `${index + 1}. ${model}`)
            .join('\n');
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
// EXPORT
// =============================================================================
export default MCPServer;



