package org.archuser.CalCount.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.LogEntry

internal fun confirmLogDeletion(context: Context, entry: LogEntry, viewModel: AppViewModel) {
    MaterialAlertDialogBuilder(context)
        .setTitle("Remove ${entry.foodName} from your log?")
        .setMessage("This removes one logged entry and updates the day’s totals.")
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton("Delete") { _, _ -> viewModel.deleteLogEntry(entry.id) }
        .show()
}
