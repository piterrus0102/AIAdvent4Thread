package ru.piterrus.aiadvent4thread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Передаем значения из BuildConfig как default values
            App(
                defaultApiKey = BuildConfig.YANDEX_API_KEY,
                defaultFolderId = BuildConfig.YANDEX_FOLDER_ID
            )
        }
    }
}

