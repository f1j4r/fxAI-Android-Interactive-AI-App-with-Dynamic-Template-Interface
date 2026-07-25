package fx.fxAI;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import fx.fxAI.adapter.*;
import fx.fxAI.bridge.*;
import fx.fxAI.model.*;
import fx.fxAI.util.*;
import org.json.*;

public class MainActivity extends Activity {

	private WebView webView;
	private LinearLayout inputLayout, inputToolbar;
	private EditText chatInput;
	private ImageButton sendButton;
	private TextView hideInput, chatMode;
	private AIBridge aiBridge;
	private PrefsManager prefsManager;

	private ChatHistoryManager historyManager;
	private boolean isCapturingState = false;
	private boolean isNewSession = true;
	private String lastUserPrompt = null;

	// Track the currently loaded template info (provided by AIBridge)
	private String currentTemplateName = null;
	private String currentHtmlFileName = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		prefsManager = new PrefsManager(this);
		historyManager = new ChatHistoryManager(this);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setActionBar(toolbar);

		webView = findViewById(R.id.webView);
		setupWebView();

		inputLayout = findViewById(R.id.inputLayout);
		chatInput = findViewById(R.id.chatInput);
		sendButton = findViewById(R.id.sendButton);
		hideInput = findViewById(R.id.hideInput);
		chatMode = findViewById(R.id.chatMode);
    inputToolbar = findViewById(R.id.inputToolbar);

		// Initialize AIBridge with callback
		aiBridge = new AIBridge(this, webView, new AIBridge.BridgeCallback() {
			@Override
			public void onLoadingStart() {
				sendButton.setEnabled(false);
			}

			@Override
			public void onLoadingEnd() {
				sendButton.setEnabled(true);
				currentTemplateName = aiBridge.getCurrentTemplateName();
				currentHtmlFileName = aiBridge.getCurrentHtmlFileName();

				// Save state (forceNew based on isNewSession, but continuation flag overrides)
				saveCurrentState(isNewSession, lastUserPrompt, null);
				isNewSession = false;

				// If it was a continuation, reset the flag after saving
				if (aiBridge.isContinuing()) {
					// The flag will be reset after the save? Actually we can reset it here.
					// But the flag is used inside saveCurrentState, so we can reset after.
					// We need to call a method to reset it.
					aiBridge.resetContinuing();
				}
			}

			@Override
			public void onError(String message) {
				sendButton.setEnabled(true);
				Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
				// Clear template info on error
				currentTemplateName = null;
				currentHtmlFileName = null;
				aiBridge.clearCurrentTemplateInfo();
			}

			@Override
			public void onProgressSaved() {
				saveCurrentState(false, null, null);
			}
		});

		webView.addJavascriptInterface(aiBridge, "AndroidBridge");

		sendButton.setOnClickListener(v -> {
			String userPrompt = chatInput.getText().toString().trim();
			if (!userPrompt.isEmpty()) {
				hideKeyboard();
				if (lastUserPrompt == null) {
					lastUserPrompt = userPrompt;
				}
				aiBridge.requestUI(userPrompt);
				chatInput.setText("");
			} else {
				Toast.makeText(MainActivity.this, "Please enter a prompt", Toast.LENGTH_SHORT).show();
			}
		});

		hideInput.setOnClickListener(v -> {
			inputLayout.setVisibility(inputLayout.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
		});

		chatMode.setOnClickListener(v -> {
      String cTemplate = currentHtmlFileName == null ? "No selected template." : currentHtmlFileName;
			if (cTemplate.matches("chat.html")) return;
			aiBridge.requestUI("Choose \"AI Chat\" template and show welcome introduction.");
      lastUserPrompt = "AI Chat";
			inputLayout.setVisibility(View.GONE);
		});
    
    inputToolbar.setOnLongClickListener(v -> {
      webView.evaluateJavascript(
        "if (typeof FXAI !== 'undefined' && typeof FXAI.showDebug === 'function') " +
        "{ FXAI.showDebug(); } else { 'FXAI not loaded'; }",
        new ValueCallback<String>() {
          @Override
          public void onReceiveValue(String result) {
            if ("FXAI not loaded".equals(result)) {
              Toast.makeText(MainActivity.this, 
                             "Debug panel not available on this page", 
                             Toast.LENGTH_SHORT).show();
            }
          }
        });
      return true;
    });
  
		showConnectionStatus();
		loadWelcomePage();
		isNewSession = true;
	}

	// ─── WebView Setup ─────────────────────────────────────────────────────────

