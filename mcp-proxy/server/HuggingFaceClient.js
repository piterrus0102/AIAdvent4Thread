// =============================================================================
// HuggingFaceClient - Клиент для работы с HuggingFace API
// =============================================================================
// Использует Qwen/Qwen2.5-7B-Instruct
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
     * Вызвать модель L3-8B-Stheno
     * 
     * @param {Array} messages - Массив сообщений в формате {role: 'system'|'user'|'assistant', text: string}
     * @param {number} temperature - Температура генерации (по умолчанию 0.7)
     * @param {number} maxTokens - Максимальное количество токенов (по умолчанию 2000)
     * @param {Array} tools - Массив инструментов в формате MCP (опционально)
     * @returns {Promise<string>} - Ответ модели
     */
    async callModel(messages, temperature = 0.7, maxTokens = 2000, tools = null) {
        console.log('[HuggingFace] Вызов Qwen/Qwen2.5-7B-Instruct');
        console.log(`[HuggingFace] Сообщений: ${messages.length}`);
        console.log(`[HuggingFace] Temperature: ${temperature}`);
        console.log(`[HuggingFace] Tools передано: ${tools ? tools.length : 0}`);
        
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
            top_p: 0.95  // Для Qwen thinking mode
        };
        
        // Добавляем tools в формате OpenAI, если они переданы
        if (tools && tools.length > 0) {
            requestBody.tools = tools.map(tool => ({
                type: 'function',
                function: {
                    name: tool.name,
                    description: tool.description,
                    parameters: tool.parameters || tool.inputSchema || {
                        type: 'object',
                        properties: {},
                        required: []
                    }
                }
            }));
            
            console.log(`[HuggingFace] ✅ Добавлено ${requestBody.tools.length} инструментов в запрос`);
            console.log(`[HuggingFace] Список: ${requestBody.tools.map(t => t.function.name).join(', ')}`);
        }

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
                    throw new Error('⏳ Модель Qwen/Qwen2.5-7B-Instruct загружается. Попробуйте через 20-30 секунд.');
                }
                
                throw new Error(`HuggingFace API error: ${response.status} - ${errorText}`);
            }

            const data = await response.json();
            let responseText = data.choices[0].message.content;
            
            // Парсим и удаляем <think>...</think> блоки для Qwen thinking mode
            const thinkPattern = /<think>[\s\S]*?<\/think>/g;
            const thinkMatches = responseText.match(thinkPattern);
            if (thinkMatches) {
                console.log('[HuggingFace] 🧠 Thinking content найден, удаляем из ответа');
                responseText = responseText.replace(thinkPattern, '').trim();
            }
            
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



