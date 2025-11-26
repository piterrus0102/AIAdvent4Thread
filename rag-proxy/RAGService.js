// =============================================================================
// RAGService - Сервис для RAG (Retrieval-Augmented Generation)
// =============================================================================
// Этот сервис интегрирует векторный поиск с LLM для ответов на вопросы
// по курсу Android Studio
// =============================================================================

import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import VectorizationClient from './VectorizationClient.js';
import IndexManager from './IndexManager.js';
import Reranker from './Reranker.js';
import { createRAGSystemMessage, createRAGUserMessage } from './RAGPrompts.js';

// Загружаем .env из папки rag-proxy
const __filename_rag = fileURLToPath(import.meta.url);
const __dirname_rag = path.dirname(__filename_rag);
dotenv.config({ path: path.join(__dirname_rag, '.env') });

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const INDEX_FILE = path.join(__dirname, 'data/vector_index.json');

class RAGService {
    constructor() {
        this.vectorClient = new VectorizationClient();
        this.indexManager = new IndexManager(INDEX_FILE, this.vectorClient);
        this.reranker = new Reranker();
        this.isInitialized = false;
        
        // Настройки реранкинга (из .env или по умолчанию)
        this.rerankingEnabled = false; // ПО УМОЛЧАНИЮ ВЫКЛЮЧЕН! Включается только при INCORRECT_RAG_ANSWER
        this.rerankingOptions = {
            minSimilarity: parseFloat(process.env.RAG_MIN_SIMILARITY) || 0.25,  // Порог similarity для первичной фильтрации
            topK: parseInt(process.env.RAG_TOP_K) || 3
        };
        
        // ДЕМО-РЕЖИМ: Для наглядности первый ответ будет субоптимальным
        // Управляется через rag-proxy/.env: RAG_DEMO_MODE=true/false
        this.demoMode = process.env.RAG_DEMO_MODE !== 'false'; // По умолчанию включен
        
        console.log('[RAGService] Инициализирован');
        console.log(`[RAGService] Реранкинг: ${this.rerankingEnabled ? 'ВКЛЮЧЕН' : 'ВЫКЛЮЧЕН (включается при INCORRECT_RAG_ANSWER)'}`);
        console.log(`[RAGService] Демо-режим: ${this.demoMode ? '🎭 ВКЛЮЧЕН (первый ответ будет субоптимальным)' : 'ВЫКЛЮЧЕН'}`);
        if (this.rerankingEnabled) {
            console.log(`[RAGService] Настройки реранкинга:`, this.rerankingOptions);
        }
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
        console.log(`\n[RAGService] ======================================`);
        console.log(`[RAGService] RAG Pipeline с${this.rerankingEnabled ? ' реранкингом' : 'out реранкинга'}`);
        console.log(`[RAGService] ======================================`);
        
        let ragResult;
        let rerankStats = null;
        
        if (this.rerankingEnabled) {
            // РЕЖИМ С РЕРАНКИНГОМ
            console.log(`[RAGService] 🔄 Режим: ГИБРИДНЫЙ РЕРАНКИНГ`);
            
            // Этап 1: Получаем больше кандидатов для реранкинга (топ-10-15)
            const candidateCount = Math.max(topK * 3, 10);
            console.log(`[RAGService] Этап 1: Векторный поиск (топ-${candidateCount} кандидатов)...`);
            
            const searchResults = await this.indexManager.search(query, candidateCount);
            
            if (searchResults.length === 0) {
                console.log(`[RAGService] ❌ Векторный поиск не дал результатов`);
                return {
                    query: query,
                    lessons: [],
                    context: '',
                    answer: 'К сожалению, не удалось найти информацию по вашему вопросу в курсе.',
                    incorrectRAG: false,
                    reranking_used: true,
                    reranking_stats: { initial: 0, final: 0 }
                };
            }
            
            console.log(`[RAGService] ✓ Найдено кандидатов: ${searchResults.length}`);
            
            // Этап 2: Реранкинг
            console.log(`[RAGService] Этап 2: Гибридный реранкинг...`);
            const rerankResult = await this.reranker.hybridRerank(
                query,
                searchResults,
                llmCallback,
                this.rerankingOptions
            );
            
            if (rerankResult.reason !== 'success' || rerankResult.results.length === 0) {
                console.log(`[RAGService] ❌ Реранкинг не дал релевантных результатов`);
                console.log(`[RAGService] Причина: ${rerankResult.reason}`);
                
                return {
                    query: query,
                    lessons: [],
                    context: '',
                    answer: rerankResult.message || 'К сожалению, не удалось найти релевантную информацию по вашему вопросу.',
                    incorrectRAG: false,
                    reranking_used: true,
                    reranking_stats: rerankResult.stats
                };
            }
            
            console.log(`[RAGService] ✓ Реранкинг завершен: ${rerankResult.results.length} результатов`);
            rerankStats = rerankResult.stats;
            
            // Формируем результат из переранжированных документов
            const context = this._buildContext(rerankResult.results);
            const lessons = rerankResult.results.map(r => ({
                id: r.id,
                title: r.lesson_title,
                section: r.section,
                url: r.url,
                relevance: (r.similarity * 100).toFixed(1) + '%',
                llm_score: r.llm_score ? `${r.llm_score}/10` : 'N/A'
            }));
            
            ragResult = {
                query: query,
                lessons: lessons,
                context: context
            };
            
        } else {
            // РЕЖИМ БЕЗ РЕРАНКИНГА (оригинальный)
            console.log(`[RAGService] 📊 Режим: БЕЗ РЕРАНКИНГА (baseline)`);
            
            // В ДЕМО-РЕЖИМЕ берем субоптимальные результаты для контраста
            if (this.demoMode) {
                console.log(`[RAGService] 🎭 ДЕМО-РЕЖИМ: Намеренно берем СУБОПТИМАЛЬНЫЕ результаты для демонстрации`);
                console.log(`[RAGService] (это покажет разницу с реранкингом после жалобы)`);
                ragResult = await this._queryWithWorseResults(query, topK);
            } else {
                ragResult = await this.query(query, topK);
            }
        }
        
        // Этап 3: Генерация ответа через LLM
        console.log(`[RAGService] Этап 3: Генерация финального ответа...`);
        
        // ВАЖНО: Детекция INCORRECT_RAG_ANSWER ВСЕГДА ВКЛЮЧЕНА!
        // LLM НЕ ДОЛЖНА САМА решать релевантен ли ответ - это решает ПОЛЬЗОВАТЕЛЬ!
        // LLM возвращает INCORRECT_RAG_ANSWER ТОЛЬКО при ЯВНОЙ ЖАЛОБЕ пользователя
        console.log(`[RAGService] 🔍 Детекция INCORRECT_RAG_ANSWER: ВСЕГДА ВКЛЮЧЕНА`);
        if (this.demoMode && !this.rerankingEnabled) {
            console.log(`[RAGService] 🎭 Демо-режим: ответ на основе субоптимальных результатов`);
            console.log(`[RAGService] 📋 Пользователь может пожаловаться → INCORRECT_RAG_ANSWER → реранкинг`);
        } else if (!this.rerankingEnabled) {
            console.log(`[RAGService] 📋 Если пользователь пожалуется → INCORRECT_RAG_ANSWER → реранкинг`);
        }
        
        // Детекция ВСЕГДА включена!
        const systemPrompt = createRAGSystemMessage();
        const userMessageText = createRAGUserMessage(query, ragResult.context);
        
        console.log('[RAGService] Вызов LLM для генерации ответа...');
        
        // Вызываем LLM через callback
        const llmResponse = await llmCallback(
            [systemPrompt, { role: 'user', text: userMessageText }],
            [] // tools не нужны для RAG
        );
        
        ragResult.answer = llmResponse.text;
        ragResult.incorrectRAG = llmResponse.incorrectRAG || false;
        ragResult.reranking_used = this.rerankingEnabled;
        
        if (rerankStats) {
            ragResult.reranking_stats = rerankStats;
        }
        
        console.log('[RAGService] ✓ Ответ сгенерирован');
        console.log(`[RAGService] ======================================\n`);
        
        return ragResult;
    }
    
