# 📖 Подробное объяснение localserver.js

Полный построчный разбор каждой функции и компонента сервера.

---

## 🏗️ Структура файла

```
1. Импорты и инициализация (строки 1-29)
2. Middleware (строки 31-33)
3. Класс MCPServer (строки 38-209)
4. Класс MCPClient (строки 214-230)
5. Класс MainServer (строки 235-416)
6. Инициализация компонентов (строки 421-423)
7. API Endpoints (строки 434-594)
8. Запуск сервера (строки 599-628)
```

---

## 1️⃣ ИМПОРТЫ И ИНИЦИАЛИЗАЦИЯ (строки 1-29)

### Строки 1-7: Импорты модулей

```javascript
import express from 'express';           // Web-фреймворк для создания HTTP сервера
import cors from 'cors';                 // Middleware для кросс-доменных запросов
import { EventEmitter } from 'events';   // Для событийной модели (пока не используется)
import fs from 'fs/promises';            // Файловая система (асинхронные операции)
import path from 'path';                 // Работа с путями файлов
import { fileURLToPath } from 'url';     // Преобразование URL в путь (для ES modules)
import dotenv from 'dotenv';             // Загрузка переменных из .env файла
```

**Что это:**
- Подключаем библиотеки, которые будем использовать
- `express` - основа нашего HTTP сервера
- `fs/promises` - асинхронная работа с файлами (читаем/пишем count.json)
- `dotenv` - читает файл `.env` и загружает переменные окружения

---

### Строки 9-10: Определение путей

```javascript
const __filename = fileURLToPath(import.meta.url);  // Полный путь к текущему файлу
const __dirname = path.dirname(__filename);         // Путь к папке с файлом
```

**Зачем:**
- В ES modules нет встроенных `__filename` и `__dirname`
- Нужно вручную их определить
- Используем для создания пути к `count.json` позже

---

### Строки 12-13: Загрузка .env

```javascript
// Загружаем переменные окружения из .env файла
dotenv.config();
```

**Что происходит:**
1. `dotenv.config()` ищет файл `.env` в текущей папке
2. Читает все переменные из него
3. Помещает их в `process.env`

**Пример:**
```
Файл .env:
YANDEX_API_KEY=abc123

После dotenv.config():
process.env.YANDEX_API_KEY === 'abc123'
```

---

### Строки 15-16: Создание Express приложения

```javascript
const app = express();                        // Создаем экземпляр Express приложения
const PORT = process.env.PORT || 3001;       // Порт из .env или по умолчанию 3001
```

**Что это:**
- `app` - объект Express приложения, через него создаем endpoints
- `PORT` - порт, на котором будет работать сервер

---

### Строки 18-29: Проверка обязательных переменных

```javascript
// YandexGPT credentials (загружаются из .env файла)
const YANDEX_API_KEY = process.env.YANDEX_API_KEY;       // API ключ Yandex
const YANDEX_FOLDER_ID = process.env.YANDEX_FOLDER_ID;   // ID папки Yandex

if (!YANDEX_API_KEY || !YANDEX_FOLDER_ID) {
    console.error('❌ ОШИБКА: Не заданы переменные окружения...');
    // ... вывод инструкций
    process.exit(1);  // Останавливаем процесс с кодом ошибки 1
}
```

**Цель:**
- Проверяем, что ключи YandexGPT загружены
- Если нет - показываем ошибку и **останавливаем сервер**
- **Защита:** сервер не запустится без валидных ключей

---

## 2️⃣ MIDDLEWARE (строки 31-33)

```javascript
// Middleware
app.use(cors());           // Разрешаем кросс-доменные запросы
app.use(express.json());   // Парсим JSON в теле запросов
```

**Что делает:**
- `cors()` - разрешает Android приложению делать запросы с другого домена
- `express.json()` - автоматически парсит JSON из `req.body`

**Без этого:**
```javascript
// БЕЗ express.json():
req.body === undefined  ❌

// С express.json():
req.body === { message: "привет" }  ✅
```

---

## 3️⃣ КЛАСС MCPServer (строки 38-209)

### 📌 Назначение класса

**Роль:** Управление инструментами и данными
- Определяет доступные инструменты (tools)
- Хранит данные в `count.json`
- Выполняет вызовы инструментов

---

### Строки 39-68: Конструктор MCPServer

```javascript
constructor() {
    this.tools = [ ... ];           // Массив доступных инструментов
    this.dataFile = path.join(__dirname, 'count.json');  // Путь к файлу данных
    this.availableModels = ['L3-8B-Stheno', 'MiniMax-M2', 'Qwen2.5-7B-Instruct'];
    this.initializeDataFile();      // Инициализация файла при старте
}
```

**Что инициализируется:**
1. **`this.tools`** - описание инструментов (название, описание, параметры)
2. **`this.dataFile`** - полный путь к `count.json` (например: `/path/to/mcp-proxy/count.json`)
3. **`this.availableModels`** - список моделей (используется в `get_available_models`)
4. **`this.initializeDataFile()`** - создает файл, если его нет

---

### Инструмент 1: get_message_count (строки 41-54)

