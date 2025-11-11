package ru.piterrus.aiadvent4thread.presentation.temperature

sealed interface TemperatureDetailScreenCommand {
    object NavigateBack : TemperatureDetailScreenCommand
}

