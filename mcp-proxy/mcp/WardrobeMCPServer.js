// =============================================================================
// WardrobeMCPServer - Локальный MCP сервер для гардеробной
// =============================================================================
// Предоставляет инструменты для выбора одежды и обуви на основе погоды
// =============================================================================
class WardrobeMCPServer {
    /**
     * @param {AppDatabase} database - База данных приложения
     */
    constructor(database) {
        this.database = database;
        
        // Инструменты гардеробной
        this.tools = [
            {
                name: 'get_all_clothing',
                description: '🚨 ОБЯЗАТЕЛЕН для ЛЮБЫХ вопросов "что одеть", "какую одежду", "во что одеться"! Возвращает полный список одежды из гардероба пользователя. Без вызова этого инструмента ЗАПРЕЩЕНО рекомендовать одежду! Не параметров не нужно - просто вызови.',
                inputSchema: {
                    type: 'object',
                    properties: {},
                    required: []
                }
            },
            {
                name: 'get_all_shoes',
                description: '🚨 ОБЯЗАТЕЛЕН для ЛЮБЫХ вопросов "что одеть", "какую обувь"! Возвращает полный список обуви из гардероба пользователя. Без вызова этого инструмента ЗАПРЕЩЕНО рекомендовать обувь! Никаких параметров не нужно - просто вызови.',
                inputSchema: {
                    type: 'object',
                    properties: {},
                    required: []
                }
            }
        ];
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
        // console.log('[Wardrobe-MCP-Server] Запрошен список инструментов');
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
        console.log(`[Wardrobe-MCP-Server] Вызов инструмента: ${toolName}`);
        
        switch (toolName) {
            case 'get_all_clothing':
                return await this.getAllClothing();
            case 'get_all_shoes':
                return await this.getAllShoes();
            default:
                throw new Error(`Unknown tool: ${toolName}`);
        }
    }

    // =========================================================================
    // TOOLS IMPLEMENTATION - Реализация инструментов
    // =========================================================================
    
    /**
     * Инструмент: получить всю одежду из гардероба
     */
    async getAllClothing() {
        try {
            console.log(`[Wardrobe-MCP-Server] Получение всей одежды из гардероба`);
            
            // Получаем ВСЮ одежду из БД без фильтрации
            const clothes = this.database.getAllClothes();
            
            console.log(`[Wardrobe-MCP-Server] Всего одежды в гардеробе: ${clothes.length}`);
            
            if (clothes.length === 0) {
                return {
                    content: [
                        {
                            type: 'text',
                            text: 'Гардероб пуст. Добавьте одежду в базу данных.'
                        }
                    ]
                };
            }
            
            // Формируем структурированный список
            const clothingList = clothes.map(item => 
                `• ${item.name} (${item.type})\n  - Цвет: ${item.color}, Материал: ${item.material}\n  - Сезон: ${item.season}\n  - Температура: от ${item.temperature_min}°C до ${item.temperature_max}°C\n  - Погодные условия: ${item.weather_conditions}`
            ).join('\n\n');
            
            return {
                content: [
                    {
                        type: 'text',
                        text: `Полный список одежды в гардеробе (${clothes.length} предметов):\n\n${clothingList}\n\n⚠️ ВАЖНО: Выбери ТОЛЬКО подходящие предметы для текущей температуры и погоды! Не выдумывай одежду которой нет в списке!`
                    }
                ]
            };
            
        } catch (error) {
            console.error('[Wardrobe-MCP-Server] Ошибка получения одежды:', error);
            throw error;
        }
    }

    /**
     * Инструмент: получить всю обувь из гардероба
     */
    async getAllShoes() {
        try {
            console.log(`[Wardrobe-MCP-Server] Получение всей обуви из гардероба`);
            
            // Получаем ВСЮ обувь из БД без фильтрации
            const shoes = this.database.getAllShoes();
            
            console.log(`[Wardrobe-MCP-Server] Всего обуви в гардеробе: ${shoes.length}`);
            
            if (shoes.length === 0) {
                return {
                    content: [
                        {
                            type: 'text',
                            text: 'В гардеробе нет обуви. Добавьте обувь в базу данных.'
                        }
                    ]
                };
            }
            
            // Формируем структурированный список
            const shoesList = shoes.map(item => 
                `• ${item.name} (${item.type})\n  - Цвет: ${item.color}, Материал: ${item.material}\n  - Сезон: ${item.season}\n  - Температура: от ${item.temperature_min}°C до ${item.temperature_max}°C\n  - Погодные условия: ${item.weather_conditions}`
            ).join('\n\n');
            
            return {
                content: [
                    {
                        type: 'text',
                        text: `Полный список обуви в гардеробе (${shoes.length} пар):\n\n${shoesList}\n\n⚠️ ВАЖНО: Выбери ТОЛЬКО подходящую обувь для текущей температуры и погоды! Не выдумывай обувь которой нет в списке!`
                    }
                ]
            };
            
        } catch (error) {
            console.error('[Wardrobe-MCP-Server] Ошибка получения обуви:', error);
            throw error;
        }
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default WardrobeMCPServer;