    /**
     * Включить/выключить реранкинг
     * 
     * @param {boolean} enabled - Включить реранкинг?
     * @param {Object} options - Опции реранкинга (опционально)
     */
    setReranking(enabled, options = null) {
        this.rerankingEnabled = enabled;
        
        if (options) {
            this.rerankingOptions = { ...this.rerankingOptions, ...options };
        }
        
        console.log(`\n[RAGService] ====================================`);
        console.log(`[RAGService] Реранкинг: ${enabled ? 'ВКЛЮЧЕН ✅' : 'ВЫКЛЮЧЕН ❌'}`);
        if (enabled && options) {
            console.log(`[RAGService] Обновленные настройки:`, this.rerankingOptions);
        }
        console.log(`[RAGService] ====================================\n`);
    }
    
    /**
     * Включить/выключить демо-режим
     * 
     * @param {boolean} enabled - Включить демо-режим?
     */
    setDemoMode(enabled) {
        this.demoMode = enabled;
        console.log(`\n[RAGService] ====================================`);
        console.log(`[RAGService] Демо-режим: ${enabled ? '🎭 ВКЛЮЧЕН' : 'ВЫКЛЮЧЕН'}`);
        if (enabled) {
            console.log(`[RAGService] (первый ответ будет субоптимальным)`);
            console.log(`[RAGService] Детекция INCORRECT_RAG_ANSWER всегда включена!`);
        }
        console.log(`[RAGService] ====================================\n`);
    }
    
