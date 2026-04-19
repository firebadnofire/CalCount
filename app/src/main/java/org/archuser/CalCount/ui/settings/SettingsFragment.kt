package org.archuser.CalCount.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.checkbox.MaterialCheckBox
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MacroTier
import org.archuser.CalCount.data.model.VolumeUnit
import org.archuser.CalCount.data.model.WeightUnit
import org.archuser.CalCount.databinding.FragmentSettingsBinding
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.NonFilteringArrayAdapter
import org.archuser.CalCount.ui.UiFormatters

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private val macroCheckboxes = mutableMapOf<MainMacro, MaterialCheckBox>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]

        binding.preferredUnitDropdown.setAdapter(
            NonFilteringArrayAdapter(
                context = requireContext(),
                resource = com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                items = WeightUnit.entries.map { it.displayName }
            )
        )
        binding.preferredUnitDropdown.threshold = 0

        binding.preferredLiquidUnitDropdown.setAdapter(
            NonFilteringArrayAdapter(
                context = requireContext(),
                resource = com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                items = VolumeUnit.entries.map { it.displayName }
            )
        )
        binding.preferredLiquidUnitDropdown.threshold = 0

        binding.mainMacroDropdown.setAdapter(
            NonFilteringArrayAdapter(
                context = requireContext(),
                resource = com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                items = MainMacro.entries.map { it.displayName }
            )
        )
        binding.mainMacroDropdown.threshold = 0

        setupMacroCheckboxes()
        binding.toggleUsefulMacrosButton.setOnClickListener {
            setUsefulExpanded(!binding.usefulMacrosContainer.isVisible)
        }
        binding.toggleExtendedMacrosButton.setOnClickListener {
            setExtendedExpanded(!binding.extendedMacrosContainer.isVisible)
        }

        binding.saveSettingsButton.setOnClickListener {
            val previousUseMaterialYou = viewModel.uiState.value?.goals?.useMaterialYou ?: true
            val macroSummarySelection = macroCheckboxes
                .filterValues { it.isChecked }
                .keys
                .toSet()
            val didSave = viewModel.updateGoals(
                AppViewModel.UpdateGoalsInput(
                    dailyCalories = binding.dailyCaloriesEditText.text?.toString().orEmpty(),
                    fatTargetGrams = binding.fatTargetEditText.text?.toString().orEmpty(),
                    carbsTargetGrams = binding.carbsTargetEditText.text?.toString().orEmpty(),
                    proteinTargetGrams = binding.proteinTargetEditText.text?.toString().orEmpty(),
                    saturatedFatTargetGrams = binding.saturatedFatTargetEditText.text?.toString().orEmpty(),
                    fiberTargetGrams = binding.fiberTargetEditText.text?.toString().orEmpty(),
                    sugarsTargetGrams = binding.sugarsTargetEditText.text?.toString().orEmpty(),
                    sodiumTargetMilligrams = binding.sodiumTargetEditText.text?.toString().orEmpty(),
                    potassiumTargetMilligrams = binding.potassiumTargetEditText.text?.toString().orEmpty(),
                    preferredUnit = binding.preferredUnitDropdown.text?.toString().orEmpty(),
                    preferredLiquidUnit = binding.preferredLiquidUnitDropdown.text?.toString().orEmpty(),
                    useMaterialYou = binding.useMaterialYouCheckbox.isChecked,
                    mainMacro = binding.mainMacroDropdown.text?.toString().orEmpty(),
                    showCaloriesInLivePreview = binding.showCaloriesPreviewCheckbox.isChecked,
                    macroSummarySelection = macroSummarySelection
                )
            )

            if (didSave) {
                binding.root.clearFocus()
                if (previousUseMaterialYou != binding.useMaterialYouCheckbox.isChecked) {
                    requireActivity().recreate()
                }
            }
        }

        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.settingsSavedEvent.observe(viewLifecycleOwner) { eventTimestamp ->
            if (eventTimestamp == null) {
                return@observe
            }
            Toast.makeText(requireContext(), "Settings saved", Toast.LENGTH_SHORT).show()
            viewModel.consumeSettingsSavedEvent()
        }
    }

    private fun render(state: AppUiState) {
        binding.weightStorageNote.text =
            "Food amounts store as grams and liquid amounts store as ml. Logged amounts display as ${state.goals.preferredUnit.shortLabel} and ${state.goals.preferredLiquidUnit.shortLabel}."

        binding.dailyCaloriesEditText.setText(UiFormatters.number(state.goals.dailyCalories))
        binding.fatTargetEditText.setText(state.goals.fatTargetGrams?.let(UiFormatters::number).orEmpty())
        binding.carbsTargetEditText.setText(state.goals.carbsTargetGrams?.let(UiFormatters::number).orEmpty())
        binding.proteinTargetEditText.setText(state.goals.proteinTargetGrams?.let(UiFormatters::number).orEmpty())
        binding.saturatedFatTargetEditText.setText(state.goals.saturatedFatTargetGrams?.let(UiFormatters::number).orEmpty())
        binding.fiberTargetEditText.setText(state.goals.fiberTargetGrams?.let(UiFormatters::number).orEmpty())
        binding.sugarsTargetEditText.setText(state.goals.sugarsTargetGrams?.let(UiFormatters::number).orEmpty())
        binding.sodiumTargetEditText.setText(state.goals.sodiumTargetMilligrams?.let(UiFormatters::number).orEmpty())
        binding.potassiumTargetEditText.setText(state.goals.potassiumTargetMilligrams?.let(UiFormatters::number).orEmpty())
        binding.preferredUnitDropdown.setText(state.goals.preferredUnit.displayName, false)
        binding.preferredLiquidUnitDropdown.setText(state.goals.preferredLiquidUnit.displayName, false)
        binding.useMaterialYouCheckbox.isChecked = state.goals.useMaterialYou
        binding.mainMacroDropdown.setText(state.goals.mainMacro.displayName, false)
        binding.showCaloriesPreviewCheckbox.isChecked = state.goals.showCaloriesInLivePreview

        val selectedMacros = state.goals.macroSummarySelection
        macroCheckboxes.forEach { (macro, checkbox) ->
            checkbox.isChecked = selectedMacros.contains(macro)
        }
    }

    private fun setupMacroCheckboxes() {
        macroCheckboxes.clear()
        binding.mainMacrosContainer.removeAllViews()
        binding.usefulMacrosContainer.removeAllViews()
        binding.extendedMacrosContainer.removeAllViews()

        addMacroCheckboxes(
            macros = MainMacro.entries
                .filter { it.tier == MacroTier.MAIN }
                .filterNot { it == MainMacro.CALORIES },
            container = binding.mainMacrosContainer
        )
        addMacroCheckboxes(
            macros = MainMacro.entries.filter { it.tier == MacroTier.USEFUL },
            container = binding.usefulMacrosContainer
        )
        addMacroCheckboxes(
            macros = MainMacro.entries.filter { it.tier == MacroTier.EXTENDED },
            container = binding.extendedMacrosContainer
        )

        setUsefulExpanded(false)
        setExtendedExpanded(false)
    }

    private fun addMacroCheckboxes(macros: List<MainMacro>, container: ViewGroup) {
        macros.forEach { macro ->
            val checkbox = MaterialCheckBox(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = macro.displayName
            }
            container.addView(checkbox)
            macroCheckboxes[macro] = checkbox
        }
    }

    private fun setUsefulExpanded(isExpanded: Boolean) {
        binding.usefulMacrosContainer.isVisible = isExpanded
        binding.toggleUsefulMacrosButton.text = if (isExpanded) {
            "Hide useful nutrients"
        } else {
            "Show useful nutrients"
        }
    }

    private fun setExtendedExpanded(isExpanded: Boolean) {
        binding.extendedMacrosContainer.isVisible = isExpanded
        binding.toggleExtendedMacrosButton.text = if (isExpanded) {
            "Hide extended nutrients"
        } else {
            "Show extended nutrients"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
