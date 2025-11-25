#!/bin/bash
# =============================================================================
# Toggle RAG Mode - Переключение режима RAG
# =============================================================================
# Использование:
#   ./toggle-rag.sh on   - Включить RAG режим
#   ./toggle-rag.sh off  - Выключить RAG режим
#   ./toggle-rag.sh      - Показать текущий режим
# =============================================================================

PORT=3001
HOST="http://localhost:$PORT"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

if [ "$1" == "on" ]; then
    echo -e "${BLUE}🔍 Включаю RAG режим (векторный поиск по курсу)...${NC}"
    
    response=$(curl -s -X POST "$HOST/api/rag-mode" \
        -H "Content-Type: application/json" \
        -d '{"useRAG": true}')
    
    if echo "$response" | grep -q '"success":true'; then
        echo -e "${GREEN}✅ RAG режим включен!${NC}"
        echo -e "${YELLOW}Теперь все запросы будут обрабатываться через векторный поиск по курсу${NC}"
    else
        echo -e "${RED}❌ Ошибка: $response${NC}"
        exit 1
    fi

elif [ "$1" == "off" ]; then
    echo -e "${BLUE}💬 Выключаю RAG режим (прямой запрос к LLM)...${NC}"
    
    response=$(curl -s -X POST "$HOST/api/rag-mode" \
        -H "Content-Type: application/json" \
        -d '{"useRAG": false}')
    
    if echo "$response" | grep -q '"success":true'; then
        echo -e "${GREEN}✅ RAG режим выключен!${NC}"
        echo -e "${YELLOW}Теперь запросы идут напрямую к LLM с инструментами${NC}"
    else
        echo -e "${RED}❌ Ошибка: $response${NC}"
        exit 1
    fi

else
    echo -e "${BLUE}📊 Текущий режим RAG:${NC}"
    
    response=$(curl -s -X GET "$HOST/api/rag-mode")
    
    if echo "$response" | grep -q '"success":true'; then
        enabled=$(echo "$response" | grep -o '"enabled":[^,}]*' | cut -d':' -f2)
        mode=$(echo "$response" | grep -o '"mode":"[^"]*"' | cut -d'"' -f4)
        description=$(echo "$response" | grep -o '"description":"[^"]*"' | cut -d'"' -f4)
        
        if [ "$enabled" == "true" ]; then
            echo -e "${GREEN}✅ RAG режим ВКЛЮЧЕН${NC}"
        else
            echo -e "${RED}❌ RAG режим ВЫКЛЮЧЕН${NC}"
        fi
        
        echo -e "${YELLOW}Режим: $mode${NC}"
        echo -e "${YELLOW}Описание: $description${NC}"
        echo ""
        echo -e "${BLUE}Использование:${NC}"
        echo -e "  ${YELLOW}./toggle-rag.sh on${NC}   - Включить RAG"
        echo -e "  ${YELLOW}./toggle-rag.sh off${NC}  - Выключить RAG"
    else
        echo -e "${RED}❌ Ошибка: $response${NC}"
        exit 1
    fi
fi