```javascript
{
    name: 'get_message_count',
    description: 'Получить текущее количество сообщений...',
    inputSchema: {
        type: 'object',
        properties: {
            model_name: {
                type: 'string',
                description: 'Название модели. Если не указано, вернутся счетчики для всех моделей...'
            }
        },
        required: []  // model_name НЕ обязательный параметр
    }
}
```

**Структура инструмента:**
- **`name`** - уникальное имя инструмента
- **`description`** - что делает инструмент (для LLM)
- **`inputSchema`** - какие параметры принимает
- **`required: []`** - все параметры опциональные

**Как это используется:**
1. YandexGPT видит описание в system prompt
2. Решает использовать инструмент: `USE_TOOL: get_message_count`
3. Сервер вызывает `mcpServer.callTool('get_message_count', {})`

---

### Инструмент 2: get_available_models (строки 55-63)

```javascript
{
    name: 'get_available_models',
    description: 'Получить список доступных моделей для общения...',
    inputSchema: {
        type: 'object',
        properties: {},   // НЕТ параметров
        required: []
    }
}
```

**Особенность:**
- Не принимает параметры (`properties: {}`)
- Просто возвращает список `this.availableModels`

---

### Строки 70-101: Метод initializeDataFile()

```javascript
async initializeDataFile() {
    try {
        await fs.access(this.dataFile);  // Проверяем, существует ли файл
        
        // Файл существует, читаем его
        const data = await fs.readFile(this.dataFile, 'utf-8');
        const parsed = JSON.parse(data);
        
        // Если старый формат (просто count), мигрируем на новый
        if (parsed.count !== undefined && !parsed.models) {
            // Создаем новый формат с моделями
            const newData = { models: { ... } };
            await fs.writeFile(this.dataFile, JSON.stringify(newData, null, 2));
        }
    } catch {
        // Файл НЕ существует, создаем новый
        const initialData = { models: { ... } };
        await fs.writeFile(this.dataFile, JSON.stringify(initialData, null, 2));
    }
}
```

**Логика:**
1. **Пробуем открыть файл** (`fs.access`)
   - Если успешно → файл существует → проверяем формат
   - Если ошибка → файл не существует → создаем новый

2. **Проверка формата:**
   ```javascript
   // Старый формат (миграция)
   { "count": 10 }  →  { "models": { "L3-8B-Stheno": 0, ... } }
   
   // Новый формат (ничего не делаем)
   { "models": { "L3-8B-Stheno": 5, ... } }
   ```

3. **Создание файла:**
   ```javascript
   // Если файла нет вообще
   { "models": { "L3-8B-Stheno": 0, "MiniMax-M2": 0, "Qwen2.5-7B-Instruct": 0 } }
   ```

**Зачем `try-catch`:**
- `fs.access()` выбрасывает ошибку, если файл не существует
- `catch` блок создает новый файл

---

### Строки 103-109: Метод listTools()

```javascript
listTools() {
    console.log('[MCP-Server] Запрошен список инструментов');
    return {
        tools: this.tools  // Возвращаем массив инструментов
    };
}
```

**Что возвращает:**
```javascript
{
    tools: [
        { name: 'get_message_count', description: '...', inputSchema: {...} },
        { name: 'get_available_models', description: '...', inputSchema: {...} }
    ]
}
```

**Кто вызывает:**
- `MCPClient.listTools()` → вызывает этот метод
- `MainServer.getToolsForLLM()` → получает список для YandexGPT

---

### Строки 112-123: Метод callTool(toolName, args)

```javascript
async callTool(toolName, args) {
    console.log(`[MCP-Server] Вызов инструмента: ${toolName}`, args);
    
    switch (toolName) {
        case 'get_message_count':
            return await this.getMessageCount(args);
        case 'get_available_models':
            return await this.getAvailableModels();
        default:
            throw new Error(`Unknown tool: ${toolName}`);
    }
}
```

**Роль:** Диспетчер вызовов инструментов

**Входные данные:**
- `toolName` - название инструмента (например: `'get_message_count'`)
- `args` - параметры (например: `{ model_name: 'L3-8B-Stheno' }`)

**Что делает:**
1. Проверяет `toolName`
2. Вызывает соответствующий метод
3. Возвращает результат

**Пример вызова:**
```javascript
// YandexGPT запросила: "Сколько сообщений?"
mcpServer.callTool('get_message_count', {})
// → вызывает this.getMessageCount({})
```

---

### Строки 126-174: Метод getMessageCount(args = {})

```javascript
async getMessageCount(args = {}) {
    try {
        // 1. Читаем файл count.json
        const data = await fs.readFile(this.dataFile, 'utf-8');
        const parsed = JSON.parse(data);
        const { model_name } = args;  // Извлекаем параметр model_name
        
        if (model_name) {
            // 2а. Запрошен счетчик для ОДНОЙ модели
            const count = parsed.models[model_name];
            
            if (count === undefined) {
                // Модель не найдена
                return {
                    content: [{
                        type: 'text',
                        text: `Модель ${model_name} не найдена. Доступные: ...`
                    }]
                };
            }
            
            // Модель найдена, возвращаем её счетчик
            return {
                content: [{
                    type: 'text',
                    text: `Количество сообщений с моделью ${model_name}: ${count}`
                }]
            };
        } else {
            // 2б. Запрошены счетчики для ВСЕХ моделей
            const modelsInfo = Object.entries(parsed.models)
                .map(([model, count]) => `- ${model}: ${count}`)
                .join('\n');
            
            return {
                content: [{
                    type: 'text',
                    text: `Количество сообщений по моделям:\n${modelsInfo}`
                }]
            };
        }
    } catch (error) {
        console.error('[MCP-Server] Ошибка при чтении count.json:', error);
        throw error;
    }
}
```

