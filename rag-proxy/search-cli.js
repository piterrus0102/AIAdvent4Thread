#!/usr/bin/env node

// =============================================================================
// search-cli.js - CLI для RAG поиска с генерацией ответов через LLM
// =============================================================================
// Использование:
//   node search-cli.js "ваш поисковый запрос"
// =============================================================================

// ВАЖНО: загружаем .env ДО импорта HuggingFaceClient!
import dotenv from 'dotenv';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Загружаем .env из папки mcp-proxy
const envPath = path.join(__dirname, '../.env');
dotenv.config({ path: envPath });

// Теперь импортируем остальное
import VectorizationClient from './VectorizationClient.js';
import IndexManager from './IndexManager.js';
import HuggingFaceClient from '../mcp-proxy/server/HuggingFaceClient.js';
import readline from 'readline';

const INDEX_FILE = path.join(__dirname, 'data/vector_index.json');
const LLM_MODEL = 'Qwen/Qwen2.5-7B-Instruct'; // HuggingFace модель

// Клиент для HuggingFace LLM
const huggingFaceClient = new HuggingFaceClient();

// =============================================================================
// LLM ГЕНЕРАЦИЯ
// =============================================================================

async function generateLLMAnswer(query, results, indexManager) {
    try {
        // Формируем контекст: берем ВЕСЬ контент из result (уже содержит полную статью)
        let contextParts = [];
        
        for (const result of results) {
            contextParts.push(`СТАТЬЯ: "${result.lesson_title}"
Раздел: ${result.section}

${result.content}`);
        }
        
        const context = contextParts.join('\n\n' + '='.repeat(80) + '\n\n');
        
        // Упрощенный промпт
        const systemMessage = {
            role: 'system',
            text: `Ты помощник по Android Studio. Отвечай кратко и точно.`
        };
        
        const userMessage = {
            role: 'user',
            text: `Прочитай следующие статьи и ответь на вопрос.

ВОПРОС: "${query}"

СТАТЬИ:
${context}

Дай краткий ответ (2-3 предложения) на основе информации из статей выше.`
        };
        
        // Вызов HuggingFace LLM
        console.log('⏳ Генерация ответа через HuggingFace (Qwen2.5-7B)...\n');
        
        const answer = await huggingFaceClient.callModel(
            [systemMessage, userMessage],
            0.2,  // Низкая температура для точности
            300   // Максимум токенов
        );
        
        console.log('💬 ОТВЕТ LLM:');
        console.log('─'.repeat(80));
        console.log(answer);
        console.log('─'.repeat(80));
        console.log('');
        
    } catch (error) {
        console.error('❌ Ошибка генерации ответа:', error.message);
        if (error.response) {
            console.error('Статус:', error.response.status);
            console.error('Данные:', error.response.data);
        }
        throw error; // Пробрасываем ошибку дальше
    }
}

// =============================================================================
// ИНТЕРАКТИВНЫЙ РЕЖИМ
// =============================================================================

async function interactiveMode(indexManager) {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout
    });

    console.log('\n' + '='.repeat(80));
    console.log('🔍 ИНТЕРАКТИВНЫЙ ПОИСК ПО КУРСУ (с LLM)');
    console.log('='.repeat(80));
    console.log('\nВведите поисковый запрос (или "exit" для выхода)');
    console.log('Примеры запросов:');
    console.log('  - Как работать с эмулятором?');
    console.log('  - Что такое Power Save Mode?');
    console.log('  - Для чего нужна Android Studio?');
    console.log('  - Invalidate Caches');
    console.log('\n🤖 LLM модель: Qwen2.5-7B-Instruct (HuggingFace)\n');

    const askQuestion = () => {
        rl.question('> ', async (query) => {
            if (!query || query.trim() === '') {
                askQuestion();
                return;
            }

            if (query.toLowerCase() === 'exit' || query.toLowerCase() === 'quit') {
                console.log('\n👋 До свидания!');
                rl.close();
                return;
            }

            try {
                await performSearch(indexManager, query.trim());
            } catch (error) {
                console.error('\n❌ Ошибка:', error.message);
            }

            console.log('');
            askQuestion();
        });
    };

    askQuestion();
}

// =============================================================================
// ПОИСК
// =============================================================================

async function performSearch(indexManager, query, topK = 3, useLLM = true) {
    console.log('\n' + '─'.repeat(80));
    
    const results = await indexManager.search(query, topK);
    
    if (results.length === 0) {
        console.log('Ничего не найдено 😢');
        return;
    }
    
    // Если включен LLM - генерируем ответ
    if (useLLM) {
        await generateLLMAnswer(query, results, indexManager);
    }
    
    console.log('📊 НАЙДЕННЫЕ СТАТЬИ:');
    console.log('─'.repeat(80));
    
    results.forEach((result, i) => {
        console.log(`\n${i + 1}. ${result.lesson_title}`);
        console.log('   ' + '─'.repeat(76));
        console.log(`   📈 Релевантность: ${(result.similarity * 100).toFixed(1)}%`);
        console.log(`   📁 Раздел: ${result.section}`);
        if (result.url) {
            console.log(`   🔗 URL: ${result.url}`);
        }
        
        // Показываем первые 200 символов контента
        const preview = result.content.substring(0, 200).replace(/\n/g, ' ').trim();
        console.log(`   📝 Превью: ${preview}...`);
    });
    
    console.log('\n' + '─'.repeat(80));
    console.log('\n💡 Режимы работы:');
    console.log('   node mcp-proxy/rag/search-cli.js "запрос"  - Поиск с LLM ответом');
    console.log('   node mcp-proxy/rag/search-cli.js          - Интерактивный режим\n');
}

// =============================================================================
// MAIN
// =============================================================================

async function main() {
    const query = process.argv.slice(2).join(' ');
    
    try {
        // Инициализация
        const vectorClient = new VectorizationClient();
        const indexManager = new IndexManager(INDEX_FILE, vectorClient);
        
        // Проверяем наличие индекса
        const exists = await indexManager.indexExists();
        if (!exists) {
            console.error('❌ Индекс не найден!');
            console.error('Сначала создайте индекс командой:');
            console.error('  node mcp-proxy/rag/build-index.js');
            process.exit(1);
        }
        
        // Загружаем индекс
        await indexManager.loadIndex();
        
        // Показываем статистику
        const stats = indexManager.getStats();
        console.log(`\n📚 Индекс загружен: ${stats.documents_count} документов`);
        console.log(`🔤 Embedding модель: ${stats.model}`);
        console.log(`🤖 LLM модель: Qwen2.5-7B-Instruct (HuggingFace)`);
        
        // Если запрос передан как аргумент - выполняем разовый поиск
        if (query) {
            await performSearch(indexManager, query);
        } else {
            // Иначе - интерактивный режим
            await interactiveMode(indexManager);
        }
        
    } catch (error) {
        console.error('\n❌ ОШИБКА:', error.message);
        process.exit(1);
    }
}

// Запускаем
main();
