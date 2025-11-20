# Настройка GitHub MCP Server

## Что было сделано

1. **Установлен Go** через Homebrew для сборки GitHub MCP Server
2. **Собран GitHub MCP Server** из официального репозитория `github/github-mcp-server`
3. **Создан локальный бинарник** `/Users/ruslanhafizov/Desktop/AIAdvent4Thread/mcp-proxy/github-mcp-server`
4. **Обновлен `github-mcp.js`** для использования локального бинарника вместо Docker/Bun

## Требования для работы

### GitHub Personal Access Token (PAT)

Для работы с GitHub MCP Server нужен **GitHub Personal Access Token** с правами:

- `repo` (полный доступ к репозиториям)
- `read:org` (чтение информации об организации)
- `read:user` (чтение профиля пользователя)

### Как создать токен:

1. Перейди на https://github.com/settings/tokens
2. Нажми **"Generate new token"** → **"Generate new token (classic)"**
3. Выбери необходимые права (см. выше)
4. Скопируй созданный токен (он показывается только один раз!)

## Как использовать

### 1. В Android приложении

1. Открой экран чата с сервером
2. Включи тумблер **"GitHub"** в AppBar
3. В появившемся диалоге введи свой **GitHub Personal Access Token**
4. Нажми **"Подтвердить"**

Теперь чат будет работать через GitHub MCP Server с доступом к GitHub API.

### 2. Проверка работы сервера

Запусти сервер:

```bash
cd /Users/ruslanhafizov/Desktop/AIAdvent4Thread/mcp-proxy
node localserver.js
```

Сервер запустится на `http://localhost:3001`

### 3. Тестирование GitHub MCP

После включения режима GitHub в приложении попробуй команды типа:

- "Покажи мои последние репозитории"
- "Найди issues в репозитории X"
- "Покажи комментарии к PR #123"

## Структура файлов

```
mcp-proxy/
├── localserver.js          # Основной сервер
├── mcpserver.js           # Локальный MCP сервер
├── github-mcp.js          # Клиент для GitHub MCP Server
├── github-mcp-server      # Собранный бинарник GitHub MCP Server
├── database.js            # SQLite база данных
└── app_data.db            # Файл базы данных
```

## Устранение неполадок

### Ошибка "404" при подключении к GitHub MCP

**Причина:** GitHub MCP Server не запустился или упал.

**Решение:**
1. Проверь, что бинарник существует: `ls -la mcp-proxy/github-mcp-server`
2. Проверь, что он исполняемый: `chmod +x mcp-proxy/github-mcp-server`
3. Попробуй запустить вручную:
   ```bash
   export GITHUB_PERSONAL_ACCESS_TOKEN=your_token
   ./mcp-proxy/github-mcp-server
   ```

### Ошибка авторизации

**Причина:** Неверный или истекший токен.

**Решение:**
1. Проверь, что токен еще действителен на https://github.com/settings/tokens
2. Создай новый токен с необходимыми правами
3. Введи новый токен в приложении

## Дополнительная информация

- [Официальный репозиторий GitHub MCP Server](https://github.com/github/github-mcp-server)
- [Документация MCP Protocol](https://github.com/modelcontextprotocol)
- [Руководство по GitHub Personal Access Tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)


