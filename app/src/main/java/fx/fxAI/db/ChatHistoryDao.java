package fx.fxAI.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import fx.fxAI.model.ChatHistoryEntry;

public class ChatHistoryDao {

  private AppDatabase db;

  public ChatHistoryDao(AppDatabase db) {
    this.db = db;
  }

  public long insert(ChatHistoryEntry entry) {
    SQLiteDatabase database = db.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AppDatabase.COL_HISTORY_TITLE, entry.getTitle());
    values.put(AppDatabase.COL_HISTORY_HTML, entry.getHtmlContent());
    values.put(AppDatabase.COL_HISTORY_JSON, entry.getJsonData());
    values.put(AppDatabase.COL_HISTORY_TEMPLATE_NAME, entry.getTemplateName());
    values.put(AppDatabase.COL_HISTORY_HTML_FILE, entry.getHtmlFileName());
    values.put(AppDatabase.COL_HISTORY_TIMESTAMP, entry.getTimestamp());
    values.put(AppDatabase.COL_HISTORY_IS_TEMPLATE, entry.isTemplateBased() ? 1 : 0);
    return database.insert(AppDatabase.TABLE_CHAT_HISTORY, null, values);
  }

  public void update(ChatHistoryEntry entry) {
    SQLiteDatabase database = db.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AppDatabase.COL_HISTORY_TITLE, entry.getTitle());
    values.put(AppDatabase.COL_HISTORY_HTML, entry.getHtmlContent());
    values.put(AppDatabase.COL_HISTORY_JSON, entry.getJsonData());
    values.put(AppDatabase.COL_HISTORY_TEMPLATE_NAME, entry.getTemplateName());
    values.put(AppDatabase.COL_HISTORY_HTML_FILE, entry.getHtmlFileName());
    values.put(AppDatabase.COL_HISTORY_TIMESTAMP, entry.getTimestamp());
    values.put(AppDatabase.COL_HISTORY_IS_TEMPLATE, entry.isTemplateBased() ? 1 : 0);
    database.update(AppDatabase.TABLE_CHAT_HISTORY, values,
                    AppDatabase.COL_ID + " = ?",
                    new String[]{String.valueOf(entry.getId())});
  }

  public void delete(long id) {
    SQLiteDatabase database = db.getWritableDatabase();
    database.delete(AppDatabase.TABLE_CHAT_HISTORY,
                    AppDatabase.COL_ID + " = ?",
                    new String[]{String.valueOf(id)});
  }

  public List<ChatHistoryEntry> getAllOrderedByTimestamp() {
    List<ChatHistoryEntry> list = new ArrayList<>();
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(AppDatabase.TABLE_CHAT_HISTORY,
                                   null, null, null, null, null,
                                   AppDatabase.COL_HISTORY_TIMESTAMP + " ASC");
    while (cursor.moveToNext()) {
      list.add(cursorToEntry(cursor));
    }
    cursor.close();
    return list;
  }

  public void deleteAll() {
    SQLiteDatabase database = db.getWritableDatabase();
    database.delete(AppDatabase.TABLE_CHAT_HISTORY, null, null);
  }

  private ChatHistoryEntry cursorToEntry(Cursor cursor) {
    long id = cursor.getLong(cursor.getColumnIndex(AppDatabase.COL_ID));
    String title = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_HISTORY_TITLE));
    String html = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_HISTORY_HTML));
    String json = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_HISTORY_JSON));
    String templateName = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_HISTORY_TEMPLATE_NAME));
    String htmlFileName = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_HISTORY_HTML_FILE));
    long timestamp = cursor.getLong(cursor.getColumnIndex(AppDatabase.COL_HISTORY_TIMESTAMP));
    boolean isTemplate = cursor.getInt(cursor.getColumnIndex(AppDatabase.COL_HISTORY_IS_TEMPLATE)) == 1;

    // Create entry – use appropriate constructor
    ChatHistoryEntry entry;
    if (isTemplate) {
      entry = new ChatHistoryEntry(title, null, json, templateName, htmlFileName, timestamp);
    } else {
      entry = new ChatHistoryEntry(title, html, timestamp);
    }
    entry.setId(id);
    return entry;
  }
  
  public List<ChatHistoryEntry> getRecent(int limit) {
    List<ChatHistoryEntry> list = new ArrayList<>();
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(
      AppDatabase.TABLE_CHAT_HISTORY,
      null,
      null,
      null,
      null,
      null,
      AppDatabase.COL_HISTORY_TIMESTAMP + " DESC",
      String.valueOf(limit)
    );
    while (cursor.moveToNext()) {
      list.add(cursorToEntry(cursor));
    }
    cursor.close();
    return list;
  }
  
  public void updateTemplateName(String oldName, String newName) {
    SQLiteDatabase database = db.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AppDatabase.COL_HISTORY_TEMPLATE_NAME, newName);
    database.update(AppDatabase.TABLE_CHAT_HISTORY,
                    values,
                    AppDatabase.COL_HISTORY_TEMPLATE_NAME + " = ?",
                    new String[]{oldName});
  }
}
