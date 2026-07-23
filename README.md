# FxAI – AI‑driven interactive content on Android

**FxAI** is a native Android application that uses AI to generate and update interactive HTML templates inside a WebView. It supports multiple AI providers (Groq, Google Gemini, or any OpenAI‑compatible API) and allows users to create, edit, and reuse custom templates.

## ✨ Features

- **AI‑powered content generation** – choose from built‑in templates (Quiz, Text Adventure, Flashcards, AI Chat) or create your own.
- **Continuation & state management** – keep a conversation going; the app merges new AI responses into existing data.
- **In‑app memory** – save progress (topic + score) and recall it in future prompts.
- **Full chat history** – every session is saved and can be restored later.
- **Template manager** – add, edit, delete, and toggle templates with a simple UI.
- **No external libraries** – built purely with Android SDK and Java.

## 🧱 Architecture

- **Java** (no Kotlin) – all code is written in Java.
- **SQLite** with DAO pattern for `templates`, `user_memory`, and `chat_history`.
- **WebView** with a custom JavaScript bridge (`AIBridge`) for two‑way communication.
- **Async** operations via a custom `ThreadManager` (no RxJava/Coroutines).
- **Assets** – templates are shipped as HTML/JS/CSS files inside `assets/templates/`, with metadata embedded in HTML comments.

## 🚀 Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and run on an Android device/emulator (min API 21).
4. In the app, go to **Settings** → add your AI provider’s API key.
5. Start a new chat or choose a template from the main screen.

> **Note:** The app uses `android.permission.INTERNET` and reads files from internal storage. No other permissions are required.

## 📦 Built‑in Templates

| Template | Description |
|----------|-------------|
| **AI Chat** | General‑purpose chat with HTML‑formatted responses. |
| **Quiz**   | Multiple‑choice quiz with scoring and explanations. |
| **Text Adventure** | Interactive story with choices and inventory. |
| **Flashcards** | Flip‑card memorization with simple progress tracking. |

You can add your own templates by adding an HTML file (with metadata comment) via Settings > "Add New Template".

## 🔧 AI Providers

- **Groq** – fast inference with Llama, Mixtral, Gemma.
- **Google Gemini** – multimodal models (Gemini 1.5/2.0).
- **Custom** – any OpenAI‑compatible endpoint (enter URL + API key).

## 📄 License

MIT – feel free to use, modify, and distribute.