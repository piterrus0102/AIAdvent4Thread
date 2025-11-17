# MCP Proxy Server

HTTP прокси-сервер для подключения Android приложения к MCP (Model Context Protocol) серверам.

## Зачем нужен прокси?

MCP серверы используют stdio (стандартный ввод/вывод) для коммуникации, что невозможно напрямую использовать в Android приложении. Этот прокси-сервер:

1. Подключается к реальному MCP серверу через stdio
2. Предоставляет REST API для Android приложения
3. Транслирует запросы между Android и MCP сервером

## Установка

```bash
cd mcp-proxy
npm install
```

## Запуск

```bash
npm start
```

Сервер запустится на `http://localhost:3000`

## Для Android эмулятора

Android эмулятор не может подключиться к `localhost` на хост-машине напрямую. Используйте специальный IP:

- **`10.0.2.2`** - для Android эмулятора (это адрес хост-машины)
- **`localhost`** или **`127.0.0.1`** - для реального устройства в одной сети

В коде Android приложения уже настроен адрес `http://10.0.2.2:3000` для эмулятора.

## API Endpoints

### 1. Подключение к MCP серверу

```http
POST /connect
Content-Type: application/json

{
  "serverName": "filesystem"
}
```

Доступные серверы:
- `filesystem` - Доступ к файловой системе
- `memory` - Сервер памяти
- `everything` - Поиск файлов (для Windows)

Ответ:
```json
{
  "success": true,
  "message": "Connected to filesystem MCP server"
}
```

### 2. Получение списка инструментов

```http
GET /tools
```

Ответ:
```json
{
  "tools": [
    {
      "name": "read_file",
      "description": "Read the complete contents of a file",
      "inputSchema": {
        "type": "object",
        "properties": {
          "path": {
            "type": "string",
            "description": "Path to the file to read"
          }
        },
        "required": ["path"]
      }
    }
  ]
}
```

### 3. Вызов инструмента

```http
POST /tools/read_file
Content-Type: application/json

{
  "arguments": {
    "path": "/path/to/file.txt"
  }
}
```

### 4. Отключение

```http
POST /disconnect
```

### 5. Health Check

```http
GET /health
```

## Примеры использования

### С помощью curl

```bash
# 1. Подключиться к filesystem MCP серверу
curl -X POST http://localhost:3000/connect \
  -H "Content-Type: application/json" \
  -d '{"serverName": "filesystem"}'

# 2. Получить список инструментов
curl http://localhost:3000/tools

# 3. Вызвать инструмент
curl -X POST http://localhost:3000/tools/list_directory \
  -H "Content-Type: application/json" \
  -d '{"arguments": {"path": "."}}'

# 4. Отключиться
curl -X POST http://localhost:3000/disconnect
```

### Из Android приложения

Android приложение уже настроено для работы с прокси:

1. Запустите прокси-сервер: `npm start`
2. Запустите Android приложение
3. На главном экране нажмите "MCP Connection"
4. Нажмите кнопку "Подключиться"
5. После успешного подключения увидите список доступных инструментов

## Поддерживаемые MCP серверы

Вы можете добавить поддержку любого MCP сервера, изменив switch в `server.js`:

```javascript
case 'your-server':
    command = 'node';
    args = ['path/to/your/mcp-server.js'];
    break;
```

## Логи

Сервер выводит подробные логи всех операций:

```
[MCP Proxy] Server is running on http://0.0.0.0:3000
[MCP Proxy] Connecting to MCP server: filesystem
[MCP Proxy] Successfully connected to MCP server
[MCP Proxy] Requesting tools list...
[MCP Proxy] Received 5 tools
```

## Требования

- Node.js >= 18.0.0
- npm или yarn

## Зависимости

- `@modelcontextprotocol/sdk` - Официальный SDK для MCP
- `express` - Web framework
- `cors` - CORS middleware для кросс-доменных запросов

