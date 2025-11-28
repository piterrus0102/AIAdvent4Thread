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
        // Миграция: добавляем rag_metadata если его нет
        this._migrateRagChatTable();
        
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

            -- Таблица для одежды (Wardrobe)
            CREATE TABLE IF NOT EXISTS wardrobe_clothes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                season TEXT NOT NULL,
                color TEXT,
                material TEXT,
                temperature_min INTEGER,
                temperature_max INTEGER,
                weather_conditions TEXT
            );

            -- Таблица для обуви (Wardrobe)
            CREATE TABLE IF NOT EXISTS wardrobe_shoes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                season TEXT NOT NULL,
                color TEXT,
                material TEXT,
                temperature_min INTEGER,
                temperature_max INTEGER,
                weather_conditions TEXT
            );

            -- Таблица для истории RAG-чата (чат с курсом)
            CREATE TABLE IF NOT EXISTS rag_chat (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                rag_metadata TEXT,
                timestamp INTEGER DEFAULT (strftime('%s', 'now')),
                created_at TEXT DEFAULT (datetime('now', 'localtime'))
            );

            CREATE INDEX IF NOT EXISTS idx_rag_chat_timestamp ON rag_chat(timestamp);

            -- GitHub данные сохраняются в таблицу messages
            -- Отдельные таблицы для каждого инструмента не нужны!
        `);

        console.log('[Database] Таблицы созданы/проверены');
        
        // Предзаполнение данными гардеробной (если таблицы пусты)
        this.seedWardrobeData();
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

    // =========================================================================
    // Методы для работы с гардеробной (Wardrobe)
    // =========================================================================

    /**
     * Предзаполнить таблицы гардеробной данными
     */
    seedWardrobeData() {
        // Проверяем, не заполнены ли уже таблицы
        const clothesCount = this.db.prepare('SELECT COUNT(*) as count FROM wardrobe_clothes').get().count;
        const shoesCount = this.db.prepare('SELECT COUNT(*) as count FROM wardrobe_shoes').get().count;

        if (clothesCount > 0 && shoesCount > 0) {
            console.log('[Database] Гардеробная уже содержит данные');
            return;
        }

        console.log('[Database] Предзаполнение гардеробной...');

        // Одежда
        const clothesData = [
            { name: 'Легкая футболка', type: 'верх', season: 'лето', color: 'белая', material: 'хлопок', temp_min: 20, temp_max: 40, weather: 'солнечно, ясно' },
            { name: 'Рубашка с коротким рукавом', type: 'верх', season: 'лето', color: 'синяя', material: 'лён', temp_min: 18, temp_max: 35, weather: 'солнечно, облачно' },
            { name: 'Свитер тонкий', type: 'верх', season: 'весна-осень', color: 'серый', material: 'шерсть', temp_min: 10, temp_max: 20, weather: 'облачно, прохладно' },
            { name: 'Толстовка', type: 'верх', season: 'весна-осень', color: 'черная', material: 'хлопок', temp_min: 8, temp_max: 18, weather: 'облачно, ветер' },
            { name: 'Теплый свитер', type: 'верх', season: 'зима', color: 'темно-синий', material: 'шерсть', temp_min: -10, temp_max: 10, weather: 'холодно, снег' },
            { name: 'Зимняя куртка', type: 'верх', season: 'зима', color: 'черная', material: 'пуховик', temp_min: -30, temp_max: 5, weather: 'мороз, снег, ветер' },
            { name: 'Легкая ветровка', type: 'верх', season: 'весна-осень', color: 'синяя', material: 'полиэстер', temp_min: 10, temp_max: 20, weather: 'дождь, ветер' },
            { name: 'Джинсы', type: 'низ', season: 'всесезонные', color: 'синие', material: 'деним', temp_min: 5, temp_max: 25, weather: 'любая' },
            { name: 'Шорты', type: 'низ', season: 'лето', color: 'бежевые', material: 'хлопок', temp_min: 20, temp_max: 40, weather: 'солнечно, жарко' },
            { name: 'Теплые брюки', type: 'низ', season: 'зима', color: 'черные', material: 'шерсть', temp_min: -20, temp_max: 10, weather: 'холодно, мороз' }
        ];

        // Обувь
        const shoesData = [
            { name: 'Сандалии', type: 'летняя', season: 'лето', color: 'коричневые', material: 'кожа', temp_min: 20, temp_max: 40, weather: 'солнечно, жарко, сухо' },
            { name: 'Кроссовки летние', type: 'спортивная', season: 'лето', color: 'белые', material: 'текстиль', temp_min: 15, temp_max: 35, weather: 'солнечно, облачно' },
            { name: 'Кроссовки демисезонные', type: 'спортивная', season: 'весна-осень', color: 'черные', material: 'синтетика', temp_min: 5, temp_max: 20, weather: 'облачно, дождь' },
            { name: 'Туфли классические', type: 'классическая', season: 'весна-осень', color: 'черные', material: 'кожа', temp_min: 10, temp_max: 25, weather: 'сухо, офис' },
            { name: 'Ботинки демисезонные', type: 'демисезонная', season: 'весна-осень', color: 'коричневые', material: 'кожа', temp_min: 0, temp_max: 15, weather: 'дождь, слякоть' },
            { name: 'Зимние ботинки', type: 'зимняя', season: 'зима', color: 'черные', material: 'кожа+мех', temp_min: -25, temp_max: 5, weather: 'снег, мороз, слякоть' },
            { name: 'Резиновые сапоги', type: 'дождевая', season: 'весна-осень', color: 'синие', material: 'резина', temp_min: 5, temp_max: 20, weather: 'дождь, лужи' }
        ];

        // Вставка одежды
        const insertClothes = this.db.prepare(`
            INSERT INTO wardrobe_clothes (name, type, season, color, material, temperature_min, temperature_max, weather_conditions)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `);

        const insertClothesMany = this.db.transaction((items) => {
            for (const item of items) {
                insertClothes.run(item.name, item.type, item.season, item.color, item.material, item.temp_min, item.temp_max, item.weather);
            }
        });

        insertClothesMany(clothesData);

        // Вставка обуви
        const insertShoes = this.db.prepare(`
            INSERT INTO wardrobe_shoes (name, type, season, color, material, temperature_min, temperature_max, weather_conditions)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `);

        const insertShoesMany = this.db.transaction((items) => {
            for (const item of items) {
                insertShoes.run(item.name, item.type, item.season, item.color, item.material, item.temp_min, item.temp_max, item.weather);
            }
        });

        insertShoesMany(shoesData);

        console.log(`[Database] Добавлено одежды: ${clothesData.length}`);
        console.log(`[Database] Добавлено обуви: ${shoesData.length}`);
    }

    /**
     * Получить ВСЮ одежду из гардеробной (без фильтрации)
     */
    getAllClothes() {
        const stmt = this.db.prepare('SELECT * FROM wardrobe_clothes');
        return stmt.all();
    }

    /**
     * Получить ВСЮ обувь из гардеробной (без фильтрации)
     */
    getAllShoes() {
        const stmt = this.db.prepare('SELECT * FROM wardrobe_shoes');
        return stmt.all();
    }

    // =========================================================================
    // Миграция базы данных
    // =========================================================================
    
    /**
     * Миграция: добавить поле rag_metadata в таблицу rag_chat если его нет
     */
    _migrateRagChatTable() {
        try {
            // Проверяем существует ли таблица
            const tableExists = this.db.prepare(`
                SELECT name FROM sqlite_master 
                WHERE type='table' AND name='rag_chat'
            `).get();
            
            if (tableExists) {
                // Проверяем есть ли поле rag_metadata
                const columns = this.db.prepare(`PRAGMA table_info(rag_chat)`).all();
                const hasRagMetadata = columns.some(col => col.name === 'rag_metadata');
                
                if (!hasRagMetadata) {
                    console.log('[Database] Миграция: добавление поля rag_metadata...');
                    this.db.exec(`ALTER TABLE rag_chat ADD COLUMN rag_metadata TEXT`);
                    console.log('[Database] ✓ Поле rag_metadata добавлено');
                }
            }
        } catch (error) {
            console.error('[Database] Ошибка миграции:', error.message);
        }
    }

    // =========================================================================
    // Методы для работы с историей RAG-чата
    // =========================================================================

    /**
     * Получить всю историю RAG-чата
     */
    getRagChatHistory() {
        const stmt = this.db.prepare(`
            SELECT * FROM rag_chat 
            ORDER BY timestamp ASC
        `);
        return stmt.all();
    }

    /**
     * Добавить сообщение в историю RAG-чата
     * @param {string} role - 'user' или 'assistant'
     * @param {string} text - Текст сообщения
     * @param {Object} ragMetadata - Метаданные RAG (ragLessons и т.д.)
     */
    addRagChatMessage(role, text, ragMetadata = null) {
        const stmt = this.db.prepare(`
            INSERT INTO rag_chat (role, text, rag_metadata)
            VALUES (?, ?, ?)
        `);
        const metadataJson = ragMetadata ? JSON.stringify(ragMetadata) : null;
        const result = stmt.run(role, text, metadataJson);
        console.log(`[Database] Добавлено сообщение в RAG-чат: ${role} (id: ${result.lastInsertRowid})`);
        return { success: true, id: result.lastInsertRowid };
    }

    /**
     * Очистить историю RAG-чата
     */
    clearRagChatHistory() {
        const stmt = this.db.prepare(`DELETE FROM rag_chat`);
        const result = stmt.run();
        const deleted = result.changes;
        console.log(`[Database] Очищена история RAG-чата: ${deleted} сообщений`);
        return { success: true, deleted };
    }

    /**
     * Закрыть соединение с БД
     */
    close() {
        this.db.close();
        console.log('[Database] Соединение закрыто');
    }
}

export default AppDatabase;

