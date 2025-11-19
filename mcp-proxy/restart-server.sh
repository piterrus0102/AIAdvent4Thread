#!/bin/bash

echo "🛑 Останавливаем старый сервер..."
pkill -f "node.*index.js"
pkill -f "github-mcp-server"

echo "⏳ Ждем 2 секунды..."
sleep 2

echo "🚀 Запускаем новый сервер..."
cd "$(dirname "$0")"
node index.js

