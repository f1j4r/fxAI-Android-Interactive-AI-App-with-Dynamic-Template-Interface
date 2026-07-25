package fx.fxAI.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import fx.fxAI.model.MemoryEntry;

public class MemoryDao {

  private final AppDatabase db;
  private static String cachedJson = null;
  private static long cacheTimestamp = 0;
  private static final long CACHE_TTL_MS = 5000;

  public MemoryDao(AppDatabase db) {
    this.db = db;
  }

  // ─── Write operations ────────────────────────────────────────────

  /**
   * Inserts or replaces a memory entry.
   * If a row with the same topic exists, it is replaced (updated) with the new values.
   */
  public void insert(MemoryEntry entry) {
    SQLiteDatabase database = db.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AppDatabase.COL_TOPIC, entry.topic);
    values.put(AppDatabase.COL_SCORE, entry.score);
    values.put(AppDatabase.COL_DETAILS, entry.details);
    values.put(AppDatabase.COL_TIMESTAMP, entry.timestamp);
    database.insertWithOnConflict(
      AppDatabase.TABLE_MEMORY,
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE  // This handles the "upsert" automatically
    );
    invalidateCache();
  }

  public void deleteAll() {
    SQLiteDatabase database = db.getWritableDatabase();
    database.delete(AppDatabase.TABLE_MEMORY, null, null);
    invalidateCache();
  }

  // ─── Read operations ─────────────────────────────────────────────

  public List<MemoryEntry> getRecent(int limit) {
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(
      AppDatabase.TABLE_MEMORY,
      null,
      null,
      null,
      null,
      null,
      AppDatabase.COL_TIMESTAMP + " DESC",
      String.valueOf(limit)
    );
    List<MemoryEntry> list = new ArrayList<>();
    while (cursor.moveToNext()) {
      list.add(cursorToMemory(cursor));
    }
    cursor.close();
    return list;
  }

  public String getRecentAsJson(int limit) {
    long now = System.currentTimeMillis();
    if (cachedJson != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
      return cachedJson;
    }
    List<MemoryEntry> entries = getRecent(limit);
    JSONArray array = new JSONArray();
    for (MemoryEntry entry : entries) {
      try {
        JSONObject obj = new JSONObject();
        obj.put("topic", entry.topic);
        obj.put("score", entry.score);
        obj.put("details", entry.details);
        obj.put("timestamp", entry.timestamp);
        array.put(obj);
      } catch (Exception ignored) {}
    }
    cachedJson = array.toString();
    cacheTimestamp = now;
    return cachedJson;
  }

  // ─── Cache management ────────────────────────────────────────────

  private void invalidateCache() {
    cachedJson = null;
    cacheTimestamp = 0;
  }

  // ─── Helpers ─────────────────────────────────────────────────────

  private MemoryEntry cursorToMemory(Cursor cursor) {
    MemoryEntry entry = new MemoryEntry();
    entry.id = cursor.getLong(cursor.getColumnIndex(AppDatabase.COL_ID));
    entry.topic = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_TOPIC));
    entry.score = cursor.getInt(cursor.getColumnIndex(AppDatabase.COL_SCORE));
    entry.details = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_DETAILS));
    entry.timestamp = cursor.getLong(cursor.getColumnIndex(AppDatabase.COL_TIMESTAMP));
    return entry;
  }
}
