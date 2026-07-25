// ─────────────────────────────────────────────────────────────────────────────
//  fxAI Universal Template Helper Library
//  Version 1.4
//  Place this file in assets/templates/ and include it in your template:
//  <script src="fxai.js"></script>
// ─────────────────────────────────────────────────────────────────────────────

(function(global) {
  'use strict';
  
  const d = document,
        $q = qs => d.querySelector(qs),
        $qa = qs => d.querySelectorAll(qs);

  // ─── Internal State ────────────────────────────────────────────────────────

  var _data = null; // clean data (without _state)
  var _state = {}; // runtime state (restored from _state or empty)
  var _loadingOverlay = null; // lazy‑created overlay element

  // ─── Core Overridable Functions ──────────────────────────────────────────

  /**
   * Override this function in your template to build the UI from _data.
   * It will be called automatically when new data is injected.
   * You MUST call FXAI.init() at the beginning to extract the state.
   */
  function render() {
    console.warn('FXAI: render() not implemented. Override it in your template.');
  }

  /**
   * Override this function in your template to return the current runtime state
   * as a plain object (e.g., { score: 5, currentIndex: 2 }).
   * This will be persisted alongside the data.
   */
  function getState() {
    console.warn('FXAI: getState() not implemented. Override it in your template.');
    return {};
  }

  // ─── Data Lifecycle ────────────────────────────────────────────────────────

  /**
   * Initialises the library with the raw data from window.templateData.
   * Extracts the _state property and stores it separately.
   * Returns the clean data object (without _state) for your template to use.
   *
   * @param {Object} rawData - window.templateData (may contain _state)
   * @returns {Object} clean data (without _state)
   */
  function init(rawData) {
    if (!rawData || typeof rawData !== 'object') {
      console.warn('FXAI.init(): invalid data, using empty object');
      rawData = {};
    }
    _data = JSON.parse(JSON.stringify(rawData));
    if (_data._state) {
      _state = _data._state;
      delete _data._state;
    } else {
      _state = {};
    }
    return _data;
  }

  /**
   * Persists the current data and state back to the Java bridge.
   * Call this whenever the UI changes (e.g., after render, after user interaction).
   */
  function persist() {
    if (!_data) {
      console.warn('FXAI.persist(): no data to persist. Call init() first.');
      return;
    }
    var toSave = JSON.parse(JSON.stringify(_data));
    toSave._state = typeof getState === 'function' ? getState() : _state;
    if (typeof AndroidBridge !== 'undefined' && AndroidBridge.updateCurrentData) {
      AndroidBridge.updateCurrentData(JSON.stringify(toSave));
    } else {
      console.warn('FXAI.persist(): AndroidBridge.updateCurrentData not available');
    }
  }

  // ─── Bridge Helpers ────────────────────────────────────────────────────────

  /**
   * Calls the AI continuation with a user prompt.
   * Automatically shows and hides the loading overlay.
   * The response will be delivered via a new window.templateData and render() call.
   *
   * @param {string} prompt - The user's action or input.
   * @param {string} [loadingMsg] - Optional custom loading message.
   */
  function generateNext(prompt, loadingMsg) {
    if (typeof AndroidBridge === 'undefined' || !AndroidBridge.generateNext) {
      console.error('FXAI.generateNext(): AndroidBridge.generateNext not available');
      return;
    }
    AndroidBridge.generateNext(prompt);
  }

  /**
   * Saves a progress entry to the memory database.
   * @param {string} topic - The topic or subject.
   * @param {number} score - Numeric score (e.g., correct answers).
   * @param {string} details - Optional details.
   */
  function saveProgress(topic, score, details) {
    if (typeof AndroidBridge !== 'undefined' && AndroidBridge.saveProgress) {
      AndroidBridge.saveProgress(topic, score, details || '');
    } else {
      console.warn('FXAI.saveProgress(): AndroidBridge.saveProgress not available');
    }
  }

  /**
   * Shows a toast message on the Android device.
   */
  function showToast(msg) {
    if (typeof AndroidBridge !== 'undefined' && AndroidBridge.showToast) {
      AndroidBridge.showToast(msg);
    } else {
      console.log('[FXAI] Toast: ' + msg);
    }
  }

  /**
   * Returns recent memory entries as a JSON string (or an empty array if bridge is unavailable).
   */
  function getMemory() {
    if (typeof AndroidBridge !== 'undefined' && AndroidBridge.getRecentMemory) {
      return AndroidBridge.getRecentMemory();
    }
    return '[]';
  }

  // ─── Debugging Helper ──────────────────────────────────────────────────────

  var _debugPanel = null;
  var _logEntries = [];

  /**
   * Logs a message to the debug panel, console, and Android bridge.
   * @param {string} msg - The message to log.
   */
  function log(msg) {
    var timestamp = new Date().toLocaleTimeString();
    var entry = '[' + timestamp + '] ' + msg;
    _logEntries.push(entry);
    // Console
    console.log('[FXAI] ' + msg);
    // Android bridge
    if (typeof AndroidBridge !== 'undefined' && AndroidBridge.log) {
      AndroidBridge.log('[FXAI] ' + msg);
    }
    // Update panel if visible
    if (_debugPanel && _debugPanel.style.display !== 'none') {
      var logArea = _debugPanel.querySelector('.fxai-debug-log');
      if (logArea) {
        logArea.textContent = _logEntries.join('\n');
        logArea.scrollTop = logArea.scrollHeight;
      }
    }
  }

  /**
   * Shows the debug panel.
   */
  function showDebug() {
    if (!_debugPanel) {
      _debugPanel = document.createElement('div');
      _debugPanel.className = 'fxai-debug-panel';
      _debugPanel.style.cssText =
        'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.7);' +
        'z-index:9999;display:flex;align-items:center;justify-content:center;' +
        'font-family:monospace;font-size:14px;color:#f0f0f0;';

      var content = document.createElement('div');
      content.style.cssText =
        'background:#1e1e2f;border-radius:16px;width:95vmin;max-width:95vmin;' +
        'max-height:80vh;display:flex;flex-direction:column;border:1px solid rgba(255,255,255,0.1);';

      // Header
      var header = document.createElement('div');
      header.style.cssText = 'display:flex;justify-content:space-between;align-items:center;';
      var title = document.createElement('span');
      title.textContent = '🐞 Debug Panel';
      title.style.fontWeight = '700';
      var closeBtn = document.createElement('button');
      closeBtn.textContent = '✕';
      closeBtn.style.cssText = 'background:none;border:none;color:#aaa;font-size:22px;cursor:pointer;';
      closeBtn.onclick = hideDebug;
      header.appendChild(title);
      header.appendChild(closeBtn);
      content.appendChild(header);

      // Log area
      var logArea = document.createElement('pre');
      logArea.className = 'fxai-debug-log';
      logArea.style.cssText =
        'background:#0a0a14;border-radius:8px;padding:12px;overflow-y:auto;' +
        'min-height:200px;max-height:40vh;white-space:pre-wrap;word-break:break-all;' +
        'font-size:13px;line-height:1.5;margin-bottom:12px;border:1px solid rgba(255,255,255,0.05);';
      logArea.textContent = _logEntries.join('\n') || 'No logs yet.';
      content.appendChild(logArea);

      // JS execution area
      var execRow = document.createElement('div');
      execRow.style.cssText = 'display:flex;gap:8px;margin-bottom:8px;';
      var execInput = document.createElement('input');
      execInput.type = 'text';
      execInput.placeholder = 'Enter JavaScript expression…';
      execInput.style.cssText =
        'flex:1;padding:10px 14px;border-radius:8px;border:1px solid rgba(255,255,255,0.15);' +
        'background:rgba(255,255,255,0.05);color:#f0f0f0;font-size:14px;outline:none;';
      execInput.addEventListener('keydown', function(e) {
                                   if (e.key === 'Enter') execJSFromInput();
                                 });
      var execBtn = document.createElement('button');
      execBtn.textContent = '▶ Execute';
      execBtn.style.cssText =
        'padding:10px 20px;border:none;border-radius:8px;background:#667eea;color:#fff;' +
        'font-weight:600;cursor:pointer;transition:0.2s;';
      execBtn.onmouseover = function() { this.style.background = '#5a6fd6'; };
      execBtn.onmouseout = function() { this.style.background = '#667eea'; };
      execBtn.onclick = execJSFromInput;
      execRow.appendChild(execInput);
      execRow.appendChild(execBtn);
      content.appendChild(execRow);

      // Result area (display evaluation result)
      var resultArea = document.createElement('pre');
      resultArea.id = 'fxai-debug-result';
      resultArea.style.cssText =
        'background:#0a0a14;border-radius:8px;padding:8px 12px;overflow-y:auto;min-height:30px;' +
        'font-size:13px;border:1px solid rgba(255,255,255,0.05);color:#a0e7a0;' +
        'white-space:pre-wrap;word-break:break-all;';
      resultArea.textContent = 'Result will appear here.';
      content.appendChild(resultArea);

      _debugPanel.appendChild(content);
      document.body.appendChild(_debugPanel);
    }

    _debugPanel.style.display = 'flex';
    // Update log area with latest entries
    var logArea = _debugPanel.querySelector('.fxai-debug-log');
    if (logArea) {
      logArea.textContent = _logEntries.join('\n');
      logArea.scrollTop = logArea.scrollHeight;
    }
  }

  /**
   * Hides the debug panel.
   */
  function hideDebug() {
    if (_debugPanel) {
      _debugPanel.style.display = 'none';
    }
  }

  /**
   * Executes the code from the input field and displays the result.
   */
  function execJSFromInput() {
    var input = _debugPanel.querySelector('input');
    var code = input.value.trim();
    if (!code) return;
    var resultArea = _debugPanel.querySelector('#fxai-debug-result');
    try {
      var result = eval(code);
      var resultStr = typeof result === 'object' ? JSON.stringify(result, null, 2) : String(result);
      resultArea.textContent = '✅ Result:\n' + resultStr;
      log('Executed: ' + code + ' → ' + resultStr);
    } catch (e) {
      resultArea.textContent = '❌ Error: ' + e.message;
      log('Error executing: ' + code + ' → ' + e.message);
    }
    input.value = '';
  }

  /**
   * Returns the current raw templateData (for debugging).
   * @param {boolean} showAlert - If true, shows an alert with the data.
   * @returns {Object|null} The raw data.
   */
  function debugData(showAlert) {
    var raw = typeof window !== 'undefined' ? window.templateData : null;
    if (showAlert) {
      alert('📦 Raw templateData:\n' + JSON.stringify(raw, null, 2));
    }
    log('debugData() called');
    return raw;
  }

  // ─── Loading Overlay ──────────────────────────────────────────────────────

  /**
   * Shows a full‑screen loading spinner with an optional message.
   * Creates the overlay element lazily if it doesn't exist.
   */
  function showLoading(msg) {
    if (!_loadingOverlay) {
      _loadingOverlay = document.createElement('div');
      _loadingOverlay.id = 'fxai-loading-overlay';
      _loadingOverlay.style.cssText =
        'position:fixed;top:0;left:0;width:100%;height:100%;' +
        'background:rgba(0,0,0,0.6);display:flex;flex-direction:column;' +
        'align-items:center;justify-content:center;z-index:999;' +
        'color:white;font-family:sans-serif;';

      var spinner = document.createElement('div');
      spinner.style.cssText =
        'width:48px;height:48px;border:5px solid rgba(255,255,255,0.3);' +
        'border-top-color:#fff;border-radius:50%;animation:fxai-spin 0.8s linear infinite;';
      _loadingOverlay.appendChild(spinner);

      var msgEl = document.createElement('div');
      msgEl.id = 'fxai-loading-message';
      msgEl.style.cssText = 'margin-top:20px;font-size:18px;';
      _loadingOverlay.appendChild(msgEl);

      // Inject keyframe animation into the page
      var style = document.createElement('style');
      style.textContent = '@keyframes fxai-spin { to { transform: rotate(360deg); } }';
      document.head.appendChild(style);

      document.body.appendChild(_loadingOverlay);
    }

    _loadingOverlay.style.display = 'flex';
    var msgEl = _loadingOverlay.querySelector('#fxai-loading-message');
    if (msgEl) {
      msgEl.textContent = msg || 'Loading…';
    }
  }

  /**
   * Hides the loading overlay if it exists.
   */
  function hideLoading() {
    if (_loadingOverlay) {
      _loadingOverlay.style.display = 'none';
    }
  }

  /**
   * Checks if the loading overlay is currently visible.
   */
  function isLoadingVisible() {
    return _loadingOverlay && _loadingOverlay.style.display !== 'none';
  }

  // ─── Global Exposure ──────────────────────────────────────────────────────

  /**
   * The main FXAI object. All public methods are attached here.
   * Template authors should override render() and getState().
   */
  var FXAI = {
    // Core overridable functions
    render: render,
    getState: getState,

    // Data lifecycle
    init: init,
    persist: persist,

    // Bridge helpers
    generateNext: generateNext,
    saveProgress: saveProgress,
    log: log,
    showToast: showToast,
    getMemory: getMemory,

    // Internal accessors
    getData: function() { return _data; },
    getStateObj: function() { return _state; },

    // Debug panel
    showDebug: showDebug,
    hideDebug: hideDebug,
    debugData: debugData,

    // Loading overlay (fixed)
    showLoading: showLoading,
    hideLoading: hideLoading,
    isLoadingVisible: isLoadingVisible
  };

  // Expose globally
  global.FXAI = FXAI;

  // Global shortcuts
  if (typeof window !== 'undefined') {
    window.__debug = function() { FXAI.showDebug(); };
    window.__log = function(msg) { FXAI.log(msg); };
    window.__eval = function(code) {
      try {
        return eval(code);
      } catch (e) {
        FXAI.log('eval error: ' + e.message);
        throw e;
      }
    };
  }

  // ─── Automatic Initialisation (if data is already present) ──────────────

  // If window.templateData already exists (e.g., on history load), call render automatically.
  if (typeof window !== 'undefined' && window.templateData) {
    // Wait a tick to let the template's own scripts override render() if needed.
    setTimeout(function() {
                 if (typeof FXAI.render === 'function') {
                   FXAI.render();
                 }
               }, 0);
  }

})(typeof window !== 'undefined' ? window : this);
