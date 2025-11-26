// =============================================================================
// Reranker - Реранкинг результатов поиска для улучшения релевантности
// =============================================================================
// Реализует два подхода:
// 1. LLM-based reranking - использует LLM для оценки релевантности
// 2. Threshold-based filtering - фильтрация по порогу similarity
// =============================================================================

class Reranker {
    /**
     * @param {HuggingFaceClient} llmClient - Клиент для LLM (опционально)
     */
    constructor(llmClient = null) {
        this.llmClient = llmClient;
        console.log('[Reranker] Инициализирован');
    }

    // =========================================================================
    // THRESHOLD-BASED FILTERING
    // =========================================================================
    
    /**
     * Фильтрация результатов по порогу similarity
     * 
     * @param {Array} results - Результаты поиска с полем similarity
     * @param {number} minSimilarity - Минимальный порог (0.0 - 1.0)
     * @returns {Array} - Отфильтрованные результаты
     */
    filterByThreshold(results, minSimilarity = 0.4) {
        console.log(`\n[Reranker] === Пороговая фильтрация ===`);
        console.log(`[Reranker] Порог: ${(minSimilarity * 100).toFixed(1)}%`);
        console.log(`[Reranker] Результатов до фильтрации: ${results.length}`);
        
        const filtered = results.filter(r => r.similarity >= minSimilarity);
        
        console.log(`[Reranker] Результатов после фильтрации: ${filtered.length}`);
        
        if (filtered.length === 0) {
            console.log(`[Reranker] ⚠️ Все результаты отфильтрованы (низкая релевантность)`);
        } else {
            console.log(`[Reranker] ✓ Диапазон similarity: ${(filtered[filtered.length-1].similarity * 100).toFixed(1)}% - ${(filtered[0].similarity * 100).toFixed(1)}%`);
        }
        
        return filtered;
    }

    // =========================================================================
    // LLM-BASED RERANKING
    // =========================================================================
    
    /**
     * Реранкинг через LLM - модель оценивает релевантность каждого документа
     * 
     * @param {string} query - Вопрос пользователя
     * @param {Array} results - Результаты поиска
     * @param {Function} llmCallback - Функция для вызова LLM: (messages, tools) => Promise<response>
     * @returns {Promise<Array>} - Переранжированные результаты (отсортированные по оценке)
     */
    async rerankWithLLM(query, results, llmCallback) {
        if (!llmCallback) {
            console.log('[Reranker] ⚠️ LLM callback не предоставлен, пропускаем LLM-реранкинг');
            return results;
        }
        
        console.log(`\n[Reranker] === LLM-based реранкинг ===`);
        console.log(`[Reranker] Оценка ${results.length} документов...`);
        
        const scoredResults = [];
        
        for (let i = 0; i < results.length; i++) {
            const result = results[i];
            
            console.log(`\n[Reranker] [${i + 1}/${results.length}] Оценка: ${result.lesson_title}`);
            
            // Формируем промпт для LLM
            const systemMessage = {
                role: 'system',
                text: `Ты - эксперт по оценке релевантности документов для курса Android Studio.

Твоя задача: оценить насколько документ может помочь ответить на вопрос пользователя.

ШКАЛА ОЦЕНКИ (0-10):
- 9-10: ИДЕАЛЬНО - документ полностью отвечает на вопрос, содержит именно то что нужно
- 7-8: ХОРОШО - документ содержит релевантную информацию, может помочь ответить
- 5-6: ПОДХОДИТ - документ частично релевантен, есть связанная информация
- 3-4: СЛАБО - документ касается темы, но косвенно
- 0-2: НЕ РЕЛЕВАНТЕН - документ не связан с вопросом

ВАЖНО:
- Оценивай ЛИБЕРАЛЬНО - если документ хоть как-то связан с темой вопроса, ставь >= 5
- Учитывай что это курс для новичков - даже общая информация может быть полезна
- Если в документе есть ключевые слова из вопроса - это уже >= 5 баллов
- Ставь низкие оценки (< 5) ТОЛЬКО если документ вообще о другом

ФОРМАТ ОТВЕТА:
Верни ТОЛЬКО ОДНО ЧИСЛО от 0 до 10 (без точки, без текста)`
            };
            
            // Используем best_chunk_content если есть (только релевантный чанк), иначе весь content
            const contentForEvaluation = result.best_chunk_content || result.content;
            
            const userMessage = {
                role: 'user',
                text: `ВОПРОС: ${query}

ДОКУМЕНТ:
Заголовок: ${result.lesson_title}
Раздел: ${result.section}

Контент:
${contentForEvaluation.substring(0, 1000)}${contentForEvaluation.length > 1000 ? '...' : ''}

Оцени релевантность документа вопросу (0-10):`
            };
            
            try {
                // Вызываем LLM для оценки (без инструментов)
                const response = await llmCallback([systemMessage, userMessage], []);
                
                // Парсим оценку (ожидаем число)
                // response может быть строкой или объектом с полем text
                const scoreText = (typeof response === 'string' ? response : response.text || response).trim();
                const score = parseFloat(scoreText);
                
                if (isNaN(score) || score < 0 || score > 10) {
                    scoredResults.push({ ...result, llm_score: 0, llm_raw: scoreText });
                } else {
                    scoredResults.push({ ...result, llm_score: score, llm_raw: scoreText });
                }
                
                // Небольшая задержка чтобы не перегружать LLM
                if (i < results.length - 1) {
                    await new Promise(resolve => setTimeout(resolve, 500));
                }
                
            } catch (error) {
                console.error(`[Reranker] ❌ Ошибка оценки через LLM:`, error.message);
                scoredResults.push({ ...result, llm_score: 0, llm_error: error.message });
            }
        }
        
        // Сортируем ВСЕ результаты по оценке LLM (descending)
        scoredResults.sort((a, b) => b.llm_score - a.llm_score);
        
        // ВАЖНО: Отдаем ВСЕ отсортированные результаты (не фильтруем!)
        // Реранкинг = сортировка, не фильтрация
        return scoredResults;
    }

