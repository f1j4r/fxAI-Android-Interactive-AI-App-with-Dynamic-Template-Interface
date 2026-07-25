package fx.fxAI.util;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import fx.fxAI.*;
import java.io.*;

import android.os.Process;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

  private Context context;
  private Thread.UncaughtExceptionHandler defaultHandler;

  public CrashHandler(Context context) {
    this.context = context.getApplicationContext();
    this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
  }

  public static void init(Context context) {
    Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
  }
  
  /**
   * Global error dialog with Copy and Dismiss buttons.
   * Can be called from any thread – UI is posted to main thread.
   */
  public static void showErrorDialog(final Context context, final String title, final String message) {
    if (context == null) return;

    ThreadManager.getInstance().runOnMain(new Runnable() {
        @Override
        public void run() {
          try {
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setTitle(title != null ? title : "Error")
            .setMessage(message)
            .setPositiveButton("📋 Copy", (dialog, which) -> {
              copyToClipboard(context, message);
              Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Dismiss", null)
              .setCancelable(true);
            builder.show();
          } catch (Exception e) {
            // Fallback to Toast if dialog fails
            Toast.makeText(context, "AD Error: " + message, Toast.LENGTH_LONG).show();
          }
        }
      });
  }

  private static void copyToClipboard(Context context, String text) {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText("Error Details", text);
    clipboard.setPrimaryClip(clip);
  }

  @Override
  public void uncaughtException(Thread thread, Throwable throwable) {
    try {
      // Build crash report
      String crashReport = buildCrashReport(thread, throwable);

      // Launch CrashActivity to show the dialog
      Intent intent = new Intent(context, CrashActivity.class);
      intent.putExtra(CrashActivity.EXTRA_CRASH_REPORT, crashReport);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      context.startActivity(intent);

      // Kill the current process
      Process.killProcess(Process.myPid());
      System.exit(1);

    } catch (Exception e) {
      // If we can't show the crash dialog, let the default handler deal with it
      if (defaultHandler != null) {
        defaultHandler.uncaughtException(thread, throwable);
      }
    }
  }

  private String buildCrashReport(Thread thread, Throwable throwable) {
    StringBuilder report = new StringBuilder();

    // App info
    report.append("=== APP INFO ===\n");
    try {
      String packageName = context.getPackageName();
      String versionName = context.getPackageManager()
        .getPackageInfo(packageName, 0).versionName;
      int versionCode = context.getPackageManager()
        .getPackageInfo(packageName, 0).versionCode;

      report.append("Package: ").append(packageName).append("\n");
      report.append("Version: ").append(versionName)
        .append(" (").append(versionCode).append(")\n");
    } catch (Exception e) {
      report.append("Could not retrieve app info\n");
    }

    // Device info
    report.append("\n=== DEVICE INFO ===\n");
    report.append("Android: ").append(Build.VERSION.RELEASE)
      .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
    report.append("Device: ").append(Build.MANUFACTURER).append(" ")
      .append(Build.MODEL).append("\n");
    report.append("Product: ").append(Build.PRODUCT).append("\n");

    // Thread info
    report.append("\n=== THREAD INFO ===\n");
    report.append("Thread: ").append(thread.getName()).append("\n");

    // Stack trace
    report.append("\n=== STACK TRACE ===\n");
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);
    report.append(sw.toString());

    // Cause chain
    Throwable cause = throwable.getCause();
    while (cause != null) {
      report.append("\n=== CAUSED BY ===\n");
      sw = new StringWriter();
      pw = new PrintWriter(sw);
      cause.printStackTrace(pw);
      report.append(sw.toString());
      cause = cause.getCause();
    }

    return report.toString();
  }
}

