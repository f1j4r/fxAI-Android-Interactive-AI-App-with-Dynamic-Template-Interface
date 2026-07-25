package fx.fxAI;

import android.app.Application;

import fx.fxAI.util.CrashHandler;

public class FxAIApp extends Application {

  @Override
  public void onCreate() {
    super.onCreate();

    // Initialize global crash handler
    CrashHandler.init(this);
  }
}

