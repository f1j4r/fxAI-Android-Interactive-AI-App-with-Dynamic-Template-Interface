package fx.fxAI.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import fx.fxAI.util.TemplateFileManager;

public class AppDatabase extends SQLiteOpenHelper {

  private static final String TAG = "AppDatabase";
  private static final String DB_NAME = "fxai.db";
  private static final int DB_VERSION = 4;                     // Clean v4 only

  private static AppDatabase instance;
  private final Context context;

  public static synchronized AppDatabase getInstance(Context context) {
    if (instance == null) {
      instance = new AppDatabase(context.getApplicationContext());
    }
    return instance;
  }

  private AppDatabase(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
    this.context = context;
  }

  // ─── Table names ──────────────────────────────────────────────────
  public static final String TABLE_TEMPLATES    = "templates";
  public static final String TABLE_MEMORY       = "user_memory";
  public static final String TABLE_CHAT_HISTORY = "chat_history";

  // ─── Common columns ──────────────────────────────────────────────
  public static final String COL_ID = "id";

  // ─── Templates columns ───────────────────────────────────────────
  public static final String COL_NAME           = "name";
  public static final String COL_DESCRIPTION    = "description";
  public static final String COL_HTML_FILE      = "htmlFileName";
  public static final String COL_JSON_SCHEMA    = "jsonSchema";
  public static final String COL_IS_DEFAULT     = "isDefault";
  public static final String COL_IS_ACTIVE      = "isActive";

  // ─── Memory columns ──────────────────────────────────────────────
  public static final String COL_TOPIC          = "topic";
  public static final String COL_SCORE          = "score";
  public static final String COL_DETAILS        = "details";
  public static final String COL_TIMESTAMP      = "timestamp";

  // ─── Chat history columns ────────────────────────────────────────
  public static final String COL_HISTORY_TITLE          = "title";
  public static final String COL_HISTORY_HTML           = "htmlContent";
  public static final String COL_HISTORY_JSON           = "jsonData";
  public static final String COL_HISTORY_TEMPLATE_NAME  = "templateName";
  public static final String COL_HISTORY_HTML_FILE      = "htmlFileName";
  public static final String COL_HISTORY_TIMESTAMP      = "timestamp";
  public static final String COL_HISTORY_IS_TEMPLATE    = "isTemplateBased";

  // ─── Lifecycle ──────────────────────────────────────────────────────

