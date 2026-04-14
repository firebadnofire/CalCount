package org.archuser.CalCount.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import org.archuser.CalCount.MainActivity
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.databinding.FragmentFoodLibraryBinding
import org.archuser.CalCount.databinding.ItemFoodBinding
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters

class FoodLibraryFragment : Fragment() {

    private var _binding: FragmentFoodLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var latestState = AppUiState()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFoodLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]

        binding.searchEditText.addTextChangedListener {
            render(latestState)
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            latestState = state
            render(state)
        }
    }

    private fun render(state: AppUiState) {
        val query = binding.searchEditText.text?.toString().orEmpty().trim().lowercase()
        val recentFoods = if (query.isBlank()) state.recentFoods(limit = 4) else emptyList()
        val allFoods = state.foods.filter { it.matches(query) }

        binding.emptyLibraryState.isVisible = state.foods.isEmpty()
        binding.recentFoodsSection.isVisible = recentFoods.isNotEmpty()

        populateRecentFoods(recentFoods)
        populateFoodContainer(
            container = binding.allFoodsContainer,
            foods = allFoods,
            emptyMessage = if (query.isBlank()) {
                "Create your first food to start logging."
            } else {
                "No foods match \"$query\"."
            },
            goals = state.goals
        )
    }

    private fun populateRecentFoods(foods: List<Food>) {
        binding.recentFoodsChips.removeAllViews()

        foods.forEach { food ->
            val chip = layoutInflater.inflate(
                R.layout.item_recent_food_chip,
                binding.recentFoodsChips,
                false
            ) as Chip
            chip.text = food.name
            chip.setOnClickListener {
                LogFoodDialogFragment.newInstance(food.id)
                    .show(childFragmentManager, "log_food_${food.id}")
            }
            binding.recentFoodsChips.addView(chip)
        }
    }

    private fun populateFoodContainer(
        container: ViewGroup,
        foods: List<Food>,
        emptyMessage: String,
        goals: Goals
    ) {
        container.removeAllViews()
        if (foods.isEmpty()) {
            val emptyView = layoutInflater.inflate(
                android.R.layout.simple_list_item_1,
                container,
                false
            )
            emptyView.findViewById<android.widget.TextView>(android.R.id.text1).apply {
                text = emptyMessage
                setPadding(0, 12, 0, 12)
            }
            container.addView(emptyView)
            return
        }

        foods.forEach { food ->
            val itemBinding = ItemFoodBinding.inflate(layoutInflater, container, false)
            itemBinding.foodName.text = food.name
            itemBinding.foodServing.text =
                "${food.servingDescription} • ${UiFormatters.grams(food.servingWeightGrams)}"
            val mainMacroValue = UiFormatters.mainMacroValue(food.nutritionPerServing, goals.mainMacro)
            val macroLine = UiFormatters.macroSummarySelectionLine(food.nutritionPerServing, goals)
            itemBinding.foodNutrition.text = if (macroLine.isNullOrBlank()) {
                mainMacroValue
            } else {
                "$mainMacroValue • $macroLine"
            }
            itemBinding.foodOptionalInfo.text = buildOptionalInfo(food)

            itemBinding.logButton.setOnClickListener {
                LogFoodDialogFragment.newInstance(food.id)
                    .show(childFragmentManager, "log_food_${food.id}")
            }
            itemBinding.editButton.setOnClickListener {
                viewModel.beginEditingFood(food.id)
                (activity as? MainActivity)?.navigateToCreateFood()
            }

            container.addView(itemBinding.root)
        }
    }

    private fun buildOptionalInfo(food: Food): String {
        val details = mutableListOf<String>()
        food.servingsPerContainer?.let {
            details += "${UiFormatters.number(it)} servings per container"
        }
        return details.joinToString(" • ").ifBlank { "Reusable saved food" }
    }

    private fun Food.matches(query: String): Boolean {
        if (query.isBlank()) {
            return true
        }
        return name.lowercase().contains(query) ||
            servingDescription.lowercase().contains(query)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
