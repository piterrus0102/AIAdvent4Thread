// =============================================================================
// WeatherMCPClient - Клиент для работы с погодой
// =============================================================================
// Архитектура:
//   MainServer → WeatherMCPClient → WeatherMCPServer → Open-Meteo API
// =============================================================================
class WeatherMCPClient {
    /**
     * @param {WeatherMCPServer} weatherServer - Сервер погоды
     */
    constructor(weatherServer) {
        this.weatherServer = weatherServer;
    }

    // =========================================================================
    // PUBLIC API - Методы для работы с Weather Server
    // =========================================================================
    
    /**
     * Получить список инструментов от Weather-сервера
     *
     * @returns {Promise<{tools: Array}>} - Список инструментов
     */
    async listTools() {
        console.log('[Weather-MCP-Client] Запрос списка инструментов от Weather-Server');
        return this.weatherServer.listTools();
    }

    /**
     * Вызвать инструмент на Weather-сервере
     *
     * @param {string} toolName - Название инструмента
     * @param {object} args - Аргументы
     * @returns {Promise<{content: Array}>} - Результат
     */
    async callTool(toolName, args) {
        console.log(`[Weather-MCP-Client] Передача вызова '${toolName}' в Weather-Server`);
        return await this.weatherServer.callTool(toolName, args);
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default WeatherMCPClient;