**Входные данные:**
- `args = {}` - объект с параметрами (может быть пустым)
- `args.model_name` - опциональное название модели

**Выходные данные (MCP формат):**
```javascript
{
    content: [
        {
            type: 'text',
            text: 'Количество сообщений с моделью L3-8B-Stheno: 5'
        }
    ]
}
```

**Логика:**
1. **Читаем `count.json`:**
   ```javascript
   { "models": { "L3-8B-Stheno": 5, "MiniMax-M2": 3, "Qwen2.5-7B-Instruct": 2 } }
   ```

2. **Если указан `model_name`:**
   ```javascript
   args = { model_name: 'L3-8B-Stheno' }
   → Возвращаем: "Количество сообщений с моделью L3-8B-Stheno: 5"
   ```

3. **Если `model_name` не указан:**
   ```javascript
   args = {}
   → Возвращаем:
   "Количество сообщений по моделям:
   - L3-8B-Stheno: 5
   - MiniMax-M2: 3
   - Qwen2.5-7B-Instruct: 2"
   ```

**Формат MCP:**
- Все результаты инструментов MCP возвращаются в формате:
  ```javascript
  { content: [{ type: 'text', text: '...' }] }
  ```

---

### Строки 177-194: Метод saveMessageCount(modelName, count)

```javascript
async saveMessageCount(modelName, count) {
    try {
        // 1. Читаем текущий файл
        const data = await fs.readFile(this.dataFile, 'utf-8');
        const parsed = JSON.parse(data);
        
        // 2. Проверяем, что есть поле models
        if (!parsed.models) {
            parsed.models = {};
        }
        
        // 3. Обновляем счетчик для модели
        parsed.models[modelName] = count;
        
        // 4. Записываем обратно в файл
        await fs.writeFile(this.dataFile, JSON.stringify(parsed, null, 2));
        console.log(`[MCP-Server] Сохранено количество сообщений для ${modelName}: ${count}`);
    } catch (error) {
        console.error('[MCP-Server] Ошибка при сохранении count.json:', error);
        throw error;
    }
}
```

**Входные данные:**
- `modelName` - название модели (например: `'L3-8B-Stheno'`)
- `count` - новое значение счетчика (например: `7`)

**Процесс:**
1. **Читаем файл:**
   ```javascript
   { "models": { "L3-8B-Stheno": 5, "MiniMax-M2": 3 } }
   ```

2. **Обновляем:**
   ```javascript
   parsed.models['L3-8B-Stheno'] = 7
   ```

3. **Записываем:**
   ```javascript
   { "models": { "L3-8B-Stheno": 7, "MiniMax-M2": 3 } }
   ```

**Кто вызывает:**
- `MainServer.updateMessageCount()` → вызывает этот метод
- Когда Android приложение отправляет `POST /api/message-count`

---

### Строки 197-208: Метод getAvailableModels()

```javascript
async getAvailableModels() {
    console.log(`[MCP-Server] Запрошен список доступных моделей`);
    
    // Форматируем список моделей
    const modelsList = this.availableModels.map((model, index) => 
        `${index + 1}. ${model}`
    ).join('\n');
    
    return {
        content: [{
            type: 'text',
            text: `Доступные модели для общения:\n${modelsList}`
        }]
    };
}
```

**Что делает:**
1. Берет массив `this.availableModels`
2. Форматирует с нумерацией
3. Возвращает в формате MCP

**Пример:**
```javascript
this.availableModels = ['L3-8B-Stheno', 'MiniMax-M2', 'Qwen2.5-7B-Instruct']

→ Возвращает:
"Доступные модели для общения:
1. L3-8B-Stheno
2. MiniMax-M2
3. Qwen2.5-7B-Instruct"
```

---

## 4️⃣ КЛАСС MCPClient (строки 214-230)

### 📌 Назначение класса

**Роль:** Посредник между Main Server и MCP Server
- Передает запросы от Main Server к MCP Server
- **Не содержит логики** - просто прослойка

---

### Строки 215-217: Конструктор

```javascript
constructor(mcpServer) {
    this.mcpServer = mcpServer;  // Ссылка на экземпляр MCPServer
}
```

**Инициализация:**
```javascript
const mcpServer = new MCPServer();
const mcpClient = new MCPClient(mcpServer);  // Передаем ссылку
```

---

### Строки 220-223: Метод listTools()

```javascript
async listTools() {
    console.log('[MCP-Client] Запрос списка инструментов от MCP-Server');
    return this.mcpServer.listTools();  // Просто передаем вызов
}
```

**Поток:**
```
MainServer.getToolsForLLM()
  → mcpClient.listTools()
    → mcpServer.listTools()
      → return { tools: [...] }
```

---

### Строки 226-229: Метод callTool(toolName, args)

```javascript
async callTool(toolName, args) {
    console.log(`[MCP-Client] Передача вызова инструмента '${toolName}' в MCP-Server`);
    return await this.mcpServer.callTool(toolName, args);  // Просто передаем
}
```

