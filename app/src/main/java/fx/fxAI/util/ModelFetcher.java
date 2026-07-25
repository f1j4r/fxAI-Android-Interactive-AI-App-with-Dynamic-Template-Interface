package fx.fxAI.util;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ModelFetcher {

  private static final String PREFS_NAME = "fxai_prefs";
  private static final String KEY_CACHED_MODELS = "cached_models";
  private static final String KEY_CACHED_PROVIDER = "cached_provider";

  public interface ModelFetchCallback {
    void onSuccess(List<String> models);
    void onError(String errorMessage);
  }

  /**
   * Fetch available models from the provider's API.
   * For Groq: https://api.groq.com/openai/v1/models
   * For Gemini: https://generativelanguage.googleapis.com/v1beta/models?key=API_KEY
   * For custom: returns an empty list (user can manually enter).
   * All callbacks are invoked on the main thread.
   */
  public static void fetchModels(String provider, String apiKey, Context context, ModelFetchCallback callback) {
    // Check cache first (on main thread, but we read quickly)
    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    String cachedProvider = prefs.getString(KEY_CACHED_PROVIDER, "");
    String cachedModelsJson = prefs.getString(KEY_CACHED_MODELS, null);

    if (cachedProvider.equals(provider) && cachedModelsJson != null) {
      try {
        JSONArray arr = new JSONArray(cachedModelsJson);
        List<String> models = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
          models.add(arr.getString(i));
        }
        // Callback on main thread
        ThreadManager.getInstance().runOnMain(() -> callback.onSuccess(models));
        return;
      } catch (Exception ignored) {
        // Cache corrupt – fall through to fetch
      }
    }

    // No cache or provider changed – fetch on background
    ThreadManager.getInstance().runOnBackground(() -> {
      try {
        String urlString;
        if (provider.equals("groq")) {
          urlString = "https://api.groq.com/openai/v1/models";
        } else if (provider.equals("gemini")) {
          if (apiKey == null || apiKey.isEmpty()) {
            ThreadManager.getInstance().runOnMain(() ->
            callback.onError("API key required to fetch Gemini models")
            );
            return;
          }
          urlString = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
        } else {
          // Custom – no fetch, return empty list on main thread
          ThreadManager.getInstance().runOnMain(() -> callback.onSuccess(new ArrayList<>()));
          return;
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (provider.equals("groq") && apiKey != null && !apiKey.isEmpty()) {
          conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        int responseCode = conn.getResponseCode();
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
          reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
          reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          response.append(line);
        }
        reader.close();
        conn.disconnect();

        if (responseCode >= 200 && responseCode < 300) {
          List<String> models = parseModels(provider, response.toString());
          if (models.isEmpty()) {
            ThreadManager.getInstance().runOnMain(() -> callback.onError("No models found"));
            return;
          }
          // Cache the list on background (we're already on background)
          JSONArray arr = new JSONArray();
          for (String m : models) arr.put(m);
          prefs.edit()
            .putString(KEY_CACHED_MODELS, arr.toString())
            .putString(KEY_CACHED_PROVIDER, provider)
            .apply();

          // Callback on main thread
          ThreadManager.getInstance().runOnMain(() -> callback.onSuccess(models));
        } else {
          String errorMsg = "HTTP " + responseCode;
          try {
            JSONObject errorJson = new JSONObject(response.toString());
            if (errorJson.has("error")) {
              Object err = errorJson.get("error");
              if (err instanceof JSONObject) {
                errorMsg = ((JSONObject) err).optString("message", errorMsg);
              } else {
                errorMsg = err.toString();
              }
            }
          } catch (Exception ignored) {}
          final String finalError = errorMsg;
          ThreadManager.getInstance().runOnMain(() -> callback.onError(finalError));
        }

      } catch (Exception e) {
        final String err = "Network error: " + e.getMessage();
        ThreadManager.getInstance().runOnMain(() -> callback.onError(err));
      }
    });
  }

  private static List<String> parseModels(String provider, String jsonResponse) throws Exception {
    JSONObject obj = new JSONObject(jsonResponse);
    List<String> models = new ArrayList<>();

    if (provider.equals("groq")) {
      JSONArray data = obj.getJSONArray("data");
      for (int i = 0; i < data.length(); i++) {
        JSONObject model = data.getJSONObject(i);
        String id = model.getString("id");
        // Filter out non‑chat models (optional)
        if (id.startsWith("whisper") || id.startsWith("distil")) continue;
        models.add(id);
      }
    } else if (provider.equals("gemini")) {
      JSONArray data = obj.getJSONArray("models");
      for (int i = 0; i < data.length(); i++) {
        JSONObject model = data.getJSONObject(i);
        String name = model.getString("name");
        if (name.startsWith("models/")) {
          name = name.substring(7);
        }
        models.add(name);
      }
    }
    return models;
  }
}
