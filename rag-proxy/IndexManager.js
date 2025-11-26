// =============================================================================
// IndexManager - Управление векторным индексом
// =============================================================================
// Этот класс управляет векторным индексом:
// 1. Создание индекса из чанков документов
// 2. Сохранение/загрузка индекса из файла
// 3. Поиск по индексу (векторный поиск)
// =============================================================================

import fs from 'fs/promises';
import path from 'path';
import VectorizationClient from './VectorizationClient.js';

class IndexManager {
    /**
     * @param {string} indexPath - Путь к файлу индекса (vector_index.json)
     * @param {VectorizationClient} vectorClient - Клиент векторизации
     */
    constructor(indexPath, vectorClient = null) {
        this.indexPath = indexPath;
        this.vectorClient = vectorClient || new VectorizationClient();
        this.index = null; // { version, model, dimension, documents: [] }
        
        console.log(`[IndexManager] Инициализирован с индексом: ${indexPath}`);
    }

    // =========================================================================
    // INDEX BUILDING - Создание индекса из документов
    // =========================================================================
    
    /**
     * Создать индекс из массива документов (чанков)
     * 
     * @param {Array<Object>} chunks - Массив чанков с полями: id, content, metadata
     * @returns {Promise<Object>} - Созданный индекс
     */
    async buildIndex(chunks) {
        console.log(`[IndexManager] Создание индекса из ${chunks.length} чанков...`);
        
        // Проверяем доступность Ollama
        const isHealthy = await this.vectorClient.checkHealth();
        if (!isHealthy) {
            throw new Error('Ollama недоступна. Убедитесь что Ollama запущена (ollama serve)');
        }
        
        const documents = [];
        
        for (let i = 0; i < chunks.length; i++) {
            const chunk = chunks[i];
            console.log(`\n[IndexManager] === Обработка чанка ${i + 1}/${chunks.length} ===`);
            console.log(`[IndexManager] ID: ${chunk.id}`);
            console.log(`[IndexManager] Заголовок: ${chunk.metadata?.lesson_title || 'Без заголовка'}`);
            console.log(`[IndexManager] Длина контента: ${chunk.content.length} символов`);
            
            // Генерируем эмбеддинг для контента
            const vector = await this.vectorClient.embed(chunk.content);
            
            documents.push({
                id: chunk.id,
                parent_id: chunk.parent_id || chunk.id,
                content: chunk.content,
                vector: vector,
                metadata: chunk.metadata || {}
            });
            
            console.log(`[IndexManager] ✓ Чанк проиндексирован (размерность вектора: ${vector.length})`);
            
            // Задержка между запросами чтобы не перегружать Ollama
            if (i < chunks.length - 1) {
                console.log('[IndexManager] Пауза 2 секунды...');
                await new Promise(resolve => setTimeout(resolve, 2000));
            }
        }
        
        // Создаем структуру индекса
        this.index = {
            version: '1.0',
            model: this.vectorClient.model,
            dimension: documents[0]?.vector.length || 0,
            created_at: new Date().toISOString(),
            documents: documents
        };
        
        console.log(`\n[IndexManager] ✓ Индекс создан:`);
        console.log(`  - Документов: ${documents.length}`);
        console.log(`  - Размерность векторов: ${this.index.dimension}`);
        console.log(`  - Модель: ${this.index.model}`);
        
        return this.index;
    }

    // =========================================================================
    // INDEX PERSISTENCE - Сохранение/загрузка индекса
    // =========================================================================
    
    /**
     * Сохранить индекс в файл
     * 
     * @returns {Promise<void>}
     */
    async saveIndex() {
        if (!this.index) {
            throw new Error('Индекс не создан. Сначала вызовите buildIndex()');
        }
        
        console.log(`[IndexManager] Сохранение индекса в ${this.indexPath}...`);
        
        // Создаем директорию если её нет
        const dir = path.dirname(this.indexPath);
        await fs.mkdir(dir, { recursive: true });
        
        // Сохраняем индекс в JSON
        const json = JSON.stringify(this.index, null, 2);
        await fs.writeFile(this.indexPath, json, 'utf-8');
        
        const sizeKB = (json.length / 1024).toFixed(2);
        console.log(`[IndexManager] ✓ Индекс сохранен (${sizeKB} KB)`);
    }

    /**
     * Загрузить индекс из файла
     * 
     * @returns {Promise<Object>} - Загруженный индекс
     */
    async loadIndex() {
        console.log(`[IndexManager] Загрузка индекса из ${this.indexPath}...`);
        
        try {
            const json = await fs.readFile(this.indexPath, 'utf-8');
            this.index = JSON.parse(json);
            
            console.log(`[IndexManager] ✓ Индекс загружен:`);
            console.log(`  - Версия: ${this.index.version}`);
            console.log(`  - Модель: ${this.index.model}`);
            console.log(`  - Документов: ${this.index.documents.length}`);
            console.log(`  - Размерность: ${this.index.dimension}`);
            console.log(`  - Создан: ${this.index.created_at}`);
            
            return this.index;
            
        } catch (error) {
            if (error.code === 'ENOENT') {
                throw new Error(`Индекс не найден: ${this.indexPath}. Запустите build-index.js`);
            }
            throw error;
        }
    }

    /**
     * Проверить существует ли индекс
     * 
     * @returns {Promise<boolean>}
     */
    async indexExists() {
        try {
            await fs.access(this.indexPath);
            return true;
        } catch {
            return false;
        }
    }

    // =========================================================================
    // SEARCH - Векторный поиск по индексу
    // =========================================================================
    
