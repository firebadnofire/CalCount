package org.archuser.CalCount.data.model

data class AppState(
    val foods: List<Food> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val goals: Goals = Goals()
)
