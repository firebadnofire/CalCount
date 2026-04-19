package org.archuser.CalCount.ui.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.databinding.FragmentCreateFoodBinding
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters

class CreateFoodFragment : Fragment() {

    private var _binding: FragmentCreateFoodBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var lastEditingFoodId: String? = null
    private var currentFoodKind: FoodKind = FoodKind.FOOD
    private var latestGoals = Goals()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateFoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]

        binding.foodKindGroup.check(org.archuser.CalCount.R.id.food_kind_food_button)
        binding.foodKindGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            currentFoodKind = if (checkedId == org.archuser.CalCount.R.id.food_kind_liquid_button) {
                FoodKind.LIQUID
            } else {
                FoodKind.FOOD
            }
            applyKindToUi()
        }

        binding.toggleUsefulNutrientsButton.setOnClickListener {
            setUsefulVisible(!binding.usefulNutrientsContainer.isVisible)
        }

        binding.toggleExtendedNutrientsButton.setOnClickListener {
            setExtendedVisible(!binding.extendedNutrientsContainer.isVisible)
        }

        binding.saveFoodButton.setOnClickListener {
            val wasEditing = lastEditingFoodId != null
            val didSave = viewModel.saveFood(
                AppViewModel.SaveFoodInput(
                    kind = currentFoodKind.displayName,
                    name = binding.foodNameEditText.text?.toString().orEmpty(),
                    servingDescription = binding.servingDescriptionEditText.text?.toString().orEmpty(),
                    servingAmount = binding.servingWeightEditText.text?.toString().orEmpty(),
                    calories = binding.caloriesEditText.text?.toString().orEmpty(),
                    fatGrams = binding.fatEditText.text?.toString().orEmpty(),
                    carbsGrams = binding.carbsEditText.text?.toString().orEmpty(),
                    proteinGrams = binding.proteinEditText.text?.toString().orEmpty(),
                    saturatedFatGrams = binding.saturatedFatEditText.text?.toString().orEmpty(),
                    fiberGrams = binding.fiberEditText.text?.toString().orEmpty(),
                    sugarGrams = binding.sugarsEditText.text?.toString().orEmpty(),
                    addedSugarsGrams = binding.addedSugarsEditText.text?.toString().orEmpty(),
                    sugarAlcoholsGrams = binding.sugarAlcoholsEditText.text?.toString().orEmpty(),
                    sodiumMilligrams = binding.sodiumEditText.text?.toString().orEmpty(),
                    potassiumMilligrams = binding.potassiumEditText.text?.toString().orEmpty(),
                    cholesterolMilligrams = binding.cholesterolEditText.text?.toString().orEmpty(),
                    transFatGrams = binding.transFatEditText.text?.toString().orEmpty(),
                    monounsaturatedFatGrams = binding.monounsaturatedFatEditText.text?.toString().orEmpty(),
                    polyunsaturatedFatGrams = binding.polyunsaturatedFatEditText.text?.toString().orEmpty(),
                    omega3FattyAcidsGrams = binding.omega3EditText.text?.toString().orEmpty(),
                    omega6FattyAcidsGrams = binding.omega6EditText.text?.toString().orEmpty(),
                    calciumMilligrams = binding.calciumEditText.text?.toString().orEmpty(),
                    chlorideMilligrams = binding.chlorideEditText.text?.toString().orEmpty(),
                    folateMicrograms = binding.folateEditText.text?.toString().orEmpty(),
                    ironMilligrams = binding.ironEditText.text?.toString().orEmpty(),
                    magnesiumMilligrams = binding.magnesiumEditText.text?.toString().orEmpty(),
                    phosphorusMilligrams = binding.phosphorusEditText.text?.toString().orEmpty(),
                    zincMilligrams = binding.zincEditText.text?.toString().orEmpty(),
                    vitaminAMicrograms = binding.vitaminAEditText.text?.toString().orEmpty(),
                    vitaminB12Micrograms = binding.vitaminB12EditText.text?.toString().orEmpty(),
                    vitaminCMilligrams = binding.vitaminCEditText.text?.toString().orEmpty(),
                    vitaminDMicrograms = binding.vitaminDEditText.text?.toString().orEmpty(),
                    vitaminEMilligrams = binding.vitaminEEditText.text?.toString().orEmpty(),
                    vitaminKMicrograms = binding.vitaminKEditText.text?.toString().orEmpty(),
                    servingsPerContainer = binding.servingsPerContainerEditText.text?.toString()
                        .orEmpty()
                )
            )

            if (didSave && !wasEditing) {
                clearForm()
            }
        }

        binding.cancelEditButton.setOnClickListener {
            viewModel.cancelEditingFood()
        }

        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun render(state: AppUiState) {
        val editingFood = state.editingFood
        latestGoals = state.goals
        applyKindToUi()
        binding.weightHint.text =
            "Food serving amounts store as grams and liquid serving amounts store as ml. Logged amounts display as ${state.goals.preferredUnit.shortLabel} and ${state.goals.preferredLiquidUnit.shortLabel}."

        binding.editingBanner.isVisible = editingFood != null
        binding.cancelEditButton.isVisible = editingFood != null
        binding.saveFoodButton.text = if (editingFood == null) {
            getString(org.archuser.CalCount.R.string.save_food)
        } else {
            getString(org.archuser.CalCount.R.string.update_food)
        }

        if (editingFood?.id != lastEditingFoodId) {
            if (editingFood != null) {
                bindFood(editingFood)
                binding.editingTitle.text =
                    "Editing ${editingFood.name} (${formatServingAmount(editingFood)})"
            } else if (lastEditingFoodId != null) {
                clearForm()
            }
            lastEditingFoodId = editingFood?.id
        }
    }

    private fun bindFood(food: Food) {
        currentFoodKind = food.kind
        binding.foodKindGroup.check(
            if (food.kind == FoodKind.LIQUID) {
                org.archuser.CalCount.R.id.food_kind_liquid_button
            } else {
                org.archuser.CalCount.R.id.food_kind_food_button
            }
        )
        applyKindToUi()
        binding.foodNameEditText.setText(food.name)
        binding.servingDescriptionEditText.setText(food.servingDescription)
        binding.servingWeightEditText.setText(
            UiFormatters.number(
                if (food.kind == FoodKind.LIQUID) food.servingVolumeMilliliters else food.servingWeightGrams
            )
        )
        binding.caloriesEditText.setText(UiFormatters.number(food.nutritionPerServing.calories))
        binding.fatEditText.setText(UiFormatters.number(food.nutritionPerServing.fatGrams))
        binding.carbsEditText.setText(UiFormatters.number(food.nutritionPerServing.carbsGrams))
        binding.proteinEditText.setText(UiFormatters.number(food.nutritionPerServing.proteinGrams))
        binding.saturatedFatEditText.setText(food.nutritionPerServing.saturatedFatGrams?.let(UiFormatters::number).orEmpty())
        binding.fiberEditText.setText(food.nutritionPerServing.fiberGrams?.let(UiFormatters::number).orEmpty())
        binding.sugarsEditText.setText(food.nutritionPerServing.sugarGrams?.let(UiFormatters::number).orEmpty())
        binding.addedSugarsEditText.setText(food.nutritionPerServing.addedSugarsGrams?.let(UiFormatters::number).orEmpty())
        binding.sugarAlcoholsEditText.setText(food.nutritionPerServing.sugarAlcoholsGrams?.let(UiFormatters::number).orEmpty())
        binding.sodiumEditText.setText(food.nutritionPerServing.sodiumMilligrams?.let(UiFormatters::number).orEmpty())
        binding.potassiumEditText.setText(food.nutritionPerServing.potassiumMilligrams?.let(UiFormatters::number).orEmpty())
        binding.cholesterolEditText.setText(food.nutritionPerServing.cholesterolMilligrams?.let(UiFormatters::number).orEmpty())
        binding.transFatEditText.setText(food.nutritionPerServing.transFatGrams?.let(UiFormatters::number).orEmpty())
        binding.monounsaturatedFatEditText.setText(food.nutritionPerServing.monounsaturatedFatGrams?.let(UiFormatters::number).orEmpty())
        binding.polyunsaturatedFatEditText.setText(food.nutritionPerServing.polyunsaturatedFatGrams?.let(UiFormatters::number).orEmpty())
        binding.omega3EditText.setText(food.nutritionPerServing.omega3FattyAcidsGrams?.let(UiFormatters::number).orEmpty())
        binding.omega6EditText.setText(food.nutritionPerServing.omega6FattyAcidsGrams?.let(UiFormatters::number).orEmpty())
        binding.calciumEditText.setText(food.nutritionPerServing.calciumMilligrams?.let(UiFormatters::number).orEmpty())
        binding.chlorideEditText.setText(food.nutritionPerServing.chlorideMilligrams?.let(UiFormatters::number).orEmpty())
        binding.folateEditText.setText(food.nutritionPerServing.folateMicrograms?.let(UiFormatters::number).orEmpty())
        binding.ironEditText.setText(food.nutritionPerServing.ironMilligrams?.let(UiFormatters::number).orEmpty())
        binding.magnesiumEditText.setText(food.nutritionPerServing.magnesiumMilligrams?.let(UiFormatters::number).orEmpty())
        binding.phosphorusEditText.setText(food.nutritionPerServing.phosphorusMilligrams?.let(UiFormatters::number).orEmpty())
        binding.zincEditText.setText(food.nutritionPerServing.zincMilligrams?.let(UiFormatters::number).orEmpty())
        binding.vitaminAEditText.setText(food.nutritionPerServing.vitaminAMicrograms?.let(UiFormatters::number).orEmpty())
        binding.vitaminB12EditText.setText(food.nutritionPerServing.vitaminB12Micrograms?.let(UiFormatters::number).orEmpty())
        binding.vitaminCEditText.setText(food.nutritionPerServing.vitaminCMilligrams?.let(UiFormatters::number).orEmpty())
        binding.vitaminDEditText.setText(food.nutritionPerServing.vitaminDMicrograms?.let(UiFormatters::number).orEmpty())
        binding.vitaminEEditText.setText(food.nutritionPerServing.vitaminEMilligrams?.let(UiFormatters::number).orEmpty())
        binding.vitaminKEditText.setText(food.nutritionPerServing.vitaminKMicrograms?.let(UiFormatters::number).orEmpty())
        binding.servingsPerContainerEditText.setText(food.servingsPerContainer?.let(UiFormatters::number).orEmpty())

        setUsefulVisible(hasUsefulNutrients(food))
        setExtendedVisible(hasExtendedNutrients(food))
    }

    private fun clearForm() {
        currentFoodKind = FoodKind.FOOD
        binding.foodKindGroup.check(org.archuser.CalCount.R.id.food_kind_food_button)
        applyKindToUi()
        setUsefulVisible(false)
        setExtendedVisible(false)
        binding.foodNameEditText.text = null
        binding.servingDescriptionEditText.text = null
        binding.servingWeightEditText.text = null
        binding.caloriesEditText.text = null
        binding.fatEditText.text = null
        binding.carbsEditText.text = null
        binding.proteinEditText.text = null
        binding.saturatedFatEditText.text = null
        binding.fiberEditText.text = null
        binding.sugarsEditText.text = null
        binding.addedSugarsEditText.text = null
        binding.sugarAlcoholsEditText.text = null
        binding.sodiumEditText.text = null
        binding.potassiumEditText.text = null
        binding.cholesterolEditText.text = null
        binding.transFatEditText.text = null
        binding.monounsaturatedFatEditText.text = null
        binding.polyunsaturatedFatEditText.text = null
        binding.omega3EditText.text = null
        binding.omega6EditText.text = null
        binding.calciumEditText.text = null
        binding.chlorideEditText.text = null
        binding.folateEditText.text = null
        binding.ironEditText.text = null
        binding.magnesiumEditText.text = null
        binding.phosphorusEditText.text = null
        binding.zincEditText.text = null
        binding.vitaminAEditText.text = null
        binding.vitaminB12EditText.text = null
        binding.vitaminCEditText.text = null
        binding.vitaminDEditText.text = null
        binding.vitaminEEditText.text = null
        binding.vitaminKEditText.text = null
        binding.servingsPerContainerEditText.text = null
    }

    private fun applyKindToUi() {
        binding.servingAmountLayout.hint = if (currentFoodKind == FoodKind.LIQUID) {
            "Serving volume (ml)"
        } else {
            "Serving weight (grams)"
        }
    }

    private fun formatServingAmount(food: Food): String {
        return if (food.kind == FoodKind.LIQUID) {
            UiFormatters.volume(food.servingVolumeMilliliters, latestGoals.preferredLiquidUnit)
        } else {
            UiFormatters.grams(food.servingWeightGrams)
        }
    }

    private fun setUsefulVisible(isVisible: Boolean) {
        binding.usefulNutrientsContainer.isVisible = isVisible
        binding.toggleUsefulNutrientsButton.text = if (isVisible) {
            "Hide useful nutrients"
        } else {
            "Show useful nutrients"
        }
    }

    private fun setExtendedVisible(isVisible: Boolean) {
        binding.extendedNutrientsContainer.isVisible = isVisible
        binding.toggleExtendedNutrientsButton.text = if (isVisible) {
            "Hide extended nutrients"
        } else {
            "Show extended nutrients"
        }
    }

    private fun hasUsefulNutrients(food: Food): Boolean {
        val nutrition = food.nutritionPerServing
        return listOf(
            nutrition.saturatedFatGrams,
            nutrition.addedSugarsGrams,
            nutrition.cholesterolMilligrams,
            nutrition.potassiumMilligrams
        ).any { it != null }
    }

    private fun hasExtendedNutrients(food: Food): Boolean {
        val nutrition = food.nutritionPerServing
        return listOf(
            nutrition.sugarAlcoholsGrams,
            nutrition.transFatGrams,
            nutrition.monounsaturatedFatGrams,
            nutrition.polyunsaturatedFatGrams,
            nutrition.omega3FattyAcidsGrams,
            nutrition.omega6FattyAcidsGrams,
            nutrition.calciumMilligrams,
            nutrition.chlorideMilligrams,
            nutrition.folateMicrograms,
            nutrition.ironMilligrams,
            nutrition.magnesiumMilligrams,
            nutrition.phosphorusMilligrams,
            nutrition.vitaminAMicrograms,
            nutrition.vitaminB12Micrograms,
            nutrition.vitaminCMilligrams,
            nutrition.vitaminDMicrograms,
            nutrition.vitaminEMilligrams,
            nutrition.vitaminKMicrograms,
            nutrition.zincMilligrams
        ).any { it != null }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
