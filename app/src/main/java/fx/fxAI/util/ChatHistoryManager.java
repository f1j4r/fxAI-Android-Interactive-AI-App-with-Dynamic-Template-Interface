package fx.fxAI.util;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fx.fxAI.db.AppDatabase;
import fx.fxAI.db.ChatHistoryDao;
import fx.fxAI.model.ChatHistoryEntry;

/**
 * Manages in‑memory chat history with persistence.
 * Loads only the most recent entries (default 50) for performance.
 */
public class ChatHistoryManager {

  private final ChatHistoryDao dao;
  private List<ChatHistoryEntry> history = new ArrayList<>();
  private int currentIndex = -1;

  // Limit for initial load; -1 means "load all"
  private static final int INITIAL_LOAD_LIMIT = 50;

  public ChatHistoryManager(Context context) {
    AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
    this.dao = new ChatHistoryDao(db);
    loadFromDisk(INITIAL_LOAD_LIMIT);
  }

  // ─── Getters ─────────────────────────────────────────────────────────────

  public List<ChatHistoryEntry> getHistory() {
    return history;
  }

  public int getCurrentIndex() {
    return currentIndex;
  }

  public void setCurrentIndex(int index) {
    this.currentIndex = index;
  }

  public int size() {
    return history.size();
  }

  public boolean isEmpty() {
    return history.isEmpty();
  }

  public ChatHistoryEntry getEntry(int position) {
    if (position >= 0 && position < history.size()) {
      return history.get(position);
    }
    return null;
  }

  // ─── Modifications ──────────────────────────────────────────────────────

  /**
   * Adds a new entry to the end of the history.
   * If we are in the middle, forward entries are removed.
   */
  public void addEntry(ChatHistoryEntry entry) {
    // If we're in the middle, remove forward entries from DB and list
    if (currentIndex >= 0 && currentIndex < history.size() - 1) {
      // Collect entries to remove (indices > currentIndex)
      List<ChatHistoryEntry> toRemove = new ArrayList<>(history.subList(currentIndex + 1, history.size()));
      // Delete from DB
      for (ChatHistoryEntry e : toRemove) {
        dao.delete(e.getId());
      }
      // Remove from list (safe because we're not iterating)
      history.subList(currentIndex + 1, history.size()).clear();
    }

    // Insert new entry and get its ID
    long id = dao.insert(entry);
    entry.setId(id);
    history.add(entry);
    currentIndex = history.size() - 1;
  }

  /**
   * Deletes an entry by position.
   */
  public void deleteEntry(int position) {
    if (position < 0 || position >= history.size()) return;
    ChatHistoryEntry entry = history.get(position);
    dao.delete(entry.getId());
    history.remove(position);

    if (history.isEmpty()) {
      currentIndex = -1;
    } else if (position < currentIndex) {
      currentIndex--;
    } else if (position == currentIndex) {
      currentIndex = Math.max(0, position - 1);
    }
  }

  /**
   * Updates the last entry (used for saving updated state).
   */
  public void updateLastEntry(ChatHistoryEntry entry) {
    if (history.isEmpty()) {
      addEntry(entry);
      return;
    }
    ChatHistoryEntry last = history.get(history.size() - 1);
    // Copy all fields
    last.setTitle(entry.getTitle());
    last.setHtmlContent(entry.getHtmlContent());
    last.setJsonData(entry.getJsonData());
    last.setTemplateName(entry.getTemplateName());
    last.setHtmlFileName(entry.getHtmlFileName());
    last.setTemplateBased(entry.isTemplateBased());
    last.setTimestamp(entry.getTimestamp());
    dao.update(last);
    currentIndex = history.size() - 1;
  }

  /**
   * Updates only the title of a specific entry.
   */
  public void updateTitle(int position, String newTitle) {
    if (position >= 0 && position < history.size()) {
      ChatHistoryEntry entry = history.get(position);
      entry.setTitle(newTitle);
      dao.update(entry);
    }
  }

  // ─── Loading from disk ─────────────────────────────────────────────────

  /**
   * Loads all entries from disk (full history).
   */
  public void loadFromDisk() {
    loadFromDisk(-1);
  }

  /**
   * Loads the most recent N entries.
   * If limit <= 0, loads all.
   */
  public void loadFromDisk(int limit) {
    history.clear();
    List<ChatHistoryEntry> loaded;
    if (limit > 0) {
      loaded = dao.getRecent(limit);
      // dao.getRecent returns in descending order; we want chronological
      Collections.reverse(loaded);
    } else {
      loaded = dao.getAllOrderedByTimestamp();
    }
    history.addAll(loaded);

    if (!history.isEmpty()) {
      currentIndex = history.size() - 1;
    } else {
      currentIndex = -1;
    }
  }

  // ─── Persistence (no‑op for compatibility) ─────────────────────────────

  public void saveToDisk() {
    // No‑op: changes are saved immediately via DAO operations.
  }

  // ─── Optional: Load more entries lazily ───────────────────────────────

  /**
   * Loads additional older entries (for pagination).
   * Not yet used, but can be called from UI if needed.
   */
  public void loadMore(int count) {
    if (history.isEmpty()) return;
    long oldestTimestamp = history.get(0).getTimestamp();
    // We'd need to implement a method in DAO: getOlderThan(timestamp, count)
    // For now, placeholder.
  }
}
