package org.archuser.CalCount.ui.library

import android.net.Uri
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.archuser.CalCount.data.Carousel
import org.archuser.CalCount.data.RecipeArchive
import org.archuser.CalCount.data.model.Recipe
import java.util.UUID
import java.io.File
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.webkit.URLUtil
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.archuser.CalCount.BuildConfig
import org.archuser.CalCount.MainActivity
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.databinding.DialogCatalogMetadataBinding
import org.archuser.CalCount.databinding.FragmentFoodLibraryBinding
import org.archuser.CalCount.databinding.ItemFoodBinding
import org.archuser.CalCount.databinding.ItemRecipeBinding
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.domain.RecipeCalculator
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters

class FoodLibraryFragment : Fragment() {

    private var _binding: FragmentFoodLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var latestState = AppUiState()
    private var showingRecipes = false
    // Document providers may append .json to a filename with an unknown extension.
    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"), ::writeDocument
    )
    private val importDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) (requireActivity() as MainActivity).importDocument(uri)
        }

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

        binding.searchEditText.addTextChangedListener { render(latestState) }
        binding.searchRecipes.addTextChangedListener { render(latestState) }
        binding.catalogLinkText.text = HtmlCompat.fromHtml(
            getString(R.string.food_library_catalog_link),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.catalogLinkText.movementMethod = LinkMovementMethod.getInstance()
        binding.importButton.setOnClickListener {
            promptForImportMethod()
        }

        showingRecipes = savedInstanceState?.getBoolean("showing_recipes") ?: showingRecipes
        ViewCompat.setAccessibilityHeading(binding.pantryPanelTitle, true)
        binding.swapPanelButton.setOnClickListener {
            showingRecipes = !showingRecipes
            render(latestState)
        }
        binding.createRecipeButton.setOnClickListener {
            RecipeDialogs(requireContext(), viewModel, childFragmentManager).create()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            latestState = state
            render(state)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("showing_recipes", showingRecipes)
    }

    private fun render(state: AppUiState) {
        binding.cansPanel.isVisible = !showingRecipes
        binding.recipesPanel.isVisible = showingRecipes
        binding.pantryPanelTitle.setText(if (showingRecipes) R.string.pantry_recipes else R.string.pantry_cans)
        val target = getString(if (showingRecipes) R.string.pantry_cans else R.string.pantry_recipes)
        binding.swapPanelButton.contentDescription = getString(R.string.pantry_swap_to, target)
        if (showingRecipes) {
            populateRecipes(state)
            return
        }

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

    private fun populateRecipes(state: AppUiState) {
        val query = binding.searchRecipes.text?.toString().orEmpty().trim()
        val recipes = state.recipes.filter { it.name.contains(query, ignoreCase = true) }
        binding.emptyRecipesState.isVisible = recipes.isEmpty()
        binding.emptyRecipesState.text = if (query.isBlank()) getString(R.string.pantry_empty_recipes)
            else "No recipes match \"$query\"."
        binding.recipesContainer.removeAllViews()
        val dialogs = RecipeDialogs(requireContext(), viewModel, childFragmentManager)
        recipes.forEach { recipe ->
            val card = ItemRecipeBinding.inflate(layoutInflater, binding.recipesContainer, false)
            card.recipeName.text = recipe.name
            ViewCompat.setAccessibilityHeading(card.recipeName, true)
            card.recipeMeta.text = UiFormatters.recipeMetadata(recipe)
            val result = runCatching { RecipeCalculator.calculate(recipe, state.foods) }.getOrNull()
            if (result == null) {
                card.recipeNutrition.text = "Nutrition unavailable"
                card.nutritionNotice.isVisible = false
            } else {
                val nutrients = listOf(MainMacro.CALORIES, MainMacro.CARBS, MainMacro.FAT, MainMacro.PROTEIN)
                card.recipeNutrition.text = nutrients.joinToString(" • ") {
                    UiFormatters.recipeNutrientLabel(it, result.perServing, result.completeness[it])
                }
                card.nutritionNotice.isVisible = result.completeness.values.any { it != NutrientCompleteness.COMPLETE }
                card.nutritionNotice.text = "Some nutrition information is incomplete."
            }
            card.root.setOnLongClickListener {
                dialogs.confirmDeletion(recipe)
                true
            }
            card.details.setOnClickListener { dialogs.details(recipe) }
            card.share.setOnClickListener { exportRecipe(recipe) }
            card.edit.setOnClickListener { dialogs.edit(recipe) }
            card.log.setOnClickListener { dialogs.log(recipe) }
            binding.recipesContainer.addView(card.root)
        }
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
            val servingAmount = if (food.kind == FoodKind.LIQUID) {
                UiFormatters.volume(food.servingVolumeMilliliters, goals.preferredLiquidUnit)
            } else {
                UiFormatters.grams(food.servingWeightGrams)
            }
            itemBinding.foodServing.text =
                "${food.servingDescription} • $servingAmount"
            val mainMacroValue = UiFormatters.mainMacroLabeledValue(food.nutritionPerServing, goals.mainMacro)
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
            itemBinding.shareButton.setOnClickListener {
                promptForExportMethod(food)
            }
            itemBinding.shareButton.setOnLongClickListener {
                if (BuildConfig.DEBUG) {
                    promptForCatalogExport(food)
                    true
                } else {
                    false
                }
            }
            itemBinding.editButton.setOnClickListener {
                viewModel.beginEditingFood(food.id)
                (activity as? MainActivity)?.navigateToCreateFood()
            }
            itemBinding.root.setOnLongClickListener {
                promptForDelete(food)
                true
            }

            container.addView(itemBinding.root)
        }
    }

    private fun buildOptionalInfo(food: Food): String {
        val details = mutableListOf<String>()
        food.servingsPerContainer?.let {
            details += "${UiFormatters.servings(it)} per container"
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

    private fun promptForCatalogExport(food: Food) {
        val dialogBinding = DialogCatalogMetadataBinding.inflate(layoutInflater)
        dialogBinding.catalogIdInput.setText(buildDefaultCatalogId(food))
        dialogBinding.catalogRevisionInput.setText("1")
        dialogBinding.catalogPublisherInput.setText(getString(R.string.catalog_publisher_default))
        dialogBinding.catalogSourceNameInput.setText(getString(R.string.catalog_source_name_default))
        dialogBinding.catalogLicenseInput.setText(getString(R.string.catalog_license_default))

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.catalog_export_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .show()

        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val metadata = readCatalogMetadata(dialogBinding) ?: return@setOnClickListener
            val exportPayload = viewModel.exportCatalogFoodJson(food.id, metadata) ?: return@setOnClickListener
            dialog.dismiss()
            saveDocument(AppViewModel.TransferFile(
                buildExportFileName(exportPayload.foodName, "catalog.ccnl"),
                "ccnl", exportPayload.json.encodeToByteArray()
            ))
        }
    }

    private fun promptForExportMethod(food: Food) {
        val export = viewModel.exportFoodJson(food.id) ?: return
        offerExport(AppViewModel.TransferFile(
            buildExportFileName(export.foodName, "ccnl"), "ccnl", export.json.encodeToByteArray()
        ))
    }

    private fun promptForImportMethod() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import label or recipe")
            .setMessage("Select a CCNL nutritional label or a CCR recipe. Legacy CalCount JSON labels are also supported.")
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.transfer_method_file) { _, _ -> importDocumentLauncher.launch(arrayOf("*/*")) }
            .setPositiveButton(R.string.transfer_method_qr) { _, _ ->
                viewModel.qrReceiver = Carousel.Receiver()
                QrImportDialogFragment.newInstance().show(childFragmentManager, "qr_import")
            }
            .show()
    }

    private fun exportRecipe(recipe: Recipe) {
        val foods = latestState.foods
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { RecipeArchive.encode(recipe, foods) } }
            result.onSuccess {
                offerExport(AppViewModel.TransferFile(buildExportFileName(recipe.name, "ccr"), "ccr", it))
            }.onFailure { showLibraryMessage(it.message ?: "Could not export this recipe.") }
        }
    }

    private fun offerExport(file: AppViewModel.TransferFile) {
        val title = if (file.type == "ccnl") "Share nutritional label (.ccnl)" else "Share recipe (.ccr)"
        MaterialAlertDialogBuilder(requireContext()).setTitle(title)
            .setItems(arrayOf("Save file", "Share file", "Show QR")) { _, index ->
                when (index) {
                    0 -> saveDocument(file)
                    1 -> shareFile(file)
                    2 -> {
                        if (file.bytes.size > Carousel.CHUNK_BYTES * Carousel.MAX_FRAMES) {
                            showLibraryMessage("This transfer is too large for QR. Share it as a file.")
                        } else {
                            viewModel.pendingBroadcast = AppViewModel.Broadcast(file)
                            QrExportDialogFragment().show(childFragmentManager, "qr_export")
                        }
                    }
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun saveDocument(file: AppViewModel.TransferFile) {
        viewModel.pendingDocument = file
        createDocument.launch(file.name)
    }

    private fun writeDocument(uri: Uri?) {
        val export = viewModel.pendingDocument ?: return
        viewModel.pendingDocument = null
        if (uri == null) return
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(export.bytes) }
                        ?: error("The selected file could not be opened for writing.")
                }
            }
            result.onSuccess { showLibraryMessage("Saved ${export.name}.") }
                .onFailure { showLibraryMessage(it.message ?: "The file could not be saved.") }
        }
    }

    private fun shareFile(export: AppViewModel.TransferFile) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val directory = File(context.cacheDir, "shared-transfers")
                    check(directory.isDirectory || directory.mkdirs()) { "Could not prepare the shared file." }
                    val share = File(directory, UUID.randomUUID().toString())
                    check(share.mkdir()) { "Could not prepare the shared file." }
                    File(share, export.name).apply { writeBytes(export.bytes) }
                }
            }
            result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.transfers", file)
                val intent = Intent(Intent.ACTION_SEND).setType(export.mime).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = ClipData.newUri(context.contentResolver, export.name, uri)
                startActivity(Intent.createChooser(intent, null))
            }.onFailure { showLibraryMessage(it.message ?: "The file could not be shared.") }
        }
    }

    private fun promptForDelete(food: Food) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_food_title, food.name))
            .setMessage(R.string.delete_food_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_food_confirm) { _, _ ->
                viewModel.deleteFood(food.id)
            }
            .show()
    }

    private fun readCatalogMetadata(
        dialogBinding: DialogCatalogMetadataBinding
    ): AppViewModel.CatalogExportMetadata? {
        clearCatalogErrors(dialogBinding)

        val catalogId = dialogBinding.catalogIdInput.text?.toString().orEmpty().trim()
        val revisionRaw = dialogBinding.catalogRevisionInput.text?.toString().orEmpty().trim()
        val publisher = dialogBinding.catalogPublisherInput.text?.toString().orEmpty().trim()
        val sourceName = dialogBinding.catalogSourceNameInput.text?.toString().orEmpty().trim()
        val sourceUrl = dialogBinding.catalogSourceUrlInput.text?.toString().orEmpty().trim().ifBlank { null }
        val license = dialogBinding.catalogLicenseInput.text?.toString().orEmpty().trim().ifBlank { null }
        val regionCode = dialogBinding.catalogRegionCodeInput.text?.toString().orEmpty().trim().ifBlank { null }
        val notes = dialogBinding.catalogNotesInput.text?.toString().orEmpty().trim().ifBlank { null }

        var hasError = false

        if (catalogId.isBlank()) {
            dialogBinding.catalogIdInputLayout.error = getString(R.string.catalog_error_catalog_id)
            hasError = true
        }

        val revision = revisionRaw.toIntOrNull()
        if (revision == null || revision <= 0) {
            dialogBinding.catalogRevisionInputLayout.error = getString(R.string.catalog_error_revision)
            hasError = true
        }

        if (publisher.isBlank()) {
            dialogBinding.catalogPublisherInputLayout.error = getString(R.string.catalog_error_publisher)
            hasError = true
        }

        if (sourceName.isBlank()) {
            dialogBinding.catalogSourceNameInputLayout.error = getString(R.string.catalog_error_source_name)
            hasError = true
        }

        if (sourceUrl != null && !URLUtil.isValidUrl(sourceUrl)) {
            dialogBinding.catalogSourceUrlInputLayout.error = getString(R.string.catalog_error_source_url)
            hasError = true
        }

        if (hasError) {
            return null
        }

        return AppViewModel.CatalogExportMetadata(
            catalogId = catalogId,
            revision = revision!!,
            publisher = publisher,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            license = license,
            regionCode = regionCode,
            notes = notes
        )
    }

    private fun clearCatalogErrors(dialogBinding: DialogCatalogMetadataBinding) {
        dialogBinding.catalogIdInputLayout.error = null
        dialogBinding.catalogRevisionInputLayout.error = null
        dialogBinding.catalogPublisherInputLayout.error = null
        dialogBinding.catalogSourceNameInputLayout.error = null
        dialogBinding.catalogSourceUrlInputLayout.error = null
    }

    private fun showLibraryMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
            .show()
    }

    private fun buildExportFileName(foodName: String, extension: String): String {
        val sanitizedName = foodName
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(100)
            .ifBlank { "calcount" }
        return "$sanitizedName.$extension"
    }

    private fun buildDefaultCatalogId(food: Food): String {
        val slug = food.name
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), ".")
            .trim('.')
            .ifBlank { "food" }
        return "community.$slug"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
