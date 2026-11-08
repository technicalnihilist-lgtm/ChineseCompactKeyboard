package com.example.chinesecompactkeyboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chinesecompactkeyboard.R
import com.example.chinesecompactkeyboard.core.Theme
import com.example.chinesecompactkeyboard.data.AppDatabase
import com.example.chinesecompactkeyboard.data.ClipboardEntry
import com.example.chinesecompactkeyboard.data.ClipboardRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardActivity : AppCompatActivity() {

    private lateinit var repository: ClipboardRepository
    private lateinit var adapter: EntryAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(Theme.activityThemeRes(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clipboard)
        repository = ClipboardRepository(this)

        recycler = findViewById(R.id.recycler)
        empty = findViewById(R.id.empty_text)
        adapter = EntryAdapter(
            onCopy = { text ->
                repository.copy(text)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onDelete = { entry ->
                repository.delete(entry.id)
                reload()
            },
            onPasteInfo = {
                Toast.makeText(this, "Tap an entry in the keyboard's ☰ panel to paste it", Toast.LENGTH_SHORT).show()
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<MaterialButton>(R.id.btn_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_all)
                .setMessage("Delete all saved clipboard items?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear_all) { _, _ ->
                    repository.clear()
                    reload()
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                AppDatabase.get(applicationContext).clipboardDao().all()
            }
            adapter.submit(items)
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private class EntryAdapter(
        val onCopy: (String) -> Unit,
        val onDelete: (ClipboardEntry) -> Unit,
        val onPasteInfo: (ClipboardEntry) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.Holder>() {

        private val items = ArrayList<ClipboardEntry>()
        private val format = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        fun submit(list: List<ClipboardEntry>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = items[position]
            holder.text.text = entry.text
            holder.time.text = format.format(Date(entry.createdAt))
            holder.copy.setOnClickListener { onCopy(entry.text) }
            holder.delete.setOnClickListener { onDelete(entry) }
            holder.itemView.setOnClickListener { onPasteInfo(entry) }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.item_text)
            val time: TextView = view.findViewById(R.id.item_time)
            val copy: MaterialButton = view.findViewById(R.id.btn_copy)
            val delete: MaterialButton = view.findViewById(R.id.btn_delete)
        }
    }
}
