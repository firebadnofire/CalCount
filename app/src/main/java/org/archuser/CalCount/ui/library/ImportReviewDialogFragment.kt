package org.archuser.CalCount.ui.library

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.CalCount.data.FoodJson
import org.archuser.CalCount.data.ImportDraft
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.databinding.DialogTransferReviewBinding
import org.archuser.CalCount.domain.NutritionCalculator
import org.archuser.CalCount.domain.RecipeCalculator
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters

class ImportReviewDialogFragment : DialogFragment() {
    private val viewModel: AppViewModel by activityViewModels()
    private lateinit var binding: DialogTransferReviewBinding
    private val content get() = binding.reviewContent

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        binding = DialogTransferReviewBinding.inflate(android.view.LayoutInflater.from(builder.context))
        render()
        return builder.setTitle("Review import")
            .setView(binding.root)
            .setNegativeButton("Cancel") { _, _ -> viewModel.abandonImport() }
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        viewModel.abandonImport()
        super.onCancel(dialog)
    }

    private fun render() {
        content.removeAllViews()
        binding.reviewActions.removeAllViews()
        binding.reviewActions.isVisible = false
        val draft = viewModel.importDraft
        if (draft == null) {
            text("The import draft has expired. Open the file or scan it again.")
            return
        }
        val conflict = draft.conflict
        if (conflict == null) {
            content.addView(TextView(content.context).apply {
                text = "Changes to import"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            })
            draft.summary.forEach(::text)
            draft.incoming.recipe?.let { original ->
                val resolved = draft.incomingRecipe ?: return@let
                if (runCatching { RecipeCalculator.calculate(resolved, draft.proposedFoods) }.getOrNull() !=
                    RecipeCalculator.calculate(original, draft.incoming.foods)) {
                    text("Your ingredient choices change the imported recipe’s nutrition or completeness.")
                }
            }
            button("Commit import") {
                if (viewModel.commitImport()) dismiss() else render()
            }
            return
        }

        text("The local and imported definitions use the same ID. Changed fields are marked below.")
        when (conflict) {
            is ImportDraft.Conflict.Label -> compareLabels(conflict.local, conflict.imported, draft)
            is ImportDraft.Conflict.RecipeDefinition -> compareRecipes(conflict.local, conflict.imported, draft)
        }
        ImportDraft.Choice.entries.forEach { choice ->
            button(choice.label) {
                if (choice == ImportDraft.Choice.IMPORTED) {
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Replace local definition?")
                        .setMessage("This updates the existing entry and any recipes that use it. Saved logs retain their snapshots.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Replace") { _, _ -> resolve(draft, choice) }
                        .show()
                } else resolve(draft, choice)
            }
        }
    }

    private fun resolve(draft: ImportDraft, choice: ImportDraft.Choice) {
        try {
            draft.resolve(choice)
            render()
            binding.reviewScroll.scrollTo(0, 0)
        } catch (error: Exception) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Choose another resolution")
                .setMessage(error.message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun compareLabels(local: Food, incoming: Food, draft: ImportDraft) {
        val fields = listOf(
            Triple("Name", local.name, incoming.name),
            Triple("Serving description", local.servingDescription, incoming.servingDescription),
            Triple("Label type", local.kind.displayName, incoming.kind.displayName),
            Triple("Serving weight (g)", local.servingWeightGrams, incoming.servingWeightGrams),
            Triple("Serving volume (mL)", local.servingVolumeMilliliters, incoming.servingVolumeMilliliters),
            Triple("Servings per container", local.servingsPerContainer, incoming.servingsPerContainer),
            Triple("Created (epoch ms)", local.createdAtEpochMillis, incoming.createdAtEpochMillis)
        )
        fields.forEach { (field, a, b) -> row(field, a, b) }
        val leftCatalog = local.catalog?.let(FoodJson::encodeCatalog)
        val rightCatalog = incoming.catalog?.let(FoodJson::encodeCatalog)
        listOf("catalogId" to "Catalog ID", "revision" to "Revision", "publisher" to "Publisher",
            "sourceName" to "Source", "sourceUrl" to "Source URL", "license" to "License",
            "regionCode" to "Region", "notes" to "Catalog notes", "verifiedAtEpochMillis" to "Verified (epoch ms)")
            .forEach { (key, label) -> row(label, leftCatalog?.opt(key), rightCatalog?.opt(key)) }
        nutrients("Per serving", local.nutritionPerServing, incoming.nutritionPerServing)
        if (local.kind == incoming.kind) {
            val solid = local.kind == FoodKind.FOOD
            val a = if (solid) local.servingWeightGrams else local.servingVolumeMilliliters
            val b = if (solid) incoming.servingWeightGrams else incoming.servingVolumeMilliliters
            if (a > 0 && b > 0 && (100 / a).isFinite() && (100 / b).isFinite()) {
                nutrients(if (solid) "Per 100 g" else "Per 100 mL",
                    NutritionCalculator.scaleNutrition(local.nutritionPerServing, 100 / a),
                    NutritionCalculator.scaleNutrition(incoming.nutritionPerServing, 100 / b))
            }
        }
        val affected = draft.original.recipes.filter { it.ingredients.any { row -> row.foodId == local.id } }
        if (affected.isNotEmpty()) text("Use Imported updates these linked recipes: ${affected.joinToString { it.name }}.")
        if (draft.incoming.recipe != null) text("Use Local can change the imported recipe’s nutrition or make its ingredient units incompatible.")
    }

    private fun compareRecipes(local: Recipe, incoming: Recipe, draft: ImportDraft) {
        row("Name", local.name, incoming.name)
        row("Description", local.description, incoming.description)
        row("Notes", local.notes, incoming.notes)
        row("Yield (servings)", local.servings, incoming.servings)
        fun ingredient(recipe: Recipe, index: Int, foods: List<Food>): String? =
            recipe.ingredients.getOrNull(index)?.let { item ->
                val name = foods.firstOrNull { it.id == item.foodId }?.name ?: "Missing food"
                listOfNotNull(name, "${UiFormatters.ingredientAmount(item.quantity, item.unit)}", item.notes, item.foodId).joinToString("\n")
            }
        repeat(maxOf(local.ingredients.size, incoming.ingredients.size)) { index ->
            row("Ingredient ${index + 1}", ingredient(local, index, draft.original.foods),
                ingredient(incoming, index, draft.proposedFoods))
        }
        val a = runCatching { RecipeCalculator.calculate(local, draft.original.foods) }
        val b = runCatching { RecipeCalculator.calculate(incoming, draft.proposedFoods) }
        if (a.isSuccess && b.isSuccess) {
            val left = a.getOrThrow()
            val right = b.getOrThrow()
            nutrients("Whole recipe", left.total, right.total, left.completeness, right.completeness)
            nutrients("Per serving", left.perServing, right.perServing, left.completeness, right.completeness)
        } else {
            row("Nutrition", a.exceptionOrNull()?.message ?: "Available", b.exceptionOrNull()?.message ?: "Available")
        }
    }

    private fun nutrients(
        title: String,
        local: NutritionSnapshot,
        imported: NutritionSnapshot,
        localCompleteness: Map<MainMacro, NutrientCompleteness>? = null,
        importedCompleteness: Map<MainMacro, NutrientCompleteness>? = null
    ) {
        text(title)
        MainMacro.entries.forEach { nutrient ->
            val a = nutrient.reportedValue(local)
            val b = nutrient.reportedValue(imported)
            row(nutrient.displayName, UiFormatters.recipeNutrientValue(nutrient, local, localCompleteness?.get(nutrient)),
                UiFormatters.recipeNutrientValue(nutrient, imported, importedCompleteness?.get(nutrient)),
                a != b || localCompleteness?.get(nutrient) != importedCompleteness?.get(nutrient))
        }
    }

    private fun row(field: String, local: Any?, imported: Any?, changed: Boolean = local != imported) {
        val context = content.context
        val density = context.resources.displayMetrics.density
        val padding = (8 * density).toInt()
        val section = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, padding, 0, padding)
        }
        section.addView(TextView(context).apply {
            text = if (changed) "$field · Changed" else field
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        })
        val values = LinearLayout(context)
        listOf("Local" to local, "Imported" to imported).forEach { (side, value) ->
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, padding / 2, padding, 0)
            }
            column.addView(TextView(context).apply {
                text = side
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            })
            column.addView(TextView(context).apply {
                text = value?.toString() ?: "Unknown"
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                contentDescription = "$field, $side: $text"
                if (changed) setTypeface(typeface, Typeface.BOLD)
            })
            values.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        section.addView(values)
        content.addView(section)
    }

    private fun text(value: String) {
        content.addView(TextView(requireContext()).apply { text = value; setPadding(0, 8, 0, 8) })
    }

    private fun button(label: String, action: () -> Unit) {
        binding.reviewActions.isVisible = true
        binding.reviewActions.addView(MaterialButton(requireContext()).apply {
            text = label
            setOnClickListener { action() }
        })
    }
}

