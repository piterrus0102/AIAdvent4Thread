// =============================================================================
// IMPORTS - Зависимости
// =============================================================================
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { existsSync } from 'fs';

// Получаем путь к текущему файлу
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// =============================================================================
// GitHubMCPClient - Repository для работы с GitHub через MCP
// =============================================================================
// Аналог Repository/DataSource в Android (Clean Architecture)
// Отвечает за связь с внешним GitHub MCP Server (Go binary)
//
// Архитектура:
//   Node.js App → GitHubMCPClient → StdioClientTransport → github-mcp-server (Go) → GitHub API
//
// Похоже на:
//   Android App → Repository → Retrofit/OkHttp → Backend API
// =============================================================================
class GitHubMCPClient {
    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================
    constructor() {
        this.client = null;           // MCP Client (аналог Retrofit instance)
        this.transport = null;         // Транспорт для связи с процессом (аналог OkHttpClient)
        this.isConnected = false;      // Статус подключения (как LiveData<Boolean>)
        this.githubToken = null;       // GitHub токен (как SharedPreferences token)
    }

    // =========================================================================
    // PUBLIC API - Методы для работы с подключением
    // =========================================================================
    
    /**
     * Подключиться к GitHub MCP Server
     * 
     * Что происходит:
     * 1. Проверяем, не подключены ли уже
     * 2. Находим Go binary файл (github-mcp-server)
     * 3. Запускаем процесс через StdioClientTransport
     * 4. Создаем MCP Client для общения с процессом
     * 5. Подключаемся и устанавливаем isConnected = true
     * 
     * @param {string} githubToken - GitHub Personal Access Token
     * @returns {Promise<{success: boolean}>} - Результат подключения
     */
    async connect(githubToken) {
        try {
            // Если уже подключены - переподключаемся
            if (this.isConnected) {
                console.log('[GitHub MCP] Уже подключено, переподключаюсь...');
                await this.disconnect();
            }

            console.log('[GitHub MCP] 🔌 Подключение к GitHub MCP Server...');
            console.log('[GitHub MCP] Token: ${githubToken.substring(0, 10)}...');
            
            this.githubToken = githubToken;

            // ===== ШАГ 1: Проверяем наличие Go бинарника =====
            // Путь к скомпилированному GitHub MCP Server (Go binary)
            const serverPath = join(__dirname, 'github-mcp-server');
            console.log('[GitHub MCP] Путь к binary:', serverPath);

            if (!existsSync(serverPath)) {
                throw new Error(`GitHub MCP Server binary не найден: ${serverPath}\nУбедитесь что файл существует и исполняемый (chmod +x)`);
            }

            // ===== ШАГ 2: Создаем транспорт для связи с процессом =====
            // Запускает процесс и общается с ним через stdin/stdout
            this.transport = new StdioClientTransport({
                command: serverPath,           // Путь к Go binary
                args: ['stdio'],               // Аргументы: 'stdio' - режим работы через stdio
                env: {
                    ...process.env,            // Передаем все переменные окружения
                    GITHUB_PERSONAL_ACCESS_TOKEN: githubToken  // + добавляем GitHub токен
                }
            });

            // ===== ШАГ 3: Создаем MCP Client =====
            // Client - это как Retrofit instance в Android
            // Управляет запросами к MCP Server
            this.client = new Client(
                {
                    name: 'github-mcp-client',
                    version: '1.0.0'
                },
                {
                    capabilities: {}  // Пока без особых capabilities
                }
            );

            // ===== ШАГ 4: Подключаемся =====
            // Аналог: retrofit.create(ApiService::class.java)
            await this.client.connect(this.transport);
            this.isConnected = true;

            console.log('[GitHub MCP] ✅ Успешно подключено к GitHub MCP Server');
            
            return { success: true };
            
        } catch (error) {
            console.error('[GitHub MCP] ❌ Ошибка подключения:', error);
            this.isConnected = false;
            
            // Cleanup: закрываем транспорт если был создан
            if (this.transport) {
                try {
                    await this.transport.close();
                } catch (closeError) {
                    console.error('[GitHub MCP] Ошибка закрытия транспорта:', closeError);
                }
                this.transport = null;
            }
            
            throw error;
        }
    }

    /**
     * Отключиться от GitHub MCP Server
     */
    async disconnect() {
        try {
            // Закрываем клиент
            if (this.client) {
                await this.client.close();
                this.client = null;
            }
            
            // Останавливаем процесс GitHub MCP Server
            if (this.transport) {
                await this.transport.close();
                this.transport = null;
            }
            
            this.isConnected = false;
            console.log('[GitHub MCP] 🔌 Отключено от GitHub MCP Server');
            
        } catch (error) {
            console.error('[GitHub MCP] ❌ Ошибка при отключении:', error);
        }
    }

    // =========================================================================
    // API CALLS - Методы для вызова инструментов GitHub
    // =========================================================================
    
    /**
     * Получить список доступных инструментов от GitHub MCP Server
     * 
     * Возвращает список всех доступных GitHub инструментов:
     * - github_search_repositories
     * - github_list_pull_requests
     * - github_create_issue
     * - и т.д. (около 40 инструментов)
     * 
     * @returns {Promise<{tools: Array}>} - Список инструментов с описаниями
     */
    async listTools() {
        // Проверка подключения
        if (!this.isConnected || !this.client) {
            throw new Error('❌ Не подключено к GitHub MCP Server. Вызовите connect() сначала.');
        }

        try {
            console.log('[GitHub MCP] 📋 Запрос списка инструментов...');
            
            // Вызываем MCP метод
            const response = await this.client.listTools();
            
            console.log(`[GitHub MCP] ✅ Получено инструментов: ${response.tools.length}`);
            
            // Логируем первые 5 для отладки
            response.tools.slice(0, 5).forEach(tool => {
                console.log(`[GitHub MCP]   🔧 ${tool.name}: ${tool.description || 'No description'}`);
            });
            
            return response;
            
        } catch (error) {
            console.error('[GitHub MCP] ❌ Ошибка получения инструментов:', error);
            throw error;
        }
    }

    /**
     * Вызвать GitHub инструмент
     * 
     * Примеры вызовов:
     * - callTool('github_search_repositories', { query: 'kotlin' })
     * - callTool('github_list_pull_requests', { owner: 'google', repo: 'android' })
     * - callTool('github_create_issue', { owner: 'me', repo: 'app', title: 'Bug' })
     * 
     * @param {string} toolName - Название инструмента
     * @param {object} args - Параметры запроса
     * @returns {Promise<{content: Array}>} - Результат выполнения
     */
    async callTool(toolName, args) {
        // Проверка подключения
        if (!this.isConnected || !this.client) {
            throw new Error('❌ Не подключено к GitHub MCP Server. Вызовите connect() сначала.');
        }

        try {
            console.log(`[GitHub MCP] 🔧 Вызов: ${toolName}`);
            console.log(`[GitHub MCP] 📝 Аргументы:`, JSON.stringify(args, null, 2));
            
            // Выполняем вызов
            const response = await this.client.callTool({
                name: toolName,
                arguments: args
            });
            
            console.log(`[GitHub MCP] ✅ Результат получен от ${toolName}`);
            return response;
            
        } catch (error) {
            console.error(`[GitHub MCP] ❌ Ошибка вызова ${toolName}:`, error);
            throw error;
        }
    }

}

// =============================================================================
// EXPORT - Экспорт класса
// =============================================================================
export default GitHubMCPClient;
