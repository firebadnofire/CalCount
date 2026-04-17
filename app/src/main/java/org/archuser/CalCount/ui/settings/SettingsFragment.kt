package org.archuser.CalCount.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.archuser.CalCount.data.model.MainMacro
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

        binding.mainMacroDropdown.setAdapter(
            NonFilteringArrayAdapter(
                context = requireContext(),
                resource = com.google.android.material.R.layout.mtrl_auto_complete_simple_item,
                items = MainMacro.entries.map { it.displayName }
            )
        )
        binding.mainMacroDropdown.threshold = 0

        binding.saveSettingsButton.setOnClickListener {
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
                    mainMacro = binding.mainMacroDropdown.text?.toString().orEmpty(),
                    showCaloriesInLivePreview = binding.showCaloriesPreviewCheckbox.isChecked,
                    showProteinInLivePreview = binding.showProteinPreviewCheckbox.isChecked,
                    showCarbsInLivePreview = binding.showCarbsPreviewCheckbox.isChecked,
                    showFatInLivePreview = binding.showFatPreviewCheckbox.isChecked,
                    showSaturatedFatInLivePreview = binding.showSaturatedFatPreviewCheckbox.isChecked,
                    showFiberInLivePreview = binding.showFiberPreviewCheckbox.isChecked,
                    showSugarsInLivePreview = binding.showSugarsPreviewCheckbox.isChecked,
                    showSodiumInLivePreview = binding.showSodiumPreviewCheckbox.isChecked,
                    showPotassiumInLivePreview = binding.showPotassiumPreviewCheckbox.isChecked
                )
            )

            if (didSave) {
                binding.root.clearFocus()
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
            "Weight-mode entries are converted to grams internally and shown as ${state.goals.preferredUnit.shortLabel} where appropriate."

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
        binding.mainMacroDropdown.setText(state.goals.mainMacro.displayName, false)
        binding.showCaloriesPreviewCheckbox.isChecked = state.goals.showCaloriesInLivePreview
        binding.showProteinPreviewCheckbox.isChecked = state.goals.showProteinInLivePreview
        binding.showCarbsPreviewCheckbox.isChecked = state.goals.showCarbsInLivePreview
        binding.showFatPreviewCheckbox.isChecked = state.goals.showFatInLivePreview
        binding.showSaturatedFatPreviewCheckbox.isChecked = state.goals.showSaturatedFatInLivePreview
        binding.showFiberPreviewCheckbox.isChecked = state.goals.showFiberInLivePreview
        binding.showSugarsPreviewCheckbox.isChecked = state.goals.showSugarsInLivePreview
        binding.showSodiumPreviewCheckbox.isChecked = state.goals.showSodiumInLivePreview
        binding.showPotassiumPreviewCheckbox.isChecked = state.goals.showPotassiumInLivePreview
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
