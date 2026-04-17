package org.archuser.CalCount.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.databinding.FragmentHistoryBinding
import org.archuser.CalCount.databinding.ItemHistoryDayBinding
import org.archuser.CalCount.ui.AppUiState
import org.archuser.CalCount.ui.AppViewModel
import org.archuser.CalCount.ui.UiFormatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AppViewModel
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
    }

    private fun render(state: AppUiState) {
        val zoneId = ZoneId.systemDefault()
        val days = state.logs
            .groupBy { it.localDate(zoneId) }
            .toSortedMap(compareByDescending { it })

        binding.historyEmptyState.isVisible = days.isEmpty()
        binding.historyDaysContainer.removeAllViews()

        days.forEach { (day, entries) ->
            val nutrition = entries.fold(NutritionSnapshot.ZERO) { total, entry ->
                total + entry.calculatedNutrition
            }

            val itemBinding = ItemHistoryDayBinding.inflate(layoutInflater, binding.historyDaysContainer, false)
            itemBinding.dayDate.text = day.format(dateFormatter)
            itemBinding.dayCalories.text = UiFormatters.mainMacroLabeledValue(nutrition, state.goals.mainMacro)
            itemBinding.dayMacros.text = UiFormatters.macroSummarySelectionLine(nutrition, state.goals).orEmpty()
            itemBinding.dayMeta.text = "${entries.size} entries"
            itemBinding.root.setOnClickListener {
                findNavController().navigate(
                    R.id.dayDetailFragment,
                    bundleOf(ARG_DATE_ISO to day.toString())
                )
            }
            binding.historyDaysContainer.addView(itemBinding.root)
        }
    }

    private fun LogEntry.localDate(zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(loggedAtEpochMillis).atZone(zoneId).toLocalDate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_DATE_ISO = "dateIso"
    }
}
