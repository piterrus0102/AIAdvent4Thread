// =============================================================================
// AIAdvent4Thread MCP Proxy Server
// =============================================================================
// Главная точка входа (Entry Point)
//
// Архитектура (Clean Architecture style):
//
//  ┌────────────────────────────────────────────────────────────────┐
//  │                     Android App (Client)                        │
//  └───────────────────────────┬────────────────────────────────────┘
//                              │ HTTP API
//  ┌───────────────────────────▼────────────────────────────────────┐
//  │                    Express Server (API Layer)                   │
//  │                         index.js                                │
//  └───────────────────────────┬────────────────────────────────────┘
//                              │
//  ┌───────────────────────────▼────────────────────────────────────┐
//  │                  MainServer (Business Logic)                    │
//  │                  Orchestrator / ViewModel                       │
//  └────┬─────────────┬─────────────┬────────────────┬──────────────┘
//       │             │             │                │
//  ┌────▼──────┐ ┌───▼────────┐ ┌──▼──────────┐ ┌──▼─────────────┐
//  │ MCPClient │ │ GitHubMCP  │ │ AppDatabase │ │  HuggingFace   │
//  │           │ │   Client   │ │             │ │ L3-8B-Stheno   │
//  └────┬──────┘ └────┬───────┘ └─────────────┘ │  Instruct      │
//       │             │                          │  (External)    │
//  ┌────▼──────┐ ┌───▼───────────┐              └────────────────┘
//  │ MCPServer │ │ github-mcp-   │
//  │ (Local)   │ │ server (Go)   │
//  └───────────┘ └───────────────┘
//
// =============================================================================

// =============================================================================
// IMPORTS
// =============================================================================
import express from 'express';
import cors from 'cors';
import readline from 'readline';

// Наши модули
import AppDatabase from './database/AppDatabase.js';
import MCPServer from './mcp/MCPServer.js';
import MCPClient from './mcp/MCPClient.js';
import GitHubMCPClient from './mcp/GitHubMCPClient.js';
import WardrobeMCPServer from './mcp/WardrobeMCPServer.js';
import WardrobeMCPClient from './mcp/WardrobeMCPClient.js';
import WeatherMCPServer from './mcp/WeatherMCPServer.js';
import WeatherMCPClient from './mcp/WeatherMCPClient.js';
import MainServer from './server/MainServer.js';
import RequestClassifier from './server/RequestClassifier.js';

// =============================================================================
// CONFIGURATION
// =============================================================================
const app = express();
const PORT = process.env.PORT || 3001;

// Middleware
app.use(cors());
app.use(express.json());

// =============================================================================
// DEPENDENCY INJECTION - Создание и связывание компонентов
// =============================================================================

console.log('[App] 🚀 Инициализация компонентов...');

// База данных
const database = new AppDatabase();
console.log('[App] ✅ База данных инициализирована');

// Локальный MCP сервер
const mcpServer = new MCPServer();
const mcpClient = new MCPClient(mcpServer);
console.log('[App] ✅ Локальный MCP сервер инициализирован');

// GitHub MCP клиент
const githubMCPClient = new GitHubMCPClient();
console.log('[App] ✅ GitHub MCP клиент инициализирован');

// Wardrobe MCP сервер (гардеробная)
const wardrobeMCPServer = new WardrobeMCPServer(database);
const wardrobeMCPClient = new WardrobeMCPClient(wardrobeMCPServer);
console.log('[App] ✅ Wardrobe MCP сервер инициализирован (гардеробная)');

// Weather MCP сервер (погода)
const weatherMCPServer = new WeatherMCPServer();
const weatherMCPClient = new WeatherMCPClient(weatherMCPServer);
console.log('[App] ✅ Weather MCP сервер инициализирован (погода)');

// Главный сервер (оркестратор)
const mainServer = new MainServer(
    mcpClient, 
    mcpServer, 
    database, 
    githubMCPClient, 
    wardrobeMCPClient, 
    weatherMCPClient
);
console.log('[App] ✅ MainServer инициализирован (оркестратор)');

// Инициализируем RAG сервис (загружаем векторный индекс)
console.log('[App] 🔄 Инициализация RAG сервиса...');
try {
    await mainServer.ragService.initialize();
    console.log('[App] ✅ RAG сервис инициализирован');
} catch (error) {
    console.error('[App] ⚠️ RAG сервис недоступен:', error.message);
    console.error('[App] Для использования RAG запустите: cd rag-proxy && node build-index.js');
}

