#!/bin/bash
# =============================================================================
# Test RAG Difference - Демонстрация разницы между RAG ON и RAG OFF
# =============================================================================

PORT=3001
HOST="http://localhost:$PORT"
QUERY="power safe mode"

# Цвета
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   🔍 СРАВНЕНИЕ RAG ON vs RAG OFF                            ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Запрос: \"${QUERY}\"${NC}"
echo ""

# Проверка доступности сервера
if ! curl -s "$HOST/health" > /dev/null; then
    echo -e "${RED}❌ Сервер недоступен. Запустите: cd mcp-proxy && npm start${NC}"
    exit 1
fi

# ============================================================================
# ТЕСТ 1: RAG OFF (Прямой запрос к LLM)
# ============================================================================
echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
echo -e "${MAGENTA}  ТЕСТ 1: RAG OFF (Прямой запрос к LLM)${NC}"
echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
echo ""

# Выключаем RAG
echo -e "${YELLOW}1. Выключаю RAG режим...${NC}"
curl -s -X POST "$HOST/api/rag-mode" \
    -H "Content-Type: application/json" \
    -d '{"useRAG": false}' > /dev/null
echo -e "${GREEN}✅ RAG выключен${NC}"
echo ""

# Отправляем запрос
echo -e "${YELLOW}2. Отправляю запрос: \"${QUERY}\"${NC}"
response_off=$(curl -s -X POST "$HOST/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"${QUERY}\", \"history\": []}")

message_off=$(echo "$response_off" | grep -o '"message":"[^"]*"' | cut -d'"' -f4 | sed 's/\\n/\n/g')
tool_used_off=$(echo "$response_off" | grep -o '"toolUsed":"[^"]*"' | cut -d'"' -f4)

echo ""
echo -e "${CYAN}💬 Ответ LLM (БЕЗ индексации):${NC}"
echo -e "${CYAN}────────────────────────────────────────────────────────────${NC}"
echo "$message_off"
echo -e "${CYAN}────────────────────────────────────────────────────────────${NC}"
echo -e "${YELLOW}Инструменты: ${tool_used_off:-"Нет"}${NC}"
echo ""

# Ждем немного
sleep 2

# ============================================================================
# ТЕСТ 2: RAG ON (Векторный поиск по курсу)
# ============================================================================
echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
echo -e "${MAGENTA}  ТЕСТ 2: RAG ON (Векторный поиск по курсу)${NC}"
echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
echo ""

# Включаем RAG
echo -e "${YELLOW}1. Включаю RAG режим...${NC}"
curl -s -X POST "$HOST/api/rag-mode" \
    -H "Content-Type: application/json" \
    -d '{"useRAG": true}' > /dev/null
echo -e "${GREEN}✅ RAG включен${NC}"
echo ""

# Отправляем тот же запрос
echo -e "${YELLOW}2. Отправляю тот же запрос: \"${QUERY}\"${NC}"
response_on=$(curl -s -X POST "$HOST/api/chat" \
    -H "Content-Type: application/json" \
    -d "{\"message\": \"${QUERY}\", \"history\": []}")

message_on=$(echo "$response_on" | grep -o '"message":"[^"]*"' | cut -d'"' -f4 | sed 's/\\n/\n/g')
tool_used_on=$(echo "$response_on" | grep -o '"toolUsed":"[^"]*"' | cut -d'"' -f4)

echo ""
echo -e "${GREEN}🔍 Ответ LLM (С векторным поиском по курсу):${NC}"
echo -e "${GREEN}────────────────────────────────────────────────────────────${NC}"
echo "$message_on"
echo -e "${GREEN}────────────────────────────────────────────────────────────${NC}"
echo -e "${YELLOW}Инструменты: ${tool_used_on}${NC}"
echo ""

# ============================================================================
# СРАВНЕНИЕ
# ============================================================================
echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   📊 СРАВНЕНИЕ РЕЗУЛЬТАТОВ                                   ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}❌ RAG OFF:${NC} Общий ответ (может быть не про Android Studio)"
echo -e "${GREEN}✅ RAG ON:${NC} Ответ основан на курсе Android Studio"
echo ""
echo -e "${YELLOW}💡 Вывод:${NC}"
echo -e "   - CLI скрипт ${MAGENTA}search-cli.js${NC} ВСЕГДА использует RAG"
echo -e "   - Сервер ${MAGENTA}/api/chat${NC} учитывает настройку rag on/off"
echo -e "   - Для тестирования режимов используйте ${MAGENTA}этот скрипт${NC}"
echo ""

# Выключаем RAG обратно
curl -s -X POST "$HOST/api/rag-mode" \
    -H "Content-Type: application/json" \
    -d '{"useRAG": false}' > /dev/null

echo -e "${GREEN}✅ Тест завершен (RAG возвращен в режим OFF)${NC}"
echo ""

