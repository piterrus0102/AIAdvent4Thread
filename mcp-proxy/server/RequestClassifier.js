// =============================================================================
// RequestClassifier - Классификатор запросов через LLM
// =============================================================================
// Определяет тип запроса пользователя:
// - Обычный чат
// - Создание напоминания (periodic reminder)
// =============================================================================

import dotenv from 'dotenv';
import HuggingFaceClient from './HuggingFaceClient.js';
dotenv.config();

class RequestClassifier {
    constructor() {
        this.huggingFaceClient = new HuggingFaceClient();
    }
    /**
     * Классифицировать запрос пользователя
     * 
     * @param {string} userMessage - Сообщение пользователя
     * @returns {Promise<{type: 'chat'|'reminder', reminder?: {scheduleTime: number, scheduleUnit: string, queryString: string}}>}
     */
    async classify(userMessage) {
        console.log('[Classifier] 🔍 Классификация запроса...');
        console.log('[Classifier] Запрос:', userMessage);
        
        const systemPrompt = `Ты - классификатор запросов. Определи тип запроса пользователя.

ТИПЫ ЗАПРОСОВ:

1. **CHAT** - обычный вопрос/команда (разовое действие)
   Примеры:
   - "Сколько у меня репозиториев?"
   - "Покажи мои issues"
   - "Найди PR #17"

2. **REMINDER** - запрос на периодическое выполнение действия
   Признаки:
   - Упоминание периодичности (каждые, раз в, периодически)
   - Просьба о регулярных оповещениях/проверках
   Примеры:
   - "Оповещай меня каждые 10 секунд об issues"
   - "Напоминай раз в минуту о PR"
   - "Проверяй каждый час статус репозитория"

ФОРМАТ ОТВЕТА:

Для CHAT:
{"type": "chat"}

Для REMINDER:
{
  "type": "reminder",
  "scheduleTime": <число>,
  "scheduleUnit": "seconds"|"minutes"|"hours",
  "queryString": "<что именно проверять>"
}

ВАЖНО:
- Возвращай ТОЛЬКО JSON, без комментариев
- scheduleTime - числовое значение (например: 10, 30, 1)
- scheduleUnit - единица измерения (seconds/minutes/hours)
- queryString - ЧТО нужно проверять (issues, PR, релизы и т.д.)

ПРИМЕРЫ:

Запрос: "Сколько у меня репозиториев?"
Ответ: {"type": "chat"}

Запрос: "отправляй каждые 10 секунд количество issues к репозиторию AIAdvent4Thread"
Ответ: {"type": "reminder", "scheduleTime": 10, "scheduleUnit": "seconds", "queryString": "количество issues к репозиторию AIAdvent4Thread"}

Запрос: "напоминай раз в 2 минуты о pull requests"
Ответ: {"type": "reminder", "scheduleTime": 2, "scheduleUnit": "minutes", "queryString": "pull requests"}

Запрос: "проверяй каждый час релизы в моих репозиториях"
Ответ: {"type": "reminder", "scheduleTime": 1, "scheduleUnit": "hours", "queryString": "релизы в моих репозиториях"}`;

        try {
            const llmResponse = await this.huggingFaceClient.callWithPrompt(
                systemPrompt, 
                userMessage, 
                0.2,  // Низкая температура для точной классификации
                300
            );
            
            // console.log('[Classifier] Ответ LLM:', llmResponse);
            
            // Парсим JSON из ответа
            const jsonMatch = llmResponse.match(/\{[\s\S]*\}/);
            if (!jsonMatch) {
                console.log('[Classifier] ⚠️ JSON не найден, считаем как chat');
                return { type: 'chat' };
            }
            
            const classification = JSON.parse(jsonMatch[0]);
            
            // Валидация
            if (classification.type === 'reminder') {
                if (!classification.scheduleTime || !classification.scheduleUnit || !classification.queryString) {
                    console.log('[Classifier] ⚠️ Некорректные данные для reminder, считаем как chat');
                    return { type: 'chat' };
                }
                
                console.log('[Classifier] ✅ Тип: REMINDER');
                console.log('[Classifier]   Интервал:', classification.scheduleTime, classification.scheduleUnit);
                console.log('[Classifier]   Запрос:', classification.queryString);
            } else {
                console.log('[Classifier] ✅ Тип: CHAT');
            }
            
            return classification;
            
        } catch (error) {
            console.error('[Classifier] ❌ Ошибка классификации:', error);
            // В случае ошибки считаем как обычный чат
            return { type: 'chat' };
        }
    }
}

export default RequestClassifier;