**Поток:**
```
GET /api/message-count
  → mcpClient.callTool('get_message_count', {})
    → mcpServer.callTool('get_message_count', {})
      → mcpServer.getMessageCount({})
        → return { content: [...] }
```

---

## 5️⃣ КЛАСС MainServer (строки 235-416)

### 📌 Назначение класса

**Роль:** Главный компонент сервера
- Обрабатывает запросы от Android приложения
- Взаимодействует с YandexGPT
- Управляет MCP инструментами через MCP Client

---

### Строки 236-239: Конструктор

```javascript
constructor(mcpClient, mcpServer) {
    this.mcpClient = mcpClient;  // Для вызова инструментов
    this.mcpServer = mcpServer;  // Для прямого доступа (сохранение счетчиков)
}
```

**Инициализация:**
```javascript
const mainServer = new MainServer(mcpClient, mcpServer);
```

---

### Строки 242-251: Метод getToolsForLLM()

```javascript
async getToolsForLLM() {
    // 1. Получаем инструменты от MCP Client
    const toolsResponse = await this.mcpClient.listTools();
    
    // 2. Преобразуем формат MCP → формат YandexGPT
    return toolsResponse.tools.map(tool => ({
        name: tool.name,
        description: tool.description,
        parameters: tool.inputSchema
    }));
}
```

**Что делает:**
1. Запрашивает список инструментов у MCP Client
2. Преобразует в формат, понятный YandexGPT

**Преобразование:**
```javascript
// Формат MCP (от MCPServer):
{
    tools: [
        { name: 'get_message_count', description: '...', inputSchema: {...} }
    ]
}

// Формат для YandexGPT:
[
    { name: 'get_message_count', description: '...', parameters: {...} }
]
```

**Зачем преобразование:**
- MCP использует `inputSchema`
- YandexGPT ожидает `parameters`
- Просто переименовываем поле

---

### Строки 254-370: Метод callLLM(messages, tools) 🧠

Это **самый важный метод** - обрабатывает взаимодействие с YandexGPT.

#### Часть 1: Подготовка запроса (строки 254-289)

```javascript
async callLLM(messages, tools) {
    // Логирование
    console.log('[Server] Отправка запроса в YandexGPT');
    console.log('[Server] Количество сообщений в истории:', messages.length);
    console.log('[Server] Доступные инструменты:', tools.map(t => t.name).join(', '));

    // 1. Формируем описание инструментов для system prompt
    const toolsDescription = tools.map(tool => 
        `- ${tool.name}: ${tool.description}`
    ).join('\n');

    // 2. Создаем system message с инструкциями для LLM
    const systemMessage = {
        role: 'system',
        text: `Ты — полезный ассистент. У тебя есть доступ к следующим инструментам:

${toolsDescription}

Когда пользователь задает вопрос, связанный с функционалом инструмента, используй соответствующий инструмент.

Для использования инструмента ответь в следующем формате:
USE_TOOL: имя_инструмента

Например:
USE_TOOL: get_message_count

После этого я вызову инструмент и предоставлю результат, а ты сформируешь финальный ответ пользователю.`
    };

    // 3. Формируем тело запроса для YandexGPT API
    const requestBody = {
        modelUri: `gpt://${YANDEX_FOLDER_ID}/yandexgpt/latest`,
        completionOptions: {
            stream: false,        // Не нужен streaming
            temperature: 0.6,     // Креативность (0.0 - детерминированно, 1.0 - творчески)
            maxTokens: 2000       // Максимум токенов в ответе
        },
        messages: [systemMessage, ...messages]  // System prompt + история
    };
