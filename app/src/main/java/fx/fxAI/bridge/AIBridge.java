package fx.fxAI.bridge;

import android.content.*;
import android.graphics.*;
import android.webkit.*;
import android.widget.*;
import fx.fxAI.ai.*;
import fx.fxAI.db.*;
import fx.fxAI.model.*;
import fx.fxAI.util.*;
import java.util.*;
import org.json.*;

public class AIBridge {

  private static final String TAG = "AIBridge";

  private final Context context;
  private final Context appContext;
  private final WebView webView;
  private final PrefsManager prefs;
  private final TemplateDao templateDao;
  private final MemoryDao memoryDao;

  private String currentDataJson = null;
  private boolean isContinuing = false;
  private String currentTemplateName = null;
  private String currentHtmlFileName = null;
  private String pendingDataJson = null;
  
  private List<Template> activeTemplates;

  public interface BridgeCallback {
    void onLoadingStart();
    void onLoadingEnd();
    void onError(String message);
    void onProgressSaved();
  }

  private BridgeCallback callback;
  private WebViewClient originalClient;

  public AIBridge(Context context, WebView webView, BridgeCallback callback) {
    this.context = context;
    this.appContext = context.getApplicationContext();
    this.webView = webView;
    this.callback = callback;
    this.prefs = new PrefsManager(context);
    AppDatabase db = AppDatabase.getInstance(context);
    this.templateDao = new TemplateDao(db);
    this.memoryDao = new MemoryDao(db);

    this.originalClient = webView.getWebViewClient();
    webView.setWebViewClient(new InjectionWebViewClient(originalClient));
  }

  // ─── Public API ───────────────────────────────────────────────────────────

  public void requestUI(final String userPrompt) {
    if (!prefs.hasApiKey()) {
      CrashHandler.showErrorDialog(context, "API Key Missing", "No API key configured.");
      return;
    }
    if (callback != null) callback.onLoadingStart();

    ThreadManager.getInstance().runOnBackground(() -> {
      try {
        activeTemplates = templateDao.getActive();
        if (activeTemplates.isEmpty()) {
          CrashHandler.showErrorDialog(context, "No Templates", "No active templates.");
          return;
        }

        String memoryJson = memoryDao.getRecentAsJson(10);
        String systemPrompt = PromptBuilder.build(activeTemplates, memoryJson);

        AIService.callAI(prefs, systemPrompt, userPrompt, new AIService.AICallback() {
            @Override
            public void onSuccess(String responseContent) {
              parseAndLoadTemplate(responseContent, true);
            }

            @Override
            public void onError(String errorMessage) {
              CrashHandler.showErrorDialog(context, "AI Error", errorMessage);
              if (callback != null) callback.onLoadingEnd();
            }
          });

      } catch (Exception e) {
        CrashHandler.showErrorDialog(context, "Error", e.getMessage());
        if (callback != null) callback.onLoadingEnd();
      }
    });
  }

