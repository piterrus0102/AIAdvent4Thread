#!/usr/bin/env node

// =============================================================================
// build-index.js - Скрипт создания векторного индекса
// =============================================================================
// Этот скрипт:
// 1. Читает чанки из course_chunks_for_vectorization.json
// 2. Генерирует эмбеддинги через Ollama
// 3. Сохраняет индекс в vector_index.json
// =============================================================================

import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';
import VectorizationClient from './VectorizationClient.js';
import IndexManager from './IndexManager.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Пути к файлам
const CHUNKS_FILE = path.join(__dirname, '../../course_chunks_smart.json');
const INDEX_FILE = path.join(__dirname, '../data/vector_index.json');

// =============================================================================
// MAIN
// =============================================================================

async function main() {
    console.log('='.repeat(80));
    console.log('📚 ПОСТРОЕНИЕ ВЕКТОРНОГО ИНДЕКСА');
    console.log('='.repeat(80));
    
    try {
        // 1. Проверяем наличие Ollama
        console.log('\n[1/5] Проверка доступности Ollama...');
        const vectorClient = new VectorizationClient();
        const isHealthy = await vectorClient.checkHealth();
        
        if (!isHealthy) {
            console.error('\n❌ Ollama недоступна!');
            console.error('Запустите Ollama командой: ollama serve');
            console.error('И установите модель: ollama pull nomic-embed-text');
            process.exit(1);
        }
        
        console.log('✓ Ollama доступна');
        
        // 2. Проверяем наличие модели эмбеддингов
        console.log('\n[2/5] Проверка модели эмбеддингов...');
        const models = await vectorClient.listModels();
        
        const hasModel = models.some(m => m.includes('mxbai-embed-large'));
        if (!hasModel) {
            console.error('\n❌ Модель mxbai-embed-large не найдена!');
            console.error('Установите модель командой: ollama pull mxbai-embed-large');
            console.error(`Доступные модели: ${models.join(', ')}`);
            process.exit(1);
        }
        
        console.log('✓ Модель mxbai-embed-large найдена (многоязычная, стабильная)');
        
        // 3. Читаем чанки
        console.log('\n[3/5] Чтение чанков из файла...');
        console.log(`Путь: ${CHUNKS_FILE}`);
        
        const chunksJson = await fs.readFile(CHUNKS_FILE, 'utf-8');
        const chunks = JSON.parse(chunksJson);
        
        console.log(`✓ Загружено чанков: ${chunks.length}`);
        console.log('\nИнформация о чанках:');
        chunks.forEach((chunk, i) => {
            console.log(`  ${i + 1}. [${chunk.id}] ${chunk.metadata?.lesson_title || 'Без заголовка'}`);
            console.log(`     Раздел: ${chunk.metadata?.section || 'N/A'}`);
            console.log(`     Длина: ${chunk.content.length} символов`);
        });
        
        // 4. Создаем индекс
        console.log('\n[4/5] Создание векторного индекса...');
        console.log('⚠️  Это может занять несколько минут...');
        
        const indexManager = new IndexManager(INDEX_FILE, vectorClient);
        await indexManager.buildIndex(chunks);
        
        // 5. Сохраняем индекс
        console.log('\n[5/5] Сохранение индекса...');
        await indexManager.saveIndex();
        
        // Показываем статистику
        console.log('\n' + '='.repeat(80));
        console.log('✅ ИНДЕКС УСПЕШНО СОЗДАН!');
        console.log('='.repeat(80));
        
        const stats = indexManager.getStats();
        console.log('\nСтатистика индекса:');
        console.log(`  Версия: ${stats.version}`);
        console.log(`  Модель: ${stats.model}`);
        console.log(`  Размерность векторов: ${stats.dimension}`);
        console.log(`  Документов: ${stats.documents_count}`);
        console.log(`  Разделов: ${stats.sections}`);
        console.log(`  Общая длина контента: ${stats.total_content_length} символов`);
        console.log(`  Средняя длина чанка: ${stats.avg_content_length} символов`);
        console.log(`  Создан: ${stats.created_at}`);
        console.log(`  Путь к индексу: ${INDEX_FILE}`);
        
        console.log('\n💡 Теперь можете использовать поиск:');
        console.log('   node mcp-proxy/rag/search-cli.js "ваш запрос"');
        console.log('\n');
        
    } catch (error) {
        console.error('\n❌ ОШИБКА:', error.message);
        console.error('\nПодробности:', error);
        process.exit(1);
    }
}

// Запускаем
main();