// Классификатор запросов
const requestClassifier = new RequestClassifier();
console.log('[App] ✅ RequestClassifier инициализирован');

console.log('[App] ====== Все компоненты готовы ======');

// =============================================================================
// API ENDPOINTS - REST API для Android приложения
// =============================================================================

/**
 * POST /api/chat
 * Отправить сообщение в чат (используется HuggingFace L3-8B-Stheno + MCP)
 * 
 * Аналог: @POST("/api/chat") suspend fun chat(@Body request: ChatRequest)
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
        
        console.log(`\n[API] POST /api/chat`);
        console.log(`[API] Сообщение: "${message.substring(0, 50)}..."`);
        
        // ===== LLM-BASED РОУТИНГ: Определяем тип запроса =====
        const classification = await requestClassifier.classify(message);
        
        if (classification.type === 'reminder') {
            console.log('[API] 🔔 LLM определила: запрос на создание напоминания');
            
            try {
                // Конвертируем единицы времени в секунды
                let intervalSeconds = classification.scheduleTime;
                if (classification.scheduleUnit === 'minutes') {
                    intervalSeconds *= 60;
                } else if (classification.scheduleUnit === 'hours') {
                    intervalSeconds *= 3600;
                }
                
                // Создаем напоминание напрямую с распарсенными данными
                const reminderId = mainServer.reminderManager.nextId++;
                const intervalMs = intervalSeconds * 1000;
                
                const config = {
                    id: reminderId,
                    interval: intervalSeconds,
                    intervalMs: intervalMs,
                    topic: classification.queryString,
                    query: `Проверь ${classification.queryString}`,
                    owner: null,
                    repo: null,
                    createdAt: new Date().toISOString(),
                    executionCount: 0
                };
                
                // Запускаем таймер
                const intervalId = setInterval(async () => {
                    await mainServer.reminderManager.executeReminder(reminderId);
                }, intervalMs);
                
                mainServer.reminderManager.reminders.set(reminderId, { config, intervalId });
                
                console.log('[API] ✅ Напоминание создано');
                console.log('[API] ID:', reminderId);
                console.log('[API] Интервал:', intervalSeconds, 'секунд');
                console.log('[API] Тема:', classification.queryString);
                
                // Первый запуск сразу
                setImmediate(() => mainServer.reminderManager.executeReminder(reminderId));
                
                return res.json({
                    success: true,
                    message: `Напоминание создано! Буду проверять ${classification.queryString} каждые ${classification.scheduleTime} ${classification.scheduleUnit}.`,
                    isReminder: true,
                    reminderId,
                    interval: intervalSeconds,
                    topic: classification.queryString
                });
                
            } catch (reminderError) {
                console.error('[API] ❌ Ошибка создания напоминания:', reminderError);
                return res.status(500).json({
                    success: false,
                    error: reminderError.message
                });
            }
        }
        
        // ===== ОБЫЧНЫЙ ЧАТ =====
        console.log('[API] 💬 LLM определила: обычный чат');
        const result = await mainServer.handleMessage(message, history);
        
        if (result.incorrectRAG) {
            console.log('[API] 🚨 LLM обнаружила жалобу на неправильное понимание!');
            console.log('[API] Флаг incorrectRAG установлен - требуется улучшение релевантности');
        }
        
        res.json(result);
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/chat:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * POST /api/message-count
 * Обновить счетчик сообщений для модели
 */
