package org.archuser.CalCount.ui.library

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.domain.NutritionCalculator
import org.archuser.CalCount.databinding.DialogLogFoodBinding
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.NonFilteringArrayAdapter
import org.archuser.CalCount.ui.UiFormatters

class LogFoodDialogFragment : DialogFragment() {

    private var _binding: DialogLogFoodBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var currentInputMode: InputMode = InputMode.SERVINGS

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogLogFoodBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]

        val foodId = requireArguments().getString(ARG_FOOD_ID).orEmpty()
        val food = viewModel.getFood(foodId)

        if (food == null) {
            return MaterialAlertDialogBuilder(requireContext())
                .setMessage("That food is no longer available.")
                .setPositiveButton(android.R.string.ok, null)
                .create()
        }

        setupDialog(food)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.log_food_title, food.name))
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_log, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                submit(food)
            }
        }

        return dialog
    }

    private fun setupDialog(food: Food) {
        binding.servingInfo.text =
            "${food.servingDescription} = ${UiFormatters.grams(food.servingWeightGrams)}"

        val mealTypeOptions = MealType.entries.map { it.displayName }
        binding.mealTypeDropdown.setAdapter(
            NonFilteringArrayAdapter(
                context = requireContext(),
                resource = com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                items = mealTypeOptions
            )
        )
        binding.mealTypeDropdown.threshold = 0
        binding.mealTypeDropdown.setText(MealType.SNACK.displayName, false)

        binding.inputModeGroup.check(R.id.log_by_servings_button)
        updateAmountHint()
        updatePreview(food)

        binding.inputModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            currentInputMode = if (checkedId == R.id.log_by_weight_button) {
                InputMode.WEIGHT
            } else {
                InputMode.SERVINGS
            }
            updateAmountHint()
            updatePreview(food)
        }

        binding.amountEditText.addTextChangedListener {
            updatePreview(food)
        }
    }

    private fun updateAmountHint() {
        val preferredUnit = currentGoals().preferredUnit
        binding.amountLayout.hint = if (currentInputMode == InputMode.SERVINGS) {
            getString(R.string.amount_in_servings)
        } else {
            getString(R.string.amount_in_weight, preferredUnit.shortLabel)
        }
    }

    private fun updatePreview(food: Food) {
        val goals = currentGoals()
        val preferredUnit = goals.preferredUnit
        val amountText = binding.amountEditText.text?.toString().orEmpty()
        binding.amountLayout.error = null

        if (amountText.isBlank()) {
            binding.previewAmount.text = getString(R.string.preview_waiting_amount)
            binding.previewCalories.text = getString(R.string.preview_waiting_calories)
            binding.previewSecondaryCalories.visibility = View.GONE
            binding.previewMacros.text = getString(R.string.preview_waiting_macros)
            return
        }

        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            binding.amountLayout.error = getString(R.string.positive_number_required)
            binding.previewAmount.text = getString(R.string.preview_invalid_amount)
            binding.previewCalories.text = getString(R.string.preview_waiting_calories)
            binding.previewSecondaryCalories.visibility = View.GONE
            binding.previewMacros.text = getString(R.string.preview_waiting_macros)
            return
        }

        val calculation = try {
            when (currentInputMode) {
                InputMode.SERVINGS -> NutritionCalculator.calculateForServings(food, amount)
                InputMode.WEIGHT -> NutritionCalculator.calculateForWeight(
                    food,
                    NutritionCalculator.convertToGrams(amount, preferredUnit)
                )
            }
        } catch (_: IllegalArgumentException) {
            binding.previewAmount.text = getString(R.string.preview_invalid_amount)
            binding.previewCalories.text = getString(R.string.preview_waiting_calories)
            binding.previewSecondaryCalories.visibility = View.GONE
            binding.previewMacros.text = getString(R.string.preview_waiting_macros)
            return
        }

        binding.previewAmount.text =
            "${UiFormatters.servings(calculation.servings)} • ${UiFormatters.weight(calculation.weightGrams, preferredUnit)}"

        binding.previewCalories.text =
            UiFormatters.mainMacroLabeledValue(calculation.nutrition, goals.mainMacro)

        val shouldShowSecondaryCalories = goals.showCaloriesInLivePreview && goals.mainMacro != MainMacro.CALORIES
        binding.previewSecondaryCalories.visibility = if (shouldShowSecondaryCalories) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (shouldShowSecondaryCalories) {
            binding.previewSecondaryCalories.text = UiFormatters.calories(calculation.nutrition.calories)
        }

        binding.previewMacros.text = UiFormatters.macroSummarySelectionLine(
            nutritionSnapshot = calculation.nutrition,
            goals = goals,
            includeCaloriesUnderMainMacro = false
        ) ?: getString(R.string.preview_macros_hidden)
    }

    private fun submit(food: Food) {
        val mealType = MealType.fromDisplayName(binding.mealTypeDropdown.text?.toString().orEmpty())
        if (mealType == null) {
            binding.mealTypeLayout.error = getString(R.string.select_meal_type)
            return
        }

        binding.mealTypeLayout.error = null
        val didLog = viewModel.logFood(
            AppViewModel.LogFoodInput(
                foodId = food.id,
                mealType = mealType,
                inputMode = currentInputMode,
                amount = binding.amountEditText.text?.toString().orEmpty()
            )
        )

        if (didLog) {
            dismiss()
        }
    }

    private fun foodDefaultUnit(): org.archuser.CalCount.data.model.WeightUnit {
        return org.archuser.CalCount.data.model.WeightUnit.GRAMS
    }

    private fun currentGoals(): org.archuser.CalCount.data.model.Goals {
        return viewModel.uiState.value?.goals ?: org.archuser.CalCount.data.model.Goals()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    companion object {
        private const val ARG_FOOD_ID = "food_id"

        fun newInstance(foodId: String): LogFoodDialogFragment {
            return LogFoodDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FOOD_ID, foodId)
                }
            }
        }
    }
}
