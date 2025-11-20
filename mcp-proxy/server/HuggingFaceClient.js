// =============================================================================
// HuggingFaceClient - Клиент для работы с HuggingFace API
// =============================================================================
// Использует Qwen2.5-7B-Instruct (третья модель)
// =============================================================================

import dotenv from 'dotenv';
dotenv.config();

const HUGGINGFACE_API_KEY = process.env.HUGGINGFACE_API_KEY;

if (!HUGGINGFACE_API_KEY) {
    console.error('❌ Не задан HUGGINGFACE_API_KEY');
    console.error('Добавьте HUGGINGFACE_API_KEY в файл .env в папке mcp-proxy');
    process.exit(1);
}

class HuggingFaceClient {
    constructor() {
        this.modelId = 'Qwen/Qwen2.5-7B-Instruct';
        this.apiUrl = 'https://router.huggingface.co/v1/chat/completions';
    }

    /**
     * Вызвать модель Qwen2.5-7B-Instruct
     * 
     * @param {Array} messages - Массив сообщений в формате {role: 'system'|'user'|'assistant', text: string}
     * @param {number} temperature - Температура генерации (по умолчанию 0.7)
     * @param {number} maxTokens - Максимальное количество токенов (по умолчанию 2000)
     * @returns {Promise<string>} - Ответ модели
     */
    async callModel(messages, temperature = 0.7, maxTokens = 2000) {
        console.log('[HuggingFace] Вызов Qwen2.5-7B-Instruct');
        console.log(`[HuggingFace] Сообщений: ${messages.length}`);
        console.log(`[HuggingFace] Temperature: ${temperature}`);
        
        // Преобразуем формат сообщений из {role, text} в {role, content}
        const formattedMessages = messages.map(msg => ({
            role: msg.role,
            content: msg.text || msg.content
        }));
        
        const requestBody = {
            model: this.modelId,
            messages: formattedMessages,
            stream: false,
            max_tokens: maxTokens,
            temperature: temperature,
            top_p: 0.9
        };

        try {
            const response = await fetch(this.apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${HUGGINGFACE_API_KEY}`
                },
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                const errorText = await response.text();
                console.error('[HuggingFace] Ошибка API:', response.status, errorText);
                
                if (response.status === 503 && (errorText.includes('loading') || errorText.includes('Loading'))) {
                    throw new Error('⏳ Модель Qwen2.5-7B загружается. Попробуйте через 20-30 секунд.');
                }
                
                throw new Error(`HuggingFace API error: ${response.status} - ${errorText}`);
            }

            const data = await response.json();
            const responseText = data.choices[0].message.content;
            
            console.log('[HuggingFace] ✅ Ответ получен');
            console.log('[HuggingFace]', responseText.substring(0, 150) + '...');
            
            return responseText;
            
        } catch (error) {
            console.error('[HuggingFace] ❌ Ошибка:', error.message);
            throw error;
        }
    }

    /**
     * Вызвать модель с системным промптом и одним пользовательским сообщением
     * (удобный метод для простых запросов)
     * 
     * @param {string} systemPrompt - Системный промпт
     * @param {string} userMessage - Сообщение пользователя
     * @param {number} temperature - Температура генерации
     * @param {number} maxTokens - Максимальное количество токенов
     * @returns {Promise<string>} - Ответ модели
     */
    async callWithPrompt(systemPrompt, userMessage, temperature = 0.7, maxTokens = 2000) {
        const messages = [
            { role: 'system', text: systemPrompt },
            { role: 'user', text: userMessage }
        ];
        
        return await this.callModel(messages, temperature, maxTokens);
    }
}

export default HuggingFaceClient;