```

**Что происходит:**
1. **Создаем описание инструментов:**
   ```
   - get_message_count: Получить текущее количество сообщений...
   - get_available_models: Получить список доступных моделей...
   ```

2. **Создаем system message:**
   - Объясняем YandexGPT, что у неё есть инструменты
   - Объясняем, как их использовать (`USE_TOOL: имя`)

3. **Формируем запрос:**
   ```javascript
   {
       modelUri: "gpt://b1gpro.../yandexgpt/latest",
       completionOptions: { ... },
       messages: [
           { role: 'system', text: 'Ты — полезный ассистент...' },
           { role: 'user', text: 'Сколько у меня сообщений?' }
       ]
   }
   ```

---

#### Часть 2: Отправка запроса (строки 291-312)

```javascript
    try {
        // 4. Отправляем запрос в YandexGPT API
        const response = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Api-Key ${YANDEX_API_KEY}`,
                'x-folder-id': YANDEX_FOLDER_ID
            },
            body: JSON.stringify(requestBody)
        });

        // 5. Проверяем статус ответа
        if (!response.ok) {
            const errorText = await response.text();
            console.error('[Server] Ошибка от YandexGPT:', response.status, errorText);
            throw new Error(`YandexGPT API error: ${response.status}`);
        }

        // 6. Парсим ответ
        const data = await response.json();
        const assistantMessage = data.result.alternatives[0].message.text;
        
        console.log('[Server] Получен ответ от YandexGPT');
        console.log('[Server] Ответ:', assistantMessage.substring(0, 100) + '...');
```

**Что происходит:**
1. **Отправляем HTTP POST запрос** в Yandex Cloud
2. **Передаем авторизацию** через API ключ
3. **Проверяем ответ** - если ошибка, выбрасываем exception
4. **Извлекаем текст ответа** из JSON структуры

**Структура ответа YandexGPT:**
```javascript
{
    result: {
        alternatives: [
            {
                message: {
                    role: 'assistant',
                    text: 'USE_TOOL: get_message_count'  // или обычный ответ
                }
            }
        ]
    }
}
```

---

#### Часть 3: Обработка инструмента (строки 314-358)

```javascript
        // 7. Проверяем, хочет ли LLM использовать инструмент
        if (assistantMessage.includes('USE_TOOL:')) {
            const toolMatch = assistantMessage.match(/USE_TOOL:\s*(\w+)/);
            
            if (toolMatch) {
                const toolName = toolMatch[1];  // Извлекаем название инструмента
                console.log(`[Server] LLM запросила использование инструмента: ${toolName}`);
                
                // 8. Вызываем инструмент через MCP-клиент
                const toolResult = await this.mcpClient.callTool(toolName, {});
                const toolResultText = toolResult.content[0].text;
                
                console.log('[Server] Результат инструмента:', toolResultText);
                
                // 9. Отправляем результат обратно в LLM
                const followUpMessages = [
                    ...messages,
                    { role: 'assistant', text: assistantMessage },  // "USE_TOOL: ..."
                    { role: 'user', text: `Результат выполнения инструмента ${toolName}:\n${toolResultText}\n\nТеперь, пожалуйста, предоставь понятный ответ пользователю на основе этих данных.` }
                ];
                
                // 10. Второй запрос к YandexGPT с результатом инструмента
                const followUpResponse = await fetch('https://llm.api.cloud.yandex.net/foundationModels/v1/completion', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Api-Key ${YANDEX_API_KEY}`,
                        'x-folder-id': YANDEX_FOLDER_ID
                    },
                    body: JSON.stringify({
                        ...requestBody,
                        messages: [systemMessage, ...followUpMessages]
                    })
                });
                
                const followUpData = await followUpResponse.json();
                const finalMessage = followUpData.result.alternatives[0].message.text;
                
                console.log('[Server] Финальный ответ от LLM:', finalMessage.substring(0, 100) + '...');
                
                // 11. Возвращаем финальный ответ
                return {
                    text: finalMessage,
                    toolUsed: toolName,
                    toolResult: toolResultText
                };
            }
        }
```

**Логика работы с инструментами (двухэтапный процесс):**

**Шаг 1: Первый запрос к YandexGPT**
```
User: "Сколько у меня сообщений?"
  → YandexGPT: "USE_TOOL: get_message_count"
```

**Шаг 2: Вызов инструмента**
```
Server → MCP Client → MCP Server
  → getMessageCount({})
    → "Количество сообщений по моделям:\n- L3-8B-Stheno: 5\n..."
```

**Шаг 3: Второй запрос к YandexGPT с результатом**
```
Messages:
[
  { role: 'user', text: 'Сколько у меня сообщений?' },
  { role: 'assistant', text: 'USE_TOOL: get_message_count' },
  { role: 'user', text: 'Результат выполнения инструмента:\nКоличество сообщений:\n- L3-8B-Stheno: 5\n...' }
]

→ YandexGPT: "У вас 5 сообщений с моделью L3-8B-Stheno, 3 с MiniMax-M2 и 2 с Qwen2.5-7B-Instruct. Всего 10 сообщений."
```

**Зачем два запроса:**
1. **Первый запрос** - YandexGPT решает, нужен ли инструмент
2. **Второй запрос** - YandexGPT формулирует красивый ответ на основе данных инструмента

---

#### Часть 4: Обычный ответ без инструмента (строки 360-369)

```javascript
        // Обычный ответ без инструментов
        return {
            text: assistantMessage,
            toolUsed: null
        };
        
    } catch (error) {
        console.error('[Server] Ошибка при вызове YandexGPT:', error);
        throw error;
    }
}
```

**Если YandexGPT не использует инструмент:**
```
User: "Привет!"
→ YandexGPT: "Здравствуйте! Чем могу помочь?"
→ return { text: "Здравствуйте! Чем могу помочь?", toolUsed: null }
```

---

### Строки 373-409: Метод handleMessage(userMessage, messageHistory)

```javascript
async handleMessage(userMessage, messageHistory) {
    try {
        console.log('[Server] Запрос списка инструментов от MCP Client...');
        
        // 1. Получаем список инструментов динамически от MCP Client
        const tools = await this.getToolsForLLM();
        
        console.log('[Server] Получено инструментов:', tools.length);
        tools.forEach(tool => {
            console.log(`[Server]   - ${tool.name}: ${tool.description}`);
        });
        
        // 2. Формируем историю сообщений
        const messages = [
            ...messageHistory,
            { role: 'user', text: userMessage }
        ];
        
        // 3. Вызываем LLM с динамически полученными инструментами
        const response = await this.callLLM(messages, tools);
        
        // 4. Возвращаем результат
        return {
            success: true,
            message: response.text,
            toolUsed: response.toolUsed || null,
            toolResult: response.toolResult || null
        };
        
    } catch (error) {
        console.error('[Server] Ошибка при обработке сообщения:', error);
        return {
            success: false,
            error: error.message
        };
    }
}
```

**Это главная точка входа для обработки сообщений от Android приложения.**

**Входные данные:**
- `userMessage` - текст нового сообщения пользователя
- `messageHistory` - история предыдущих сообщений

**Выходные данные:**
```javascript
{
    success: true,
    message: "У вас 5 сообщений с моделью L3-8B-Stheno",
    toolUsed: "get_message_count",
    toolResult: "Количество сообщений:\n- L3-8B-Stheno: 5\n..."
}
```

**Поток:**
```
Android App
  → POST /api/chat
    → mainServer.handleMessage('Сколько сообщений?', [...])
      → this.getToolsForLLM()  // Получаем инструменты
      → this.callLLM(messages, tools)  // Идем в YandexGPT
        → (возможно вызов инструмента)
      → return { success: true, message: '...', ... }
    → res.json({ success: true, ... })
  → Android App получает ответ
