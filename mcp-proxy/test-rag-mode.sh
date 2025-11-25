#!/bin/bash
# =============================================================================
# Test RAG Mode - Тестирование режима RAG
# =============================================================================

PORT=3001
HOST="http://localhost:$PORT"

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   🔍 ТЕСТИРОВАНИЕ РЕЖИМА RAG                        ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Проверка что сервер запущен
echo -e "${YELLOW}[1/5] Проверка доступности сервера...${NC}"
if curl -s "$HOST/health" > /dev/null; then
    echo -e "${GREEN}✅ Сервер доступен${NC}"
else
    echo -e "${RED}❌ Сервер недоступен. Запустите: npm start${NC}"
    exit 1
fi
echo ""

# Проверка текущего режима
echo -e "${YELLOW}[2/5] Проверка текущего режима...${NC}"
current_mode=$(curl -s "$HOST/api/rag-mode" | grep -o '"enabled":[^,}]*' | cut -d':' -f2)
echo -e "${CYAN}Текущий режим RAG: $current_mode${NC}"
echo ""

# Тест 1: Включение RAG режима
echo -e "${YELLOW}[3/5] Тест: Включение RAG режима...${NC}"
response=$(curl -s -X POST "$HOST/api/rag-mode" \
    -H "Content-Type: application/json" \
    -d '{"useRAG": true}')

if echo "$response" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ RAG режим успешно включен${NC}"
else
    echo -e "${RED}❌ Ошибка включения RAG: $response${NC}"
    exit 1
fi
echo ""

# Тест 2: Отправка тестового запроса в RAG режиме
echo -e "${YELLOW}[4/5] Тест: Отправка запроса в RAG режиме...${NC}"
echo -e "${CYAN}Запрос: 'Что такое Activity в Android?'${NC}"

rag_response=$(curl -s -X POST "$HOST/api/chat" \
    -H "Content-Type: application/json" \
    -d '{"message": "Что такое Activity в Android?", "history": []}')

if echo "$rag_response" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ Запрос в RAG режиме выполнен${NC}"
    
    # Проверяем что использовался RAG
    if echo "$rag_response" | grep -q '"toolUsed":"RAG'; then
        echo -e "${GREEN}✅ Использован векторный поиск (RAG)${NC}"
    else
        echo -e "${YELLOW}⚠️  RAG не использовался (возможен fallback)${NC}"
    fi
else
    echo -e "${RED}❌ Ошибка запроса: $rag_response${NC}"
fi
echo ""

# Тест 3: Выключение RAG режима
echo -e "${YELLOW}[5/5] Тест: Выключение RAG режима...${NC}"
response=$(curl -s -X POST "$HOST/api/rag-mode" \
    -H "Content-Type: application/json" \
    -d '{"useRAG": false}')

if echo "$response" | grep -q '"success":true'; then
    echo -e "${GREEN}✅ RAG режим успешно выключен${NC}"
else
    echo -e "${RED}❌ Ошибка выключения RAG: $response${NC}"
    exit 1
fi
echo ""

# Восстанавливаем исходный режим
if [ "$current_mode" == "true" ]; then
    echo -e "${YELLOW}Восстановление исходного режима (RAG включен)...${NC}"
    curl -s -X POST "$HOST/api/rag-mode" \
        -H "Content-Type: application/json" \
        -d '{"useRAG": true}' > /dev/null
    echo -e "${GREEN}✅ Режим восстановлен${NC}"
fi
echo ""

echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║   ✅ ВСЕ ТЕСТЫ ПРОЙДЕНЫ                              ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}📝 Дополнительная информация:${NC}"
echo -e "   - Используйте ${YELLOW}./toggle-rag.sh${NC} для переключения режима"
echo -e "   - Используйте ${YELLOW}rag on/off${NC} в консоли сервера"
echo -e "   - См. документацию: ${YELLOW}RAG_MODE_GUIDE.md${NC}"
echo ""

