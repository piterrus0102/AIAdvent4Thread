// =============================================================================
// WeatherMCPServer - MCP сервер для работы с погодой (Open-Meteo API)
// =============================================================================
// Предоставляет инструменты для получения данных о погоде
// =============================================================================
class WeatherMCPServer {
    constructor() {
        // Инструменты для работы с погодой
        this.tools = [
            {
                name: 'get_current_weather',
                description: 'Получить текущую погоду для указанного места. ОБЯЗАТЕЛЬНО нужно передать название города. Возвращает температуру, скорость ветра, влажность, погодные условия и другие метеоданные.',
                inputSchema: {
                    type: 'object',
                    properties: {
                        city: {
                            type: 'string',
                            description: 'Название города (ОБЯЗАТЕЛЬНЫЙ параметр). Любой город мира. Примеры: "Москва", "Санкт-Петербург", "London", "New York". Если пользователь не указал город - СПРОСИ его!'
                        }
                    },
                    required: ['city']
                }
            },
            {
                name: 'get_weather_forecast',
                description: 'Получить прогноз погоды на несколько дней для указанного места. ОБЯЗАТЕЛЬНО нужно передать название города. Возвращает прогноз температуры, осадков и условий на каждый день.',
                inputSchema: {
                    type: 'object',
                    properties: {
                        city: {
                            type: 'string',
                            description: 'Название города (ОБЯЗАТЕЛЬНЫЙ параметр). Любой город мира. Примеры: "Москва", "Санкт-Петербург", "London". Если пользователь не указал город - СПРОСИ его!'
                        },
                        days: {
                            type: 'number',
                            description: 'Количество дней для прогноза (1-7, по умолчанию 3)',
                            default: 3
                        }
                    },
                    required: ['city']
                }
            }
        ];
    }

    // =========================================================================
    // PUBLIC API - MCP протокол
    // =========================================================================
    
    /**
     * Получить список доступных инструментов
     *
     * @returns {{tools: Array}} - Список инструментов
     */
    listTools() {
        console.log('[Weather-MCP-Server] Запрошен список инструментов');
        return {
            tools: this.tools
        };
    }

