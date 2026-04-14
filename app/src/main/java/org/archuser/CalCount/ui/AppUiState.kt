package org.archuser.CalCount.ui

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.data.model.NutritionSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AppUiState(
    val foods: List<Food> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val goals: Goals = Goals(),
    val editingFood: Food? = null
) {
    fun todayLogs(zoneId: ZoneId = ZoneId.systemDefault()): List<LogEntry> {
        return logsForDate(LocalDate.now(zoneId), zoneId)
    }

    fun logsForDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<LogEntry> {
        return logs
            .filter { Instant.ofEpochMilli(it.loggedAtEpochMillis).atZone(zoneId).toLocalDate() == date }
            .sortedByDescending(LogEntry::loggedAtEpochMillis)
    }

    fun todayNutrition(zoneId: ZoneId = ZoneId.systemDefault()): NutritionSnapshot {
        return nutritionForDate(LocalDate.now(zoneId), zoneId)
    }

    fun nutritionForDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): NutritionSnapshot {
        return logsForDate(date, zoneId)
            .fold(NutritionSnapshot.ZERO) { total, entry -> total + entry.calculatedNutrition }
    }

    fun remainingCalories(zoneId: ZoneId = ZoneId.systemDefault()): Double {
        return goals.dailyCalories - todayNutrition(zoneId).calories
    }

    fun mealsForToday(zoneId: ZoneId = ZoneId.systemDefault()): Map<MealType, List<LogEntry>> {
        return mealsForDate(LocalDate.now(zoneId), zoneId)
    }

    fun mealsForDate(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Map<MealType, List<LogEntry>> {
        val dateLogs = logsForDate(date, zoneId)
        return MealType.entries.associateWith { mealType ->
            dateLogs.filter { it.mealType == mealType }
        }
    }

    fun recentFoods(limit: Int = 5): List<Food> {
        val foodsById = foods.associateBy(Food::id)
        val recentIds = logs
            .sortedByDescending(LogEntry::loggedAtEpochMillis)
            .map(LogEntry::foodId)
            .distinct()

        return recentIds.mapNotNull(foodsById::get).take(limit)
    }
}
