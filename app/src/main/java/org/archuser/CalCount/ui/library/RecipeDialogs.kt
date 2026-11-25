package org.archuser.CalCount.ui.library

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MacroTier
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.databinding.DialogLogFoodBinding
import org.archuser.CalCount.databinding.DialogRecipeDetailsBinding
import org.archuser.CalCount.databinding.ItemRecipeIngredientBinding
import org.archuser.CalCount.databinding.ItemRecipeNutrientBinding
import org.archuser.CalCount.databinding.RecipeNutritionScopeBinding
import org.archuser.CalCount.domain.NutritionCalculator
import org.archuser.CalCount.domain.RecipeCalculator
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.NonFilteringArrayAdapter
import org.archuser.CalCount.ui.UiFormatters

internal class RecipeDialogs(
    private val context: Context,
    private val viewModel: AppViewModel,
    private val fragmentManager: FragmentManager
) {
    fun create() = openEditor(null)

    fun edit(recipe: Recipe) = openEditor(recipe.id)

    private fun openEditor(recipeId: String?) {
        if (viewModel.uiState.value?.foods.isNullOrEmpty()) {
            MaterialAlertDialogBuilder(context).setTitle("Add a food first")
                .setMessage("Create or import a nutritional label to use in this recipe.")
                .setPositiveButton(android.R.string.ok, null).show()
            return
        }
        RecipeEditorDialogFragment.newInstance(recipeId).show(fragmentManager, "recipe_editor")
    }

    fun details(recipe: Recipe) {
        val state = viewModel.uiState.value ?: return
        val builder = MaterialAlertDialogBuilder(context)
        val inflater = LayoutInflater.from(builder.context)
        val page = DialogRecipeDetailsBinding.inflate(inflater)
        page.recipeName.text = recipe.name
        ViewCompat.setAccessibilityHeading(page.recipeName, true)
        page.nutrition.previewTitle.text = "Nutrition"
        page.recipeMeta.text = UiFormatters.recipeMetadata(recipe)
        page.description.text = recipe.description
        page.description.isVisible = !recipe.description.isNullOrBlank()
        page.notesCard.isVisible = !recipe.notes.isNullOrBlank()
        page.notes.text = recipe.notes

        val foods = state.foods.associateBy { it.id }
        recipe.ingredients.forEach { ingredient ->
            val row = ItemRecipeIngredientBinding.inflate(inflater, page.ingredients, false)
            val food = foods[ingredient.foodId]
            row.ingredientName.text = food?.name ?: "Missing food"
            val amount = UiFormatters.ingredientAmount(ingredient.quantity, ingredient.unit)
            val contribution = food?.let {
                runCatching { RecipeCalculator.ingredientNutrition(ingredient, it) }.getOrNull()
            }?.let {
                val primary = state.goals.mainMacro
                UiFormatters.recipeNutrientLabel(primary, it, null)
            }
            row.ingredientAmount.text = listOfNotNull(amount, contribution).joinToString(" • ")
            row.ingredientNotes.isVisible = !ingredient.notes.isNullOrBlank()
            row.ingredientNotes.text = ingredient.notes?.let { "Notes: $it" }
            page.ingredients.addView(row.root)
        }

        val result = runCatching { RecipeCalculator.calculate(recipe, state.foods) }
        val nutrition = result.getOrNull()
        if (nutrition == null) {
            page.scope.root.isVisible = false
            page.nutrition.showMessage(result.exceptionOrNull()?.message ?: "Nutrition unavailable.")
        } else {
            if (nutrition.completeness.values.any { it == NutrientCompleteness.INCOMPLETE }) {
                page.nutrients.addView(android.widget.TextView(page.root.context).apply {
                    text = context.getString(R.string.recipe_subtotal_legend)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                })
            }
            val rows = addNutrientRows(page.nutrients, inflater)
            page.scope.bindScope { whole ->
                val snapshot = if (whole) nutrition.total else nutrition.perServing
                page.nutrition.showNutrition(snapshot, state.goals, completeness = nutrition.completeness)
                rows.forEach { (nutrient, row) ->
                    row.nutrientValue.text =
                        UiFormatters.recipeNutrientValue(nutrient, snapshot, nutrition.completeness[nutrient])
                }
            }
        }

        val dialog = builder.setView(page.root)
            .setNegativeButton("Close", null).create()
        page.deleteRecipe.setOnClickListener { confirmDeletion(recipe) { dialog.dismiss() } }
        dialog.show()
    }

    fun confirmDeletion(recipe: Recipe, afterDelete: () -> Unit = {}) {
        MaterialAlertDialogBuilder(context).setTitle("Delete ${recipe.name}?")
            .setMessage("Saved consumption entries will stay in your history.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Delete") { _, _ -> if (viewModel.deleteRecipe(recipe.id)) afterDelete() }
            .show()
    }

    private fun addNutrientRows(container: LinearLayout, inflater: LayoutInflater): Map<MainMacro, ItemRecipeNutrientBinding> =
        buildMap {
            MacroTier.entries.forEach { tier ->
                val section = LinearLayout(container.context).apply {
                    orientation = LinearLayout.VERTICAL
                    isVisible = !tier.isCollapsible
                }
                if (tier.isCollapsible) {
                    container.addView(MaterialButton(container.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Show ${tier.displayName.lowercase()}"
                        setOnClickListener {
                            section.isVisible = !section.isVisible
                            val action = if (section.isVisible) "Hide" else "Show"
                            text = "$action ${tier.displayName.lowercase()}"
                        }
                    })
                } else {
                    container.addView(android.widget.TextView(container.context).apply {
                        text = tier.displayName
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    })
                }
                container.addView(section)
                MainMacro.entries.filter { it.tier == tier }.forEach { nutrient ->
                    val row = ItemRecipeNutrientBinding.inflate(inflater, section, false)
                    row.nutrientName.text = nutrient.displayName
                    section.addView(row.root)
                    put(nutrient, row)
                }
            }
        }

    fun log(recipe: Recipe) {
        val builder = MaterialAlertDialogBuilder(context)
        val page = DialogLogFoodBinding.inflate(LayoutInflater.from(builder.context))
        page.servingInfo.text = "Yields ${UiFormatters.servings(recipe.servings)}"
        page.logByLabel.isVisible = false
        page.inputModeGroup.isVisible = false
        page.amountLayout.setHint(R.string.amount_in_servings)
        page.mealTypeDropdown.setAdapter(NonFilteringArrayAdapter(
            builder.context, com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
            MealType.entries.map(MealType::displayName)
        ))
        page.mealTypeDropdown.threshold = 0
        page.mealTypeDropdown.setText(MealType.SNACK.displayName, false)
        page.amountEditText.setText("1")

        fun update(): Boolean {
            page.logError.isVisible = false
            page.amountLayout.error = null
            val amount = page.amountEditText.text?.toString()?.toDoubleOrNull()
            if (amount == null || amount <= 0 || !amount.isFinite()) {
                val message = context.getString(R.string.positive_number_required)
                page.amountLayout.error = message
                page.nutritionPreview.showMessage(message)
                return false
            }
            val state = viewModel.uiState.value ?: return false
            val result = runCatching { RecipeCalculator.calculate(recipe, state.foods) }
            val calculated = result.getOrNull()
            if (calculated == null) {
                page.nutritionPreview.showMessage(result.exceptionOrNull()?.message ?: "Nutrition unavailable.")
                return false
            }
            val scaled = NutritionCalculator.scaleNutrition(calculated.perServing, amount)
            if (MainMacro.entries.any { it.reportedValue(scaled)?.isFinite() == false }) {
                page.amountLayout.error = "The consumed nutrition exceeds the supported range."
                page.nutritionPreview.showMessage(page.amountLayout.error.toString())
                return false
            }
            page.nutritionPreview.showNutrition(scaled, state.goals, UiFormatters.servings(amount), calculated.completeness)
            return true
        }

        page.amountEditText.addTextChangedListener { update() }
        update()
        val dialog = builder.setTitle(context.getString(R.string.log_food_title, recipe.name))
            .setView(page.root).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save_log, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!update()) return@setOnClickListener
                val meal = MealType.fromDisplayName(page.mealTypeDropdown.text.toString())
                if (meal == null) {
                    page.mealTypeLayout.error = context.getString(R.string.select_meal_type)
                    return@setOnClickListener
                }
                val saved = viewModel.logRecipe(recipe.id, meal, page.amountEditText.text.toString()) { message ->
                    page.logError.text = message
                    page.logError.isVisible = true
                }
                if (saved) dialog.dismiss()
            }
        }
        dialog.show()
    }
}

internal fun RecipeNutritionScopeBinding.bindScope(whole: Boolean = false, render: (Boolean) -> Unit) {
    // MaterialButtonToggleGroup defaults children to one line.
    listOf(perServing, wholeRecipe).forEach { button ->
        button.setSingleLine(false)
        button.maxLines = 2
        button.ellipsize = null
    }
    root.check(if (whole) wholeRecipe.id else perServing.id)
    root.addOnButtonCheckedListener { _, id, checked -> if (checked) render(id == wholeRecipe.id) }
    render(whole)
}
