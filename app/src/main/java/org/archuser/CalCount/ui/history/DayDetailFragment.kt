package org.archuser.CalCount.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class DayDetailFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var latestState = AppUiState()

    private lateinit var day: LocalDate
    private val titleFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rawDate = requireArguments().getString(HistoryFragment.ARG_DATE_ISO).orEmpty()
        day = LocalDate.parse(rawDate)
    }

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
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            latestState = state
            render(state)
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = day.format(titleFormatter)
    }

    private fun render(state: AppUiState) {
        val zoneId = ZoneId.systemDefault()
        val dayNutrition = state.nutritionForDate(day, zoneId)
        val remainingCalories = state.goals.dailyCalories - dayNutrition.calories
        val dayLogs = state.logsForDate(day, zoneId)

        binding.todayDate.text = day.format(titleFormatter)

        binding.caloriesConsumedValue.text = UiFormatters.calories(dayNutrition.calories)
        binding.caloriesGoalValue.text = UiFormatters.exactCalories(state.goals.dailyCalories)
        binding.caloriesRemainingValue.text = if (remainingCalories >= 0.0) {
            "${UiFormatters.calories(remainingCalories)} remaining"
        } else {
            "${UiFormatters.calories(abs(remainingCalories))} over"
        }

        renderNutritionSummary(
            summaryLines = UiFormatters.dashboardSummaryLines(dayNutrition, state.goals)
        )

        binding.todayEmptyState.text = "No food logged for this day."
        binding.todayEmptyState.isVisible = dayLogs.isEmpty()

        val mealSections = state.mealsForDate(day, zoneId)
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
