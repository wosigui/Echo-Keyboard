package app.echo.ime

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.echo.pinyin.PinyinCandidate

/** Bounded local data. Password/incognito eligibility is decided by the service before calling learn/capture. */
class InputRepository(context: Context, name: String = "personal-input.db") : SQLiteOpenHelper(context,name,null,1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE words(k TEXT NOT NULL,word TEXT NOT NULL,spelling TEXT NOT NULL,n INTEGER NOT NULL,t INTEGER NOT NULL,PRIMARY KEY(k,word))")
        db.execSQL("CREATE TABLE transitions(prev TEXT NOT NULL,next TEXT NOT NULL,n INTEGER NOT NULL,t INTEGER NOT NULL,PRIMARY KEY(prev,next))")
        db.execSQL("CREATE TABLE clips(text TEXT PRIMARY KEY NOT NULL,t INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE emoji(text TEXT PRIMARY KEY NOT NULL,n INTEGER NOT NULL,t INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int) = Unit
    @Synchronized fun learn(input: String, entry: PinyinCandidate, previous: String, allowed: Boolean) {
        if(!allowed || entry.text.isBlank() || entry.text.length>64) return
        val spelling=entry.spelling
        val english=spelling=="@en"
        val segments=spelling.split('\'').filter { it.isNotEmpty() }
        val keys=if(input.isEmpty()) emptyList() else if(english) listOf(entry.text.lowercase()) else listOf(input,spelling.replace("'",""),segments.joinToString("") { it.take(1) },T9Composition.encode(spelling))
        val db=writableDatabase
        db.beginTransaction()
        try {
            keys.distinct().filter { it.length in 1..64 }.forEach { k ->
                val word=if(english) entry.text.lowercase() else entry.text
                db.execSQL("INSERT OR IGNORE INTO words(k,word,spelling,n,t) VALUES(?,?,?,0,0)",arrayOf(k.lowercase(),word,spelling))
                db.execSQL("UPDATE words SET n=MIN(n+1,1000000),spelling=?,t=? WHERE k=? AND word=?",arrayOf(spelling,System.currentTimeMillis(),k.lowercase(),word))
            }
            if(previous.isNotEmpty()) {
                db.execSQL("INSERT OR IGNORE INTO transitions(prev,next,n,t) VALUES(?,?,0,0)",arrayOf(previous,entry.text))
                db.execSQL("UPDATE transitions SET n=MIN(n+1,1000000),t=? WHERE prev=? AND next=?",arrayOf(System.currentTimeMillis(),previous,entry.text))
            }
            db.execSQL("DELETE FROM words WHERE rowid NOT IN (SELECT rowid FROM words ORDER BY t DESC LIMIT 12000)")
            db.execSQL("DELETE FROM transitions WHERE rowid NOT IN (SELECT rowid FROM transitions ORDER BY t DESC LIMIT 3000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun words(input: String, locked: String = ""): List<PinyinCandidate> {
        val result=mutableListOf<PinyinCandidate>()
        readableDatabase.rawQuery("SELECT word,spelling FROM words WHERE k=? AND spelling!='@en' ORDER BY n DESC,t DESC LIMIT 60",arrayOf(input.lowercase())).use { c -> while(c.moveToNext()) {
            val spelling=c.getString(1)
            if(locked.isEmpty() || spelling.replace("'","").startsWith(locked.replace("'",""))) result+=PinyinCandidate(c.getString(0),input.length,spelling)
        } }
        return result
    }
    @Synchronized fun english(prefix: String): List<String> {
        val result=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT word FROM words WHERE spelling='@en' AND k LIKE ? AND k=word ORDER BY n DESC,t DESC LIMIT 12",arrayOf(prefix.lowercase()+"%")).use { c -> while(c.moveToNext()) result+=c.getString(0) }
        return result
    }
    @Synchronized fun next(previous: String): List<String> {
        val result=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT next FROM transitions WHERE prev=? ORDER BY n DESC,t DESC LIMIT 12",arrayOf(previous)).use { c -> while(c.moveToNext()) result+=c.getString(0) }
        return result
    }
    @Synchronized fun clearLearning() { writableDatabase.execSQL("DELETE FROM words"); writableDatabase.execSQL("DELETE FROM transitions") }
    @Synchronized fun captureClip(text: String, allowed: Boolean) {
        if(!allowed || text.isBlank() || text.length>10000) return
        writableDatabase.insertWithOnConflict("clips",null,ContentValues().apply { put("text",text); put("t",System.currentTimeMillis()) },SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL("DELETE FROM clips WHERE text NOT IN (SELECT text FROM clips ORDER BY t DESC LIMIT 20)")
    }
    @Synchronized fun clips(): List<String> = mutableListOf<String>().also { rows -> readableDatabase.rawQuery("SELECT text FROM clips ORDER BY t DESC LIMIT 20",null).use { c -> while(c.moveToNext()) rows+=c.getString(0) } }
    @Synchronized fun deleteClip(text: String) { writableDatabase.delete("clips","text=?",arrayOf(text)) }
    @Synchronized fun clearClips() { writableDatabase.delete("clips",null,null) }
    @Synchronized fun useEmoji(text: String, allowed: Boolean = true) {
        if(!allowed) return
        writableDatabase.execSQL("INSERT OR IGNORE INTO emoji(text,n,t) VALUES(?,0,0)",arrayOf(text))
        writableDatabase.execSQL("UPDATE emoji SET n=MIN(n+1,1000000),t=? WHERE text=?",arrayOf(System.currentTimeMillis(),text))
        writableDatabase.execSQL("DELETE FROM emoji WHERE text NOT IN (SELECT text FROM emoji ORDER BY t DESC LIMIT 30)")
    }
    @Synchronized fun recentEmoji(): List<String> = mutableListOf<String>().also { rows -> readableDatabase.rawQuery("SELECT text FROM emoji ORDER BY t DESC LIMIT 30",null).use { c -> while(c.moveToNext()) rows+=c.getString(0) } }
    @Synchronized fun clearEmoji() { writableDatabase.delete("emoji",null,null) }
}