app.post('/api/message-count', async (req, res) => {
    try {
        const { modelName, count } = req.body;
        
        if (!modelName || typeof count !== 'number') {
            return res.status(400).json({
                success: false,
                error: 'modelName and count are required'
            });
        }
        
        console.log(`\n[API] POST /api/message-count: ${modelName} = ${count}`);
        
        await mainServer.updateMessageCount(modelName, count);
        
        res.json({
            success: true,
            modelName,
            count
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/message-count:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * GET /api/message-count
 * Получить текущее количество сообщений
 */
app.get('/api/message-count', async (req, res) => {
    try {
        const { modelName } = req.query;
        
        console.log(`\n[API] GET /api/message-count${modelName ? `: ${modelName}` : ''}`);
        
        const args = modelName ? { model_name: modelName } : {};
        const result = await mcpClient.callTool('get_message_count', args);
        const resultText = result.content[0].text;
        
        if (modelName) {
            const countMatch = resultText.match(/(\d+)/);
            const count = countMatch ? parseInt(countMatch[1]) : 0;
            
            res.json({
                success: true,
                modelName,
                count
            });
        } else {
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
        console.error('[API] ❌ Ошибка /api/message-count:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * GET /api/tools
 * Получить список доступных инструментов
 */
app.get('/api/tools', async (req, res) => {
    try {
        console.log('\n[API] GET /api/tools');
        
        const tools = await mainServer.getToolsForLLM();
        
        res.json({
            success: true,
            tools
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/tools:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * POST /api/sync-messages
 * Синхронизировать сообщения из приложения в БД
 */
app.post('/api/sync-messages', async (req, res) => {
    try {
        const { messages } = req.body;
        
        if (!messages || !Array.isArray(messages)) {
            return res.status(400).json({
                success: false,
                error: 'messages array is required'
            });
        }
        
        console.log(`\n[API] POST /api/sync-messages: ${messages.length} сообщений`);
        
        const result = await mainServer.syncMessages(messages);
        
        res.json({
            success: true,
            synced: result.synced
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/sync-messages:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * GET /api/messages
 * Получить сообщения из БД
 */
app.get('/api/messages', async (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 100;
        const screenType = req.query.screenType;
        
        console.log(`\n[API] GET /api/messages (limit: ${limit}, screenType: ${screenType || 'all'})`);
        
        let messages;
        if (screenType) {
            const stmt = database.db.prepare(`
                SELECT * FROM messages 
                WHERE screen_type = ? 
                ORDER BY timestamp DESC 
                LIMIT ?
            `);
            messages = stmt.all(screenType, limit);
        } else {
            messages = database.getAllMessages(limit);
        }
        
        console.log(`[API] Найдено сообщений: ${messages.length}`);
        
        res.json({
            success: true,
            messages: messages.map(msg => ({
                messageId: msg.message_id,
                text: msg.text,
                isUser: msg.is_user === 1,
                timestamp: msg.timestamp,
                modelName: msg.model_name,
                toolUsed: msg.tool_used,
                toolResult: msg.tool_result,
                screenType: msg.screen_type
            })),
            count: messages.length
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/messages:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * POST /api/mcp-mode
 * Включить/выключить базовые MCP инструменты (local, wardrobe, weather)
 */
app.post('/api/mcp-mode', async (req, res) => {
    try {
        const { useMCP } = req.body;
        
        if (typeof useMCP !== 'boolean') {
            return res.status(400).json({
                success: false,
                error: 'useMCP (boolean) is required'
            });
        }
        
        console.log(`\n[API] POST /api/mcp-mode: ${useMCP ? 'Включен' : 'Выключен'}`);
        
        mainServer.setMCPMode(useMCP);
        
        res.json({
            success: true,
            useMCP: useMCP
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/mcp-mode:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * POST /api/github-mcp-mode
 * Включить/выключить GitHub MCP
 */
app.post('/api/github-mcp-mode', async (req, res) => {
    try {
        const { useGitHub, githubToken } = req.body;
        
        if (typeof useGitHub !== 'boolean') {
            return res.status(400).json({
                success: false,
                error: 'useGitHub (boolean) is required'
            });
        }
        
        if (useGitHub && !githubToken) {
            return res.status(400).json({
                success: false,
                error: 'githubToken is required when useGitHub is true'
            });
        }
        
        console.log(`\n[API] POST /api/github-mcp-mode: ${useGitHub ? 'Включен' : 'Выключен'}`);
        
        mainServer.setGitHubMCPMode(useGitHub, githubToken);
        
        res.json({
            success: true,
            useGitHub: useGitHub
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/github-mcp-mode:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * POST /api/rag-mode
 * Переключить режим RAG (включить/выключить)
 */
app.post('/api/rag-mode', async (req, res) => {
    try {
        const { useRAG } = req.body;
        
        if (typeof useRAG !== 'boolean') {
            return res.status(400).json({
                success: false,
                error: 'useRAG (boolean) is required'
            });
        }
        
        console.log(`\n[API] POST /api/rag-mode: ${useRAG ? 'RAG (Векторный поиск)' : 'Обычный (Прямой LLM)'}`);
        
        mainServer.setRAGMode(useRAG);
        
        res.json({
            success: true,
            mode: useRAG ? 'rag' : 'direct',
            description: useRAG 
                ? 'RAG режим: векторный поиск по курсу + LLM'
                : 'Обычный режим: прямой запрос к LLM с инструментами'
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/rag-mode:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * GET /api/rag-mode
 * Получить текущий режим RAG
 */
app.get('/api/rag-mode', async (req, res) => {
    try {
        console.log('\n[API] GET /api/rag-mode');
        
        const isRAGEnabled = mainServer.getRAGMode();
        
        res.json({
            success: true,
            mode: isRAGEnabled ? 'rag' : 'direct',
            enabled: isRAGEnabled,
            description: isRAGEnabled 
                ? 'RAG режим: векторный поиск по курсу + LLM'
                : 'Обычный режим: прямой запрос к LLM с инструментами'
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/rag-mode:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * POST /api/rag-demo-mode
 * Включить/выключить демо-режим RAG (субоптимальный первый ответ)
 */
app.post('/api/rag-demo-mode', async (req, res) => {
    try {
        const { demoMode } = req.body;
        
        console.log(`\n[API] POST /api/rag-demo-mode`);
        console.log(`[API] Демо-режим: ${demoMode ? 'ВКЛЮЧЕН' : 'ВЫКЛЮЧЕН'}`);
        
        // Устанавливаем демо-режим в RAG сервисе
        mainServer.ragService.setDemoMode(demoMode);
        
        res.json({
            success: true,
            demoMode: mainServer.ragService.demoMode,
            message: demoMode 
                ? 'Демо-режим включен: первый ответ будет субоптимальным'
                : 'Демо-режим выключен: нормальная работа'
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/rag-demo-mode:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * GET /api/rag-demo-mode
 * Получить состояние демо-режима RAG
 */
app.get('/api/rag-demo-mode', async (req, res) => {
    try {
        console.log('\n[API] GET /api/rag-demo-mode');
        
        const demoMode = mainServer.ragService.demoMode;
        
        res.json({
            success: true,
            demoMode: demoMode,
            description: demoMode
                ? '🎭 Демо-режим: первый ответ субоптимальный (для демонстрации реранкинга)'
                : '✅ Нормальный режим: оптимальные ответы с первого раза',
            rerankingOptions: mainServer.ragService.rerankingOptions
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/rag-demo-mode:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * DELETE /api/messages/clear
 * Очистить историю сообщений
 */
app.delete('/api/messages/clear', async (req, res) => {
    try {
        const { screenType } = req.query;
        
        console.log(`\n[API] DELETE /api/messages/clear${screenType ? ` (${screenType})` : ''}`);
        
        const result = await database.clearMessages(screenType);
        
        res.json({
            success: true,
            deleted: result.deleted
        });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/messages/clear:', error);
        res.status(500).json({
            success: false,
            error: error.message
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
        server: 'AIAdvent4Thread MCP Proxy',
        version: '2.0.0 (MCP Orchestration + RAG)',
        architecture: {
            app: 'Android App',
            api: 'Express REST API',
            server: 'MainServer (Orchestrator)',
            mcp_servers: {
                local: 'MCPServer (локальные инструменты)',
                wardrobe: 'WardrobeMCPServer (гардеробная)',
                weather: 'WeatherMCPServer (погода Open-Meteo)',
                github: 'GitHubMCPClient (опционально)'
            },
            database: 'SQLite (с таблицами для гардеробной)',
            llm: 'HuggingFace L3-8B-Stheno',
            rag: 'RAGService (Векторный поиск по курсу Android Studio)'
        },
        features: {
            orchestration: 'Одновременная работа нескольких MCP серверов',
            github_mode: 'Дополняет инструменты (не заменяет)',
            rag_mode: 'Переключаемый режим: RAG (векторный поиск) или прямой LLM',
            example: 'что мне сегодня одеть? → погода + гардеробная'
        },
        modes: {
            rag_enabled: mainServer.getRAGMode(),
            github_enabled: mainServer.useGitHubMCP
        },
        timestamp: new Date().toISOString()
    });
});

// =============================================================================
// REMINDER ENDPOINTS - Планировщик напоминаний
// =============================================================================

/**
 * POST /api/reminder/create
 * Создать новое напоминание
 * 
 * Body: { request: "оповещай меня каждые 10 секунд об issues" }
 */
app.post('/api/reminder/create', async (req, res) => {
    try {
        const { request } = req.body;
        
        if (!request) {
            return res.status(400).json({
                success: false,
                error: 'Request is required'
            });
        }
        
        console.log(`\n[API] POST /api/reminder/create`);
        console.log(`[API] Запрос: "${request}"`);
        
        const result = await mainServer.reminderManager.createReminder(request);
        res.json(result);
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/reminder/create:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * DELETE /api/reminder/:id
 * Остановить напоминание
 */
app.delete('/api/reminder/:id', async (req, res) => {
    try {
        const reminderId = parseInt(req.params.id);
        
        console.log(`\n[API] DELETE /api/reminder/${reminderId}`);
        
        const success = mainServer.reminderManager.stopReminder(reminderId);
        
        if (success) {
            res.json({ success: true, message: 'Reminder stopped' });
        } else {
            res.status(404).json({ success: false, error: 'Reminder not found' });
        }
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/reminder/:id:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

/**
 * GET /api/reminders
 * Получить список активных напоминаний
 */
app.get('/api/reminders', async (req, res) => {
    try {
        console.log(`\n[API] GET /api/reminders`);
        
        const reminders = mainServer.reminderManager.listReminders();
        res.json({ success: true, reminders });
        
    } catch (error) {
        console.error('[API] ❌ Ошибка /api/reminders:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});

// =============================================================================
// SERVER LIFECYCLE - Запуск и остановка сервера
// =============================================================================
// Аналог: onCreate() / onDestroy() в Android

const server = app.listen(PORT, '0.0.0.0', () => {
    console.log('\n========================================================================================================');
    console.log(`🚀 AIAdvent4Thread MCP Proxy Server запущен на http://0.0.0.0:${PORT}`);
    console.log('========================================================================================================');
    console.log('\n📐 Архитектура (Clean Architecture + MCP Orchestration):');
    console.log('  📱 Android App');
    console.log('     ↓ HTTP REST API');
    console.log('  🌐 Express Server (index.js)');
    console.log('     ↓');
    console.log('  🧠 MainServer (Orchestrator)');
    console.log('     ├─→ 🔧 MCPClient → MCPServer (Локальные инструменты)');
    console.log('     ├─→ 👔 WardrobeMCPClient → WardrobeMCPServer (Гардеробная)');
    console.log('     ├─→ 🌤️  WeatherMCPClient → WeatherMCPServer (Погода Open-Meteo)');
    console.log('     ├─→ 🐙 GitHubMCPClient → github-mcp-server (опционально)');
    console.log('     ├─→ 💾 AppDatabase (SQLite)');
    console.log('     └─→ 🤖 HuggingFace Qwen/Qwen2.5-7B-Instruct (LLM)');
    console.log('\n🎯 Возможности оркестрации:');
    console.log('  • Одновременная работа нескольких MCP серверов');
    console.log('  • Автоматический выбор правильного сервера для каждого инструмента');
    console.log('  • GitHub MCP дополняет инструменты (не заменяет)');
    console.log('  • Пример: "что мне сегодня одеть?" → погода + гардеробная');
    console.log('\n📡 API Endpoints:');
    console.log(`  POST   /api/chat              - Отправить сообщение`);
    console.log(`  POST   /api/message-count     - Обновить счетчик`);
    console.log(`  GET    /api/message-count     - Получить счетчик`);
    console.log(`  GET    /api/tools             - Список инструментов`);
    console.log(`  POST   /api/sync-messages     - Синхронизация сообщений`);
    console.log(`  GET    /api/messages          - Получить сообщения`);
    console.log(`  POST   /api/mcp-mode          - Включить/выключить базовые MCP инструменты`);
    console.log(`  POST   /api/github-mcp-mode   - Включить/выключить GitHub MCP`);
    console.log(`  POST   /api/rag-mode          - Включить/выключить RAG режим 🔍`);
    console.log(`  GET    /api/rag-mode          - Получить текущий режим RAG`);
    console.log(`  DELETE /api/messages/clear    - Очистить историю`);
    console.log(`  POST   /api/reminder/create   - Создать напоминание`);
    console.log(`  DELETE /api/reminder/:id      - Остановить напоминание`);
    console.log(`  GET    /api/reminders         - Список напоминаний`);
    console.log(`  GET    /health                - Health check`);
    console.log('\n💡 Для Android эмулятора используй: http://10.0.2.2:${PORT}');
    console.log('========================================================================================================\n');
});

// Устанавливаем таймаут для HTTP соединений (60 секунд)
server.timeout = 60000; // 60 секунд
server.keepAliveTimeout = 65000; // 65 секунд (чуть больше чем timeout)
server.headersTimeout = 66000; // 66 секунд (чуть больше чем keepAliveTimeout)

console.log('[Server] ⏱️ Таймауты установлены:');
console.log(`  - Request timeout: ${server.timeout}ms (60s)`);
console.log(`  - Keep-alive timeout: ${server.keepAliveTimeout}ms (65s)`);
console.log(`  - Headers timeout: ${server.headersTimeout}ms (66s)`);

// =============================================================================
// CONSOLE INTERFACE - Консольное управление сервером
// =============================================================================

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: ''
});

console.log('\n⌨️  КОНСОЛЬНОЕ УПРАВЛЕНИЕ:');
console.log('  Команды:');
console.log('    mcp on    - Включить MCP инструменты (local, wardrobe, weather)');
console.log('    mcp off   - Выключить MCP инструменты');
console.log('    mcp       - Показать текущий режим MCP');
console.log('    rag on    - Включить RAG режим (векторный поиск по курсу)');
console.log('    rag off   - Выключить RAG режим (прямой запрос к LLM)');
console.log('    rag       - Показать текущий режим RAG');
console.log('    status    - Показать статус всех режимов');
console.log('    help      - Показать список команд');
console.log('');

rl.on('line', (input) => {
    const command = input.trim().toLowerCase();
    
    switch(command) {
        case 'mcp on':
            mainServer.setMCPMode(true);
            break;
            
        case 'mcp off':
            mainServer.setMCPMode(false);
            break;
            
        case 'mcp':
            const mcpEnabled = mainServer.getMCPMode();
            console.log(`\n[Console] Текущий режим MCP: ${mcpEnabled ? '✅ ВКЛЮЧЕН' : '❌ ВЫКЛЮЧЕН'}`);
            console.log(`[Console] Описание: ${mcpEnabled 
                ? 'Инструменты MCP доступны (local, wardrobe, weather)'
                : 'Только прямой запрос к LLM без инструментов'}\n`);
            break;
            
        case 'rag on':
            mainServer.setRAGMode(true);
            break;
            
        case 'rag off':
            mainServer.setRAGMode(false);
            break;
            
        case 'rag':
            const ragEnabled = mainServer.getRAGMode();
            console.log(`\n[Console] Текущий режим RAG: ${ragEnabled ? '✅ ВКЛЮЧЕН' : '❌ ВЫКЛЮЧЕН'}`);
            console.log(`[Console] Описание: ${ragEnabled 
                ? 'Векторный поиск по курсу + LLM'
                : 'Прямой запрос к LLM с инструментами'}\n`);
            break;
            
        case 'status':
            const isMCP = mainServer.getMCPMode();
            const isRAG = mainServer.getRAGMode();
            const isGitHub = mainServer.useGitHubMCP;
            console.log('\n[Console] ====== СТАТУС СЕРВЕРА ======');
            console.log(`[Console] MCP режим:    ${isMCP ? '✅ ВКЛЮЧЕН' : '❌ ВЫКЛЮЧЕН'}`);
            console.log(`[Console] RAG режим:    ${isRAG ? '✅ ВКЛЮЧЕН' : '❌ ВЫКЛЮЧЕН'}`);
            console.log(`[Console] GitHub MCP:   ${isGitHub ? '✅ ВКЛЮЧЕН' : '❌ ВЫКЛЮЧЕН'}`);
            console.log(`[Console] Напоминаний:  ${mainServer.reminderManager.reminders.size}`);
            console.log('[Console] ====================================\n');
            break;
            
        case 'help':
            console.log('\n[Console] ====== ДОСТУПНЫЕ КОМАНДЫ ======');
            console.log('[Console] mcp on    - Включить MCP инструменты');
            console.log('[Console] mcp off   - Выключить MCP инструменты');
            console.log('[Console] mcp       - Показать текущий режим MCP');
            console.log('[Console] rag on    - Включить RAG режим');
            console.log('[Console] rag off   - Выключить RAG режим');
            console.log('[Console] rag       - Показать текущий режим RAG');
            console.log('[Console] status    - Статус всех режимов');
            console.log('[Console] help      - Показать эту справку');
            console.log('[Console] =====================================\n');
            break;
            
        case '':
            // Игнорируем пустые строки
            break;
            
        default:
            if (command) {
                console.log(`\n[Console] ⚠️  Неизвестная команда: "${command}"`);
                console.log('[Console] Используйте "help" для списка команд\n');
            }
    }
});

// Graceful shutdown (аналог onDestroy в Android)
process.on('SIGINT', async () => {
    console.log('\n🛑 Остановка сервера...');
    rl.close();
    mainServer.reminderManager.stopAll();
    database.close();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n🛑 Остановка сервера...');
    rl.close();
    mainServer.reminderManager.stopAll();
    database.close();
    process.exit(0);
});

