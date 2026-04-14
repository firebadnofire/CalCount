package org.archuser.CalCount.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.databinding.FragmentTodayBinding
import org.archuser.CalCount.databinding.ItemLogEntryBinding
import org.archuser.CalCount.databinding.ItemSummaryLineBinding
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        binding.todayDate.text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d"))

        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun render(state: AppUiState) {
        val todayNutrition = state.todayNutrition()
        val remainingCalories = state.remainingCalories()
        val todayLogs = state.todayLogs()

        binding.caloriesConsumedValue.text = UiFormatters.calories(todayNutrition.calories)
        binding.caloriesGoalValue.text = UiFormatters.exactCalories(state.goals.dailyCalories)
        binding.caloriesRemainingValue.text = if (remainingCalories >= 0.0) {
            "${UiFormatters.calories(remainingCalories)} remaining"
        } else {
            "${UiFormatters.calories(abs(remainingCalories))} over"
        }

        renderNutritionSummary(
            summaryLines = UiFormatters.dashboardSummaryLines(todayNutrition, state.goals)
        )

        binding.todayEmptyState.isVisible = todayLogs.isEmpty()

        val mealSections = state.mealsForToday()
        renderMealSection(
            container = binding.breakfastEntries,
            emptyView = binding.breakfastEmpty,
            entries = mealSections[MealType.BREAKFAST].orEmpty(),
            state = state
        )
        renderMealSection(
            container = binding.lunchEntries,
            emptyView = binding.lunchEmpty,
            entries = mealSections[MealType.LUNCH].orEmpty(),
            state = state
        )
        renderMealSection(
            container = binding.dinnerEntries,
            emptyView = binding.dinnerEmpty,
            entries = mealSections[MealType.DINNER].orEmpty(),
            state = state
        )
        renderMealSection(
            container = binding.snackEntries,
            emptyView = binding.snackEmpty,
            entries = mealSections[MealType.SNACK].orEmpty(),
            state = state
        )
    }

    private fun renderNutritionSummary(summaryLines: List<String>) {
        binding.nutritionSummaryContainer.removeAllViews()
        summaryLines.forEachIndexed { index, summaryLine ->
            val rowBinding = ItemSummaryLineBinding.inflate(layoutInflater, binding.nutritionSummaryContainer, false)
            rowBinding.summaryText.text = summaryLine
            val layoutParams = rowBinding.root.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.topMargin = if (index == 0) 12.dp else 8.dp
            rowBinding.root.layoutParams = layoutParams
            binding.nutritionSummaryContainer.addView(rowBinding.root)
        }
    }

    private fun renderMealSection(
        container: ViewGroup,
        emptyView: View,
        entries: List<LogEntry>,
        state: AppUiState
    ) {
        container.removeAllViews()
        emptyView.isVisible = entries.isEmpty()
        if (entries.isEmpty()) {
            return
        }

        entries.forEach { entry ->
            val itemBinding = ItemLogEntryBinding.inflate(layoutInflater, container, false)
            itemBinding.foodName.text = entry.foodName
            itemBinding.foodAmount.text = UiFormatters.entryAmount(entry, state.goals.preferredUnit)
            itemBinding.foodCalories.text =
                UiFormatters.mainMacroValue(entry.calculatedNutrition, state.goals.mainMacro)
            itemBinding.foodMacros.text =
                UiFormatters.macroSummarySelectionLine(entry.calculatedNutrition, state.goals).orEmpty()
            itemBinding.foodMeta.text = "${entry.servingDescription} • ${UiFormatters.time(entry.loggedAtEpochMillis)}"
            container.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
