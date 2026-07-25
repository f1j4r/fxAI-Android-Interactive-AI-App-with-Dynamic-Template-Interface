package fx.fxAI.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import fx.fxAI.model.Template;

public class TemplateDao {

  private AppDatabase db;

  public TemplateDao(AppDatabase db) {
    this.db = db;
  }

  public void insert(Template template) {
    SQLiteDatabase database = db.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AppDatabase.COL_NAME, template.name);
    values.put(AppDatabase.COL_DESCRIPTION, template.description);
    values.put(AppDatabase.COL_HTML_FILE, template.htmlFileName);
    values.put(AppDatabase.COL_JSON_SCHEMA, template.jsonSchema);
    values.put(AppDatabase.COL_IS_DEFAULT, template.isDefault ? 1 : 0);
    values.put(AppDatabase.COL_IS_ACTIVE, template.isActive ? 1 : 0);
    database.insert(AppDatabase.TABLE_TEMPLATES, null, values);
  }

  public void delete(long id) {
    SQLiteDatabase database = db.getWritableDatabase();
    database.delete(AppDatabase.TABLE_TEMPLATES,
                    AppDatabase.COL_ID + " = ?",
                    new String[]{String.valueOf(id)});
  }

  public void update(Template template) {
    SQLiteDatabase database = db.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(AppDatabase.COL_NAME, template.name);
    values.put(AppDatabase.COL_DESCRIPTION, template.description);
    values.put(AppDatabase.COL_HTML_FILE, template.htmlFileName);
    values.put(AppDatabase.COL_JSON_SCHEMA, template.jsonSchema);
    values.put(AppDatabase.COL_IS_ACTIVE, template.isActive ? 1 : 0);
    database.update(AppDatabase.TABLE_TEMPLATES, values,
                    AppDatabase.COL_ID + " = ?",
                    new String[]{String.valueOf(template.id)});
  }

  public void toggleActive(long id) {
    SQLiteDatabase database = db.getWritableDatabase();
    Template template = getById(id);
    if (template != null) {
      ContentValues values = new ContentValues();
      values.put(AppDatabase.COL_IS_ACTIVE, template.isActive ? 0 : 1);
      database.update(AppDatabase.TABLE_TEMPLATES, values,
                      AppDatabase.COL_ID + " = ?",
                      new String[]{String.valueOf(id)});
    }
  }

  public Template getById(long id) {
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(AppDatabase.TABLE_TEMPLATES,
                                   null,
                                   AppDatabase.COL_ID + " = ?",
                                   new String[]{String.valueOf(id)},
                                   null, null, null);

    Template template = null;
    if (cursor.moveToFirst()) {
      template = cursorToTemplate(cursor);
    }
    cursor.close();
    return template;
  }

  public Template getByName(String name) {
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(AppDatabase.TABLE_TEMPLATES,
                                   null,
                                   AppDatabase.COL_NAME + " = ?",
                                   new String[]{name},
                                   null, null, null);

    Template template = null;
    if (cursor.moveToFirst()) {
      template = cursorToTemplate(cursor);
    }
    cursor.close();
    return template;
  }

  /**
   * Case‑insensitive lookup by template name.
   * Uses COLLATE NOCASE for comparison.
   */
  public Template getByNameIgnoreCase(String name) {
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(AppDatabase.TABLE_TEMPLATES,
                                   null,
                                   AppDatabase.COL_NAME + " COLLATE NOCASE = ?",
                                   new String[]{name},
                                   null, null, null);

    Template template = null;
    if (cursor.moveToFirst()) {
      template = cursorToTemplate(cursor);
    }
    cursor.close();
    return template;
  }

  public List<Template> getAll() {
    List<Template> list = new ArrayList<>();
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(AppDatabase.TABLE_TEMPLATES,
                                   null, null, null, null, null,
                                   AppDatabase.COL_NAME + " ASC");

    while (cursor.moveToNext()) {
      list.add(cursorToTemplate(cursor));
    }
    cursor.close();
    return list;
  }

  public List<Template> getActive() {
    List<Template> list = new ArrayList<>();
    SQLiteDatabase database = db.getReadableDatabase();
    Cursor cursor = database.query(AppDatabase.TABLE_TEMPLATES,
                                   null,
                                   AppDatabase.COL_IS_ACTIVE + " = 1",
                                   null, null, null,
                                   AppDatabase.COL_NAME + " ASC");

    while (cursor.moveToNext()) {
      list.add(cursorToTemplate(cursor));
    }
    cursor.close();
    return list;
  }

  private Template cursorToTemplate(Cursor cursor) {
    Template template = new Template();
    template.id = cursor.getLong(cursor.getColumnIndex(AppDatabase.COL_ID));
    template.name = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_NAME));
    template.description = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_DESCRIPTION));
    template.htmlFileName = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_HTML_FILE));
    template.jsonSchema = cursor.getString(cursor.getColumnIndex(AppDatabase.COL_JSON_SCHEMA));
    template.isDefault = cursor.getInt(cursor.getColumnIndex(AppDatabase.COL_IS_DEFAULT)) == 1;
    template.isActive = cursor.getInt(cursor.getColumnIndex(AppDatabase.COL_IS_ACTIVE)) == 1;
    return template;
  }
}
