package ru.piterrus.aiadvent4thread

/**
 * Модель эксперта для дискуссии
 */
data class ExpertRole(
    val name: String,
    val description: String,
    val answer: String = "",
    val isLoading: Boolean = false
)

/**
 * Состояние экрана дискуссии для сохранения при навигации
 */
data class DiscussionState(
    val topic: String = "",
    val roles: List<ExpertRole> = emptyList(),
    val summary: String = "",
    val scrollPosition: Int = 0
)

/**
 * Парсер ответов для экспертной дискуссии
 */
object DiscussionParser {
    
    /**
     * Парсит текст ответа от координатора и извлекает роли экспертов
     * 
     * Ожидаемый формат:
     * РОЛЬ1: [название]
     * ОПИСАНИЕ1: [описание]
     * РОЛЬ2: [название]
     * ОПИСАНИЕ2: [описание]
     * РОЛЬ3: [название]
     * ОПИСАНИЕ3: [описание]
     */
    fun parseRoles(text: String): List<ExpertRole> {
        val roles = mutableListOf<ExpertRole>()
        
        try {
            val lines = text.lines().map { it.trim() }
            var currentRole: String? = null
            var currentDescription: String?
            
            for (line in lines) {
                when {
                    line.startsWith("РОЛЬ1:") -> {
                        currentRole = line.substringAfter("РОЛЬ1:").trim()
                    }
                    line.startsWith("ОПИСАНИЕ1:") -> {
                        currentDescription = line.substringAfter("ОПИСАНИЕ1:").trim()
                        val role = currentRole
                        val desc = currentDescription
                        if (role != null && role.isNotEmpty() && desc.isNotEmpty()) {
                            roles.add(ExpertRole(role, desc))
                        }
                        currentRole = null
                    }
                    line.startsWith("РОЛЬ2:") -> {
                        currentRole = line.substringAfter("РОЛЬ2:").trim()
                    }
                    line.startsWith("ОПИСАНИЕ2:") -> {
                        currentDescription = line.substringAfter("ОПИСАНИЕ2:").trim()
                        val role = currentRole
                        val desc = currentDescription
                        if (role != null && role.isNotEmpty() && desc.isNotEmpty()) {
                            roles.add(ExpertRole(role, desc))
                        }
                        currentRole = null
                    }
                    line.startsWith("РОЛЬ3:") -> {
                        currentRole = line.substringAfter("РОЛЬ3:").trim()
                    }
                    line.startsWith("ОПИСАНИЕ3:") -> {
                        currentDescription = line.substringAfter("ОПИСАНИЕ3:").trim()
                        val role = currentRole
                        val desc = currentDescription
                        if (role != null && role.isNotEmpty() && desc.isNotEmpty()) {
                            roles.add(ExpertRole(role, desc))
                        }
                        currentRole = null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return roles
    }
}

