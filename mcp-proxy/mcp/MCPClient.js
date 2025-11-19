// =============================================================================
// MCPClient - Клиент для работы с локальным MCP Server
// =============================================================================
// Архитектура:
//   MainServer → MCPClient → MCPServer
//   (ViewModel → Repository → DataSource)
// =============================================================================
class MCPClient {
    /**
     * @param {MCPServer} mcpServer - Локальный MCP сервер
     */
    constructor(mcpServer) {
        this.mcpServer = mcpServer;
    }

    // =========================================================================
    // PUBLIC API - Методы для работы с MCP Server
    // =========================================================================
    
    /**
     * Получить список инструментов от MCP-сервера
     *
     * @returns {Promise<{tools: Array}>} - Список инструментов
     */
    async listTools() {
        console.log('[MCP-Client] Запрос списка инструментов от MCP-Server');
        return this.mcpServer.listTools();
    }

    /**
     * Вызвать инструмент на MCP-сервере
     *
     * @param {string} toolName - Название инструмента
     * @param {object} args - Аргументы
     * @returns {Promise<{content: Array}>} - Результат
     */
    async callTool(toolName, args) {
        console.log(`[MCP-Client] Передача вызова '${toolName}' в MCP-Server`);
        return await this.mcpServer.callTool(toolName, args);
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default MCPClient;

