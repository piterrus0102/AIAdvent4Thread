# Local Server (MCP + YandexGPT)

HTTP сервер, объединяющий:
- **Main Server** - обработка запросов от Android приложения и взаимодействие с YandexGPT
- **MCP Client** - посредник между Main Server и MCP Server  
- **MCP Server** - управление инструментами и данными

## 🚀 Быстрый старт

### 1. Установка зависимостей

```bash
cd mcp-proxy
npm install
```

### 2. Настройка переменных окружения

Создайте файл `.env` на основе шаблона:

```bash
cd mcp-proxy
cp .env.example .env
```

Откройте `.env` и вставьте **свои** ключи от YandexGPT:

```bash
nano .env  # или откройте в любом редакторе
```

Содержимое `.env`:
```env
YANDEX_API_KEY=ваш_реальный_ключ_здесь
YANDEX_FOLDER_ID=ваш_реальный_folder_id_здесь
PORT=3001
```

> 💡 **Как получить ключи:**
> 1. Перейдите на https://console.cloud.yandex.ru/
> 2. Создайте сервисный аккаунт
> 3. Получите API ключ
> 4. Скопируйте Folder ID из консоли
>
> 💡 Файл `.env` защищен `.gitignore` и **не попадет в репозиторий**

### 3. Запуск сервера

```bash
# Обычный запуск (читает ключи из .env)
npm run local

# Запуск с автоперезагрузкой при изменениях
npm run local:dev
```

Сервер запустится на `http://localhost:3001`

> ✅ Ключи автоматически загружаются из `.env` файла
> ✅ Не нужно запоминать длинные строки!

---

## 📡 API Endpoints

### 1. POST /api/chat

Отправить сообщение в чат (используется YandexGPT с MCP инструментами)

**Request:**
```json
{
  "message": "Сколько у меня сообщений?",
  "history": [
    { "role": "user", "text": "Привет" },
    { "role": "assistant", "text": "Здравствуйте!" }
  ]
}
```

**Response:**
```json
{
  "success": true,
  "message": "У вас 5 сообщений",
  "toolUsed": "get_message_count",
  "toolResult": "Количество сообщений: 5"
}
```

### 2. POST /api/message-count

Обновить счетчик сообщений для модели

**Request:**
```json
{
  "modelName": "L3-8B-Stheno",
  "count": 10
}
```

**Response:**
```json
{
  "success": true,
  "modelName": "L3-8B-Stheno",
  "count": 10
}
```

### 3. GET /api/message-count

Получить текущее количество сообщений

**Request:**
```bash
# Для всех моделей
GET /api/message-count

# Для конкретной модели
GET /api/message-count?modelName=L3-8B-Stheno
```

**Response (все модели):**
```json
{
  "success": true,
  "models": {
    "L3-8B-Stheno": 10,
    "MiniMax-M2": 5,
    "Qwen2.5-7B-Instruct": 3
  }
}
```

**Response (одна модель):**
```json
{
  "success": true,
  "modelName": "L3-8B-Stheno",
  "count": 10
}
```

### 4. GET /api/tools

Получить список доступных MCP инструментов

**Response:**
```json
{
  "success": true,
  "tools": [
    {
      "name": "get_message_count",
      "description": "Получить текущее количество сообщений в чате с моделями",
      "parameters": { ... }
    },
    {
      "name": "get_available_models",
      "description": "Получить список доступных моделей для общения",
      "parameters": { ... }
    }
  ]
}
```

### 5. GET /health

Health check сервера

**Response:**
```json
{
  "status": "ok",
  "server": "localserver",
  "architecture": {
    "app": "Android App",
    "server": "Main Server (Express + YandexGPT)",
    "mcpClient": "MCP Client",
    "mcpServer": "MCP Server (Tools)"
  },
  "timestamp": "2025-11-18T14:00:00.000Z"
}
```

---

## 🔧 MCP Инструменты

### get_message_count

Получить текущее количество сообщений в чате с моделями

**Параметры:**
- `model_name` (string, optional) - название модели

**Пример использования через YandexGPT:**
- "Сколько у меня сообщений?"
- "Сколько сообщений с моделью L3-8B-Stheno?"

### get_available_models

Получить список доступных моделей для общения

**Параметры:** нет

**Пример использования через YandexGPT:**
- "Какие у меня модели?"
- "Какая первая модель?"

---

## 🏗️ Архитектура

```
┌────────────────────────────────────┐
│        Android App                 │
│  • HuggingFace чат (3 модели)     │
│  • Server Chat (MCP)               │
└───────────────┬────────────────────┘
                │ HTTP API
                │
┌───────────────▼────────────────────┐
│        Main Server                 │
│  • Обработка запросов от App       │
│  • Взаимодействие с YandexGPT      │
│  • Управление инструментами через  │
│    MCP Client                      │
└───────────────┬────────────────────┘
                │ MCP Protocol
                │
┌───────────────▼────────────────────┐
│        MCP Client                  │
│  • Посредник между Server и MCP    │
│    Server                          │
└───────────────┬────────────────────┘
                │ Internal API
                │
┌───────────────▼────────────────────┐
│        MCP Server                  │
│  • Управление инструментами        │
│  • Хранение данных (count.json)    │
│  • Реализация инструментов:        │
│    - get_message_count             │
│    - get_available_models          │
└────────────────────────────────────┘
```

---

## 💾 Данные

Счетчики сообщений хранятся в файле `count.json`:

```json
{
  "models": {
    "L3-8B-Stheno": 10,
    "MiniMax-M2": 5,
    "Qwen2.5-7B-Instruct": 3
  }
}
```

---

## 📱 Для Android эмулятора

Android эмулятор не может подключиться к `localhost` на хост-машине напрямую. Используйте специальный IP:

- **`10.0.2.2`** - для Android эмулятора (это адрес хост-машины)
- **`localhost`** или **`127.0.0.1`** - для реального устройства в одной сети

В коде Android приложения уже настроен адрес `http://10.0.2.2:3001` для эмулятора.

---

## 🧪 Тестирование

```bash
# Health check
curl http://localhost:3001/health

# Получить счетчики всех моделей
curl http://localhost:3001/api/message-count

# Получить счетчик одной модели
curl "http://localhost:3001/api/message-count?modelName=L3-8B-Stheno"

# Обновить счетчик
curl -X POST http://localhost:3001/api/message-count \
  -H "Content-Type: application/json" \
  -d '{"modelName": "L3-8B-Stheno", "count": 15}'

# Получить список инструментов
curl http://localhost:3001/api/tools

# Отправить сообщение в чат
curl -X POST http://localhost:3001/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Сколько у меня сообщений?"}'
```

---

## 📋 Требования

- Node.js >= 18.0.0
- npm или yarn
- YandexGPT API ключи

## 📦 Зависимости

- `express` - Web framework
- `cors` - CORS middleware для кросс-доменных запросов
- `dotenv` - Загрузка переменных окружения из `.env` файла

---

## ⚠️ Важно

- **Не коммитьте файл `.env` с реальными ключами в git!**
- Файл `.env` уже добавлен в `.gitignore`
- Используйте `.env.example` как шаблон для настройки

---

## 🔒 Безопасность

- API ключи YandexGPT хранятся только в переменных окружения
- Нет хардкода секретов в коде
- Сервер принимает запросы только от разрешенных источников (настроено через CORS)