	private void setupWebView() {
		WebSettings settings = webView.getSettings();
		settings.setJavaScriptEnabled(true);
		settings.setDomStorageEnabled(true);
		settings.setAllowFileAccess(true);
		settings.setAllowContentAccess(true);
		settings.setCacheMode(WebSettings.LOAD_DEFAULT);

		webView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
				super.onPageFinished(view, url);
				invalidateOptionsMenu();
			}
		});
	}

	private void loadWelcomePage() {
		currentTemplateName = null;
		currentHtmlFileName = null;
		aiBridge.clearCurrentTemplateInfo();
		webView.loadUrl("file:///android_asset/welcome.html");
	}

	// ─── Toolbar Menu ─────────────────────────────────────────────────────────

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.toolbar_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem rawDataItem = menu.findItem(R.id.action_show_raw_data);
		if (rawDataItem != null) {
			String data = aiBridge.getCurrentDataJson();
			boolean hasData = data != null && !data.isEmpty() && !"null".equals(data);
			rawDataItem.setEnabled(hasData);
			rawDataItem.setIcon(
					hasData ? android.R.drawable.ic_menu_info_details : android.R.drawable.ic_menu_close_clear_cancel);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_back) {
			webView.goBack();
			return true;
		} else if (id == R.id.action_forward) {
			webView.goForward();
			return true;
		} else if (id == R.id.action_new_chat) {
			confirmNewChat();
			return true;
		} else if (id == R.id.action_history) {
			showChatHistory();
			return true;
		} else if (id == R.id.action_settings) {
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		} else if (id == R.id.action_show_raw_data) {
			showRawDataDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showRawDataDialog() {
		String dataJson = aiBridge.getCurrentDataJson();
		if (dataJson == null || dataJson.isEmpty() || "null".equals(dataJson)) {
			Toast.makeText(this, "No data available", Toast.LENGTH_SHORT).show();
			return;
		}

		// Try to pretty‑print the JSON
		String formatted = dataJson;
		try {
			JSONObject obj = new JSONObject(dataJson);
			formatted = obj.toString(2); // indentation of 2 spaces
		} catch (Exception e) {
			// keep raw string if not valid JSON
		}

		// Create the dialog
		TextView textView = new TextView(this);
		textView.setPadding(40, 20, 40, 20);
		textView.setTextIsSelectable(true);
		textView.setText(formatted);
		textView.setTextSize(12);
		textView.setTypeface(android.graphics.Typeface.MONOSPACE);

		ScrollView scrollView = new ScrollView(this);
		scrollView.addView(textView);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Current Template Data");
		builder.setView(scrollView);

		builder.setPositiveButton("Copy", (dialog, which) -> {
			ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip = ClipData.newPlainText("Template Data", dataJson);
			clipboard.setPrimaryClip(clip);
			Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
		});

		builder.setNegativeButton("Close", null);
		builder.show();
	}

	// ─── Navigation ──────────────────────────────────────────────────────────

	private void confirmNewChat() {
		new AlertDialog.Builder(this).setTitle("New Chat")
				.setMessage("Start a new chat? Current conversation will be saved to history.")
				.setPositiveButton("New Chat", (dialog, which) -> {
					startNewChat(); webView.clearHistory(); webView.clearCache(false);
					inputLayout.setVisibility(View.VISIBLE);
				}).setNegativeButton("Cancel", null).show();
	}

	private void startNewChat() {
		if (!historyManager.isEmpty() && historyManager.getCurrentIndex() >= 0
				&& historyManager.getEntry(historyManager.getCurrentIndex()).getHtmlContent() != null) {
			saveCurrentState(true, null, () -> clearAndResetChat());
		} else {
			clearAndResetChat();
		}
	}

	private void clearAndResetChat() {
		lastUserPrompt = null;
		isNewSession = true;
		currentTemplateName = null;
		currentHtmlFileName = null;
		aiBridge.clearCurrentTemplateInfo();
		historyManager.setCurrentIndex(historyManager.isEmpty() ? -1 : historyManager.size() - 1);
		loadWelcomePage();
		Toast.makeText(this, "New chat started", Toast.LENGTH_SHORT).show();
	}

	private void hideKeyboard() {
		View view = this.getCurrentFocus();
		if (view != null) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	// ─── State Capture (NEW) ──────────────────────────────────────────────

	private void saveCurrentState(boolean forceNew, String promptTitle, Runnable callback) {
		// If we are continuing, we always want to update the last entry.
		if (aiBridge.isContinuing()) {
			forceNew = false;
		}
		if (isCapturingState) {
			if (callback != null)
				callback.run();
			return;
		}
		isCapturingState = true;

		final boolean fforceNew = forceNew;
		// Try to get state from template via window.getState()
		webView.evaluateJavascript(
				"(function() { try { return window.getState ? window.getState() : null; } catch(e) { return null; } })();",
				new ValueCallback<String>() {
					@Override
					public void onReceiveValue(String result) {
						try {
							String stateJson = cleanQuotedString(result);
							// Inside saveCurrentState(), when we get state from getState():
							if (stateJson != null && !stateJson.equals("null") && !stateJson.equals("undefined")) {
								String templateName = currentTemplateName;
								String htmlFileName = currentHtmlFileName;
								if (templateName == null || htmlFileName == null) {
									templateName = aiBridge.getCurrentTemplateName();
									htmlFileName = aiBridge.getCurrentHtmlFileName();
								}
								// Inside saveCurrentState(), in the template‑based branch:
								String dataJson = aiBridge.getCurrentDataJson();
								String combinedJson;

								if (dataJson != null && !dataJson.isEmpty()) {
									try {
										JSONObject dataObj = new JSONObject(dataJson);
										JSONObject stateObj = new JSONObject(stateJson);
										dataObj.put("_state", stateObj);
										combinedJson = dataObj.toString();
									} catch (Exception e) {
										combinedJson = stateJson;
									}
								} else {
									// If dataJson is missing, wrap stateJson as _state
									try {
										JSONObject wrapper = new JSONObject();
										wrapper.put("_state", new JSONObject(stateJson));
										combinedJson = wrapper.toString();
									} catch (Exception e) {
										combinedJson = stateJson;
									}
								}

								String title = determineTitle(promptTitle, templateName);
								ChatHistoryEntry entry = new ChatHistoryEntry(title, null, combinedJson, templateName,
										htmlFileName, System.currentTimeMillis());
								addOrUpdateHistory(entry, fforceNew);
							} else {
								capturePlainHtmlInternal(promptTitle, fforceNew);
							}
						} catch (Exception e) {
							// On any error, fallback to plain HTML capture
							capturePlainHtmlInternal(promptTitle, fforceNew);
						} finally {
							isCapturingState = false;
							if (callback != null)
								callback.run();
						}
					}
				});
	}

	private void capturePlainHtmlInternal(final String promptTitle, final boolean forceNew) {
		webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();",
				new ValueCallback<String>() {
					@Override
					public void onReceiveValue(String fullHtml) {
						try {
							String html = cleanQuotedString(fullHtml);
							if (html == null || html.length() < 50) {
								// too short, ignore
								return;
							}
							String title = determineTitle(promptTitle, null);
							ChatHistoryEntry entry = new ChatHistoryEntry(title, html, System.currentTimeMillis());
							addOrUpdateHistory(entry, forceNew);
						} catch (Exception e) {
							// ignore
						} finally {
							isCapturingState = false;
						}
					}
				});
	}

	// ─── History Entry Management ───────────────────────────────────────────

	private void addOrUpdateHistory(ChatHistoryEntry entry, boolean forceNew) {
		if (forceNew || historyManager.isEmpty()) {
			historyManager.addEntry(entry);
		} else {
			historyManager.updateLastEntry(entry);
		}
	}

	private String determineTitle(String promptTitle, String templateName) {
		if (promptTitle != null && !promptTitle.trim().isEmpty()) {
			return promptTitle;
		}
		if (!historyManager.isEmpty()) {
			return historyManager.getEntry(historyManager.size() - 1).getTitle();
		}
		return templateName != null ? templateName : "Chat " + (historyManager.size() + 1);
	}

	private String cleanQuotedString(String raw) {
		if (raw == null)
			return null;
		String s = raw.trim();
		if (s.startsWith("\"") && s.endsWith("\"")) {
			s = s.substring(1, s.length() - 1);
			s = s.replace("\\\"", "\"").replace("\\\\", "\\");
		}
		return s;
	}

	// ─── History Dialog ──────────────────────────────────────────────────────

	private void showChatHistory() {
		if (historyManager.isEmpty()) {
			Toast.makeText(this, "No chat history available", Toast.LENGTH_SHORT).show();
			return;
		}

		ListView listView = new ListView(this);
		listView.setDividerHeight(1);
		listView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT));

		ChatHistoryAdapter adapter = new ChatHistoryAdapter(this, historyManager.getHistory(),
				historyManager.getCurrentIndex(), null);
		listView.setAdapter(adapter);

		adapter.setOnDeleteClickListener(position -> {
			final ChatHistoryEntry entry = historyManager.getEntry(position);
			new AlertDialog.Builder(MainActivity.this).setTitle("Delete Chat")
					.setMessage("Delete \"" + entry.getTitle() + "\"?\n\nThis action cannot be undone.")
					.setPositiveButton("Delete", (d, which) -> {
						historyManager.deleteEntry(position);
						adapter.notifyDataSetChanged();
						adapter.setCurrentIndex(historyManager.getCurrentIndex());
						Toast.makeText(MainActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
					}).setNegativeButton("Cancel", null).show();
		});

		listView.setOnItemClickListener((parent, view, position, id) -> {
			historyManager.setCurrentIndex(position);
			loadHistoryEntry(historyManager.getEntry(position));
			adapter.setCurrentIndex(historyManager.getCurrentIndex());
		});

		listView.setOnItemLongClickListener((parent, view, position, id) -> {
			final ChatHistoryEntry entry = historyManager.getEntry(position);
			EditText input = new EditText(this);
			input.setText(entry.getTitle());
			input.setSingleLine(true);
			input.setHint("Enter chat title");

			new AlertDialog.Builder(this).setTitle("Edit Chat Title").setView(input)
					.setPositiveButton("Save", (d, which) -> {
						String newTitle = input.getText().toString().trim();
						if (!newTitle.isEmpty()) {
							historyManager.updateTitle(position, newTitle);
							adapter.notifyDataSetChanged();
							Toast.makeText(MainActivity.this, "Title updated", Toast.LENGTH_SHORT).show();
						}
					}).setNegativeButton("Cancel", null).show();
			return true;
		});

		AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("Chat History (" + historyManager.size() + " entries)").setView(listView)
				.setNegativeButton("Close", null).create();
		dialog.show();
	}

	private void loadHistoryEntry(ChatHistoryEntry entry) {
    webView.clearHistory(); webView.clearCache(false);
		if (entry.isTemplateBased() && entry.getHtmlFileName() != null) {
			// Load template with saved state
			currentTemplateName = entry.getTemplateName();
			currentHtmlFileName = entry.getHtmlFileName();
			loadTemplateHistory(entry);
		} else if (entry.getHtmlContent() != null) {
			// Plain HTML
			currentTemplateName = null;
			currentHtmlFileName = null;
			aiBridge.clearCurrentTemplateInfo();
			webView.loadData(entry.getHtmlContent(), "text/html", "UTF-8");
		} else {
			Toast.makeText(this, "Cannot load this chat entry", Toast.LENGTH_SHORT).show();
		}
		Toast.makeText(this, "Loaded: " + entry.getTitle(), Toast.LENGTH_SHORT).show();
	}

	private void loadTemplateHistory(final ChatHistoryEntry entry) {
		ThreadManager.getInstance().runOnBackground(new Runnable() {
			@Override
			public void run() {
				final String htmlContent = TemplateFileManager.readTemplateHtml(MainActivity.this,
						entry.getHtmlFileName());

				ThreadManager.getInstance().runOnMain(new Runnable() {
					@Override
					public void run() {
						if (htmlContent != null && entry.getJsonData() != null) {
							// 1. Update the bridge's state
							aiBridge.setCurrentTemplateName(entry.getTemplateName());
							aiBridge.setCurrentHtmlFileName(entry.getHtmlFileName());
							aiBridge.setCurrentDataJson(entry.getJsonData());

							// 2. Also update activity fields (optional)
							currentTemplateName = entry.getTemplateName();
							currentHtmlFileName = entry.getHtmlFileName();

							// 3. Load the HTML
							String baseUrl = "file://" + getFilesDir().getAbsolutePath() + "/templates/";
							webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null);

							// 4. Inject the data after page loads
							ThreadManager.getInstance().runOnMainDelayed(new Runnable() {
								@Override
								public void run() {
									String state = entry.getJsonData();
									webView.evaluateJavascript("try { window.templateData = " + state
											+ "; window.render(); } "
											+ "catch(e) { if (typeof AndroidBridge !== 'undefined') AndroidBridge.log('History render error: ' + e.message); }",
											null);
								}
							}, 300);
						} else {
							Toast.makeText(MainActivity.this, "Template file not found: " + entry.getHtmlFileName(),
									Toast.LENGTH_LONG).show();
						}
					}
				});
			}
		});
	}

	// ─── Status ──────────────────────────────────────────────────────────────

	private void showConnectionStatus() {
		if (!prefsManager.hasApiKey()) {
			Toast.makeText(this, "⚠️ No API key configured. Please go to Settings.", Toast.LENGTH_LONG).show();
			return;
		}
		String provider = prefsManager.getAiProvider();
		String displayName;
		switch (provider) {
			case "groq" :
				displayName = "Groq";
				break;
			case "gemini" :
				displayName = "Gemini";
				break;
			case "custom" :
				displayName = "Custom";
				break;
			default :
				displayName = provider;
		}
		Toast.makeText(this, "✅ AI ready: " + displayName, Toast.LENGTH_SHORT).show();
	}

	// ─── Back Button ─────────────────────────────────────────────────────────

	@Override
	public void onBackPressed() {
		if (webView.canGoBack()) {
			webView.goBack();
		} else {
			super.onBackPressed();
		}
	}

	// ─── Lifecycle ───────────────────────────────────────────────────────────

	@Override
	protected void onPause() {
		super.onPause();
		historyManager.saveToDisk();
		webView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		webView.onResume();
	}

	@Override
	protected void onDestroy() {
		webView.clearCache(true); webView.destroy();
		super.onDestroy();
	}
}

