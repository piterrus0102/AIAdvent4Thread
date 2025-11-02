# AIAdvent4Thread - Kotlin Multiplatform Project

Проект на Kotlin Multiplatform с поддержкой Android.

## Платформы

- **Android** - полностью поддерживается

## Структура проекта

```
app/
├── src/
│   ├── commonMain/       # Общий код (переиспользуемый)
│   │   └── kotlin/
│   │       └── ru/piterrus/aiadvent4thread/
│   │           └── App.kt              # Общий UI с кнопкой
│   └── androidMain/      # Android-специфичный код
│       ├── kotlin/
│       │   └── ru/piterrus/aiadvent4thread/
│       │       └── MainActivity.kt     # Точка входа для Android
│       ├── AndroidManifest.xml
│       └── res/          # Android ресурсы
```

## Возможности

Приложение демонстрирует простой UI с:
- Текстовым приветствием
- Счетчиком нажатий на кнопку
- Кнопкой "Нажми меня!"
- Кнопкой "Сбросить" (появляется после первого нажатия)

Весь UI код находится в `commonMain` и используется обеими платформами.

## Сборка проекта

```bash
# Собрать Debug APK
./gradlew assembleDebug

# Собрать Release APK
./gradlew assembleRelease

# Установить на устройство
./gradlew installDebug

# Полная сборка
./gradlew build
```

APK файл будет создан в: `app/build/outputs/apk/debug/app-debug.apk`

## Конфигурация

### Версии зависимостей

- Kotlin: 2.0.21
- Compose Multiplatform: 1.7.1
- Android Gradle Plugin: 8.12.3

### Минимальные требования

- **Android:** API 34 (Android 14)

## Технологии

- **Kotlin Multiplatform** - общий код для нескольких платформ
- **Compose Multiplatform** - декларативный UI фреймворк
- **Material 3** - дизайн система

## Примечания

- Проект использует архитектуру Kotlin Multiplatform с разделением на `commonMain` и `androidMain`
- Это позволяет легко добавить другие платформы в будущем (iOS, Desktop, Web)
- UI код в `commonMain` может быть переиспользован для других платформ

## Troubleshooting

### Проблемы с зависимостями

```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

### Проблемы со сборкой

```bash
# Очистить кэш и пересобрать
./gradlew clean
./gradlew assembleDebug --no-build-cache
```

