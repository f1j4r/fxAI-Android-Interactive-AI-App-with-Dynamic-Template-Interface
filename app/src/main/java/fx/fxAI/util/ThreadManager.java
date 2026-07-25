package fx.fxAI.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple threading utility.
 * - runOnBackground(): for network/DB operations
 * - runOnMain(): for UI updates
 */
public class ThreadManager {

  private static ThreadManager instance;
  private ExecutorService executor;
  private Handler mainHandler;

  private ThreadManager() {
    executor = Executors.newCachedThreadPool();
    mainHandler = new Handler(Looper.getMainLooper());
  }

  public static synchronized ThreadManager getInstance() {
    if (instance == null) {
      instance = new ThreadManager();
    }
    return instance;
  }

  public void runOnBackground(Runnable task) {
    executor.execute(task);
  }

  public void runOnMain(Runnable task) {
    mainHandler.post(task);
  }

  public void runOnMainDelayed(Runnable task, long delayMs) {
    mainHandler.postDelayed(task, delayMs);
  }

  public void shutdown() {
    executor.shutdown();
  }
}

