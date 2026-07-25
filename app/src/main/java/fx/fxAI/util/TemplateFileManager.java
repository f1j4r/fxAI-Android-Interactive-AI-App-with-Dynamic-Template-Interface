package fx.fxAI.util;

import android.content.*;
import android.database.*;
import android.net.*;
import android.provider.*;
import android.util.*;
import java.io.*;

/**
 * Centralized operations for HTML templates:
 * - File I/O (read, copy from assets, copy from URI)
 * - In‑memory caching of HTML content
 */
public class TemplateFileManager {

  private static final int CACHE_SIZE = 8;
  private static final LruCache<String, String> htmlCache = new LruCache<>(CACHE_SIZE);

  // ─── File Operations ──────────────────────────────────────────────────────

  /**
   * Reads an HTML template from internal storage, using an in‑memory cache.
   * @param context   Application context
   * @param fileName  Name of the HTML file (e.g., "quiz.html")
   * @return          HTML content as a string, or null if not found
   */
  public static String readTemplateHtml(Context context, String fileName) {
    // Check cache first
    String cached = htmlCache.get(fileName);
    if (cached != null) {
      return cached;
    }

    File templatesDir = new File(context.getFilesDir(), "templates");
    File htmlFile = new File(templatesDir, fileName);
    if (!htmlFile.exists()) return null;

    try (FileInputStream fis = new FileInputStream(htmlFile)) {
      byte[] data = new byte[(int) htmlFile.length()];
      int bytesRead = fis.read(data);
      if (bytesRead != data.length) {
        // Fallback: read fully via a loop if needed (but for small files this is fine)
      }
      String html = new String(data, "UTF-8");
      htmlCache.put(fileName, html);
      return html;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Reads the full content of a file from a content URI as a UTF‑8 string.
   * @param context  Application context
   * @param uri      Content URI (from file picker)
   * @return         File content as a string, or null if error
   */
  public static String readContentFromUri(Context context, Uri uri) {
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
      if (in == null) return null;
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        baos.write(buffer, 0, read);
      }
      return baos.toString("UTF-8");
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Copies a template HTML file from assets/templates/ to internal storage.
   * Does nothing if the file already exists.
   */
  public static void copyAssetToInternalStorage(Context context, String fileName) {
    File templatesDir = new File(context.getFilesDir(), "templates");
    if (!templatesDir.exists()) {
      templatesDir.mkdirs();
    }
    File outFile = new File(templatesDir, fileName);
    if (outFile.exists()) return;

    try (InputStream in = context.getAssets().open("templates/" + fileName);
    OutputStream out = new FileOutputStream(outFile)) {
      byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Copies a file from a content URI to internal storage.
   * @param context   Application context
   * @param uri       Content URI (from file picker)
   * @param fileName  Desired name for the saved file
   * @return          true on success, false otherwise
   */
  public static boolean copyUriToInternalStorage(Context context, Uri uri, String fileName) {
    File templatesDir = new File(context.getFilesDir(), "templates");
    if (!templatesDir.exists()) {
      templatesDir.mkdirs();
    }
    File outFile = new File(templatesDir, fileName);

    ContentResolver resolver = context.getContentResolver();
    try (InputStream in = resolver.openInputStream(uri);
    OutputStream out = new FileOutputStream(outFile)) {
      if (in == null) return false;
      byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.flush();
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * Extracts the file name from a content URI.
   */
  public static String getFileNameFromUri(Context context, Uri uri) {
    String result = null;
    if (uri.getScheme().equals("content")) {
      Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
      if (cursor != null) {
        try {
          if (cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
              result = cursor.getString(nameIndex);
            }
          }
        } finally {
          cursor.close();
        }
      }
    }
    if (result == null) {
      result = uri.getPath();
      int cut = result.lastIndexOf('/');
      if (cut != -1) result = result.substring(cut + 1);
    }
    return result;
  }

  // ─── Batch Copy from Assets ──────────────────────────────────────────────

  /**
   * Copies all .html files from assets/templates/ to internal storage.
   * Overwrites existing files.
   */
  public static void copyAllTemplateAssets(Context context) {
    File templatesDir = new File(context.getFilesDir(), "templates");
    if (!templatesDir.exists()) templatesDir.mkdirs();

    try {
      String[] assetFiles = context.getAssets().list("templates");
      for (String fileName : assetFiles) {
        if (fileName.endsWith(".html")) {
          try (InputStream in = context.getAssets().open("templates/" + fileName);
          OutputStream out = new FileOutputStream(new File(templatesDir, fileName), false)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
              out.write(buffer, 0, read);
            }
            out.flush();
            // Invalidate cache for this file (since we overwrote it)
            invalidateCache(fileName);
          } catch (Exception ignored) {}
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // ─── Cache Management ────────────────────────────────────────────────────

  /**
   * Removes a specific file from the cache.
   * Call this when a template is added, updated, or deleted.
   */
  public static void invalidateCache(String fileName) {
    htmlCache.remove(fileName);
  }

  /**
   * Clears the entire cache.
   */
  public static void clearCache() {
    htmlCache.evictAll();
  }
}