```

---

### Строки 412-415: Метод updateMessageCount(modelName, count)

```javascript
async updateMessageCount(modelName, count) {
    console.log(`[Server] Обновление счетчика для модели ${modelName}: ${count}`);
    await this.mcpServer.saveMessageCount(modelName, count);
}
```

**Простой метод-обертка:**
- Принимает запрос на обновление счетчика
- Передает в MCPServer для сохранения в файл

**Вызывается из:**
```
POST /api/message-count
  → mainServer.updateMessageCount('L3-8B-Stheno', 7)
    → mcpServer.saveMessageCount('L3-8B-Stheno', 7)
      → Записывает в count.json
```

---

## 6️⃣ ИНИЦИАЛИЗАЦИЯ КОМПОНЕНТОВ (строки 421-423)

```javascript
const mcpServer = new MCPServer();
const mcpClient = new MCPClient(mcpServer);
const mainServer = new MainServer(mcpClient, mcpServer);
```

**Порядок важен!**
1. Сначала создаем `MCPServer` (содержит инструменты и данные)
2. Потом `MCPClient` (нужна ссылка на MCPServer)
3. Потом `MainServer` (нужны ссылки на оба)

**Связи:**
```
MainServer
  ├─→ mcpClient (для вызова инструментов)
  └─→ mcpServer (для прямого сохранения счетчиков)

MCPClient
  └─→ mcpServer (для передачи вызовов)
