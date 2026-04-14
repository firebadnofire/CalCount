package org.archuser.CalCount.ui.create

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.databinding.FragmentCreateFoodBinding
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters

class CreateFoodFragment : Fragment() {

    private var _binding: FragmentCreateFoodBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var lastEditingFoodId: String? = null

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

        binding.saveFoodButton.setOnClickListener {
            val wasEditing = lastEditingFoodId != null
            val didSave = viewModel.saveFood(
                AppViewModel.SaveFoodInput(
                    name = binding.foodNameEditText.text?.toString().orEmpty(),
                    servingDescription = binding.servingDescriptionEditText.text?.toString().orEmpty(),
                    servingWeightGrams = binding.servingWeightEditText.text?.toString().orEmpty(),
                    calories = binding.caloriesEditText.text?.toString().orEmpty(),
                    fatGrams = binding.fatEditText.text?.toString().orEmpty(),
                    carbsGrams = binding.carbsEditText.text?.toString().orEmpty(),
                    proteinGrams = binding.proteinEditText.text?.toString().orEmpty(),
                    saturatedFatGrams = binding.saturatedFatEditText.text?.toString().orEmpty(),
                    fiberGrams = binding.fiberEditText.text?.toString().orEmpty(),
                    sugarGrams = binding.sugarsEditText.text?.toString().orEmpty(),
                    sodiumMilligrams = binding.sodiumEditText.text?.toString().orEmpty(),
                    potassiumMilligrams = binding.potassiumEditText.text?.toString().orEmpty(),
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
        binding.weightHint.text =
            "Serving weight is always stored in grams. Logged weights can display as ${state.goals.preferredUnit.shortLabel}."

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
                    "Editing ${editingFood.name} (${UiFormatters.grams(editingFood.servingWeightGrams)})"
            } else if (lastEditingFoodId != null) {
                clearForm()
            }
            lastEditingFoodId = editingFood?.id
        }
    }

    private fun bindFood(food: Food) {
        binding.foodNameEditText.setText(food.name)
        binding.servingDescriptionEditText.setText(food.servingDescription)
        binding.servingWeightEditText.setText(UiFormatters.number(food.servingWeightGrams))
        binding.caloriesEditText.setText(UiFormatters.number(food.nutritionPerServing.calories))
        binding.fatEditText.setText(UiFormatters.number(food.nutritionPerServing.fatGrams))
        binding.carbsEditText.setText(UiFormatters.number(food.nutritionPerServing.carbsGrams))
        binding.proteinEditText.setText(UiFormatters.number(food.nutritionPerServing.proteinGrams))
        binding.saturatedFatEditText.setText(food.nutritionPerServing.saturatedFatGrams?.let(UiFormatters::number).orEmpty())
        binding.fiberEditText.setText(food.nutritionPerServing.fiberGrams?.let(UiFormatters::number).orEmpty())
        binding.sugarsEditText.setText(food.nutritionPerServing.sugarGrams?.let(UiFormatters::number).orEmpty())
        binding.sodiumEditText.setText(food.nutritionPerServing.sodiumMilligrams?.let(UiFormatters::number).orEmpty())
        binding.potassiumEditText.setText(food.nutritionPerServing.potassiumMilligrams?.let(UiFormatters::number).orEmpty())
        binding.servingsPerContainerEditText.setText(food.servingsPerContainer?.let(UiFormatters::number).orEmpty())
    }

    private fun clearForm() {
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
        binding.sodiumEditText.text = null
        binding.potassiumEditText.text = null
        binding.servingsPerContainerEditText.text = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
