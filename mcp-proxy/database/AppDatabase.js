import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

class AppDatabase {
    constructor() {
        // БД должна быть в корне mcp-proxy/, а не в database/
        // __dirname = mcp-proxy/database/, поэтому идем на уровень выше
        const dbPath = path.join(__dirname, '..', 'app_data.db');
        this.db = new Database(dbPath);
        this.initializeDatabase();
        console.log('[Database] База данных инициализирована:', dbPath);
    }

    initializeDatabase() {
        // Таблица для хранения сообщений из приложения
        this.db.exec(`
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                message_id TEXT UNIQUE NOT NULL,
                text TEXT NOT NULL,
                is_user INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                model_name TEXT,
                tool_used TEXT,
                tool_result TEXT,
                screen_type TEXT,
                created_at INTEGER DEFAULT (strftime('%s', 'now'))
            );

            CREATE INDEX IF NOT EXISTS idx_timestamp ON messages(timestamp);
            CREATE INDEX IF NOT EXISTS idx_model_name ON messages(model_name);
            CREATE INDEX IF NOT EXISTS idx_screen_type ON messages(screen_type);

            -- Таблица для счетчиков моделей
            CREATE TABLE IF NOT EXISTS model_counters (
                model_name TEXT PRIMARY KEY,
                message_count INTEGER NOT NULL DEFAULT 0,
                last_updated INTEGER DEFAULT (strftime('%s', 'now'))
            );

            -- GitHub данные сохраняются в таблицу messages
            -- Отдельные таблицы для каждого инструмента не нужны!
        `);

        console.log('[Database] Таблицы созданы/проверены');
    }

    // =========================================================================
    // Методы для работы с сообщениями
    // =========================================================================

    /**
     * Синхронизировать сообщения из приложения
     * @param {Array} messages - массив сообщений из приложения
     */
    syncMessages(messages) {
        const insert = this.db.prepare(`
            INSERT OR REPLACE INTO messages 
            (message_id, text, is_user, timestamp, model_name, tool_used, tool_result, screen_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `);

        const insertMany = this.db.transaction((msgs) => {
            for (const msg of msgs) {
                insert.run(
                    msg.messageId || `msg_${msg.timestamp}_${Math.random()}`,
                    msg.text,
                    msg.isUser ? 1 : 0,
                    msg.timestamp,
                    msg.modelName || null,
                    msg.toolUsed || null,
                    msg.toolResult || null,
                    msg.screenType || null
                );
            }
        });

        insertMany(messages);
        console.log(`[Database] Синхронизировано сообщений: ${messages.length}`);
        
        return { success: true, synced: messages.length };
    }

    /**
     * Получить все сообщения
     */
    getAllMessages(limit = 1000) {
        const stmt = this.db.prepare(`
            SELECT * FROM messages 
            ORDER BY timestamp DESC 
            LIMIT ?
        `);
        return stmt.all(limit);
    }

    /**
     * Очистить сообщения
     */
    clearMessages(screenType = null) {
        let stmt;
        let deleted;
        
        if (screenType) {
            stmt = this.db.prepare(`DELETE FROM messages WHERE screen_type = ?`);
            const result = stmt.run(screenType);
            deleted = result.changes;
            console.log(`[Database] Удалено сообщений для screenType '${screenType}': ${deleted}`);
        } else {
            stmt = this.db.prepare(`DELETE FROM messages`);
            const result = stmt.run();
            deleted = result.changes;
            console.log(`[Database] Удалено всех сообщений: ${deleted}`);
        }
        
        return { success: true, deleted };
    }

    /**
     * Получить сообщения по модели
     */
    getMessagesByModel(modelName) {
        const stmt = this.db.prepare(`
            SELECT * FROM messages 
            WHERE model_name = ? 
            ORDER BY timestamp DESC
        `);
        return stmt.all(modelName);
    }

    // =========================================================================
    // Методы для работы со счетчиками моделей
    // =========================================================================

    /**
     * Обновить счетчик модели
     */
    updateModelCounter(modelName, count) {
        const stmt = this.db.prepare(`
            INSERT OR REPLACE INTO model_counters (model_name, message_count, last_updated)
            VALUES (?, ?, strftime('%s', 'now'))
        `);
        stmt.run(modelName, count);
        console.log(`[Database] Обновлен счетчик для ${modelName}: ${count}`);
    }

    /**
     * Получить счетчик модели
     */
    getModelCounter(modelName) {
        const stmt = this.db.prepare(`
            SELECT message_count FROM model_counters WHERE model_name = ?
        `);
        const row = stmt.get(modelName);
        return row ? row.message_count : 0;
    }

    /**
     * Получить все счетчики
     */
    getAllCounters() {
        const stmt = this.db.prepare(`
            SELECT model_name, message_count FROM model_counters
        `);
        const rows = stmt.all();
        const counters = {};
        rows.forEach(row => {
            counters[row.model_name] = row.message_count;
        });
        return counters;
    }

    // =========================================================================
    // GitHub данные сохраняются в таблицу messages
    // =========================================================================
    // 
    // Все ответы от GitHub инструментов автоматически сохраняются
    // в таблицу messages вместе с tool_used и tool_result.
    // 
    // Отдельные таблицы для кэширования НЕ нужны потому что:
    // 1. Таблица messages уже сохраняет всё между сессиями
    // 2. GitHub API достаточно быстрый для повторных запросов
    // 3. Избыточное дублирование данных
    //
    // Аналогия Android: не создаём отдельную Room таблицу для каждого
    // Retrofit endpoint - используем одну таблицу Message для всех ответов
    // =========================================================================

    /**
     * Закрыть соединение с БД
     */
    close() {
        this.db.close();
        console.log('[Database] Соединение закрыто');
    }
}

export default AppDatabase;