    /**
     * ДЕМО-РЕЖИМ: Получить субоптимальные результаты (для контраста)
     * Берет результаты с НИЗКИМ similarity вместо высокого
     * 
     * @param {string} query - Вопрос пользователя
     * @param {number} topK - Количество результатов
     * @returns {Promise<Object>} - Субоптимальные результаты
     */
    async _queryWithWorseResults(query, topK) {
        // Проверяем инициализацию
        if (!this.isInitialized) {
            await this.initialize();
        }
        
        console.log(`\n[RAGService] === RAG Pipeline (ДЕМО: худшие результаты) ===`);
        
        // Получаем ВСЕ результаты (без ограничений)
        console.log(`[RAGService] Поиск ВСЕХ доступных чанков...`);
        
        // Узнаем сколько всего документов в индексе
        const stats = this.indexManager.getStats();
        const totalDocs = stats.documents_count || 100; // Берем все документы
        
        // ЭТАП 1: Векторный поиск по индексу (берем ВСЕ)
        console.log(`[RAGService] [1/2] Векторный поиск (топ-${totalDocs})...`);
        const allResults = await this.indexManager.search(query, totalDocs);
        
        if (allResults.length === 0) {
            console.log('[RAGService] ⚠️ Ничего не найдено');
            return {
                query: query,
                lessons: [],
                context: '',
                answer: null
            };
        }
        
        console.log(`[RAGService] ✓ Найдено ${allResults.length} результатов`);
        console.log(`[RAGService] Similarity диапазон: ${(allResults[allResults.length-1].similarity * 100).toFixed(1)}% - ${(allResults[0].similarity * 100).toFixed(1)}%`);
        
        // Берем самые ХУДШИЕ результаты (с конца списка)
        // Берем topK худших, но не совсем последние (пропускаем 1-2 самых худших)
        const skipWorst = Math.min(1, Math.floor(allResults.length / 10)); // Пропускаем 1 самый худший
        const startIdx = Math.max(0, allResults.length - topK - skipWorst);
        const endIdx = allResults.length - skipWorst;
        const worseResults = allResults.slice(startIdx, endIdx);
        
        console.log(`[RAGService] 🎭 Взяты ХУДШИЕ результаты с позиций ${startIdx+1}-${endIdx}:`);
        worseResults.forEach((r, i) => {
            const pos = startIdx + i + 1;
            console.log(`  ${i+1}. [${pos}/${allResults.length}] ${r.lesson_title} (${(r.similarity * 100).toFixed(1)}%)`);
        });
        
        // Формируем контекст из худших результатов
        const context = this._buildContext(worseResults);
        const lessons = worseResults.map(r => ({
            id: r.id,
            title: r.lesson_title,
            section: r.section,
            url: r.url,
            relevance: (r.similarity * 100).toFixed(1) + '%'
        }));
        
        return {
            query: query,
            lessons: lessons,
            context: context,
            answer: null
        };
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

    // Промпты для RAG теперь в ServerPrompts.js
    // См. createRAGSystemMessage() и createRAGUserMessage()

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

