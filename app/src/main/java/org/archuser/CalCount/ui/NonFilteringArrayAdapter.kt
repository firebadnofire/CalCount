package org.archuser.CalCount.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class NonFilteringArrayAdapter(
    context: Context,
    resource: Int,
    items: List<String>
) : ArrayAdapter<String>(context, resource, items.toList()) {

    private val allItems = items.toList()

    private val noFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults().apply {
                values = allItems
                count = allItems.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return resultValue?.toString().orEmpty()
        }
    }

    override fun getFilter(): Filter = noFilter
}

