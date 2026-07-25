package fx.fxAI;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import fx.fxAI.adapter.*;
import fx.fxAI.ai.*;
import fx.fxAI.db.*;
import fx.fxAI.model.*;
import fx.fxAI.util.*;
import java.io.*;
import java.text.*;
import java.util.*;
import org.json.*;

public class SettingsActivity extends Activity
implements TemplateListAdapter.OnTemplateChangedListener,
TemplateListAdapter.OnItemClickListener {

  private static final int PICK_HTML_FILE = 1001;

  private PrefsManager prefsManager;
  private AppDatabase db;
  private TemplateDao templateDao;
  private MemoryDao memoryDao;

  // Provider/Model UI
  private Spinner spinnerProvider, spinnerModel;
  private EditText editApiKey, editCustomUrl;
  private Button btnTestConnection, btnSave;
  private TextView txtStatus, txtProviderInfo, txtApiKeyHint, txtUrlHint, txtModelStatus;
  private ProgressBar progressModels;

  // Template Management UI
  private ListView listTemplates;
  private TemplateListAdapter templateAdapter;
  private List<Template> templateList;
  private Button btnAddTemplate, btnRestoreDefaults;
  private Template editingTemplate = null;
  
  // Template Dialog fields
  private EditText dialogEditName, dialogEditDesc, dialogEditSchema;
  private TextView dialogTxtFile;
  private Uri selectedHtmlUri = null;

  // Memory Management UI
  private ListView listMemory;
  private MemoryListAdapter memoryAdapter;
  private List<MemoryEntry> memoryList;
  private Button btnClearMemory;
  private TextView txtMemoryStats;

  // Provider options
  private final String[] providers = {"groq", "gemini", "custom"};
  private final String[] providerLabels = {"Groq (Fast & Free)", "Google Gemini", "Custom (OpenAI-compatible)"};
  private final String[] providerDescriptions = {
    "Groq - Fast inference with Llama, Mixtral & Gemma models",
    "Gemini - Google's multimodal AI with generous free tier",
    "Custom - Any OpenAI-compatible API endpoint"
  };
  private final String[] apiKeyHints = {
    "Get your key at: console.groq.com",
    "Get your key at: aistudio.google.com/apikey",
    "Enter the API key for your custom endpoint"
  };

  private final String[] groqModels = {
    "llama-3.3-70b-versatile", "llama-3.1-8b-instant",
    "llama3-70b-8192", "llama3-8b-8192",
    "mixtral-8x7b-32768", "gemma2-9b-it"
  };
  private final String[] geminiModels = {
    "gemini-3.1-flash-lite", "gemini-2.0-flash", "gemini-2.0-flash-lite",
    "gemini-1.5-flash", "gemini-1.5-flash-8b", "gemini-1.5-pro"
  };
  private final String[] customModels = {
    "gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo", "custom-model"
  };

  private int currentProviderIndex = 0;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);

    // Initialize managers
    prefsManager = new PrefsManager(this);
    db = AppDatabase.getInstance(this);
    templateDao = new TemplateDao(db);
    memoryDao = new MemoryDao(db);

    // Setup Toolbar
    Toolbar toolbar = findViewById(R.id.settingsToolbar);
    setActionBar(toolbar);
    getActionBar().setDisplayHomeAsUpEnabled(true);
    getActionBar().setTitle("Settings");

    // Initialize Provider/Model views
    spinnerProvider = findViewById(R.id.spinnerProvider);
    spinnerModel = findViewById(R.id.spinnerModel);
    editApiKey = findViewById(R.id.editApiKey);
    editCustomUrl = findViewById(R.id.editCustomUrl);
    btnTestConnection = findViewById(R.id.btnTestConnection);
    btnSave = findViewById(R.id.btnSave);
    txtStatus = findViewById(R.id.txtStatus);
    txtProviderInfo = findViewById(R.id.txtProviderInfo);
    txtApiKeyHint = findViewById(R.id.txtApiKeyHint);
    txtUrlHint = findViewById(R.id.txtUrlHint);
    progressModels = findViewById(R.id.progressModels); // add to layout
    txtModelStatus = findViewById(R.id.txtModelStatus); // add to layout

    // Initialize Template Management views
    listTemplates = findViewById(R.id.listTemplates);
    btnAddTemplate = findViewById(R.id.btnAddTemplate);
    btnRestoreDefaults = findViewById(R.id.btnRestoreDefaults);
    btnRestoreDefaults.setOnClickListener(v -> restoreDefaultTemplates());

    // Initialize Memory Management views
    listMemory = findViewById(R.id.listMemory);
    btnClearMemory = findViewById(R.id.btnClearMemory);
    txtMemoryStats = findViewById(R.id.txtMemoryStats);

    // Setup all sections
    setupProviderSpinner();
    setupTemplateList();
    setupMemoryManagement();
    loadSavedSettings();

    btnTestConnection.setOnClickListener(v -> testConnection());
    
    // Save button
    btnSave.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          saveSettings();
        }
      });

    // Add Template button
    btnAddTemplate.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          showAddTemplateDialog();
        }
      });
  }

  // ─────────────────────────────────────────────────────────
  //  Provider/Model Setup
  // ─────────────────────────────────────────────────────────

  private void setupProviderSpinner() {
    ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(
      this, android.R.layout.simple_spinner_item, providerLabels);
    providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinnerProvider.setAdapter(providerAdapter);

    spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
          currentProviderIndex = position;
          String provider = providers[position];

          txtProviderInfo.setText(providerDescriptions[position]);
          txtApiKeyHint.setText(apiKeyHints[position]);
          updateUrlField(provider);

          // Load the saved key for this provider
          loadApiKeyForProvider(provider);

          // Load saved model for this provider
          String savedModel = prefsManager.getSelectedModel(provider);
          fetchModelsForProvider(provider, savedModel);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
      });
  }
  
  private void fetchModelsForProvider(String provider, String preferredModel) {
    String apiKey = editApiKey.getText().toString().trim();
    progressModels.setVisibility(View.VISIBLE);
    txtModelStatus.setText("Fetching models...");
    spinnerModel.setEnabled(false);

    ModelFetcher.fetchModels(provider, apiKey, this, new ModelFetcher.ModelFetchCallback() {
        @Override
        public void onSuccess(List<String> models) {
          progressModels.setVisibility(View.GONE);
          spinnerModel.setEnabled(true);
          if (models.isEmpty()) {
            txtModelStatus.setText("No models found – enter manually");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(SettingsActivity.this,
                                                              android.R.layout.simple_spinner_item, new String[]{"custom-model"});
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerModel.setAdapter(adapter);
          } else {
            txtModelStatus.setText(models.size() + " models loaded");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(SettingsActivity.this,
                                                              android.R.layout.simple_spinner_item, models);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerModel.setAdapter(adapter);

            // Restore saved selection if possible
            if (preferredModel != null && !preferredModel.isEmpty()) {
              int pos = models.indexOf(preferredModel);
              if (pos >= 0) {
                spinnerModel.setSelection(pos);
              } else {
                // Fallback to first model if saved not found
                spinnerModel.setSelection(0);
              }
            } else {
              spinnerModel.setSelection(0);
            }
          }
        }

        @Override
        public void onError(String errorMessage) {
          progressModels.setVisibility(View.GONE);
          spinnerModel.setEnabled(true);
          txtModelStatus.setText("Error: " + errorMessage);
          // Fallback to hardcoded list
          updateModelSpinner(provider);
        }
      });
  }

  private void updateModelSpinner(String provider) {
    String[] models;
    switch (provider) {
      case "groq": models = groqModels; break;
      case "gemini": models = geminiModels; break;
      default: models = customModels; break;
    }
    ArrayAdapter<String> adapter = new ArrayAdapter<>(
      this, android.R.layout.simple_spinner_item, models);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinnerModel.setAdapter(adapter);
  }

  private void updateUrlField(String provider) {
    switch (provider) {
      case "groq":
        editCustomUrl.setText(PrefsManager.GROQ_BASE_URL);
        editCustomUrl.setEnabled(false);
        editCustomUrl.setInputType(InputType.TYPE_NULL);
        editCustomUrl.setTextColor(Color.GRAY);
        txtUrlHint.setText("Built-in Groq endpoint (read-only)");
        break;
      case "gemini":
        editCustomUrl.setText(PrefsManager.GEMINI_BASE_URL);
        editCustomUrl.setEnabled(false);
        editCustomUrl.setInputType(InputType.TYPE_NULL);
        editCustomUrl.setTextColor(Color.GRAY);
        txtUrlHint.setText("Built-in Gemini endpoint (read-only)");
        break;
      case "custom":
        editCustomUrl.setEnabled(true);
        editCustomUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        editCustomUrl.setTextColor(Color.BLACK);
        String savedUrl = prefsManager.getCustomApiUrl();
        editCustomUrl.setText(savedUrl);
        editCustomUrl.setHint("https://api.example.com/v1/chat/completions");
        txtUrlHint.setText("Enter your OpenAI-compatible API endpoint");
        break;
    }
  }
  
  private void loadSavedSettings() {
    String savedProvider = prefsManager.getAiProvider();
    int providerPosition = 0;
    for (int i = 0; i < providers.length; i++) {
      if (providers[i].equals(savedProvider)) {
        providerPosition = i;
        break;
      }
    }
    spinnerProvider.setSelection(providerPosition);

    loadApiKeyForProvider(savedProvider);
    // Load model for this provider
    String savedModel = prefsManager.getSelectedModel(savedProvider);
    if (savedModel != null && !savedModel.isEmpty()) {
      ArrayAdapter modelAdapter = (ArrayAdapter) spinnerModel.getAdapter();
      if (modelAdapter != null) {
        for (int i = 0; i < modelAdapter.getCount(); i++) {
          if (modelAdapter.getItem(i).equals(savedModel)) {
            spinnerModel.setSelection(i);
            break;
          }
        }
      }
    }
  }

  private void saveSettings() {
    String provider = providers[currentProviderIndex];
    String model = (String) spinnerModel.getSelectedItem();
    String apiKey = editApiKey.getText().toString().trim();
    String customUrl = "";

    if (provider.equals("custom")) {
      customUrl = editCustomUrl.getText().toString().trim();
      if (customUrl.isEmpty()) {
        Toast.makeText(this, "Please enter a custom API URL", Toast.LENGTH_SHORT).show();
        return;
      }
    }

    if (apiKey.isEmpty()) {
      Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show();
      return;
    }

    // Save provider‑specific key
    prefsManager.saveApiKey(provider, apiKey);
    prefsManager.saveAiProvider(provider);
    // Save model per provider
    prefsManager.saveSelectedModel(provider, model);

    if (provider.equals("custom")) {
      prefsManager.saveCustomApiUrl(customUrl);
    }

    String providerLabel = providerLabels[currentProviderIndex];
    txtStatus.setText("✓ Saved: " + providerLabel + " / " + model);
    Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
  }
  
  private void loadApiKeyForProvider(String provider) {
    String key = prefsManager.getApiKey(provider);
    if (key != null && !key.isEmpty()) {
      editApiKey.setText(key);
      editApiKey.setHint("API key found");
    } else {
      editApiKey.setText("");
      editApiKey.setHint("Enter your " + provider + " API key");
    }
  }
  
  private void testConnection() {
    String provider = providers[currentProviderIndex];
    String apiKey = editApiKey.getText().toString().trim();
    String model = (String) spinnerModel.getSelectedItem();

    if (apiKey.isEmpty()) {
      Toast.makeText(this, "Please enter API key first", Toast.LENGTH_SHORT).show();
      return;
    }

    // Show progress
    btnTestConnection.setEnabled(false);
    btnTestConnection.setText("Testing...");
    txtStatus.setText("Testing connection...");

    ThreadManager.getInstance().runOnBackground(() -> {
      try {
        // Use a simple prompt to test
        String testPrompt = "Respond with exactly 'OK' if you can read this.";
        String systemPrompt = "You are a test assistant. Respond with exactly 'OK'.";

        // Temporarily save settings for the test
        PrefsManager tempPrefs = new PrefsManager(this);
        tempPrefs.saveApiKey(provider, apiKey);
        tempPrefs.saveAiProvider(provider);
        tempPrefs.saveSelectedModel(provider, model);
        if (provider.equals("custom")) {
          tempPrefs.saveCustomApiUrl(editCustomUrl.getText().toString().trim());
        }

        AIService.callAI(tempPrefs, systemPrompt, testPrompt, new AIService.AICallback() {
            @Override
            public void onSuccess(String responseContent) {
              ThreadManager.getInstance().runOnMain(() -> {
                btnTestConnection.setEnabled(true);
                btnTestConnection.setText("Test Connection");
                if (responseContent != null && responseContent.contains("OK")) {
                  txtStatus.setText("✓ Connection successful!");
                  Toast.makeText(SettingsActivity.this, "Connection test passed", Toast.LENGTH_SHORT).show();
                } else {
                  txtStatus.setText("✗ Unexpected response: " + responseContent);
                  Toast.makeText(SettingsActivity.this, "Test failed: unexpected response", Toast.LENGTH_SHORT).show();
                }
              });
            }

            @Override
            public void onError(String errorMessage) {
              ThreadManager.getInstance().runOnMain(() -> {
                btnTestConnection.setEnabled(true);
                btnTestConnection.setText("Test Connection");
                txtStatus.setText("✗ Connection failed: " + errorMessage);
                Toast.makeText(SettingsActivity.this, "Test failed: " + errorMessage, Toast.LENGTH_LONG).show();
              });
            }
          });
      } catch (Exception e) {
        ThreadManager.getInstance().runOnMain(() -> {
          btnTestConnection.setEnabled(true);
          btnTestConnection.setText("Test Connection");
          txtStatus.setText("✗ Error: " + e.getMessage());
          Toast.makeText(SettingsActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
      }
    });
  }

  // ─────────────────────────────────────────────────────────
  //  Template Management
  // ─────────────────────────────────────────────────────────

  private void setupTemplateList() {
    templateList = templateDao.getAll();
    templateAdapter = new TemplateListAdapter(this, templateList, this, this);
    listTemplates.setAdapter(templateAdapter);
  }

  @Override
  public void onTemplateChanged() {
    templateList.clear();
    templateList.addAll(templateDao.getAll());
    templateAdapter.notifyDataSetChanged();
  }
  
  private void showTemplateDialog(String title, Template template) {
    View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_edit_template, null);
    dialogEditName = dialogView.findViewById(R.id.editTemplateName);
    dialogEditDesc = dialogView.findViewById(R.id.editTemplateDesc);
    dialogEditSchema = dialogView.findViewById(R.id.editJsonSchema);
    dialogTxtFile = dialogView.findViewById(R.id.txtSelectedFile);
    Button btnPickHtml = dialogView.findViewById(R.id.btnPickHtml);

    // Pre-fill if editing
    if (template != null) {
      dialogEditName.setText(template.name);
      dialogEditDesc.setText(template.description);
      dialogEditSchema.setText(template.jsonSchema);
      dialogTxtFile.setText("📄 " + template.htmlFileName);
      dialogTxtFile.setTextColor(0xFF4CAF50);
      selectedHtmlUri = null; // no new file selected yet
    } else {
      dialogEditName.setText("");
      dialogEditDesc.setText("");
      dialogEditSchema.setText("");
      dialogTxtFile.setText("No file selected");
      dialogTxtFile.setTextColor(Color.GRAY);
      selectedHtmlUri = null;
    }

    btnPickHtml.setOnClickListener(v -> {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("text/html");
      startActivityForResult(intent, PICK_HTML_FILE);
    });

    new AlertDialog.Builder(this)
      .setTitle(title)
      .setView(dialogView)
      .setPositiveButton(template != null ? "Update" : "Add", (dialog, which) -> {
      if (template != null) {
        handleUpdateTemplate(template);
      } else {
        handleAddTemplate();
      }
    })
    .setNegativeButton("Cancel", null)
      .show();
  }

  private void showEditTemplateDialog(Template template) {
    editingTemplate = template;
    selectedHtmlUri = null; // reset file selection
    showTemplateDialog("Edit Template", template);
  }

  private void showAddTemplateDialog() {
    editingTemplate = null;
    selectedHtmlUri = null;
    showTemplateDialog("Add New Template", null);
  }

  private void handleAddTemplate() {
    String name = dialogEditName.getText().toString().trim();
    String desc = dialogEditDesc.getText().toString().trim();
    String schema = dialogEditSchema.getText().toString().trim();

    if (name.isEmpty() || desc.isEmpty() || schema.isEmpty()) {
      Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
      return;
    }
    if (selectedHtmlUri == null) {
      Toast.makeText(this, "Please select an HTML file", Toast.LENGTH_SHORT).show();
      return;
    }
    if (templateDao.getByName(name) != null) {
      Toast.makeText(this, "A template with this name already exists", Toast.LENGTH_SHORT).show();
      return;
    }

    String htmlFileName = "custom_" + System.currentTimeMillis() + "_" +
      name.replaceAll("[^a-zA-Z0-9]", "_") + ".html";
    boolean copied = TemplateFileManager.copyUriToInternalStorage(this, selectedHtmlUri, htmlFileName);
    if (!copied) {
      Toast.makeText(this, "Failed to copy HTML file", Toast.LENGTH_SHORT).show();
      return;
    }

    Template template = new Template(name, desc, htmlFileName, schema);
    templateDao.insert(template);
    onTemplateChanged();
    Toast.makeText(this, "Template added!", Toast.LENGTH_SHORT).show();
  }

  private void handleUpdateTemplate(Template existing) {
    String newName = dialogEditName.getText().toString().trim();
    String newDesc = dialogEditDesc.getText().toString().trim();
    String newSchema = dialogEditSchema.getText().toString().trim();

    if (newName.isEmpty() || newDesc.isEmpty() || newSchema.isEmpty()) {
      Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
      return;
    }

    // Check if name already exists (excluding self)
    Template nameConflict = templateDao.getByName(newName);
    if (nameConflict != null && nameConflict.id != existing.id) {
      Toast.makeText(this, "Another template with this name already exists", Toast.LENGTH_SHORT).show();
      return;
    }

    // Handle file replacement if a new file was selected
    String newHtmlFileName = existing.htmlFileName;
    if (selectedHtmlUri != null) {
      String fileName = "custom_" + System.currentTimeMillis() + "_" +
        newName.replaceAll("[^a-zA-Z0-9]", "_") + ".html";
      boolean copied = TemplateFileManager.copyUriToInternalStorage(this, selectedHtmlUri, fileName);
      if (copied) {
        newHtmlFileName = fileName;
        // Optionally delete old file? Not necessary but can be done.
      } else {
        Toast.makeText(this, "Failed to copy new HTML file, keeping old one", Toast.LENGTH_SHORT).show();
      }
    }

    // Update template
    existing.name = newName;
    existing.description = newDesc;
    existing.jsonSchema = newSchema;
    existing.htmlFileName = newHtmlFileName;
    templateDao.update(existing);

    // If name changed, update chat history references
    if (!existing.name.equals(newName)) {
      new ChatHistoryDao(db).updateTemplateName(existing.name, newName);
    }

    onTemplateChanged();
    Toast.makeText(this, "Template updated!", Toast.LENGTH_SHORT).show();
  }
  
  /**
   * Extracts metadata from an HTML file comment block.
   * Expects a comment like:
   * <!--
   *     "name": "AI Chat",
   *     "description": "...",
   *     "jsonSchema": "{...}"
   * -->
   * @param htmlContent  The full HTML content as a string
   * @return             JSONObject with name, description, jsonSchema, or null
   */
  private JSONObject parseTemplateMetadata(String htmlContent) {
    if (htmlContent == null) return null;
    int start = htmlContent.indexOf("<!--");
    int end = htmlContent.indexOf("-->", start);
    if (start == -1 || end == -1) return null;

    String comment = htmlContent.substring(start + 4, end).trim();
    // The comment should be a JSON object without outer braces.
    // Wrap in braces to form a valid JSON object.
    try {
      String json = "{" + comment + "}";
      return new JSONObject(json);
    } catch (Exception e) {
      CrashHandler.showErrorDialog(this, "Failed to parse metadata comment", e.toString());
      return null;
    }
  }
  
  private void restoreDefaultTemplates() {
    new AlertDialog.Builder(this)
      .setTitle("Restore Built‑in Templates")
      .setMessage("This will restore the default templates from assets and copy their HTML files. Existing custom templates will not be affected.")
      .setPositiveButton("Restore", (dialog, which) -> {
      ThreadManager.getInstance().runOnBackground(() -> {
        try {
          // 1. Copy all HTML files from assets/templates/
          TemplateFileManager.copyAllTemplateAssets(this);

          // 2. Read metadata.json
          String metadataJson;
          try (InputStream in = getAssets().open("templates/metadata.json")) {
            byte[] data = new byte[in.available()];
            in.read(data);
            metadataJson = new String(data, "UTF-8");
          }

          JSONArray arr = new JSONArray(metadataJson);
          boolean anyChanged = false;

          for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String name = obj.getString("name");
            String description = obj.optString("description", "");
            String htmlFileName = obj.getString("htmlFileName");
            String jsonSchema = obj.getString("jsonSchema");
            boolean isDefault = obj.optBoolean("isDefault", true);

            Template existing = templateDao.getByName(name);
            if (existing == null) {
              // Insert new built‑in template
              Template t = new Template(name, description, htmlFileName, jsonSchema);
              t.isDefault = true;
              t.isActive = true;
              templateDao.insert(t);
              anyChanged = true;
            } else {
              // If exists but inactive, reactivate and update fields
              if (!existing.isActive) {
                existing.isActive = true;
                anyChanged = true;
              }
              // Optionally update description/schema if changed
              if (!existing.description.equals(description) ||
                  !existing.jsonSchema.equals(jsonSchema)) {
                existing.description = description;
                existing.jsonSchema = jsonSchema;
                anyChanged = true;
              }
              if (anyChanged) {
                templateDao.update(existing);
              }
            }
          }

          final boolean finalAnyChanged = anyChanged;
          ThreadManager.getInstance().runOnMain(() -> {
            templateList.clear();
            templateList.addAll(templateDao.getAll());
            templateAdapter.notifyDataSetChanged();
            Toast.makeText(SettingsActivity.this,
                           finalAnyChanged ? "Built‑in templates restored and activated" : "All built‑in templates already present",
                           Toast.LENGTH_LONG).show();
          });

        } catch (Exception e) {
          ThreadManager.getInstance().runOnMain(() ->
          Toast.makeText(SettingsActivity.this, "Error restoring templates: " + e.getMessage(),
                         Toast.LENGTH_LONG).show()
          );
        }
      });
    })
    .setNegativeButton("Cancel", null)
      .show();
  }

  // ─────────────────────────────────────────────────────────
  //  Memory Management
  // ─────────────────────────────────────────────────────────

  private void setupMemoryManagement() {
    memoryList = memoryDao.getRecent(50);
    memoryAdapter = new MemoryListAdapter(this, memoryList);
    listMemory.setAdapter(memoryAdapter);

    updateMemoryStats();

    listMemory.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          MemoryEntry entry = memoryList.get(position);
          showMemoryDetails(entry);
        }
      });

    btnClearMemory.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          confirmClearMemory();
        }
      });
  }

  private void updateMemoryStats() {
    int totalEntries = memoryList.size();

    if (totalEntries == 0) {
      txtMemoryStats.setText("No memory entries yet");
      return;
    }

    int totalScore = 0;
    for (MemoryEntry entry : memoryList) {
      totalScore += entry.score;
    }
    double avgScore = (double) totalScore / totalEntries;

    long mostRecent = 0;
    for (MemoryEntry entry : memoryList) {
      if (entry.timestamp > mostRecent) {
        mostRecent = entry.timestamp;
      }
    }

    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    String recentDate = sdf.format(new Date(mostRecent));

    txtMemoryStats.setText(String.format(
                             "Total entries: %d | Average score: %.1f | Last activity: %s",
                             totalEntries, avgScore, recentDate
                           ));
  }

  private void showMemoryDetails(MemoryEntry entry) {
    View dialogView = getLayoutInflater().inflate(R.layout.dialog_memory_details, null);

    TextView dialogTopic = dialogView.findViewById(R.id.dialogTopic);
    TextView dialogScore = dialogView.findViewById(R.id.dialogScore);
    TextView dialogDetails = dialogView.findViewById(R.id.dialogDetails);
    TextView dialogTimestamp = dialogView.findViewById(R.id.dialogTimestamp);

    dialogTopic.setText(entry.topic != null ? entry.topic : "Unknown");
    dialogScore.setText(String.valueOf(entry.score));
    dialogDetails.setText(entry.details != null ? entry.details : "No details");

    SimpleDateFormat sdf = new SimpleDateFormat(
      "EEEE, MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
    dialogTimestamp.setText(sdf.format(new Date(entry.timestamp)));

    new AlertDialog.Builder(this)
      .setTitle("Memory Entry")
      .setView(dialogView)
      .setPositiveButton("Close", null)
      .show();
  }

  private void confirmClearMemory() {
    new AlertDialog.Builder(this)
      .setTitle("Clear All Memory")
      .setMessage("Are you sure you want to delete all memory entries? This cannot be undone.")
      .setPositiveButton("Clear All", new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          memoryDao.deleteAll();
          memoryList.clear();
          memoryAdapter.notifyDataSetChanged();
          updateMemoryStats();
          Toast.makeText(SettingsActivity.this, "All memory cleared", 
                         Toast.LENGTH_SHORT).show();
        }
      })
      .setNegativeButton("Cancel", null)
      .show();
  }

  // ─────────────────────────────────────────────────────────
  //  Lifecycle
  // ─────────────────────────────────────────────────────────

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }
  
  @Override
  public void onItemClick(Template template) {
    showEditTemplateDialog(template);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_HTML_FILE && resultCode == RESULT_OK && data != null) {
      selectedHtmlUri = data.getData();
      if (selectedHtmlUri != null) {
        String fileName = TemplateFileManager.getFileNameFromUri(this, selectedHtmlUri);
        dialogTxtFile.setText("✓ Selected: " + fileName);
        dialogTxtFile.setTextColor(0xFF4CAF50);

        // ─── Auto‑fill metadata ──────────────────────────────────────
        String content = TemplateFileManager.readContentFromUri(this, selectedHtmlUri);
        if (content != null) {
          JSONObject meta = parseTemplateMetadata(content);
          if (meta != null) {
            String name = meta.optString("name");
            String description = meta.optString("description");
            String schema = meta.optString("jsonSchema");
            if (!name.isEmpty()) {
              dialogEditName.setText(name);
            }
            if (!description.isEmpty()) {
              dialogEditDesc.setText(description);
            }
            if (!schema.isEmpty()) {
              dialogEditSchema.setText(schema);
            }
            // Optional: Toast notification
            Toast.makeText(this, "Metadata auto‑filled from file", Toast.LENGTH_SHORT).show();
          } else {
            Toast.makeText(this, "No metadata found in file. Fill manually.", Toast.LENGTH_SHORT).show();
          }
        }
      }
    }
  }
}

