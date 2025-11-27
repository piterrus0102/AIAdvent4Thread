#!/usr/bin/env node
// =============================================================================
// Chat CLI - Консольный клиент для общения с сервером
// =============================================================================

import readline from 'readline';
import chalk from 'chalk';

const SERVER_URL = process.env.SERVER_URL || 'http://localhost:3001';

// =============================================================================
// HELPERS
// =============================================================================

async function fetchJSON(url, options = {}) {
    try {
        const response = await fetch(url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            }
        });
        return await response.json();
    } catch (error) {
        throw new Error(`Ошибка запроса: ${error.message}`);
    }
}

async function getRAGMode() {
    const result = await fetchJSON(`${SERVER_URL}/api/rag-mode`);
    return result;
}

async function sendMessage(message, history = []) {
    const result = await fetchJSON(`${SERVER_URL}/api/chat`, {
        method: 'POST',
        body: JSON.stringify({ message, history })
    });
    return result;
}

// =============================================================================
// UI
// =============================================================================

function printHeader() {
    console.clear();
    console.log(chalk.blue('╔════════════════════════════════════════════════════════════════════╗'));
    console.log(chalk.blue('║') + chalk.bold.white('          💬 CHAT CLI - Консольный клиент                         ') + chalk.blue('║'));
    console.log(chalk.blue('╚════════════════════════════════════════════════════════════════════╝'));
    console.log();
}

function printSeparator() {
    console.log(chalk.gray('─'.repeat(70)));
}

async function printStatus() {
    try {
        const mode = await getRAGMode();
        const isRAG = mode.enabled;
        
        if (isRAG) {
            console.log(chalk.green('🔍 Режим: RAG (Векторный поиск по курсу)'));
        } else {
            console.log(chalk.yellow('💬 Режим: Обычный (Прямой запрос к LLM)'));
        }
        console.log(chalk.gray(`   ${mode.description}`));
        console.log();
    } catch (error) {
        console.log(chalk.red('❌ Ошибка подключения к серверу'));
        console.log(chalk.gray(`   ${SERVER_URL}`));
        console.log();
    }
}

function printHelp() {
    console.log(chalk.cyan('💡 Команды:'));
    console.log(chalk.gray('   /status  - Показать текущий режим сервера'));
    console.log(chalk.gray('   /clear   - Очистить экран'));
    console.log(chalk.gray('   /help    - Показать эту справку'));
    console.log(chalk.gray('   /exit    - Выход'));
    console.log();
    console.log(chalk.yellow('⚠️  Переключение режима RAG делается на сервере командами: rag on/off'));
    console.log();
}

// =============================================================================
// MAIN
// =============================================================================

