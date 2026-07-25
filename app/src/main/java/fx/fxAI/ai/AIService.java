package fx.fxAI.ai;

import fx.fxAI.util.*;
import java.io.*;
import java.net.*;
import org.json.*;

public class AIService {
  /**
   * Handles HTTP calls to AI APIs.
   * Supports: Groq (OpenAI-compatible), Gemini (Google native), Custom (OpenAI-compatible).
   */

  private static final String TAG = "AIService";
  private static final int TIMEOUT_MS = 30000; // 30 seconds
  private static final int MAX_RETRIES = 2;

  /**
   * Callback interface for async AI responses.
   */
  public interface AICallback {
    void onSuccess(String responseContent);
    void onError(String errorMessage);
  }

  /**
   * Main entry point. Call this to send a prompt to the AI.
   * Automatically routes to the correct API format based on PrefsManager.
   */
  public static void callAI(PrefsManager prefs, String systemPrompt, 
                            String userPrompt, AICallback callback) {
    String provider = prefs.getAiProvider();

    if (provider.equals("gemini")) {
      callGemini(prefs, systemPrompt, userPrompt, callback);
    } else {
      // Groq and Custom both use OpenAI-compatible format
      callOpenAICompatible(prefs, systemPrompt, userPrompt, callback);
    }
  }

