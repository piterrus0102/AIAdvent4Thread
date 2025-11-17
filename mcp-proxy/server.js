import express from 'express';
import cors from 'cors';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Global MCP client instance
let mcpClient = null;
let mcpTransport = null;

/**
 * Подключение к MCP серверу
 * POST /connect
 * Body: { serverName: "filesystem" }
 */
app.post('/connect', async (req, res) => {
    try {
        const { serverName = 'filesystem' } = req.body;
        
        console.log(`[MCP Proxy] Connecting to MCP server: ${serverName}`);
        
        // Отключаем существующее соединение, если есть
        if (mcpClient) {
            console.log('[MCP Proxy] Disconnecting existing client...');
            await mcpClient.close();
            mcpClient = null;
            mcpTransport = null;
        }
        
        // Создаем новое подключение
        // Здесь используется встроенный MCP сервер для файловой системы
        // Для других серверов нужно изменить команду и аргументы
        let command, args;
        
        switch (serverName) {
            case 'filesystem':
                // Используем npx для запуска @modelcontextprotocol/server-filesystem
                command = 'npx';
                args = [
                    '-y',
                    '@modelcontextprotocol/server-filesystem',
                    process.cwd() // Разрешаем доступ к текущей директории
                ];
                break;
            
            case 'memory':
                command = 'npx';
                args = ['-y', '@modelcontextprotocol/server-memory'];
                break;
                
            case 'everything':
                // Everything MCP server (для поиска файлов на Windows)
                command = 'npx';
                args = ['-y', '@modelcontextprotocol/server-everything'];
                break;
            
            default:
                return res.status(400).json({
                    success: false,
                    message: `Unknown server type: ${serverName}`
                });
        }
        
        console.log(`[MCP Proxy] Starting server with command: ${command} ${args.join(' ')}`);
        
        // Создаем транспорт
        mcpTransport = new StdioClientTransport({
            command,
            args
        });
        
        // Создаем клиент
        mcpClient = new Client({
            name: 'mcp-proxy-client',
            version: '1.0.0'
        }, {
            capabilities: {}
        });
        
        // Подключаемся
        await mcpClient.connect(mcpTransport);
        
        console.log('[MCP Proxy] Successfully connected to MCP server');
        
        res.json({
            success: true,
            message: `Connected to ${serverName} MCP server`
        });
        
    } catch (error) {
        console.error('[MCP Proxy] Connection error:', error);
        res.status(500).json({
            success: false,
            message: error.message || 'Failed to connect to MCP server'
        });
    }
});

/**
 * Получение списка доступных инструментов
 * GET /tools
 */
app.get('/tools', async (req, res) => {
    try {
        if (!mcpClient) {
            return res.status(400).json({
                success: false,
                message: 'Not connected to MCP server. Call /connect first.'
            });
        }
        
        console.log('[MCP Proxy] Requesting tools list...');
        
        // Получаем список инструментов от MCP сервера
        const response = await mcpClient.listTools();
        
        console.log(`[MCP Proxy] Received ${response.tools.length} tools`);
        
        res.json({
            tools: response.tools.map(tool => ({
                name: tool.name,
                description: tool.description || '',
                inputSchema: tool.inputSchema || {}
            }))
        });
        
    } catch (error) {
        console.error('[MCP Proxy] Error getting tools:', error);
        res.status(500).json({
            success: false,
            message: error.message || 'Failed to get tools'
        });
    }
});

/**
 * Вызов инструмента
 * POST /tools/:toolName
 * Body: { arguments: {...} }
 */
app.post('/tools/:toolName', async (req, res) => {
    try {
        if (!mcpClient) {
            return res.status(400).json({
                success: false,
                message: 'Not connected to MCP server. Call /connect first.'
            });
        }
        
        const { toolName } = req.params;
        const { arguments: toolArgs = {} } = req.body;
        
        console.log(`[MCP Proxy] Calling tool: ${toolName}`);
        console.log(`[MCP Proxy] Arguments:`, toolArgs);
        
        const response = await mcpClient.callTool({
            name: toolName,
            arguments: toolArgs
        });
        
        console.log(`[MCP Proxy] Tool response:`, response);
        
        res.json({
            success: true,
            result: response
        });
        
    } catch (error) {
        console.error('[MCP Proxy] Error calling tool:', error);
        res.status(500).json({
            success: false,
            message: error.message || 'Failed to call tool'
        });
    }
});

/**
 * Отключение от MCP сервера
 * POST /disconnect
 */
app.post('/disconnect', async (req, res) => {
    try {
        if (mcpClient) {
            console.log('[MCP Proxy] Disconnecting from MCP server...');
            await mcpClient.close();
            mcpClient = null;
            mcpTransport = null;
            console.log('[MCP Proxy] Disconnected successfully');
        }
        
        res.json({
            success: true,
            message: 'Disconnected from MCP server'
        });
        
    } catch (error) {
        console.error('[MCP Proxy] Disconnect error:', error);
        res.status(500).json({
            success: false,
            message: error.message || 'Failed to disconnect'
        });
    }
});

/**
 * Health check
 * GET /health
 */
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        connected: mcpClient !== null,
        timestamp: new Date().toISOString()
    });
});

// Запуск сервера
app.listen(PORT, '0.0.0.0', () => {
    console.log(`[MCP Proxy] Server is running on http://0.0.0.0:${PORT}`);
    console.log(`[MCP Proxy] Available endpoints:`);
    console.log(`  POST   /connect       - Connect to MCP server`);
    console.log(`  GET    /tools         - Get available tools`);
    console.log(`  POST   /tools/:name   - Call a tool`);
    console.log(`  POST   /disconnect    - Disconnect from MCP server`);
    console.log(`  GET    /health        - Health check`);
});

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n[MCP Proxy] Shutting down...');
    if (mcpClient) {
        await mcpClient.close();
    }
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n[MCP Proxy] Shutting down...');
    if (mcpClient) {
        await mcpClient.close();
    }
    process.exit(0);
});

