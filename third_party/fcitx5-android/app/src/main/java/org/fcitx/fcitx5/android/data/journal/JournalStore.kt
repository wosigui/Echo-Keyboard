package org.fcitx.fcitx5.android.data.journal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.fcitx.fcitx5.android.utils.appContext
import java.time.LocalDate
import java.time.ZoneId

/** Local submission history, not a snapshot of the document being edited. */
object JournalStore {
    data class Entry(val id: Long, val kind: Int, val text: String, val time: Long, val source: String)
    const val INPUT = 0
    const val CLIPBOARD = 1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val preferences by lazy { appContext.getSharedPreferences("journal", Context.MODE_PRIVATE) }
    private val helper by lazy { object : SQLiteOpenHelper(appContext, "journal.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY, kind INTEGER NOT NULL, text TEXT NOT NULL, time INTEGER NOT NULL, source TEXT NOT NULL, session TEXT NOT NULL)")
            db.execSQL("CREATE INDEX entries_time ON entries(time)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    } }
    private val changes = MutableStateFlow(0L)
    val updates = changes.asStateFlow()
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", true)
        set(value) { preferences.edit().putBoolean("enabled", value).apply(); changes.value++ }
    fun canRecord(info: EditorInfo?): Boolean {
        return info != null && JournalRules.eligible(info.inputType, info.imeOptions)
    }

    fun recordInput(text: String, info: EditorInfo?, session: String) {
        if (canRecord(info)) record(INPUT, text, info?.packageName.orEmpty(), session)
    }
    fun recordClipboard(text: String, sensitive: Boolean) {
        if (!sensitive) record(CLIPBOARD, text, "", "")
    }
    private fun record(kind: Int, text: String, source: String, session: String) {
        if (!enabled || text.isEmpty()) return
        val now = System.currentTimeMillis()
        scope.launch {
            if (!enabled) return@launch
            val db = helper.writableDatabase
            val last = db.rawQuery("SELECT id,kind,text,time,source,session FROM entries ORDER BY id DESC LIMIT 1", null).use {
                if (it.moveToFirst()) arrayOf(it.getLong(0), it.getInt(1), it.getString(2), it.getLong(3), it.getString(4), it.getString(5)) else null
            }
            val merge = kind == INPUT && last != null && last[1] == kind && last[4] == source && last[5] == session &&
                now - (last[3] as Long) in 0..30000 && LocalDate.ofInstant(java.time.Instant.ofEpochMilli(last[3] as Long), ZoneId.systemDefault()) == LocalDate.now() &&
                (last[2] as String).length + text.length <= 20000
            val values = ContentValues().apply {
                put("kind", kind); put("text", if (merge) (last!![2] as String) + text else text)
                put("time", now); put("source", source); put("session", session)
            }
            if (merge) db.update("entries", values, "id=?", arrayOf(last!![0].toString())) else db.insertOrThrow("entries", null, values)
            changes.value++
        }
    }
    suspend fun entries(date: LocalDate): List<Entry> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val (from,to) = JournalRules.dayBounds(date, zone)
        helper.readableDatabase.rawQuery("SELECT id,kind,text,time,source FROM entries WHERE time>=? AND time<? ORDER BY time DESC,id DESC", arrayOf(from.toString(), to.toString())).use { cursor ->
            buildList { while (cursor.moveToNext()) add(Entry(cursor.getLong(0),cursor.getInt(1),cursor.getString(2),cursor.getLong(3),cursor.getString(4))) }
        }
    }
    fun delete(id: Long) { scope.launch { helper.writableDatabase.delete("entries", "id=?", arrayOf(id.toString())); changes.value++ } }
    fun clear(date: LocalDate) { scope.launch {
        val zone = ZoneId.systemDefault()
        helper.writableDatabase.delete("entries", "time>=? AND time<?", arrayOf(date.atStartOfDay(zone).toInstant().toEpochMilli().toString(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().toString()))
        changes.value++
    } }
}
