// =============================================================================
// WardrobeMCPClient - Клиент для работы с гардеробной
// =============================================================================
// Архитектура:
//   MainServer → WardrobeMCPClient → WardrobeMCPServer → AppDatabase
// =============================================================================
class WardrobeMCPClient {
    /**
     * @param {WardrobeMCPServer} wardrobeServer - Сервер гардеробной
     */
    constructor(wardrobeServer) {
        this.wardrobeServer = wardrobeServer;
    }

    // =========================================================================
    // PUBLIC API - Методы для работы с Wardrobe Server
    // =========================================================================
    
    /**
     * Получить список инструментов от Wardrobe-сервера
     *
     * @returns {Promise<{tools: Array}>} - Список инструментов
     */
    async listTools() {
        // console.log('[Wardrobe-MCP-Client] Запрос списка инструментов от Wardrobe-Server');
        return this.wardrobeServer.listTools();
    }

    /**
     * Вызвать инструмент на Wardrobe-сервере
     *
     * @param {string} toolName - Название инструмента
     * @param {object} args - Аргументы
     * @returns {Promise<{content: Array}>} - Результат
     */
    async callTool(toolName, args) {
        console.log(`[Wardrobe-MCP-Client] Передача вызова '${toolName}' в Wardrobe-Server`);
        return await this.wardrobeServer.callTool(toolName, args);
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default WardrobeMCPClient;

