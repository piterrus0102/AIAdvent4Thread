#!/bin/bash

# Скрипт для тестирования MCP прокси-сервера

echo "=========================================="
echo "  MCP Proxy Server - Test Script"
echo "=========================================="
echo ""

PROXY_URL="http://localhost:3000"

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Функция для проверки, запущен ли сервер
check_server() {
    echo -e "${YELLOW}Проверка доступности сервера...${NC}"
    if curl -s "$PROXY_URL/health" > /dev/null; then
        echo -e "${GREEN}✓ Сервер доступен${NC}"
        return 0
    else
        echo -e "${RED}✗ Сервер недоступен. Запустите сервер командой: npm start${NC}"
        exit 1
    fi
}

# Тест 1: Health check
test_health() {
    echo ""
    echo "=========================================="
    echo "Тест 1: Health Check"
    echo "=========================================="
    response=$(curl -s "$PROXY_URL/health")
    echo "Response: $response"
    
    if echo "$response" | grep -q '"status":"ok"'; then
        echo -e "${GREEN}✓ Health check passed${NC}"
    else
        echo -e "${RED}✗ Health check failed${NC}"
    fi
}

# Тест 2: Подключение к MCP серверу
test_connect() {
    echo ""
    echo "=========================================="
    echo "Тест 2: Подключение к filesystem MCP сервера"
    echo "=========================================="
    response=$(curl -s -X POST "$PROXY_URL/connect" \
        -H "Content-Type: application/json" \
        -d '{"serverName": "filesystem"}')
    echo "Response: $response"
    
    if echo "$response" | grep -q '"success":true'; then
        echo -e "${GREEN}✓ Подключение успешно${NC}"
    else
        echo -e "${RED}✗ Ошибка подключения${NC}"
    fi
}

# Тест 3: Получение списка инструментов
test_tools() {
    echo ""
    echo "=========================================="
    echo "Тест 3: Получение списка инструментов"
    echo "=========================================="
    response=$(curl -s "$PROXY_URL/tools")
    echo "Response: $response"
    
    if echo "$response" | grep -q '"tools"'; then
        tool_count=$(echo "$response" | grep -o '"name"' | wc -l)
        echo -e "${GREEN}✓ Получено инструментов: $tool_count${NC}"
        
        # Выводим названия инструментов
        echo ""
        echo "Доступные инструменты:"
        echo "$response" | jq -r '.tools[] | "  - \(.name): \(.description)"' 2>/dev/null || echo "  (установите jq для красивого вывода: brew install jq)"
    else
        echo -e "${RED}✗ Не удалось получить список инструментов${NC}"
    fi
}

# Тест 4: Вызов инструмента (list_directory)
test_call_tool() {
    echo ""
    echo "=========================================="
    echo "Тест 4: Вызов инструмента list_directory"
    echo "=========================================="
    response=$(curl -s -X POST "$PROXY_URL/tools/list_directory" \
        -H "Content-Type: application/json" \
        -d '{"arguments": {"path": "."}}')
    echo "Response (первые 500 символов): ${response:0:500}..."
    
    if echo "$response" | grep -q '"success":true'; then
        echo -e "${GREEN}✓ Инструмент вызван успешно${NC}"
    else
        echo -e "${RED}✗ Ошибка вызова инструмента${NC}"
    fi
}

# Тест 5: Отключение
test_disconnect() {
    echo ""
    echo "=========================================="
    echo "Тест 5: Отключение от MCP сервера"
    echo "=========================================="
    response=$(curl -s -X POST "$PROXY_URL/disconnect")
    echo "Response: $response"
    
    if echo "$response" | grep -q '"success":true'; then
        echo -e "${GREEN}✓ Отключение успешно${NC}"
    else
        echo -e "${RED}✗ Ошибка отключения${NC}"
    fi
}

# Запуск всех тестов
main() {
    check_server
    test_health
    test_connect
    sleep 2  # Ждем, пока MCP сервер полностью запустится
    test_tools
    test_call_tool
    test_disconnect
    
    echo ""
    echo "=========================================="
    echo "  Все тесты завершены!"
    echo "=========================================="
}

main

