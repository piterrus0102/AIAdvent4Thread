# Настройка и запуск MCP функциональности

Это руководство поможет вам настроить и запустить функциональность подключения к MCP (Model Context Protocol) в Android приложении.

## Что было реализовано

✅ **Добавлен новый пункт "MCP Connection" на стартовый экран**

✅ **Создан отдельный экран для MCP с:**
- Полем ввода URL прокси-сервера
- Кнопкой подключения/отключения
- Отображением статуса подключения
- Списком доступных инструментов после подключения

✅ **Создан MCP клиент для HTTP подключения**

✅ **Создан прокси-сервер на Node.js**
- Подключается к реальным MCP серверам через stdio
- Предоставляет REST API для Android приложения

## Архитектура решения

```
┌─────────────────┐         HTTP/REST         ┌──────────────┐        stdio       ┌──────────────┐
│  Android App    │ ◄─────────────────────── │  Node.js     │ ◄────────────────  │  MCP Server  │
│  (MCP Screen)   │                           │  Proxy       │                    │  (Real)      │
└─────────────────┘                           └──────────────┘                    └──────────────┘
```

Прокси-сервер необходим, потому что:
1. MCP серверы работают через stdio (стандартный ввод/вывод)
2. Android не может напрямую работать со stdio процессами
3. Прокси переводит stdio в HTTP REST API

## Шаг 1: Установка и запуск прокси-сервера

### 1.1. Убедитесь, что установлен Node.js

```bash
node --version  # Должна быть версия >= 18.0.0
```

Если Node.js не установлен, скачайте с https://nodejs.org/

### 1.2. Установите зависимости

```bash
cd mcp-proxy
npm install
```

### 1.3. Запустите прокси-сервер

```bash
npm start
```

Вы должны увидеть:
```
[MCP Proxy] Server is running on http://0.0.0.0:3000
[MCP Proxy] Available endpoints:
  POST   /connect       - Connect to MCP server
  GET    /tools         - Get available tools
  POST   /tools/:name   - Call a tool
  POST   /disconnect    - Disconnect from MCP server
  GET    /health        - Health check
```

**Важно:** Оставьте прокси-сервер запущенным во время тестирования Android приложения!

## Шаг 2: Запуск Android приложения

### 2.1. Соберите и запустите приложение

```bash
./gradlew installDebug
```

Или используйте Android Studio: Run → Run 'app'

### 2.2. Тестирование на эмуляторе

Если вы используете Android эмулятор:
- URL по умолчанию: `http://10.0.2.2:3000` ✅ (уже настроен в приложении)
- `10.0.2.2` - это специальный адрес для доступа к localhost хост-машины

### 2.3. Тестирование на реальном устройстве

Если вы используете реальное Android устройство:

1. Подключите устройство и компьютер к одной Wi-Fi сети
2. Узнайте IP адрес вашего компьютера:
   - **macOS/Linux:** `ifconfig | grep "inet "` или `ip addr show`
   - **Windows:** `ipconfig`
3. В приложении измените URL на: `http://YOUR_COMPUTER_IP:3000`
   Например: `http://192.168.1.100:3000`

## Шаг 3: Использование в приложении

### 3.1. Откройте MCP экран

1. Запустите приложение
2. На стартовом экране нажмите на карточку **"🔌 MCP Connection"**

### 3.2. Подключитесь к MCP серверу

1. Проверьте URL прокси-сервера (по умолчанию `http://10.0.2.2:3000`)
2. Нажмите кнопку **"Подключиться"**
3. Дождитесь подключения (статус изменится на "Подключено")

### 3.3. Просмотр инструментов

После успешного подключения вы увидите список доступных инструментов MCP сервера.

Пример инструментов от filesystem MCP сервера:
- `read_file` - Чтение файла
- `write_file` - Запись в файл
- `list_directory` - Список файлов в директории
- `create_directory` - Создание директории
- `move_file` - Перемещение файла

Каждый инструмент показывает:
- **Название**
- **Описание** - что делает инструмент
- **Schema** - какие параметры принимает

## Доступные MCP серверы

По умолчанию прокси настроен на подключение к следующим MCP серверам:

### 1. Filesystem Server (по умолчанию)
- **Название:** `filesystem`
- **Описание:** Доступ к файловой системе
- **Инструменты:** read_file, write_file, list_directory, create_directory, move_file, search_files

