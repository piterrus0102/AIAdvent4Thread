#!/usr/bin/env node

// =============================================================================
// create-smart-chunks.js - Умная разбивка документов на чанки
// =============================================================================
// Берет длинные статьи и разбивает их на части по ~800 символов
// Сохраняет привязку к родительской статье
// =============================================================================

import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const INPUT_FILE = path.join(__dirname, '../../course_chunks_for_vectorization.json');
const OUTPUT_FILE = path.join(__dirname, '../../course_chunks_smart.json');

const MAX_CHUNK_SIZE = 600; // Максимальный размер чанка в символах (~100 слов, оптимально для точного поиска)

// =============================================================================
// CHUNKING LOGIC
// =============================================================================

/**
 * Разбить текст на части по ~800 символов
 * Разбивает по параграфам, чтобы не резать посреди предложения
 */
function splitTextIntoSmartChunks(text, maxSize = MAX_CHUNK_SIZE) {
    // Разбиваем по двойным переносам строк (параграфы)
    const paragraphs = text.split(/\n\n+/);
    
    const chunks = [];
    let currentChunk = '';
    
    for (const paragraph of paragraphs) {
        // Если один параграф больше maxSize - режем его по предложениям
        if (paragraph.length > maxSize) {
            // Сохраняем текущий чанк если есть
            if (currentChunk.trim()) {
                chunks.push(currentChunk.trim());
                currentChunk = '';
            }
            
            // Режем длинный параграф по предложениям
            const sentences = paragraph.split(/\.\s+/);
            for (let i = 0; i < sentences.length; i++) {
                const sentence = sentences[i] + (i < sentences.length - 1 ? '.' : '');
                const testChunk = currentChunk + (currentChunk ? ' ' : '') + sentence;
                
                if (testChunk.length > maxSize && currentChunk.length > 0) {
                    chunks.push(currentChunk.trim());
                    currentChunk = sentence;
                } else {
                    currentChunk = testChunk;
                }
            }
            continue;
        }
        
        const testChunk = currentChunk + (currentChunk ? '\n\n' : '') + paragraph;
        
        if (testChunk.length > maxSize && currentChunk.length > 0) {
            // Текущий чанк переполнен - сохраняем и начинаем новый
            chunks.push(currentChunk.trim());
            currentChunk = paragraph;
        } else {
            currentChunk = testChunk;
        }
    }
    
    // Добавляем последний чанк
    if (currentChunk.trim()) {
        chunks.push(currentChunk.trim());
    }
    
    return chunks;
}

/**
 * Обработать все документы и создать smart chunks
 */
async function createSmartChunks() {
    console.log('='.repeat(80));
    console.log('📚 СОЗДАНИЕ УМНЫХ ЧАНКОВ');
    console.log('='.repeat(80));
    
    // Читаем исходные чанки
    console.log(`\nЧтение исходных данных: ${INPUT_FILE}`);
    const inputData = await fs.readFile(INPUT_FILE, 'utf-8');
    const originalChunks = JSON.parse(inputData);
    
    console.log(`✓ Загружено статей: ${originalChunks.length}`);
    
    // Создаем новые чанки
    const smartChunks = [];
    let totalChunks = 0;
    
    for (const original of originalChunks) {
        const contentLength = original.content.length;
        
        console.log(`\n[${original.id}] ${original.metadata?.lesson_title || 'Без названия'}`);
        console.log(`  Исходная длина: ${contentLength} символов`);
        
        if (contentLength <= MAX_CHUNK_SIZE) {
            // Короткая статья - оставляем как есть
            smartChunks.push({
                id: original.id,
                parent_id: original.id,
                chunk_index: 0,
                total_chunks: 1,
                content: original.content,
                metadata: {
                    ...original.metadata,
                    is_chunked: false
                }
            });
            totalChunks++;
            console.log(`  ✓ Статья короткая, оставлена без изменений`);
        } else {
            // Длинная статья - разбиваем на части
            const textParts = splitTextIntoSmartChunks(original.content, MAX_CHUNK_SIZE);
            
            console.log(`  ⚠️  Статья длинная, разбита на ${textParts.length} чанков:`);
            
            textParts.forEach((part, index) => {
                const chunkId = `${original.id}_chunk_${index + 1}`;
                smartChunks.push({
                    id: chunkId,
                    parent_id: original.id,
                    chunk_index: index,
                    total_chunks: textParts.length,
                    content: part,
                    metadata: {
                        ...original.metadata,
                        is_chunked: true,
                        chunk_info: `${index + 1}/${textParts.length}`
                    }
                });
                totalChunks++;
                console.log(`    Чанк ${index + 1}/${textParts.length}: ${part.length} символов`);
            });
        }
    }
    
    // Сохраняем результат
    console.log('\n' + '='.repeat(80));
    console.log('💾 СОХРАНЕНИЕ РЕЗУЛЬТАТА');
    console.log('='.repeat(80));
    
    await fs.writeFile(OUTPUT_FILE, JSON.stringify(smartChunks, null, 2), 'utf-8');
    
    console.log(`\n✓ Создано умных чанков: ${totalChunks}`);
    console.log(`✓ Исходных статей: ${originalChunks.length}`);
    console.log(`✓ Среднее количество чанков на статью: ${(totalChunks / originalChunks.length).toFixed(1)}`);
    console.log(`✓ Файл сохранен: ${OUTPUT_FILE}`);
    
    // Статистика
    console.log('\n' + '='.repeat(80));
    console.log('📊 СТАТИСТИКА ЧАНКОВ');
    console.log('='.repeat(80));
    
    smartChunks.forEach(chunk => {
        const suffix = chunk.total_chunks > 1 ? ` [${chunk.chunk_index + 1}/${chunk.total_chunks}]` : '';
        console.log(`  ${chunk.id}${suffix}: ${chunk.content.length} символов`);
    });
    
    console.log('\n✅ ГОТОВО! Теперь запустите:');
    console.log('   node mcp-proxy/rag/build-index.js\n');
}

// =============================================================================
// MAIN
// =============================================================================

createSmartChunks().catch(error => {
    console.error('\n❌ ОШИБКА:', error.message);
    process.exit(1);
});

