// =============================================================================
// VectorizationClient - Клиент для работы с Ollama (векторизация текста)
// =============================================================================
// Этот клиент общается с Ollama API для:
// 1. Генерации эмбеддингов (векторов) из текста
// 2. Использования embedding моделей (nomic-embed-text, mxbai-embed-large)
// =============================================================================

import axios from 'axios';

class VectorizationClient {
    /**
     * @param {string} baseURL - URL Ollama API (по умолчанию http://localhost:11434)
     * @param {string} model - Модель эмбеддингов (по умолчанию nomic-embed-text)
     */
    constructor(baseURL = 'http://localhost:11434', model = 'mxbai-embed-large') {
        this.baseURL = baseURL;
        this.model = model;
        this.apiClient = axios.create({
            baseURL: this.baseURL,
            timeout: 180000, // 180 секунд (3 минуты)
            headers: {
                'Content-Type': 'application/json'
            }
        });
        
        console.log(`[VectorizationClient] Инициализирован с моделью: ${this.model}`);
    }

    // =========================================================================
    // EMBEDDINGS - Генерация векторов из текста
    // =========================================================================
    
    /**
     * Получить эмбеддинг (вектор) для текста
     * 
     * @param {string} text - Текст для векторизации
     * @returns {Promise<Array<number>>} - Вектор (массив чисел)
     */
    async embed(text) {
        try {
            console.log(`[VectorizationClient] Генерация эмбеддинга для текста (${text.length} символов)...`);
            
            const response = await this.apiClient.post('/api/embeddings', {
                model: this.model,
                prompt: text
            });
            
            const embedding = response.data.embedding;
            console.log(`[VectorizationClient] ✓ Эмбеддинг сгенерирован (размерность: ${embedding.length})`);
            
            return embedding;
            
        } catch (error) {
            console.error('[VectorizationClient] ❌ Ошибка при генерации эмбеддинга:', error.message);
            if (error.response) {
                console.error('[VectorizationClient] Response status:', error.response.status);
                console.error('[VectorizationClient] Response data:', error.response.data);
            }
            throw new Error(`Failed to generate embedding: ${error.message}`);
        }
    }

    /**
     * Получить эмбеддинги для массива текстов (batch processing)
     * 
     * @param {Array<string>} texts - Массив текстов
     * @returns {Promise<Array<Array<number>>>} - Массив векторов
     */
    async embedBatch(texts) {
        console.log(`[VectorizationClient] Генерация эмбеддингов для ${texts.length} текстов...`);
        
        const embeddings = [];
        
        for (let i = 0; i < texts.length; i++) {
            const text = texts[i];
            console.log(`[VectorizationClient] Обработка ${i + 1}/${texts.length}...`);
            
            const embedding = await this.embed(text);
            embeddings.push(embedding);
            
            // Небольшая задержка чтобы не перегружать Ollama
            if (i < texts.length - 1) {
                await new Promise(resolve => setTimeout(resolve, 100));
            }
        }
        
        console.log(`[VectorizationClient] ✓ Все эмбеддинги сгенерированы`);
        return embeddings;
    }

    // =========================================================================
    // SIMILARITY - Вычисление сходства между векторами
    // =========================================================================
    
    /**
     * Вычислить косинусное сходство между двумя векторами
     * 
     * @param {Array<number>} vec1 - Первый вектор
     * @param {Array<number>} vec2 - Второй вектор
     * @returns {number} - Сходство от -1 до 1 (чем ближе к 1, тем более похожи)
     */
    cosineSimilarity(vec1, vec2) {
        if (vec1.length !== vec2.length) {
            throw new Error('Векторы должны иметь одинаковую размерность');
        }
        
        let dotProduct = 0;
        let norm1 = 0;
        let norm2 = 0;
        
        for (let i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Найти наиболее похожие векторы из списка
     * 
     * @param {Array<number>} queryVector - Вектор запроса
     * @param {Array<{id, vector, metadata}>} vectors - Массив векторов с метаданными
     * @param {number} topK - Количество результатов (по умолчанию 3)
     * @returns {Array<{id, similarity, metadata}>} - Топ-K похожих результатов
     */
    findMostSimilar(queryVector, vectors, topK = 3) {
        console.log(`[VectorizationClient] Поиск топ-${topK} похожих векторов из ${vectors.length}...`);
        
        const similarities = vectors.map(item => ({
            id: item.id,
            parent_id: item.parent_id,
            similarity: this.cosineSimilarity(queryVector, item.vector),
            metadata: item.metadata
        }));
        
        // Сортируем по убыванию сходства
        similarities.sort((a, b) => b.similarity - a.similarity);
        
        const results = similarities.slice(0, topK);
        
        console.log(`[VectorizationClient] ✓ Найдено ${results.length} результатов:`);
        results.forEach((result, i) => {
            console.log(`  ${i + 1}. [${result.id}] Сходство: ${(result.similarity * 100).toFixed(2)}%`);
        });
        
        return results;
    }

    // =========================================================================
    // HEALTH CHECK - Проверка доступности Ollama
    // =========================================================================
    
    /**
     * Проверить доступность Ollama API
     * 
     * @returns {Promise<boolean>} - true если Ollama доступна
     */
    async checkHealth() {
        try {
            console.log('[VectorizationClient] Проверка доступности Ollama...');
            await this.apiClient.get('/');
            console.log('[VectorizationClient] ✓ Ollama доступна');
            return true;
        } catch (error) {
            console.error('[VectorizationClient] ❌ Ollama недоступна:', error.message);
            return false;
        }
    }

    /**
     * Получить список доступных моделей
     * 
     * @returns {Promise<Array<string>>} - Список имен моделей
     */
    async listModels() {
        try {
            console.log('[VectorizationClient] Получение списка моделей...');
            const response = await this.apiClient.get('/api/tags');
            const models = response.data.models.map(m => m.name);
            console.log(`[VectorizationClient] ✓ Найдено моделей: ${models.length}`);
            return models;
        } catch (error) {
            console.error('[VectorizationClient] ❌ Ошибка получения моделей:', error.message);
            throw error;
        }
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default VectorizationClient;

