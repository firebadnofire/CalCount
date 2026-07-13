package org.archuser.CalCount.ui.library

import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.webkit.URLUtil
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.text.HtmlCompat
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
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters

class FoodLibraryFragment : Fragment() {

    private var _binding: FragmentFoodLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private var latestState = AppUiState()
    private var pendingExport: PendingExport? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val export = pendingExport ?: return@registerForActivityResult
            pendingExport = null
            if (uri == null) {
                return@registerForActivityResult
            }
            writeExportToUri(export, uri)
        }

    private val importDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }
            importFoodFromUri(uri)
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

        binding.searchEditText.addTextChangedListener {
            render(latestState)
        }
        binding.catalogLinkText.text = HtmlCompat.fromHtml(
            getString(R.string.food_library_catalog_link),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.catalogLinkText.movementMethod = LinkMovementMethod.getInstance()
        binding.importButton.setOnClickListener {
            promptForImportMethod()
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
            pendingExport = PendingExport(
                fileName = buildCatalogExportFileName(exportPayload.foodName),
                json = exportPayload.json
            )
            dialog.dismiss()
            createDocumentLauncher.launch(pendingExport!!.fileName)
        }
    }

    private fun promptForExportMethod(food: Food) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.transfer_method_title)
            .setMessage(R.string.transfer_method_message)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.transfer_method_file) { _, _ ->
                exportFoodToDocument(food)
            }
            .setPositiveButton(R.string.transfer_method_qr) { _, _ ->
                exportFoodToQr(food)
            }
            .show()
    }

    private fun promptForImportMethod() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.transfer_method_title)
            .setMessage(R.string.transfer_method_message)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.transfer_method_file) { _, _ ->
                importDocumentLauncher.launch(arrayOf("application/json", "text/*"))
            }
            .setPositiveButton(R.string.transfer_method_qr) { _, _ ->
                QrImportDialogFragment.newInstance()
                    .show(childFragmentManager, "qr_import")
            }
            .show()
    }

    private fun exportFoodToDocument(food: Food) {
        val exportPayload = viewModel.exportFoodJson(food.id) ?: return
        pendingExport = PendingExport(
            fileName = buildExportFileName(exportPayload.foodName),
            json = exportPayload.json
        )
        createDocumentLauncher.launch(pendingExport!!.fileName)
    }

    private fun exportFoodToQr(food: Food) {
        val exportPayload = viewModel.exportFoodJson(food.id) ?: return
        runCatching {
            QrTransferCodec.encodeToMatrix(exportPayload.json)
        }.onSuccess {
            QrExportDialogFragment.newInstance(exportPayload.json)
                .show(childFragmentManager, "qr_export_${food.id}")
        }.onFailure {
            showLibraryMessage(getString(R.string.qr_export_too_large))
        }
    }

    private fun writeExportToUri(export: PendingExport, uri: Uri) {
        val contentResolver = requireContext().contentResolver
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write(export.json)
            } ?: error("The selected file could not be opened for writing.")
        }.onSuccess {
            showLibraryMessage(getString(R.string.export_food_saved))
        }.onFailure { error ->
            showLibraryMessage(
                getString(R.string.export_food_save_failed, error.message ?: "unknown error")
            )
        }
    }

    private fun importFoodFromUri(uri: Uri) {
        val contentResolver = requireContext().contentResolver
        val rawJson = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readText()
            } ?: return showLibraryMessage(getString(R.string.import_food_open_failed))
        } catch (error: Exception) {
            return showLibraryMessage(
                getString(R.string.import_food_open_failed_detail, error.message ?: "unknown error")
            )
        }

        viewModel.importFoodJson(rawJson)
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

    private fun buildExportFileName(foodName: String): String {
        val sanitizedName = foodName
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "food" }
        return "$sanitizedName.json"
    }

    private fun buildCatalogExportFileName(foodName: String): String {
        return buildExportFileName(foodName).removeSuffix(".json") + ".catalog.json"
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
        pendingExport = null
        _binding = null
    }

    private data class PendingExport(
        val fileName: String,
        val json: String
    )
}