  // ─────────────────────────────────────────────────────────
  //  OpenAI-Compatible Format (Groq, Custom)
  // ─────────────────────────────────────────────────────────
  private static void callOpenAICompatible(PrefsManager prefs, String systemPrompt,
                                           String userPrompt, AICallback callback) {
    String apiUrl = prefs.getEffectiveApiUrl();
    String apiKey = prefs.getApiKey(prefs.getAiProvider());
    String model = prefs.getSelectedModel();

    if (apiUrl == null || apiUrl.isEmpty()) {
      callback.onError("API URL is not configured");
      return;
    }
    if (apiKey == null || apiKey.isEmpty()) {
      callback.onError("API key is not configured. Go to Settings.");
      return;
    }

    try {
      JSONObject body = new JSONObject();
      body.put("model", model);
      body.put("temperature", 0.7);

      JSONArray messages = new JSONArray();
      JSONObject sysMsg = new JSONObject();
      sysMsg.put("role", "system");
      sysMsg.put("content", systemPrompt);
      messages.put(sysMsg);

      JSONObject userMsg = new JSONObject();
      userMsg.put("role", "user");
      userMsg.put("content", userPrompt);
      messages.put(userMsg);

      body.put("messages", messages);

      // Use the helper (auth header)
      makePostRequest(apiUrl, apiKey, true, body, new AICallback() {
          @Override
          public void onSuccess(String rawResponse) {
            try {
              JSONObject jsonResponse = new JSONObject(rawResponse);
              String content = jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
              callback.onSuccess(content);
            } catch (Exception e) {
              callback.onError("Failed to parse AI response: " + e.getMessage());
            }
          }

          @Override
          public void onError(String errorMessage) {
            callback.onError(errorMessage);
          }
        }, 0);

    } catch (Exception e) {
      callback.onError("Request building error: " + e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────
  //  Google Gemini Native Format
  // ─────────────────────────────────────────────────────────
  private static void callGemini(PrefsManager prefs, String systemPrompt,
                                 String userPrompt, AICallback callback) {
    String apiKey = prefs.getApiKey(prefs.getAiProvider());
    String model = prefs.getSelectedModel(prefs.getAiProvider());

    if (apiKey == null || apiKey.isEmpty()) {
      callback.onError("API key is not configured. Go to Settings.");
      return;
    }

    String baseUrl = PrefsManager.GEMINI_BASE_URL + model + ":generateContent";

    try {
      JSONObject body = buildGeminiBody(systemPrompt, userPrompt);
      makePostRequest(baseUrl, apiKey, false, body, new AICallback() {
          @Override
          public void onSuccess(String rawResponse) {
            try {
              JSONObject jsonResponse = new JSONObject(rawResponse);
              String content = jsonResponse
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");
              callback.onSuccess(content);
            } catch (Exception e) {
              callback.onError("Failed to parse Gemini response: " + e.getMessage());
            }
          }

          @Override
          public void onError(String errorMessage) {
            callback.onError(errorMessage);
          }
        }, 0);
    } catch (Exception e) {
      callback.onError("Request building error: " + e.getMessage());
    }
  }
  
  /**
   * Builds the Gemini request body following the official API specification.
   * Uses a lower temperature (0.3) for more consistent output.
   */
  private static JSONObject buildGeminiBody(String systemPrompt, String userPrompt) throws Exception {
    JSONObject body = new JSONObject();

    // system_instruction (official format)
    if (systemPrompt != null && !systemPrompt.isEmpty()) {
      JSONObject systemInstruction = new JSONObject();
      JSONArray systemParts = new JSONArray();
      JSONObject systemPart = new JSONObject();
      systemPart.put("text", systemPrompt);
      systemParts.put(systemPart);
      systemInstruction.put("parts", systemParts);
      body.put("system_instruction", systemInstruction);
    }

    // user message
    JSONArray contents = new JSONArray();
    JSONObject userContent = new JSONObject();
    userContent.put("role", "user");
    JSONArray userParts = new JSONArray();
    JSONObject userPart = new JSONObject();
    userPart.put("text", userPrompt);
    userParts.put(userPart);
    userContent.put("parts", userParts);
    contents.put(userContent);
    body.put("contents", contents);

    // generation config – lower temperature
    JSONObject genConfig = new JSONObject();
    genConfig.put("temperature", 0.3);
    body.put("generationConfig", genConfig);

    return body;
  }
  
  /**
   * Performs a POST request with JSON body and returns the raw response string.
   *
   * @param url             full request URL
   * @param apiKey          API key (used either in header or query param)
   * @param useAuthHeader   if true, sends "Authorization: Bearer <apiKey>"; 
   *                        if false, appends ?key=<apiKey> to the URL (Gemini style)
   * @param body            JSON payload
   * @param callback        receives the raw response body on success or error message
   */
  private static void makePostRequest(String url, String apiKey, boolean useAuthHeader,
                                               JSONObject body, AICallback callback, int attempt) {
    try {
      // Build final URL
      String finalUrl = url;
      if (!useAuthHeader && apiKey != null && !apiKey.isEmpty()) {
        finalUrl = url + (url.contains("?") ? "&" : "?") + "key=" + apiKey;
      }

      HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      if (useAuthHeader && apiKey != null && !apiKey.isEmpty()) {
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
      }
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);
      conn.setDoOutput(true);

      // Write body
      OutputStream os = conn.getOutputStream();
      os.write(body.toString().getBytes("UTF-8"));
      os.flush();
      os.close();

      // Read response
      int responseCode = conn.getResponseCode();
      InputStream inputStream = (responseCode >= 200 && responseCode < 300)
        ? conn.getInputStream()
        : conn.getErrorStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        response.append(line);
      }
      reader.close();
      conn.disconnect();

      if (responseCode >= 200 && responseCode < 300) {
        callback.onSuccess(response.toString());
      } else {
        String errorMsg = extractErrorMessage(responseCode, response.toString());
        if (attempt < MAX_RETRIES && isRetryableError(responseCode)) {
          // Retry with exponential backoff (optional)
          Thread.sleep(1000 * (attempt + 1)); // simple delay
          makePostRequest(url, apiKey, useAuthHeader, body, callback, attempt + 1);
        } else {
          callback.onError(errorMsg);
        }
      }
    } catch (Exception e) {
      if (attempt < MAX_RETRIES) {
        try { Thread.sleep(1000 * (attempt + 1)); } catch (InterruptedException ignored) {}
        makePostRequest(url, apiKey, useAuthHeader, body, callback, attempt + 1);
      } else {
        callback.onError("Network error: " + e.getMessage());
      }
    }
  }
  
  private static String extractErrorMessage(int responseCode, String responseBody) {
    String errorMsg = "API Error (" + responseCode + ")";
    try {
      JSONObject errorJson = new JSONObject(responseBody);
      if (errorJson.has("error")) {
        Object err = errorJson.get("error");
        if (err instanceof JSONObject) {
          errorMsg = ((JSONObject) err).optString("message", errorMsg);
        } else {
          errorMsg = err.toString();
        }
      }
    } catch (Exception e) {
      int maxLen = Math.min(200, responseBody.length());
      errorMsg += ": " + responseBody.substring(0, maxLen);
    }
    return errorMsg;
  }

  private static boolean isRetryableError(int responseCode) {
    return responseCode >= 500 || responseCode == 429 || responseCode == 408;
  }
}

