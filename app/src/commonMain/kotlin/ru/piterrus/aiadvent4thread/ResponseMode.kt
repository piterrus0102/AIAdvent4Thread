package ru.piterrus.aiadvent4thread

enum class ResponseMode(val value: Int) {
    DEFAULT(0),
    FIXED_RESPONSE_ENABLED(1),
    TASK(2),
    TEMPERATURE_COMPARISON(3)
    ;
    companion object {
        fun fromInt(value: Int): ResponseMode {
            return entries.find { it.value == value } ?: DEFAULT
        }
    }
}

// 3 комплимента женщине четко одним словом каждое, отвечай через запятую слова