  public void loadTemplateWithData(String htmlContent, String dataJson, String templateName, String htmlFileName) {
    this.pendingDataJson = dataJson;
    this.currentDataJson = dataJson;
    this.currentTemplateName = templateName;
    this.currentHtmlFileName = htmlFileName;
    String baseUrl = "file://" + appContext.getFilesDir().getAbsolutePath() + "/templates/";
    webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null);
  }

  private void injectData(String dataJson) {
    injectDataWithCallback(dataJson, () -> {
      if (callback != null) callback.onLoadingEnd();
    });
  }
  
  private void injectDataWithCallback(String dataJson, Runnable onComplete) {
    this.currentDataJson = dataJson;
    // Evaluate JS and invoke onComplete when done
    webView.evaluateJavascript(
      "try { window.templateData = " + dataJson + "; window.render(); } " +
      "catch(e) { if (typeof AndroidBridge !== 'undefined') AndroidBridge.log('Inject error: ' + e.message); }",
      new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String result) {
          // Result is a string representation of the return value (or null/undefined)
          // We ignore the result, just signal completion
          if (onComplete != null) {
            ThreadManager.getInstance().runOnMain(onComplete);
          }
        }
      }
    );
  }

  // ─── Internal Parsing ─────────────────────────────────────────────────────

  private void parseAndLoadTemplate(String aiResponse, boolean loadHtml) {
    try {
      String cleanResponse = aiResponse.trim();
      if (cleanResponse.startsWith("```json")) {
        cleanResponse = cleanResponse.substring(7);
      }
      if (cleanResponse.startsWith("```")) {
        cleanResponse = cleanResponse.substring(3);
      }
      if (cleanResponse.endsWith("```")) {
        cleanResponse = cleanResponse.substring(0, cleanResponse.length() - 3);
      }
      cleanResponse = cleanResponse.trim();

      JSONObject responseJson = new JSONObject(cleanResponse);
      String selectedTemplateName;
      JSONObject dataJson;

      if (responseJson.has("selected_template")) {
        selectedTemplateName = responseJson.getString("selected_template");
        dataJson = responseJson.getJSONObject("data");
      } else {
        // Fallback: AI returned only the data object.
        if (currentTemplateName == null) {
          throw new Exception("No selected_template and no current template to fall back to.");
        }
        selectedTemplateName = currentTemplateName;
        dataJson = responseJson;
      }

      if (selectedTemplateName.equals("none")) {
        String message = dataJson.optString("message", "No matching template found.");
        CrashHandler.showErrorDialog(context, "No Template", message);
        if (callback != null) callback.onLoadingEnd();
        return;
      }

      Template template = templateDao.getByName(selectedTemplateName);
      if (template == null) {
        template = templateDao.getByNameIgnoreCase(selectedTemplateName);
      }
      if (template == null) {
        CrashHandler.showErrorDialog(context, "Template Not Found", "Template not found: " + selectedTemplateName);
        if (callback != null) callback.onLoadingEnd();
        return;
      }

      // ── NEW: Append‑mode merge (for continuations) ──
      String finalDataString;
      if (!loadHtml && currentDataJson != null && dataJson.has("newItem")) {
        // The AI returned a single new item – merge it into the existing data
        finalDataString = mergeNewItem(currentDataJson, dataJson, template);
      } else {
        // Normal full‑object replacement
        finalDataString = dataJson.toString();
      }

      final Template finalTemplate = template;
      final String dataString = finalDataString;

      if (loadHtml) {
        final String htmlContent = TemplateFileManager.readTemplateHtml(context, finalTemplate.htmlFileName);
        if (htmlContent == null) {
          CrashHandler.showErrorDialog(context, "File Error", "Template file not found: " + finalTemplate.htmlFileName);
          if (callback != null) callback.onLoadingEnd();
          return;
        }
        ThreadManager.getInstance().runOnMain(() -> {
          loadTemplateWithData(htmlContent, dataString, finalTemplate.name, finalTemplate.htmlFileName);
        });
      } else {
        // Continuation: inject the merged or replaced data
        ThreadManager.getInstance().runOnMain(() -> {
          injectData(dataString);
        });
      }

    } catch (Exception e) {
      CrashHandler.showErrorDialog(context, "Parse Error", "Failed to parse AI response: " + e.getMessage());
      if (callback != null) callback.onLoadingEnd();
    }
  }
  
  /**
   * Merges a new item into the existing data for array‑based templates.
   * If the schema does not have a top‑level array, returns the new data as‑is.
   *
   * @param existingDataJson  The full current data (string)
   * @param newDataJson       The AI response containing a "newItem" object
   * @param template          The active template
   * @return                  Merged JSON string, or the new data if merge fails
   */
  private String mergeNewItem(String existingDataJson, JSONObject newDataJson, Template template) {
    try {
      JSONObject existing = new JSONObject(existingDataJson);
      JSONObject newItem = newDataJson.getJSONObject("newItem");
      String arrayKey = PromptBuilder.findFirstArrayKey(template.jsonSchema);
      if (arrayKey != null && existing.has(arrayKey)) {
        JSONArray array = existing.getJSONArray(arrayKey);
        array.put(newItem);
        return existing.toString();
      } else {
        // No array found – treat as full replacement (fallback)
        return newDataJson.toString();
      }
    } catch (Exception e) {
      // On any error, use the new data as‑is (full replacement)
      return newDataJson.toString();
    }
  }

  // ─── WebViewClient Wrapper ───────────────────────────────────────────────

  private class InjectionWebViewClient extends WebViewClient {
    private WebViewClient delegate;

    InjectionWebViewClient(WebViewClient delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      if (delegate != null) {
        delegate.onPageFinished(view, url);
      } else {
        super.onPageFinished(view, url);
      }

      if (pendingDataJson != null) {
        final String data = pendingDataJson;
        // Use the new method with a callback that clears pendingData and calls onLoadingEnd
        injectDataWithCallback(data, () -> {
          pendingDataJson = null;
          if (callback != null) callback.onLoadingEnd();
        });
      }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      if (delegate != null) {
        delegate.onPageStarted(view, url, favicon);
      } else {
        super.onPageStarted(view, url, favicon);
      }
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      if (delegate != null) {
        return delegate.shouldOverrideUrlLoading(view, url);
      }
      return super.shouldOverrideUrlLoading(view, url);
    }
  }

  // ─── Getters / Setters ──────────────────────────────────────────────────

  public boolean isContinuing() {
    return isContinuing;
  }

  public void resetContinuing() {
    isContinuing = false;
  }

  public String getCurrentTemplateName() {
    return currentTemplateName;
  }
  
  public void setCurrentTemplateName(String name) {
    this.currentTemplateName = name;
  }

  public void setCurrentHtmlFileName(String fileName) {
    this.currentHtmlFileName = fileName;
  }

  public String getCurrentHtmlFileName() {
    return currentHtmlFileName;
  }

  public String getCurrentDataJson() {
    return currentDataJson;
  }

  public void setCurrentDataJson(String dataJson) {
    this.currentDataJson = dataJson;
  }

  public void clearCurrentTemplateInfo() {
    currentTemplateName = null;
    currentHtmlFileName = null;
    currentDataJson = null;
  }

  // ─── JavaScript Interface ────────────────────────────────────────────────

  @JavascriptInterface
  public void saveProgress(final String topic, final int score, final String details) {
    ThreadManager.getInstance().runOnBackground(() -> {
      try {
        fx.fxAI.model.MemoryEntry entry = new fx.fxAI.model.MemoryEntry(topic, score, details);
        memoryDao.insert(entry);
        ThreadManager.getInstance().runOnMain(() -> {
          Toast.makeText(context, "Progress saved", Toast.LENGTH_SHORT).show();
          if (callback != null) callback.onProgressSaved();
        });
      } catch (Exception ignored) {}
    });
  }

  @JavascriptInterface
  public String getRecentMemory() {
    return memoryDao.getRecentAsJson(20);
  }
  
  @JavascriptInterface
  public void updateCurrentData(String dataJson) {
    this.currentDataJson = dataJson;
  }

  @JavascriptInterface
  public void showToast(final String message) {
    ThreadManager.getInstance().runOnMain(() ->
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    );
  }

  @JavascriptInterface
  public void generateNext(final String prompt) {
    // Validate API key
    if (!prefs.hasApiKey()) {
      CrashHandler.showErrorDialog(context, "API Key Missing", "No API key configured.");
      return;
    }

    // Validate that we have a current template and data
    if (currentTemplateName == null) {
      CrashHandler.showErrorDialog(context, "No Template", "No active template to continue.");
      return;
    }
    if (currentDataJson == null || currentDataJson.isEmpty()) {
      CrashHandler.showErrorDialog(context, "No Data", "No current data to continue from. Please start a new session.");
      return;
    }

    // Set continuation flag and show loading UI
    isContinuing = true;
    ThreadManager.getInstance().runOnMain(() -> {
      if (callback != null) callback.onLoadingStart();
    });

    // Fetch the template object and memory, then call AI on background
    ThreadManager.getInstance().runOnBackground(() -> {
      try {
        // 1. Retrieve the full template from DB
        Template template = templateDao.getByName(currentTemplateName);
        if (template == null) {
          // Fallback to case‑insensitive lookup
          template = templateDao.getByNameIgnoreCase(currentTemplateName);
        }
        if (template == null) {
          throw new Exception("Template '" + currentTemplateName + "' not found in database.");
        }

        // 2. Get recent memory as JSON
        String memoryJson = memoryDao.getRecentAsJson(10);

        // 3. Build the generic continuation system prompt
        String systemPrompt = PromptBuilder.buildContinuation(
          template,          // full Template object
          currentDataJson,   // current state
          prompt,            // user input
          memoryJson        // user memory
        );

        // 4. Call the AI service
        AIService.callAI(prefs, systemPrompt, prompt, new AIService.AICallback() {
            @Override
            public void onSuccess(String responseContent) {
              // Parse and load the new data (without reloading HTML)
              ThreadManager.getInstance().runOnMain(() -> {
                parseAndLoadTemplate(responseContent, false);
              });
            }

            @Override
            public void onError(String errorMessage) {
              CrashHandler.showErrorDialog(context, "AI Error", errorMessage);
              isContinuing = false;
              ThreadManager.getInstance().runOnMain(() -> {
                if (callback != null) callback.onLoadingEnd();
              });
            }
          });

      } catch (Exception e) {
        // Handle any errors (template lookup, memory, etc.)
        CrashHandler.showErrorDialog(context, "Continuation Error", e.getMessage());
        isContinuing = false;
        ThreadManager.getInstance().runOnMain(() -> {
          if (callback != null) callback.onLoadingEnd();
        });
      }
    });
  }

  @JavascriptInterface
  public void log(String message) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
