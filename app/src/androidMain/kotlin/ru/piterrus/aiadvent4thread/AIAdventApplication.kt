package ru.piterrus.aiadvent4thread

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import ru.piterrus.aiadvent4thread.di.appModule

/**
 * Application класс для инициализации Koin DI
 */
class AIAdventApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Инициализируем Koin
        startKoin {
            // Логирование Koin (только в debug режиме)
            androidLogger(Level.ERROR)
            
            // Android context для Koin
            androidContext(this@AIAdventApplication)
            
            // Загружаем модули
            modules(appModule)
        }
    }
}