async function main() {
    printHeader();
    await printStatus();
    printHelp();
    printSeparator();
    console.log();

    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
        prompt: chalk.bold.cyan('> ')
    });

    const history = [];

    rl.prompt();

    rl.on('line', async (input) => {
        const query = input.trim();

        if (!query) {
            rl.prompt();
            return;
        }

        // Команды
        if (query === '/exit' || query === 'exit' || query === 'quit') {
            console.log(chalk.yellow('\n👋 До свидания!\n'));
            process.exit(0);
        }

        if (query === '/clear' || query === 'clear') {
            printHeader();
            await printStatus();
            console.log();
            rl.prompt();
            return;
        }

        if (query === '/status' || query === 'status') {
            console.log();
            await printStatus();
            rl.prompt();
            return;
        }

        if (query === '/help' || query === 'help') {
            console.log();
            printHelp();
            rl.prompt();
            return;
        }

        // Отправка сообщения
        try {
            console.log();
            console.log(chalk.gray('⏳ Отправка запроса...'));

            const result = await sendMessage(query, history);

            if (!result.success) {
                console.log(chalk.red(`\n❌ Ошибка: ${result.error}\n`));
                rl.prompt();
                return;
            }

            // Сохраняем в историю
            history.push({ role: 'user', text: query });
            history.push({ role: 'assistant', text: result.message });

            // Выводим ответ
            console.log();
            printSeparator();
            
            // Инструменты
            if (result.toolUsed) {
                console.log(chalk.magenta(`🔧 Использованы инструменты: ${result.toolUsed}`));
                if (result.toolResult) {
                    console.log(chalk.gray(`   ${result.toolResult.substring(0, 100)}...`));
                }
                console.log();
            }

            // RAG результаты
            if (result.ragLessons && result.ragLessons.length > 0) {
                console.log(chalk.green('📚 Найдено уроков: ' + result.ragLessons.length));
                result.ragLessons.forEach((lesson, idx) => {
                    // Формируем кликабельную ссылку для терминала
                    const url = lesson.url ? `http://localhost:3000${lesson.url}` : null;
                    const title = lesson.title;
                    const relevance = lesson.relevance;
                    const llmScore = lesson.llm_score ? ` | LLM: ${lesson.llm_score}` : '';
                    
                    if (url) {
                        // Используем ANSI escape sequences для создания кликабельной ссылки
                        // Формат: \x1b]8;;URL\x1b\\TEXT\x1b]8;;\x1b\\
                        const clickableLink = `\x1b]8;;${url}\x1b\\${title}\x1b]8;;\x1b\\`;
                        console.log(chalk.gray(`   ${idx + 1}. `) + chalk.cyan(clickableLink) + chalk.gray(` (${relevance}${llmScore})`));
                    } else {
                        console.log(chalk.gray(`   ${idx + 1}. ${title} (${relevance}${llmScore})`));
                    }
                });
                console.log();
            }

            // Ответ
            console.log(chalk.bold.white('💬 Ответ:'));
            
            // Обрабатываем специальные ссылки в формате [LESSON_LINK:id:title]
            let formattedMessage = result.message;
            
            // Находим все ссылки в формате [LESSON_LINK:id:title]
            const linkPattern = /\[LESSON_LINK:([^:]+):([^\]]+)\]/g;
            const links = [];
            let match;
            
            while ((match = linkPattern.exec(result.message)) !== null) {
                const lessonId = match[1];
                const lessonTitle = match[2];
                
                // Находим URL для этого урока
                const lesson = result.ragLessons?.find(l => l.id === lessonId);
                if (lesson && lesson.url) {
                    const url = `http://localhost:3000${lesson.url}`;
                    // Заменяем на формат: Название - полный URL (терминал сам сделает URL кликабельным)
                    const replacement = chalk.cyan(`${lessonTitle}`) + '\n   ' + chalk.blue.underline(url);
                    links.push({
                        original: match[0],
                        replacement: replacement
                    });
                }
            }
            
            // Заменяем все найденные ссылки
            for (const link of links) {
                formattedMessage = formattedMessage.replace(link.original, link.replacement);
            }
            
            console.log(chalk.white(formattedMessage));
            
            printSeparator();
            console.log();

        } catch (error) {
            console.log(chalk.red(`\n❌ Ошибка: ${error.message}\n`));
        }

        rl.prompt();
    });

    rl.on('close', () => {
        console.log(chalk.yellow('\n👋 До свидания!\n'));
        process.exit(0);
    });
}

// Проверка подключения к серверу
async function checkServer() {
    try {
        await fetchJSON(`${SERVER_URL}/health`);
        return true;
    } catch (error) {
        console.log(chalk.red('❌ Сервер недоступен!'));
        console.log(chalk.gray(`   URL: ${SERVER_URL}`));
        console.log(chalk.yellow('\n💡 Запустите сервер: cd mcp-proxy && npm start\n'));
        process.exit(1);
    }
}

// Запуск
checkServer().then(() => {
    main().catch(error => {
        console.error(chalk.red('❌ Критическая ошибка:'), error);
        process.exit(1);
    });
});