  @Override
  public void onCreate(SQLiteDatabase db) {
    // --- Templates table (name is UNIQUE) ---
    db.execSQL("CREATE TABLE " + TABLE_TEMPLATES + " (" +
               COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
               COL_NAME + " TEXT NOT NULL UNIQUE, " +
               COL_DESCRIPTION + " TEXT, " +
               COL_HTML_FILE + " TEXT NOT NULL, " +
               COL_JSON_SCHEMA + " TEXT NOT NULL, " +
               COL_IS_DEFAULT + " INTEGER DEFAULT 0, " +
               COL_IS_ACTIVE + " INTEGER DEFAULT 1)");

    // --- User memory table (topic is UNIQUE → prevents duplicates) ---
    db.execSQL("CREATE TABLE " + TABLE_MEMORY + " (" +
               COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
               COL_TOPIC + " TEXT NOT NULL UNIQUE, " +
               COL_SCORE + " INTEGER, " +
               COL_DETAILS + " TEXT, " +
               COL_TIMESTAMP + " INTEGER)");

    // --- Chat history table (with foreign key to templates.name) ---
    db.execSQL("CREATE TABLE " + TABLE_CHAT_HISTORY + " (" +
               COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
               COL_HISTORY_TITLE + " TEXT, " +
               COL_HISTORY_HTML + " TEXT, " +
               COL_HISTORY_JSON + " TEXT, " +
               COL_HISTORY_TEMPLATE_NAME + " TEXT, " +
               COL_HISTORY_HTML_FILE + " TEXT, " +
               COL_HISTORY_TIMESTAMP + " INTEGER, " +
               COL_HISTORY_IS_TEMPLATE + " INTEGER DEFAULT 0, " +
               "FOREIGN KEY(" + COL_HISTORY_TEMPLATE_NAME + ") REFERENCES " +
               TABLE_TEMPLATES + "(" + COL_NAME + ") ON DELETE SET NULL)");

    // --- Indexes for performance ---
    db.execSQL("CREATE INDEX idx_memory_timestamp ON " + TABLE_MEMORY + " (" + COL_TIMESTAMP + ")");
    db.execSQL("CREATE INDEX idx_history_timestamp ON " + TABLE_CHAT_HISTORY + " (" + COL_HISTORY_TIMESTAMP + ")");
    db.execSQL("CREATE INDEX idx_templates_name ON " + TABLE_TEMPLATES + " (" + COL_NAME + ")");

    // --- Seed default templates from metadata.json ---
    seedDefaultTemplates(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Since we only care about version 4, drop everything and recreate.
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHAT_HISTORY);
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEMORY);
    db.execSQL("DROP TABLE IF EXISTS " + TABLE_TEMPLATES);
    onCreate(db);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    super.onConfigure(db);
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    super.onOpen(db);
    db.enableWriteAheadLogging();
  }

  // ─── Seeding from metadata.json ──────────────────────────────────────

  private void seedDefaultTemplates(SQLiteDatabase db) {
    // 1. Copy all .html files from assets/templates/ to internal storage
    TemplateFileManager.copyAllTemplateAssets(context);

    // 2. Read and parse metadata.json
    try (InputStream in = context.getAssets().open("templates/metadata.json")) {
      byte[] data = new byte[in.available()];
      in.read(data);
      String json = new String(data, "UTF-8");
      JSONArray array = new JSONArray(json);

      for (int i = 0; i < array.length(); i++) {
        JSONObject obj = array.getJSONObject(i);
        String name        = obj.getString("name");
        String description = obj.optString("description", "");
        String htmlFile    = obj.getString("htmlFileName");
        String schema      = obj.getString("jsonSchema");
        boolean isDefault  = obj.optBoolean("isDefault", true);

        insertTemplate(db, name, description, htmlFile, schema, isDefault);
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to seed default templates from metadata.json", e);
      // Fallback: hardcoded defaults (if you still want a safety net)
      // But we can also let it fail – the app will have no templates, which is a problem.
      // I'd recommend adding a fallback to hardcoded values to keep the app usable.
      fallbackSeedTemplates(db);
    }
  }

  /**
   * Fallback in case metadata.json cannot be read.
   * This ensures the app always has at least some templates.
   */
  private void fallbackSeedTemplates(SQLiteDatabase db) {
    // Copy required HTML files
    String[] files = {"quiz.html", "adventure.html", "flashcard.html", "chat.html"};
    for (String file : files) {
      TemplateFileManager.copyAssetToInternalStorage(context, file);
    }
    insertTemplate(db, "AI Chat",
                   "General-purpose AI chat with HTML formatting.",
                   "chat.html",
                   "{\"messages\": [{\"role\": \"string\", \"content\": \"string\"}]}",
                   true);
    insertTemplate(db, "Quiz",
                   "Multiple choice or open‑ended quiz with scoring",
                   "quiz.html",
                   "{\"topic\": \"string\", \"questions\": [{\"question\": \"string\", \"options\": [\"string\"], \"correct\": 0}]}",
                   true);
    insertTemplate(db, "Text Adventure",
                   "Interactive story with choices, inventory, and location names",
                   "adventure.html",
                   "{\"title\": \"string\", \"scenes\": [{\"text\": \"string\", \"location\": \"string\", \"choices\": [\"string\"]}]}",
                   true);
    insertTemplate(db, "Flashcards",
                   "Flip cards for memorization with progress tracking",
                   "flashcard.html",
                   "{\"topic\": \"string\", \"cards\": [{\"front\": \"string\", \"back\": \"string\"}]}",
                   true);
  }

  private void insertTemplate(SQLiteDatabase db, String name, String description,
                              String htmlFile, String jsonSchema, boolean isDefault) {
    ContentValues values = new ContentValues();
    values.put(COL_NAME, name);
    values.put(COL_DESCRIPTION, description);
    values.put(COL_HTML_FILE, htmlFile);
    values.put(COL_JSON_SCHEMA, jsonSchema);
    values.put(COL_IS_DEFAULT, isDefault ? 1 : 0);
    values.put(COL_IS_ACTIVE, 1);
    db.insert(TABLE_TEMPLATES, null, values);
  }
}
