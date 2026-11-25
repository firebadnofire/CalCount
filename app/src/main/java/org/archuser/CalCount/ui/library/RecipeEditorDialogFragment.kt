package org.archuser.CalCount.ui.library

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import androidx.core.view.WindowCompat
import com.google.android.material.color.MaterialColors
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.ListView
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.ArrayAdapter
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.data.model.RecipeIngredient
import org.archuser.CalCount.databinding.DialogRecipeEditorBinding
import org.archuser.CalCount.databinding.ItemRecipeEditorIngredientBinding
import org.archuser.CalCount.domain.RecipeCalculator
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.NonFilteringArrayAdapter
import org.archuser.CalCount.ui.UiFormatters
import java.util.UUID

class RecipeEditorDialogFragment : DialogFragment() {
    private val viewModel: AppViewModel by activityViewModels()
    private var binding: DialogRecipeEditorBinding? = null
    private val rows = mutableListOf<IngredientRow>()
    private lateinit var id: String
    private var wholeRecipe = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val page = DialogRecipeEditorBinding.inflate(LayoutInflater.from(requireActivity()))
        val surface = MaterialColors.getColor(page.root, com.google.android.material.R.attr.colorSurface)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(surface))
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = MaterialColors.isColorLight(surface)
            controller.isAppearanceLightNavigationBars = MaterialColors.isColorLight(surface)
        }
        binding = page
        val existingId = arguments?.getString("recipeId")
        val existing = viewModel.uiState.value?.recipes?.firstOrNull { it.id == existingId }
        id = savedInstanceState?.getString("id") ?: existingId ?: UUID.randomUUID().toString()
        page.toolbar.title = if (existingId == null) "Create recipe" else "Edit recipe"
        page.recipeName.setText(savedInstanceState?.getString("name") ?: existing?.name.orEmpty())
        page.description.setText(savedInstanceState?.getString("description") ?: existing?.description.orEmpty())
        page.yieldAmount.setText(savedInstanceState?.getString("yield") ?: existing?.servings?.toString() ?: "1")
        page.notes.setText(savedInstanceState?.getString("notes") ?: existing?.notes.orEmpty())
        val savedRows = savedInstanceState?.let { BundleCompat.getParcelableArrayList(it, "rows", Bundle::class.java) }
        when {
            savedRows != null -> savedRows.forEach {
                addIngredient(it.getString("food").orEmpty(), it.getString("quantity").orEmpty(),
                    IngredientUnit.fromLabel(it.getString("unit") ?: "servings"), it.getString("notes").orEmpty())
            }
            existing != null -> existing.ingredients.forEach {
                addIngredient(it.foodId, it.quantity.toString(), it.unit, it.notes.orEmpty())
            }
        }
        page.recipeName.addTextChangedListener { update() }
        page.yieldAmount.addTextChangedListener { update() }
        page.addIngredient.setOnClickListener { chooseFood { addIngredient(it.id, "1", IngredientUnit.SERVINGS, "") } }
        page.cancel.setOnClickListener { dismiss() }
        page.save.setOnClickListener {
            val recipe = update(showErrors = true) ?: return@setOnClickListener
            if (viewModel.saveRecipe(recipe) { showError(it) }) dismiss()
        }
        page.scope.bindScope(savedInstanceState?.getBoolean("whole") == true) {
            wholeRecipe = it
            update()
        }
        dialog.setContentView(page.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val page = binding ?: return
        outState.putString("id", id)
        outState.putString("name", page.recipeName.text.toString())
        outState.putString("description", page.description.text.toString())
        outState.putString("yield", page.yieldAmount.text.toString())
        outState.putString("notes", page.notes.text.toString())
        outState.putBoolean("whole", wholeRecipe)
        outState.putParcelableArrayList("rows", ArrayList(rows.map { row ->
            bundleOf("food" to row.foodId, "quantity" to row.binding.quantity.text.toString(),
                "unit" to row.unit.label, "notes" to row.binding.notes.text.toString())
        }))
    }

    override fun onDestroyView() {
        rows.clear()
        binding = null
        super.onDestroyView()
    }

    private fun addIngredient(foodId: String, quantity: String, unit: IngredientUnit, notes: String) {
        val page = binding ?: return
        val item = ItemRecipeEditorIngredientBinding.inflate(layoutInflater, page.ingredients, false)
        val row = IngredientRow(item, foodId, unit)
        rows.add(row)
        // Ingredient fields share layout IDs. Restore each row from its own saved draft.
        item.root.isSaveFromParentEnabled = false
        page.ingredients.addView(item.root)
        item.quantity.setText(quantity)
        item.notes.setText(notes)
        configureUnit(row)
        item.pickFood.setOnClickListener {
            chooseFood { food ->
                row.foodId = food.id
                val allowed = IngredientUnit.availableFor(food)
                if (row.unit !in allowed) {
                    row.unit = IngredientUnit.SERVINGS
                    item.quantity.text?.clear()
                }
                configureUnit(row)
                update()
            }
        }
        item.removeIngredient.setOnClickListener {
            rows.remove(row)
            page.ingredients.removeView(item.root)
            update()
        }
        item.quantity.addTextChangedListener { update() }
        update()
    }

    private fun configureUnit(row: IngredientRow) {
        val item = row.binding
        val food = viewModel.getFood(row.foodId)
        item.pickFood.text = food?.name ?: "Replace missing ingredient"
        item.pickFood.contentDescription = "Choose food for ${food?.name ?: "missing ingredient"}"
        val allowed = IngredientUnit.availableFor(food)
        val needsWeight = food?.kind == org.archuser.CalCount.data.model.FoodKind.LIQUID &&
            IngredientUnit.GRAMS !in allowed
        val weightUnits = listOf(IngredientUnit.GRAMS, IngredientUnit.OUNCES)
        val setupUnits = if (needsWeight) weightUnits else emptyList()
        val units = (allowed + setupUnits + row.unit).distinct()
        fun label(unit: IngredientUnit): String {
            val name = when (unit) {
                IngredientUnit.OUNCES -> "oz (weight)"
                IngredientUnit.FLUID_OUNCES -> "fl oz (volume)"
                else -> UiFormatters.ingredientUnit(unit)
            }
            return when (unit) {
                in allowed -> name
                in setupUnits -> "$name · Add serving weight"
                else -> "$name (unavailable)"
            }
        }
        item.unit.setAdapter(NonFilteringArrayAdapter(item.root.context,
            com.google.android.material.R.layout.mtrl_auto_complete_simple_item, units.map(::label)))
        item.unit.setText(label(row.unit), false)
        item.unit.setOnItemClickListener { _, _, position, _ ->
            val selected = units[position]
            if (food != null && selected in setupUnits) {
                item.unit.setText(label(row.unit), false)
                promptForServingWeight(food, row, selected)
            } else {
                row.unit = selected
                update()
            }
        }
    }

    private fun promptForServingWeight(food: Food, row: IngredientRow, unit: IngredientUnit) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(builder.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, 0)
        }
        val basis = "${UiFormatters.number(food.servingVolumeMilliliters)} mL"
        content.addView(android.widget.TextView(builder.context).apply {
            text = "Weigh ${food.servingDescription} ($basis), excluding the container. This saves the measured weight to the label in Cans."
        })
        val field = TextInputEditText(builder.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val input = TextInputLayout(builder.context).apply {
            hint = "Serving weight (grams)"
            addView(field)
        }
        content.addView(input)
        val dialog = builder.setTitle("Add serving weight for ${food.name}").setView(content)
            .setNegativeButton(R.string.cancel, null).setPositiveButton("Save weight", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val grams = field.text?.toString()?.toDoubleOrNull() ?: Double.NaN
                if (viewModel.addMeasuredServingWeight(food.id, grams) { input.error = it }) {
                    row.unit = unit
                    rows.filter { it.foodId == food.id }.forEach(::configureUnit)
                    update()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun chooseFood(select: (Food) -> Unit) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val pickerContext = builder.context
        val foods = viewModel.uiState.value?.foods.orEmpty()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(pickerContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val query = TextInputEditText(pickerContext).apply { setSingleLine() }
        content.addView(TextInputLayout(pickerContext).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = "Search foods"
            addView(query)
        })
        val list = ListView(pickerContext)
        content.addView(list)
        val empty = android.widget.TextView(pickerContext).apply {
            text = "No foods match your search."
            setPadding(0, padding, 0, padding)
        }
        content.addView(empty)
        list.emptyView = empty
        var matches = foods
        fun filter() {
            val search = query.text?.toString().orEmpty().trim()
            matches = foods.filter { it.name.contains(search, ignoreCase = true) }
            list.adapter = ArrayAdapter(pickerContext, android.R.layout.simple_list_item_1, matches.map {
                "${it.name} • ${it.servingDescription}"
            })
        }
        val picker = builder.setTitle("Choose ingredient").setView(content)
            .setNegativeButton(R.string.cancel, null).create()
        query.addTextChangedListener { filter() }
        list.setOnItemClickListener { _, _, position, _ ->
            val food = matches[position]
            picker.dismiss()
            select(food)
        }
        filter()
        picker.show()
    }

    private fun showError(message: String) {
        binding?.saveError?.apply {
            text = message
            isVisible = true
        }
    }

    private fun update(showErrors: Boolean = false): Recipe? {
        val page = binding ?: return null
        val state = viewModel.uiState.value ?: return null
        page.saveError.isVisible = false
        val name = page.recipeName.text.toString().trim()
        val yield = page.yieldAmount.text.toString().toDoubleOrNull()
        page.recipeNameLayout.error = "Enter a recipe name.".takeIf { showErrors && name.isBlank() }
        page.yieldAmountLayout.error = "Enter a positive yield.".takeIf { yield == null || !yield.isFinite() || yield <= 0 }
        var validationMessage = when {
            name.isBlank() -> "Enter a recipe name."
            page.yieldAmountLayout.error != null -> page.yieldAmountLayout.error.toString()
            rows.isEmpty() -> "Add at least one ingredient."
            else -> null
        }
        val ingredients = rows.map { row ->
            val item = row.binding
            val food = state.foods.firstOrNull { it.id == row.foodId }
            val amount = item.quantity.text.toString().toDoubleOrNull()
            item.foodError.isVisible = food == null
            item.foodError.text = "This ingredient is missing. Choose another food."
            item.quantityLayout.error = "Enter a positive quantity.".takeIf { amount == null || !amount.isFinite() || amount <= 0 }
            item.unitLayout.error = "Choose a compatible unit.".takeIf { row.unit !in IngredientUnit.availableFor(food) }
            if (validationMessage == null) {
                validationMessage = when {
                    food == null -> "Replace the missing ingredient."
                    item.quantityLayout.error != null -> "Enter a positive quantity for ${food.name}."
                    item.unitLayout.error != null -> "Choose a compatible unit for ${food.name}."
                    else -> null
                }
            }
            RecipeIngredient(row.foodId, amount ?: Double.NaN, row.unit, item.notes.text.toString().trim().ifBlank { null })
        }
        val draft = Recipe(id, name, yield ?: Double.NaN, ingredients,
            page.description.text.toString().trim().ifBlank { null }, page.notes.text.toString().trim().ifBlank { null })
        validationMessage?.let { message ->
            page.nutrition.showMessage(message)
            if (showErrors) showError(message)
            return null
        }
        val result = runCatching { RecipeCalculator.calculate(draft, state.foods) }.getOrElse {
            val message = it.message ?: "Nutrition unavailable."
            page.nutrition.showMessage(message)
            if (showErrors) showError(message)
            return null
        }
        page.nutrition.showNutrition(if (wholeRecipe) result.total else result.perServing,
            state.goals, completeness = result.completeness)
        return draft
    }

    private data class IngredientRow(val binding: ItemRecipeEditorIngredientBinding, var foodId: String, var unit: IngredientUnit)

    companion object {
        fun newInstance(recipeId: String?) = RecipeEditorDialogFragment().apply {
            arguments = bundleOf("recipeId" to recipeId)
        }
    }
}