    /**
     * Поиск по индексу
     * 
     * @param {string} query - Поисковый запрос
     * @param {number} topK - Количество результатов (по умолчанию 3)
     * @param {number} minSimilarity - Минимальный порог similarity (опционально, 0.0-1.0)
     * @returns {Promise<Array>} - Найденные документы с оценкой релевантности
     */
    async search(query, topK = 3, minSimilarity = null) {
        if (!this.index) {
            throw new Error('Индекс не загружен. Сначала вызовите loadIndex()');
        }
        
        console.log(`\n[IndexManager] Поиск по запросу: "${query}"`);
        console.log(`[IndexManager] Топ результатов: ${topK}`);
        
        // 1. Векторизуем запрос
        console.log('[IndexManager] Векторизация запроса...');
        const queryVector = await this.vectorClient.embed(query);
        console.log(`[IndexManager] ✓ Запрос векторизован (размерность: ${queryVector.length})`);
        
        // 2. Ищем наиболее похожие документы (берем больше чем topK для группировки)
        const vectorsWithMetadata = this.index.documents.map(doc => ({
            id: doc.id,
            parent_id: doc.parent_id || doc.id,
            vector: doc.vector,
            metadata: {
                ...doc.metadata,
                content: doc.content
            }
        }));
        
        // Ищем результаты - берем как минимум 20 для группировки, но можем взять и больше если запрошено
        const searchLimit = Math.max(20, topK * 3); // Минимум 20, или в 3 раза больше чем topK
        const allResults = this.vectorClient.findMostSimilar(queryVector, vectorsWithMetadata, Math.min(searchLimit, this.index.documents.length));
        
        // 3. Группируем по parent_id и берем лучший чанк из каждой статьи
        const groupedByParent = new Map();
        
        for (const result of allResults) {
            const parentId = result.parent_id || result.id;
            
            if (!groupedByParent.has(parentId)) {
                groupedByParent.set(parentId, {
                    parent_id: parentId,
                    best_chunk: result,
                    all_chunks: [result],
                    max_similarity: result.similarity
                });
            } else {
                const group = groupedByParent.get(parentId);
                group.all_chunks.push(result);
                if (result.similarity > group.max_similarity) {
                    group.best_chunk = result;
                    group.max_similarity = result.similarity;
                }
            }
        }
        
        // 4. Берем топ-K статей и объединяем контент всех чанков
        const topParents = Array.from(groupedByParent.values())
            .sort((a, b) => b.max_similarity - a.max_similarity)
            .slice(0, topK);
        
        // 5. Форматируем результаты
        const formattedResults = topParents.map(group => {
            // Объединяем контент всех чанков статьи
            const combinedContent = group.all_chunks
                .sort((a, b) => (a.metadata.chunk_index || 0) - (b.metadata.chunk_index || 0))
                .map(chunk => chunk.metadata.content)
                .join('\n\n');
            
            const bestChunk = group.best_chunk;
            
            // Для реранкинга используем ТОЛЬКО лучший чанк (не весь объединенный контент)
            const bestChunkContent = bestChunk.metadata.content;
            
            return {
                id: group.parent_id,
                similarity: group.max_similarity,
                lesson_title: bestChunk.metadata.lesson_title,
                section: bestChunk.metadata.section,
                url: bestChunk.metadata.url,
                content: combinedContent,  // Полный контент для генерации ответа
                best_chunk_content: bestChunkContent,  // Только лучший чанк для реранкинга
                chunks_count: group.all_chunks.length,
                metadata: bestChunk.metadata
            };
        });
        
        // Применяем пороговую фильтрацию если указан minSimilarity
        let finalResults = formattedResults;
        
        if (minSimilarity !== null) {
            console.log(`\n[IndexManager] Применение пороговой фильтрации (>= ${(minSimilarity * 100).toFixed(1)}%)...`);
            const beforeFilter = finalResults.length;
            finalResults = finalResults.filter(r => r.similarity >= minSimilarity);
            console.log(`[IndexManager] Результатов: ${beforeFilter} → ${finalResults.length}`);
            
            if (finalResults.length === 0) {
                console.log(`[IndexManager] ⚠️ Все результаты отфильтрованы (similarity ниже порога)`);
            }
        }
        
        console.log(`\n[IndexManager] ✓ Найдено ${finalResults.length} уникальных уроков:`);
        finalResults.forEach((result, i) => {
            const chunksInfo = result.chunks_count > 1 ? ` (${result.chunks_count} чанков)` : '';
            console.log(`\n  ${i + 1}. ${result.lesson_title}${chunksInfo}`);
            console.log(`     Релевантность: ${(result.similarity * 100).toFixed(2)}%`);
            console.log(`     Раздел: ${result.section}`);
            console.log(`     URL: ${result.url || 'N/A'}`);
        });
        
        return finalResults;
    }

    // =========================================================================
    // STATS - Статистика индекса
    // =========================================================================
    
    /**
     * Получить статистику индекса
     * 
     * @returns {Object} - Статистика
     */
    getStats() {
        if (!this.index) {
            return { error: 'Индекс не загружен' };
        }
        
        const totalContentLength = this.index.documents.reduce(
            (sum, doc) => sum + doc.content.length, 
            0
        );
        
        return {
            version: this.index.version,
            model: this.index.model,
            dimension: this.index.dimension,
            created_at: this.index.created_at,
            documents_count: this.index.documents.length,
            total_content_length: totalContentLength,
            avg_content_length: Math.round(totalContentLength / this.index.documents.length),
            sections: [...new Set(this.index.documents.map(d => d.metadata.section))].length
        };
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default IndexManager;