```

---

## 7️⃣ API ENDPOINTS (строки 434-594)

### Endpoint 1: POST /api/chat (строки 434-459)

```javascript
app.post('/api/chat', async (req, res) => {
    try {
        const { message, history = [] } = req.body;
        
        // Валидация
        if (!message) {
            return res.status(400).json({
                success: false,
                error: 'Message is required'
            });
        }
        
        console.log(`\n[API] POST /api/chat - Новое сообщение от App`);
        console.log(`[API] Сообщение: "${message.substring(0, 50)}..."`);
        console.log(`[API] Используется YandexGPT`);
        
        // Обработка сообщения
        const result = await mainServer.handleMessage(message, history);
        res.json(result);
        
    } catch (error) {
        console.error('[API] Ошибка в /api/chat:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});
```

**Назначение:** Отправить сообщение в чат с YandexGPT

**Request:**
```json
POST /api/chat
{
  "message": "Сколько у меня сообщений?",
  "history": [
    { "role": "user", "text": "Привет" },
    { "role": "assistant", "text": "Здравствуйте!" }
  ]
}
```

**Response (успех):**
```json
{
  "success": true,
  "message": "У вас 10 сообщений",
  "toolUsed": "get_message_count",
  "toolResult": "Количество сообщений:\n- L3-8B-Stheno: 5\n..."
}
```

**Response (ошибка):**
```json
{
  "success": false,
  "error": "Message is required"
}
```

---

### Endpoint 2: POST /api/message-count (строки 466-501)

```javascript
app.post('/api/message-count', async (req, res) => {
    try {
        const { modelName, count } = req.body;
        
        // Валидация modelName
        if (!modelName || typeof modelName !== 'string') {
            return res.status(400).json({
                success: false,
                error: 'modelName is required and must be a string'
            });
        }
        
        // Валидация count
        if (typeof count !== 'number') {
            return res.status(400).json({
                success: false,
                error: 'count must be a number'
            });
        }
        
        console.log(`\n[API] POST /api/message-count - Обновление счетчика для ${modelName}`);
        
        // Сохранение счетчика
        await mainServer.updateMessageCount(modelName, count);
        
        res.json({
            success: true,
            modelName,
            count
        });
        
    } catch (error) {
        console.error('[API] Ошибка в /api/message-count:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});
```

**Назначение:** Обновить счетчик сообщений для модели

**Request:**
```json
POST /api/message-count
{
  "modelName": "L3-8B-Stheno",
  "count": 7
}
```

**Response:**
```json
{
  "success": true,
  "modelName": "L3-8B-Stheno",
  "count": 7
}
```

**Кто вызывает:**
- Android приложение после каждого сообщения в HuggingFace чате

---

### Endpoint 3: GET /api/message-count (строки 508-552)

```javascript
app.get('/api/message-count', async (req, res) => {
    try {
        const { modelName } = req.query;  // Из URL: ?modelName=...
        
        console.log(`\n[API] GET /api/message-count - Запрос счетчика${modelName ? ` для ${modelName}` : ' для всех моделей'}`);
        
        // Формируем аргументы для инструмента
        const args = modelName ? { model_name: modelName } : {};
        
        // Вызываем инструмент get_message_count через MCP Client
        const result = await mcpClient.callTool('get_message_count', args);
        const resultText = result.content[0].text;
        
        if (modelName) {
            // Парсим счетчик для ОДНОЙ модели
            const countMatch = resultText.match(/(\d+)/);
            const count = countMatch ? parseInt(countMatch[1]) : 0;
            
            res.json({
                success: true,
                modelName,
                count
            });
        } else {
            // Парсим счетчики для ВСЕХ моделей
            const models = {};
            const lines = resultText.split('\n');
            lines.forEach(line => {
                const match = line.match(/- (.+?): (\d+)/);
                if (match) {
                    models[match[1]] = parseInt(match[2]);
                }
            });
            
            res.json({
                success: true,
                models
            });
        }
        
    } catch (error) {
        console.error('[API] Ошибка в /api/message-count:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});
```

**Назначение:** Получить текущие счетчики сообщений

**Request (одна модель):**
```
GET /api/message-count?modelName=L3-8B-Stheno
```

**Response:**
```json
{
  "success": true,
  "modelName": "L3-8B-Stheno",
  "count": 7
}
```

**Request (все модели):**
```
GET /api/message-count
```

**Response:**
```json
{
  "success": true,
  "models": {
    "L3-8B-Stheno": 7,
    "MiniMax-M2": 3,
    "Qwen2.5-7B-Instruct": 2
  }
}
```

**Парсинг результата MCP:**
```javascript
// Результат от инструмента:
"Количество сообщений по моделям:\n- L3-8B-Stheno: 7\n- MiniMax-M2: 3"

// Парсим регулярным выражением:
/- (.+?): (\d+)/
// Группа 1: название модели
// Группа 2: счетчик

// Получаем:
{ "L3-8B-Stheno": 7, "MiniMax-M2": 3 }
```

---

### Endpoint 4: GET /api/tools (строки 558-576)

```javascript
app.get('/api/tools', async (req, res) => {
    try {
        console.log('\n[API] GET /api/tools - Запрос списка инструментов');
        
        const tools = await mainServer.getToolsForLLM();
        
        res.json({
            success: true,
            tools
        });
        
    } catch (error) {
        console.error('[API] Ошибка в /api/tools:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Internal server error'
        });
    }
});
```

**Назначение:** Получить список доступных MCP инструментов

**Request:**
```
GET /api/tools
```

**Response:**
```json
{
  "success": true,
  "tools": [
    {
      "name": "get_message_count",
      "description": "Получить текущее количество сообщений...",
      "parameters": { ... }
    },
    {
      "name": "get_available_models",
      "description": "Получить список доступных моделей...",
      "parameters": { ... }
    }
  ]
}
```

---

### Endpoint 5: GET /health (строки 582-594)

```javascript
app.get('/health', (req, res) => {
    res.json({
        status: 'ok',
        server: 'localserver',
        architecture: {
            app: 'Android App',
            server: 'Main Server (Express + YandexGPT)',
            mcpClient: 'MCP Client',
            mcpServer: 'MCP Server (Tools)'
        },
        timestamp: new Date().toISOString()
    });
});
```

**Назначение:** Health check - проверка работоспособности сервера

**Request:**
```
GET /health
```

**Response:**
```json
{
  "status": "ok",
  "server": "localserver",
  "architecture": { ... },
  "timestamp": "2025-11-18T14:00:00.000Z"
}
```

---

## 8️⃣ ЗАПУСК СЕРВЕРА (строки 599-628)

### Строки 599-617: Запуск Express сервера

```javascript
app.listen(PORT, '0.0.0.0', () => {
    console.log('\n==========================================================');
    console.log(`🚀 Local Server запущен на http://0.0.0.0:${PORT}`);
    console.log('==========================================================');
    console.log('\n📐 Архитектура:');
    console.log('  📱 App → 🖥️  Main Server → 🔌 MCP Client → 🔧 MCP Server → 🛠️  Tools');
    console.log('\n📡 Доступные endpoints:');
    console.log(`  POST   /api/chat           - Отправить сообщение в чат`);
    console.log(`  POST   /api/message-count  - Обновить счетчик сообщений`);
    console.log(`  GET    /api/message-count  - Получить счетчик сообщений`);
    console.log(`  GET    /api/tools          - Список инструментов`);
    console.log(`  GET    /health             - Health check`);
    console.log('\n🔧 Доступные инструменты MCP:');
    mcpServer.tools.forEach(tool => {
        console.log(`  - ${tool.name}: ${tool.description}`);
    });
    console.log('\n💡 Данные хранятся в: count.json');
    console.log('==========================================================\n');
});
```

**Что происходит:**
1. **`app.listen(PORT, '0.0.0.0', callback)`** - запускает сервер
   - `PORT` - порт (3001 по умолчанию)
   - `'0.0.0.0'` - слушает на всех сетевых интерфейсах (нужно для Android эмулятора)
   - `callback` - выполняется после успешного запуска

2. **Callback выводит информацию:**
   - URL сервера
   - Архитектуру компонентов
   - Список endpoints
   - Динамический список инструментов MCP

**Почему `0.0.0.0` а не `localhost`:**
```
localhost (127.0.0.1) - только локальные подключения
0.0.0.0 - все интерфейсы, включая 10.0.2.2 для Android эмулятора
```

---

### Строки 620-628: Graceful shutdown

```javascript
// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n🛑 Остановка сервера...');
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('\n🛑 Остановка сервера...');
    process.exit(0);
});
```

**Что это:**
- **`SIGINT`** - сигнал при нажатии Ctrl+C
- **`SIGTERM`** - сигнал при завершении процесса системой

**Graceful shutdown:**
- Перехватываем сигнал остановки
- Выводим сообщение
- Корректно завершаем процесс

**Можно было бы добавить:**
```javascript
process.on('SIGINT', async () => {
    console.log('\n🛑 Остановка сервера...');
    
    // Закрыть соединения
    await saveImportantData();
    
    // Закрыть сервер
    server.close(() => {
        console.log('Сервер остановлен');
        process.exit(0);
    });
});
```

---

## 📊 ПОТОК ДАННЫХ: Полный пример

### Сценарий: Пользователь спрашивает "Сколько сообщений?"

```
1. Android App
   ↓ POST /api/chat { message: "Сколько сообщений?", history: [] }

2. Express Endpoint: app.post('/api/chat')
   ↓ const result = await mainServer.handleMessage(...)

3. MainServer.handleMessage()
   ↓ const tools = await this.getToolsForLLM()
   
4. MainServer.getToolsForLLM()
   ↓ const toolsResponse = await this.mcpClient.listTools()
   
5. MCPClient.listTools()
   ↓ return this.mcpServer.listTools()
   
6. MCPServer.listTools()
   ↓ return { tools: this.tools }
   ↑ [{ name: 'get_message_count', ... }, { name: 'get_available_models', ... }]

7. MainServer.callLLM(messages, tools)
   ↓ Формирует system prompt с описанием инструментов
   ↓ POST → YandexGPT API

8. YandexGPT (первый запрос)
   ↑ "USE_TOOL: get_message_count"

9. MainServer.callLLM() обнаруживает USE_TOOL
   ↓ const toolResult = await this.mcpClient.callTool('get_message_count', {})

10. MCPClient.callTool()
    ↓ return await this.mcpServer.callTool('get_message_count', {})

11. MCPServer.callTool()
    ↓ return await this.getMessageCount({})

12. MCPServer.getMessageCount()
    ↓ Читает count.json
    ↑ { content: [{ type: 'text', text: 'Количество сообщений:\n- L3-8B-Stheno: 5\n...' }] }

13. MainServer.callLLM() (второй запрос к YandexGPT)
    ↓ POST → YandexGPT API с результатом инструмента

14. YandexGPT (второй запрос)
    ↑ "У вас 5 сообщений с моделью L3-8B-Stheno, 3 с MiniMax-M2 и 2 с Qwen2.5-7B-Instruct."

15. MainServer.callLLM()
    ↑ return { text: "У вас 5 сообщений...", toolUsed: 'get_message_count', ... }

16. MainServer.handleMessage()
    ↑ return { success: true, message: "У вас 5 сообщений...", ... }

17. Express Endpoint
    ↑ res.json({ success: true, message: "У вас 5 сообщений...", ... })

18. Android App
    ↑ Получает ответ и отображает пользователю
```

---

## 🎯 Ключевые концепции

### 1. Разделение ответственности

```
MCPServer   - Инструменты и данные (count.json)
MCPClient   - Посредник (передача вызовов)
MainServer  - Бизнес-логика (YandexGPT, обработка)
Express     - HTTP API (endpoints)
```

### 2. MCP Protocol

**Формат инструмента:**
```javascript
{
    name: 'название',
    description: 'описание',
    inputSchema: { /* параметры */ }
}
```

**Формат результата:**
```javascript
{
    content: [
        {
            type: 'text',
            text: 'результат'
        }
    ]
}
```

### 3. Двухэтапный вызов YandexGPT

1. **Первый запрос:** "Нужен ли инструмент?" → `USE_TOOL: название`
2. **Вызов инструмента:** Получаем данные
3. **Второй запрос:** "Сформулируй ответ на основе данных" → Финальный ответ

### 4. Асинхронность

Все операции асинхронные:
- `async/await` для всех методов
- Чтение/запись файлов: `fs.readFile()`, `fs.writeFile()`
- HTTP запросы: `fetch()`
- Express endpoints: `async (req, res) => { ... }`

---

## 📝 Резюме

**Файл содержит:**
- 3 класса (MCPServer, MCPClient, MainServer)
- 5 HTTP endpoints (chat, message-count ×2, tools, health)
- 2 MCP инструмента (get_message_count, get_available_models)
- 630 строк кода

**Главная логика:**
- Android App → Express API → MainServer → YandexGPT
- YandexGPT → MainServer → MCPClient → MCPServer → Инструменты
- Результат → обратно по цепочке → Android App

**Хранение данных:**
- `count.json` - счетчики сообщений для каждой модели
- `.env` - API ключи YandexGPT

---

Это полное построчное объяснение **localserver.js**! 🎉

