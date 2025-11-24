// =============================================================================
// RAGService - Сервис для RAG (Retrieval-Augmented Generation)
// =============================================================================
// Этот сервис интегрирует векторный поиск с LLM для ответов на вопросы
// по курсу Android Studio
// =============================================================================

import path from 'path';
import { fileURLToPath } from 'url';
import VectorizationClient from './VectorizationClient.js';
import IndexManager from './IndexManager.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const INDEX_FILE = path.join(__dirname, '../data/vector_index.json');

class RAGService {
    constructor() {
        this.vectorClient = new VectorizationClient();
        this.indexManager = new IndexManager(INDEX_FILE, this.vectorClient);
        this.isInitialized = false;
        
        console.log('[RAGService] Инициализирован');
    }

    // =========================================================================
    // INITIALIZATION
    // =========================================================================
    
    /**
     * Инициализировать сервис (загрузить индекс)
     */
    async initialize() {
        if (this.isInitialized) {
            console.log('[RAGService] Уже инициализирован');
            return;
        }
        
        console.log('[RAGService] Инициализация...');
        
        // Проверяем наличие индекса
        const exists = await this.indexManager.indexExists();
        if (!exists) {
            throw new Error(
                'Векторный индекс не найден! ' +
                'Запустите: node mcp-proxy/rag/build-index.js'
            );
        }
        
        // Загружаем индекс
        await this.indexManager.loadIndex();
        
        const stats = this.indexManager.getStats();
        console.log(`[RAGService] ✓ Индекс загружен: ${stats.documents_count} документов`);
        
        this.isInitialized = true;
    }

    // =========================================================================
    // RAG PIPELINE
    // =========================================================================
    
    /**
     * Выполнить RAG pipeline: поиск + генерация ответа
     * 
     * @param {string} query - Вопрос пользователя
     * @param {number} topK - Количество релевантных документов (по умолчанию 3)
     * @returns {Promise<Object>} - Результат: { lessons, context, answer }
     */
    async query(query, topK = 3) {
        if (!this.isInitialized) {
            await this.initialize();
        }
        
        console.log(`\n[RAGService] === RAG Pipeline для запроса: "${query}" ===`);
        
        // ЭТАП 1: Векторный поиск по индексу
        console.log('[RAGService] [1/2] Векторный поиск...');
        const results = await this.indexManager.search(query, topK);
        
        // Формируем контекст из найденных уроков
        const context = this._buildContext(results);
        const lessons = results.map(r => ({
            id: r.id,
            title: r.lesson_title,
            section: r.section,
            url: r.url,
            relevance: (r.similarity * 100).toFixed(1) + '%'
        }));
        
        console.log(`[RAGService] ✓ Найдено ${results.length} релевантных уроков`);
        
        // ЭТАП 2: Генерация ответа через LLM (опционально, если интегрирован с MainServer)
        // Пока просто возвращаем контекст и список уроков
        console.log('[RAGService] [2/2] Подготовка ответа...');
        
        return {
            query: query,
            lessons: lessons,
            context: context,
            answer: null // Будет заполнено через MainServer.callLLM()
        };
    }

    /**
     * Выполнить полный RAG с генерацией ответа через LLM
     * 
     * @param {string} query - Вопрос пользователя
     * @param {Function} llmCallback - Функция для вызова LLM: (messages, tools) => Promise<response>
     * @param {number} topK - Количество релевантных документов
     * @returns {Promise<Object>} - Результат с сгенерированным ответом
     */
    async queryWithLLM(query, llmCallback, topK = 3) {
        // Получаем контекст
        const ragResult = await this.query(query, topK);
        
        // Формируем промпт для LLM
        const systemPrompt = this._createRAGSystemPrompt();
        const userMessage = this._createRAGUserMessage(query, ragResult.context);
        
        console.log('[RAGService] Вызов LLM для генерации ответа...');
        
        // Вызываем LLM через callback
        const llmResponse = await llmCallback(
            [systemPrompt, { role: 'user', text: userMessage }],
            [] // tools не нужны для RAG
        );
        
        ragResult.answer = llmResponse.text;
        
        console.log('[RAGService] ✓ Ответ сгенерирован');
        
        return ragResult;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================
    
    /**
     * Построить контекст из найденных документов
     */
    _buildContext(results) {
        let context = 'РЕЛЕВАНТНЫЕ УРОКИ ИЗ КУРСА:\n\n';
        
        results.forEach((result, i) => {
            context += `=== УРОК ${i + 1}: ${result.lesson_title} ===\n`;
            context += `Раздел: ${result.section}\n`;
            context += `Релевантность: ${(result.similarity * 100).toFixed(1)}%\n\n`;
            context += `${result.content}\n\n`;
            context += '─'.repeat(80) + '\n\n';
        });
        
        return context;
    }

    /**
     * Создать system prompt для RAG
     */
    _createRAGSystemPrompt() {
        return {
            role: 'system',
            text: `Ты - AI ассистент по курсу "Android Studio для непрограммистов".

Твоя задача - отвечать на вопросы пользователей, используя ТОЛЬКО информацию из предоставленных уроков курса.

ПРАВИЛА:
1. Используй только информацию из контекста (уроки курса)
2. Если информации нет в уроках - честно скажи об этом
3. Отвечай на русском языке
4. Давай конкретные и понятные ответы
5. Указывай из какого урока взята информация
6. Если релевантно - давай ссылки на уроки

ФОРМАТ ОТВЕТА:
- Краткий прямой ответ на вопрос
- Ссылка на урок/уроки откуда взята информация
- Дополнительные детали если нужно`
        };
    }

    /**
     * Создать user message для RAG
     */
    _createRAGUserMessage(query, context) {
        return `${context}

ВОПРОС ПОЛЬЗОВАТЕЛЯ: ${query}

Ответь на вопрос используя информацию из уроков выше.`;
    }

    // =========================================================================
    // STATS
    // =========================================================================
    
    /**
     * Получить статистику индекса
     */
    async getStats() {
        if (!this.isInitialized) {
            await this.initialize();
        }
        
        return this.indexManager.getStats();
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default RAGService;

