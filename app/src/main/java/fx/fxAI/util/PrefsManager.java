package fx.fxAI.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages all app preferences with support for multiple AI providers.
 * Each provider can have its own API key and selected model.
 */
public class PrefsManager {

  private static final String PREFS_NAME = "fxai_prefs";

  // Preference keys
  private static final String KEY_API_KEY_PREFIX = "api_key_";
  private static final String KEY_SELECTED_MODEL_PREFIX = "selected_model_";
  private static final String KEY_AI_PROVIDER = "ai_provider";
  private static final String KEY_CUSTOM_API_URL = "custom_api_url";

  // Legacy key for backward compatibility
  private static final String KEY_SELECTED_MODEL_LEGACY = "selected_model";

  // Built-in provider base URLs
  public static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1/chat/completions";
  public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

  // Default models per provider
  private static final String DEFAULT_MODEL_GROQ = "llama-3.3-70b-versatile";
  private static final String DEFAULT_MODEL_GEMINI = "gemini-3.1-flash-lite";
  private static final String DEFAULT_MODEL_CUSTOM = "gpt-4o-mini";

  private final SharedPreferences prefs;

  public PrefsManager(Context context) {
    this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  // ─── Provider ────────────────────────────────────────────────────────────

  /**
   * Saves the currently selected AI provider.
   * @param provider "groq", "gemini", or "custom"
   */
  public void saveAiProvider(String provider) {
    prefs.edit().putString(KEY_AI_PROVIDER, provider).apply();
  }

  /**
   * Returns the currently selected AI provider.
   * Defaults to "groq" if not set.
   */
  public String getAiProvider() {
    return prefs.getString(KEY_AI_PROVIDER, "groq");
  }

  // ─── API Key (provider‑specific) ──────────────────────────────────────

  /**
   * Saves the API key for a specific provider.
   */
  public void saveApiKey(String provider, String apiKey) {
    prefs.edit().putString(KEY_API_KEY_PREFIX + provider, apiKey).apply();
  }

  /**
   * Returns the API key for a specific provider.
   * Returns empty string if not set.
   */
  public String getApiKey(String provider) {
    return prefs.getString(KEY_API_KEY_PREFIX + provider, "");
  }

  /**
   * Legacy method: returns the API key for the current provider.
   * Delegates to getApiKey(getAiProvider()).
   */
  public String getApiKey() {
    return getApiKey(getAiProvider());
  }

  /**
   * Checks if the current provider has an API key configured.
   */
  public boolean hasApiKey() {
    return !getApiKey().trim().isEmpty();
  }

  /**
   * Checks if a specific provider has an API key configured.
   */
  public boolean hasApiKey(String provider) {
    return !getApiKey(provider).trim().isEmpty();
  }

  // ─── Model Selection (per‑provider) ────────────────────────────────────

  /**
   * Saves the selected model for a specific provider.
   * Also updates the legacy global model for backward compatibility.
   */
  public void saveSelectedModel(String provider, String model) {
    prefs.edit()
      .putString(KEY_SELECTED_MODEL_PREFIX + provider, model)
      .putString(KEY_SELECTED_MODEL_LEGACY, model)  // Legacy fallback
      .apply();
  }

  /**
   * Returns the saved model for a specific provider.
   * Falls back to: per‑provider storage → legacy global → provider default.
   */
  public String getSelectedModel(String provider) {
    // 1. Check per‑provider storage
    String model = prefs.getString(KEY_SELECTED_MODEL_PREFIX + provider, null);
    if (model != null && !model.isEmpty()) {
      return model;
    }

    // 2. Check legacy global storage
    model = prefs.getString(KEY_SELECTED_MODEL_LEGACY, null);
    if (model != null && !model.isEmpty()) {
      // Migrate to per‑provider storage
      saveSelectedModel(provider, model);
      return model;
    }

    // 3. Return provider-specific default
    return getDefaultModel(provider);
  }

  /**
   * Legacy method: returns the model for the current provider.
   * Delegates to getSelectedModel(getAiProvider()).
   */
  public String getSelectedModel() {
    return getSelectedModel(getAiProvider());
  }

  /**
   * Legacy method: saves the model globally.
   * Now delegates to per‑provider storage using the current provider.
   */
  public void saveSelectedModel(String model) {
    saveSelectedModel(getAiProvider(), model);
  }

  // ─── Custom API URL ────────────────────────────────────────────────────

  /**
   * Saves the custom API URL (only used when provider is "custom").
   */
  public void saveCustomApiUrl(String url) {
    prefs.edit().putString(KEY_CUSTOM_API_URL, url).apply();
  }

  /**
   * Returns the custom API URL, or empty string if not set.
   */
  public String getCustomApiUrl() {
    return prefs.getString(KEY_CUSTOM_API_URL, "");
  }

  /**
   * Returns the effective API URL based on the current provider.
   */
  public String getEffectiveApiUrl() {
    String provider = getAiProvider();
    switch (provider) {
      case "groq":    return GROQ_BASE_URL;
      case "gemini":  return GEMINI_BASE_URL;
      case "custom":  return getCustomApiUrl();
      default:        return GROQ_BASE_URL;
    }
  }

  /**
   * Checks if the current provider uses OpenAI‑compatible format.
   * Groq and Custom use OpenAI format; Gemini uses native format.
   */
  public boolean isOpenAICompatibleFormat() {
    String provider = getAiProvider();
    return provider.equals("groq") || provider.equals("custom");
  }

  // ─── Utilities ──────────────────────────────────────────────────────────

  /**
   * Returns the default model for a given provider.
   */
  private String getDefaultModel(String provider) {
    switch (provider) {
      case "groq":    return DEFAULT_MODEL_GROQ;
      case "gemini":  return DEFAULT_MODEL_GEMINI;
      case "custom":  return DEFAULT_MODEL_CUSTOM;
      default:        return DEFAULT_MODEL_GROQ;
    }
  }

  /**
   * Clears all preferences.
   */
  public void clearAll() {
    prefs.edit().clear().apply();
  }

  /**
   * Debug helper: prints all stored preferences.
   */
  public void dumpPrefs() {
    android.util.Log.d("PrefsManager", "=== Current Preferences ===");
    android.util.Log.d("PrefsManager", "Provider: " + getAiProvider());
    android.util.Log.d("PrefsManager", "Model (current): " + getSelectedModel());
    android.util.Log.d("PrefsManager", "Has API Key: " + hasApiKey());
    android.util.Log.d("PrefsManager", "Custom URL: " + getCustomApiUrl());
    android.util.Log.d("PrefsManager", "API URL: " + getEffectiveApiUrl());
  }
}