    /**
     * Вызвать инструмент
     *
     * @param {string} toolName - Название инструмента
     * @param {object} args - Аргументы
     * @returns {Promise<{content: Array}>} - Результат выполнения
     */
    async callTool(toolName, args) {
        console.log(`[Weather-MCP-Server] Вызов инструмента: ${toolName}`, args);
        
        switch (toolName) {
            case 'get_current_weather':
                return await this.getCurrentWeather(args);
            case 'get_weather_forecast':
                return await this.getWeatherForecast(args);
            default:
                throw new Error(`Unknown tool: ${toolName}`);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Получить координаты для города через Geocoding API Open-Meteo
     */
    async geocodeCity(city) {
        try {
            const geocodingUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(city)}&count=1&language=ru&format=json`;
            console.log(`[Weather-MCP-Server] Geocoding запрос: ${geocodingUrl}`);
            
            const response = await fetch(geocodingUrl);
            
            if (!response.ok) {
                throw new Error(`Geocoding API error: ${response.status}`);
            }
            
            const data = await response.json();
            
            if (!data.results || data.results.length === 0) {
                return null;
            }
            
            const result = data.results[0];
            return {
                latitude: result.latitude,
                longitude: result.longitude,
                name: result.name,
                country: result.country
            };
        } catch (error) {
            console.error('[Weather-MCP-Server] Ошибка geocoding:', error);
            return null;
        }
    }

    /**
     * Преобразовать код погоды в описание (WMO Weather interpretation codes)
     */
    getWeatherDescription(weatherCode) {
        const weatherDescriptions = {
            0: 'Ясно',
            1: 'В основном ясно',
            2: 'Переменная облачность',
            3: 'Облачно',
            45: 'Туман',
            48: 'Изморозь',
            51: 'Легкая морось',
            53: 'Морось',
            55: 'Сильная морось',
            61: 'Легкий дождь',
            63: 'Дождь',
            65: 'Сильный дождь',
            71: 'Легкий снег',
            73: 'Снег',
            75: 'Сильный снег',
            77: 'Снежная крупа',
            80: 'Ливни',
            81: 'Сильные ливни',
            82: 'Очень сильные ливни',
            85: 'Снегопад',
            86: 'Сильный снегопад',
            95: 'Гроза',
            96: 'Гроза с градом',
            99: 'Гроза с сильным градом'
        };
        
        return weatherDescriptions[weatherCode] || 'Неизвестно';
    }

    /**
     * Выполнить запрос к Open-Meteo API
     */
    async fetchWeatherData(latitude, longitude, params = {}) {
        try {
            const baseUrl = 'https://api.open-meteo.com/v1/forecast';
            const queryParams = new URLSearchParams({
                latitude: latitude.toString(),
                longitude: longitude.toString(),
                timezone: 'Europe/Moscow',
                ...params
            });
            
            const url = `${baseUrl}?${queryParams.toString()}`;
            console.log(`[Weather-MCP-Server] Запрос к Open-Meteo: ${url}`);
            
            const response = await fetch(url);
            
            if (!response.ok) {
                throw new Error(`Open-Meteo API error: ${response.status} ${response.statusText}`);
            }
            
            const data = await response.json();
            return data;
            
        } catch (error) {
            console.error('[Weather-MCP-Server] Ошибка запроса к Open-Meteo:', error);
            throw error;
        }
    }

    // =========================================================================
    // TOOLS IMPLEMENTATION - Реализация инструментов
    // =========================================================================
    
    /**
     * Инструмент: получить текущую погоду
     */
    async getCurrentWeather(args) {
        try {
            const { city } = args;
            
            if (!city) {
                return {
                    content: [{
                        type: 'text',
                        text: 'Ошибка: параметр city обязателен!'
                    }]
                };
            }
            
            // Получаем координаты через Geocoding API
            const location = await this.geocodeCity(city);
            
            if (!location) {
                return {
                    content: [{
                        type: 'text',
                        text: `Город "${city}" не найден. Проверьте правильность написания.`
                    }]
                };
            }
            
            console.log(`[Weather-MCP-Server] Получение погоды для: ${location.name}, ${location.country} (${location.latitude}, ${location.longitude})`);
            
            // Запрашиваем текущую погоду
            const weatherData = await this.fetchWeatherData(location.latitude, location.longitude, {
                current: 'temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m'
            });
            
            const current = weatherData.current;
            const weatherDesc = this.getWeatherDescription(current.weather_code);
            
            const weatherInfo = `
Текущая погода в ${location.name}, ${location.country}:

🌡️ Температура: ${current.temperature_2m}°C (ощущается как ${current.apparent_temperature}°C)
☁️ Условия: ${weatherDesc}
💧 Влажность: ${current.relative_humidity_2m}%
💨 Ветер: ${current.wind_speed_10m} км/ч (направление ${current.wind_direction_10m}°)
🌧️ Осадки: ${current.precipitation} мм

Данные актуальны на: ${current.time}
            `.trim();
            
            console.log(`[Weather-MCP-Server] Погода получена: ${current.temperature_2m}°C, ${weatherDesc}`);
            
            return {
                content: [
                    {
                        type: 'text',
                        text: weatherInfo
                    }
                ]
            };
            
        } catch (error) {
            console.error('[Weather-MCP-Server] Ошибка получения погоды:', error);
            return {
                content: [
                    {
                        type: 'text',
                        text: `Ошибка при получении погоды: ${error.message}`
                    }
                ]
            };
        }
    }

    /**
     * Инструмент: получить прогноз погоды
     */
    async getWeatherForecast(args) {
        try {
            const { city, days = 3 } = args;
            
            if (!city) {
                return {
                    content: [{
                        type: 'text',
                        text: 'Ошибка: параметр city обязателен!'
                    }]
                };
            }
            
            // Ограничиваем количество дней
            const forecastDays = Math.min(Math.max(days, 1), 7);
            
            // Получаем координаты через Geocoding API
            const location = await this.geocodeCity(city);
            
            if (!location) {
                return {
                    content: [{
                        type: 'text',
                        text: `Город "${city}" не найден. Проверьте правильность написания.`
                    }]
                };
            }
            
            console.log(`[Weather-MCP-Server] Получение прогноза для: ${location.name}, ${location.country} на ${forecastDays} дней`);
            
            // Запрашиваем прогноз
            const weatherData = await this.fetchWeatherData(location.latitude, location.longitude, {
                daily: 'temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code,wind_speed_10m_max',
                forecast_days: forecastDays.toString()
            });
            
            const daily = weatherData.daily;
            
            let forecastText = `Прогноз погоды для ${location.name}, ${location.country} на ${forecastDays} ${forecastDays === 1 ? 'день' : 'дня/дней'}:\n\n`;
            
            for (let i = 0; i < daily.time.length; i++) {
                const date = new Date(daily.time[i]);
                const dateStr = date.toLocaleDateString('ru-RU', { weekday: 'short', day: 'numeric', month: 'short' });
                const weatherDesc = this.getWeatherDescription(daily.weather_code[i]);
                
                forecastText += `📅 ${dateStr}:\n`;
                forecastText += `   🌡️ ${daily.temperature_2m_min[i]}°C ... ${daily.temperature_2m_max[i]}°C\n`;
                forecastText += `   ☁️ ${weatherDesc}\n`;
                forecastText += `   🌧️ Осадки: ${daily.precipitation_sum[i]} мм\n`;
                forecastText += `   💨 Ветер: до ${daily.wind_speed_10m_max[i]} км/ч\n\n`;
            }
            
            console.log(`[Weather-MCP-Server] Прогноз получен на ${forecastDays} дней`);
            
            return {
                content: [
                    {
                        type: 'text',
                        text: forecastText.trim()
                    }
                ]
            };
            
        } catch (error) {
            console.error('[Weather-MCP-Server] Ошибка получения прогноза:', error);
            return {
                content: [
                    {
                        type: 'text',
                        text: `Ошибка при получении прогноза: ${error.message}`
                    }
                ]
            };
        }
    }
}

// =============================================================================
// EXPORT
// =============================================================================
export default WeatherMCPServer;

