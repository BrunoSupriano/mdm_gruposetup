package br.com.gruposetup.mdm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView

class ColaboradorAdapter(context: Context) : ArrayAdapter<ColabItem>(context, 0) {

    private val itens = mutableListOf<ColabItem>()

    override fun getCount() = itens.size
    override fun getItem(position: Int): ColabItem = itens[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_colaborador, parent, false)
        val item = itens[position]
        v.findViewById<TextView>(R.id.itemNome).text = item.nome
        v.findViewById<TextView>(R.id.itemCargo).text = item.cargo
        return v
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val q = constraint?.toString()?.trim() ?: ""
            val res = if (q.length >= 2) ApiClient.buscarColaboradores(q) else emptyList()
            return FilterResults().apply { values = res; count = res.size }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            itens.clear()
            (results?.values as? List<ColabItem>)?.let { itens.addAll(it) }
            if ((results?.count ?: 0) > 0) notifyDataSetChanged() else notifyDataSetInvalidated()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? ColabItem)?.nome ?: ""
    }
}