### 2. Memory Server
- **Название:** `memory`
- **Описание:** Сервер для хранения данных в памяти
- **Для подключения:** Измените код в `server.js` строку `serverName = 'filesystem'` на `'memory'`

### 3. Everything Server (Windows)
- **Название:** `everything`
- **Описание:** Быстрый поиск файлов на Windows
- **Для подключения:** Измените код в `server.js` строку `serverName = 'filesystem'` на `'everything'`

## Добавление собственного MCP сервера

Вы можете подключить любой MCP сервер, изменив файл `mcp-proxy/server.js`:

```javascript
case 'your-custom-server':
    command = 'node';
    args = ['path/to/your-mcp-server.js', '--arg1', 'value1'];
    break;
```

## Troubleshooting (Решение проблем)

### Ошибка "Не удалось подключиться"

**Причины:**
1. Прокси-сервер не запущен
2. Неправильный URL
3. Firewall блокирует соединение

**Решение:**
1. Проверьте, что прокси-сервер запущен: `curl http://localhost:3000/health`
2. Проверьте URL в приложении
3. Отключите firewall или добавьте исключение для порта 3000

### Ошибка "Не удалось получить список инструментов"

**Причины:**
1. MCP сервер не смог запуститься
2. Ошибка в MCP сервере

**Решение:**
1. Проверьте логи прокси-сервера в терминале
2. Убедитесь, что MCP сервер установлен: `npx -y @modelcontextprotocol/server-filesystem`

### Android эмулятор не может подключиться

**Решение:**
- Используйте `10.0.2.2` вместо `localhost`
- Убедитесь, что прокси запущен на хост-машине

### Реальное устройство не может подключиться

**Решение:**
- Убедитесь, что устройство и компьютер в одной сети
- Используйте правильный IP адрес компьютера
- Проверьте, что прокси слушает на `0.0.0.0`, а не только на `localhost`

## API прокси-сервера

### POST /connect
Подключение к MCP серверу

```bash
curl -X POST http://localhost:3000/connect \
  -H "Content-Type: application/json" \
  -d '{"serverName": "filesystem"}'
```

### GET /tools
Получение списка инструментов

```bash
curl http://localhost:3000/tools
```

### POST /tools/:toolName
Вызов инструмента

```bash
curl -X POST http://localhost:3000/tools/list_directory \
  -H "Content-Type: application/json" \
  -d '{"arguments": {"path": "."}}'
```

### POST /disconnect
Отключение от MCP сервера

```bash
curl -X POST http://localhost:3000/disconnect
```

### GET /health
Проверка статуса прокси

```bash
curl http://localhost:3000/health
```

## Структура кода в Android приложении

### Слой Presentation
- `presentation/mcp/McpScreen.kt` - UI экрана
- `presentation/mcp/McpScreenState.kt` - Состояние экрана
- `presentation/mcp/McpScreenIntent.kt` - Действия пользователя
- `presentation/mcp/McpScreenCommand.kt` - Команды навигации
- `presentation/mcp/McpScreenViewModel.kt` - Бизнес-логика (Android)

### Слой Data
- `data/client/McpClient.kt` - HTTP клиент для прокси
- `data/model/McpModels.kt` - Модели данных

### Навигация
- `AndroidApp.kt` - Добавлен Screen.Mcp
- `StartScreen.kt` - Добавлена карточка MCP

### DI (Dependency Injection)
- `di/AppModule.kt` - Зарегистрированы McpClient и McpScreenViewModel

## Следующие шаги

Сейчас реализован просмотр списка инструментов. Вы можете расширить функциональность:

1. **Добавить вызов инструментов** - создать UI для ввода параметров и вызова инструментов
2. **Добавить историю вызовов** - сохранять результаты вызовов инструментов
3. **Добавить избранные инструменты** - помечать часто используемые инструменты
4. **Добавить выбор MCP сервера** - позволить выбирать между filesystem, memory, etc.

## Полезные ссылки

- [MCP Official Documentation](https://modelcontextprotocol.io/)
- [MCP SDK GitHub](https://github.com/modelcontextprotocol/sdk)
- [MCP Servers List](https://github.com/modelcontextprotocol/servers)