    // =========================================================================
    // HYBRID RERANKING
    // =========================================================================
    
    /**
     * Гибридный реранкинг: пороговая фильтрация + LLM-реранкинг
     * 
     * @param {string} query - Вопрос пользователя
     * @param {Array} results - Результаты поиска
     * @param {Function} llmCallback - Функция для вызова LLM
     * @param {Object} options - Опции: { minSimilarity, topK }
     * @returns {Promise<Array>} - Финальные результаты
     */
    async hybridRerank(query, results, llmCallback, options = {}) {
        const {
            minSimilarity = 0.25,  // Порог для первичной фильтрации
            topK = 3               // Количество финальных результатов
        } = options;
        
        console.log(`\n[Reranker] ====================================`);
        console.log(`[Reranker] 🔄 ГИБРИДНЫЙ РЕРАНКИНГ`);
        console.log(`[Reranker] ====================================`);
        console.log(`[Reranker] Входных результатов: ${results.length}`);
        console.log(`[Reranker] Порог similarity: ${(minSimilarity * 100).toFixed(1)}%`);
        console.log(`[Reranker] Целевое количество: топ-${topK}`);
        
        // ШАГ 1: Первичная фильтрация по similarity
        console.log(`\n[Reranker] === ШАГ 1/3: Пороговая фильтрация ===`);
        const afterThreshold = this.filterByThreshold(results, minSimilarity);
        
        if (afterThreshold.length === 0) {
            console.log(`[Reranker] ❌ Нет результатов после пороговой фильтрации`);
            return {
                results: [],
                reason: 'no_results_after_threshold',
                message: 'Не найдено релевантных результатов (низкий similarity score)'
            };
        }
        
        // ШАГ 2: LLM-реранкинг (сортировка, не фильтрация!)
        console.log(`\n[Reranker] === ШАГ 2/3: LLM-реранкинг ===`);
        const afterLLM = await this.rerankWithLLM(query, afterThreshold, llmCallback);
        
        // rerankWithLLM всегда возвращает результаты (просто отсортированные по LLM оценке)
        console.log(`[Reranker] ✓ Получено ${afterLLM.length} отсортированных результатов`);
        
        // ШАГ 3: Берем топ-K лучших (даже если они с низкими оценками)
        console.log(`\n[Reranker] === ШАГ 3/3: Финальный отбор (топ-${topK}) ===`);
        const finalResults = afterLLM.slice(0, topK);
        
        console.log(`[Reranker] ✅ Финальных результатов: ${finalResults.length}`);
        finalResults.forEach((r, i) => {
            const llmBadge = r.llm_score > 0 ? '🟢' : '🔴';
            const quality = r.llm_score > 0 ? 'РЕЛЕВАНТЕН' : 'СЛАБАЯ РЕЛЕВАНТНОСТЬ';
            console.log(`  ${i + 1}. ${llmBadge} [LLM: ${r.llm_score}/10, Similarity: ${(r.similarity * 100).toFixed(1)}%] ${r.lesson_title} (${quality})`);
        });
        
        console.log(`\n[Reranker] ====================================`);
        console.log(`[Reranker] ✅ РЕРАНКИНГ ЗАВЕРШЕН`);
        console.log(`[Reranker] ====================================`);
        
        return {
            results: finalResults,
            reason: 'success',
            stats: {
                initial: results.length,
                after_threshold: afterThreshold.length,
                after_llm: afterLLM.length,
                final: finalResults.length
            }
        };
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default Reranker;

