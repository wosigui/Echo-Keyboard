package org.fcitx.fcitx5.android.ui.main

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.journal.JournalStore
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainFragment : Fragment() {
    private var date = LocalDate.now()
    private var followToday = true
    private var filter = -1
    private var rows = emptyList<JournalStore.Entry>()
    private lateinit var dateButton: MaterialButton
    private lateinit var summary: TextView
    private lateinit var empty: TextView
    private lateinit var adapter: HistoryAdapter
    private val ink = Color.rgb(29, 43, 55)
    private val muted = Color.rgb(103, 118, 128)
    private val accent = Color.rgb(34, 110, 92)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: CharSequence, size: Float, color: Int = ink) = TextView(requireContext()).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun box(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(20).toFloat() }
    private fun button(label: Int, action: () -> Unit) = MaterialButton(requireContext(), null, android.R.attr.borderlessButtonStyle).apply {
        setText(label); setTextColor(accent); minHeight = dp(48); setOnClickListener { action() }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        date = state?.getString("date")?.let(LocalDate::parse) ?: LocalDate.now()
        filter = state?.getInt("filter") ?: -1
        followToday = state?.getBoolean("followToday", true) ?: true
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),0)
            setBackgroundColor(Color.rgb(246,248,247))
        }
        val top = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(text(getString(R.string.journal_title),30f).apply { setTypeface(typeface,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        top.addView(button(R.string.journal_settings) { navigateWithAnim(SettingsRoute.SettingsOverview) })
        root.addView(top)
        root.addView(text(getString(R.string.journal_subtitle),13f,muted).apply { setPadding(0,dp(4),0,dp(12)) })
        val hero = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; background = box(Color.rgb(224,239,232)); setPadding(dp(18),dp(10),dp(18),dp(14))
        }
        val dates = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        dateButton = button(R.string.journal_today) {
            DatePickerDialog(ctx,{ _, y,m,d -> date = LocalDate.of(y,m+1,d); followToday = false; refresh() }, date.year,date.monthValue-1,date.dayOfMonth).apply {
                datePicker.maxDate = System.currentTimeMillis(); show()
            }
        }
        dates.addView(dateButton,LinearLayout.LayoutParams(0,-2,1f))
        dates.addView(button(R.string.journal_today) { date = LocalDate.now(); followToday = true; refresh() })
        hero.addView(dates)
        summary = text("",20f,accent).apply { setTypeface(typeface,Typeface.BOLD) }
        hero.addView(summary)
        root.addView(hero)
        val toggle = Switch(ctx, null, 0, android.R.style.Widget_Material_CompoundButton_Switch).apply {
            showText = false; textOn = ""; textOff = ""
            setText(R.string.journal_recording); setTextColor(ink); isChecked = JournalStore.enabled
            setOnCheckedChangeListener { _, checked -> JournalStore.enabled = checked }
        }
        root.addView(toggle)
        root.addView(text(getString(R.string.journal_privacy),12f,muted))
        val filters = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        listOf(-1 to R.string.journal_all,0 to R.string.journal_input,1 to R.string.journal_clips).forEach { (kind,label) ->
            filters.addView(button(label) { filter = kind; render(); updateFilters(filters) },LinearLayout.LayoutParams(0,-2,1f))
        }
        updateFilters(filters)
        root.addView(filters)
        val heading = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(getString(R.string.journal_timeline),17f).apply { setTypeface(typeface,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(button(R.string.journal_clear) {
            AlertDialog.Builder(ctx).setTitle(R.string.journal_clear).setMessage(R.string.journal_clear_confirm)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok) { _,_ -> JournalStore.clear(date) }.show()
        })
        root.addView(heading)
        empty = text(getString(R.string.journal_empty),15f,muted).apply { setPadding(dp(12),dp(24),dp(12),dp(24)); gravity = Gravity.CENTER }
        root.addView(empty)
        adapter = HistoryAdapter()
        root.addView(RecyclerView(ctx).apply { layoutManager = LinearLayoutManager(ctx); adapter = this@MainFragment.adapter }, LinearLayout.LayoutParams(-1,0,1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            v.setPadding(dp(20),dp(8),dp(20),insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom); insets
        }
        return root
    }
    private fun updateFilters(layout: LinearLayout) {
        listOf(-1,0,1).forEachIndexed { i, kind ->
            layout.getChildAt(i).background = box(if(filter == kind) Color.rgb(224,239,232) else Color.TRANSPARENT)
        }
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { JournalStore.updates.collect { refresh() } }
        }
    }
    private var refreshJob: kotlinx.coroutines.Job? = null
    private fun refresh() {
        if (followToday) date = LocalDate.now()
        refreshJob?.cancel()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch { rows = JournalStore.entries(date); render() }
    }
    private fun render() {
        dateButton.text = date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
        summary.text = getString(R.string.journal_counts, rows.count { it.kind == 0 }, rows.count { it.kind == 1 })
        adapter.items = rows.filter { filter == -1 || it.kind == filter }
        adapter.notifyDataSetChanged()
        empty.visibility = if(adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }
    override fun onSaveInstanceState(state: Bundle) { super.onSaveInstanceState(state); state.putString("date",date.toString()); state.putInt("filter",filter); state.putBoolean("followToday",followToday) }
    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {
        var items = emptyList<JournalStore.Entry>()
        inner class Holder(val card: LinearLayout, val meta: TextView, val content: TextView) : RecyclerView.ViewHolder(card)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder {
            val card = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL; background = box(Color.WHITE); setPadding(dp(18),dp(14),dp(18),dp(16))
                layoutParams = RecyclerView.LayoutParams(-1,-2).apply { bottomMargin = dp(10) }
                minimumHeight = dp(88)
            }
            val meta = text("",12f,accent)
            val content = text("",16f).apply { maxLines = 5; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0,dp(8),0,0) }
            card.addView(meta); card.addView(content)
            return Holder(card,meta,content)
        }
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val time = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(item.time))
            holder.meta.text = getString(R.string.journal_entry_meta, getString(if(item.kind == 0) R.string.journal_input else R.string.journal_clips),time)
            holder.content.text = item.text
            holder.card.setOnClickListener {
                val full = TextView(requireContext()).apply { text = item.text; setTextIsSelectable(true); setPadding(dp(24),dp(12),dp(24),dp(12)); setTextColor(ink) }
                AlertDialog.Builder(requireContext()).setTitle(holder.meta.text).setView(ScrollView(requireContext()).apply { addView(full) })
                    .setPositiveButton(android.R.string.copy) { _,_ ->
                        requireContext().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Echo",item.text))
                    }.setNeutralButton(R.string.journal_delete) { _,_ ->
                        AlertDialog.Builder(requireContext()).setMessage(R.string.journal_delete_confirm).setNegativeButton(android.R.string.cancel,null)
                            .setPositiveButton(android.R.string.ok) { _,_ -> JournalStore.delete(item.id) }.show()
                    }.setNegativeButton(android.R.string.cancel,null).show()
            }
        }
    }
}
