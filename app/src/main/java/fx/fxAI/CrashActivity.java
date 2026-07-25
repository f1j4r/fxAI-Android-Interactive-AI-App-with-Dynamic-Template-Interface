package fx.fxAI;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class CrashActivity extends Activity {

  public static final String EXTRA_CRASH_REPORT = "crash_report";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Remove title bar
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    // Get crash report from intent
    Intent intent = getIntent();
    final String crashReport = intent.getStringExtra(EXTRA_CRASH_REPORT);

    // Build UI programmatically (no XML needed for crash screen)
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    layout.setBackgroundColor(0xFFF5F5F5); // Light gray background

    // Title
    TextView title = new TextView(this);
    title.setText("⚠️ App Crashed");
    title.setTextSize(24);
    title.setTextColor(0xFFD32F2F); // Red
    layout.addView(title);

    // Subtitle
    TextView subtitle = new TextView(this);
    subtitle.setText("Sorry, the app encountered an error. You can copy the details below and report it.");
    subtitle.setTextSize(14);
    subtitle.setTextColor(0xFF757575); // Gray
    subtitle.setPadding(0, 8, 0, 24);
    layout.addView(subtitle);

    // Buttons row
    LinearLayout buttonRow = new LinearLayout(this);
    buttonRow.setOrientation(LinearLayout.HORIZONTAL);
    buttonRow.setPadding(0, 0, 0, 16);

    // Copy button
    Button btnCopy = new Button(this);
    btnCopy.setText("📋 Copy Report");
    btnCopy.setBackgroundColor(0xFF1976D2); // Blue
    btnCopy.setTextColor(0xFFFFFFFF); // White
    btnCopy.setPadding(32, 16, 32, 16);
    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
      0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    btnParams.setMargins(0, 0, 8, 0);
    buttonRow.addView(btnCopy, btnParams);

    // Restart button
    Button btnRestart = new Button(this);
    btnRestart.setText("🔄 Restart App");
    btnRestart.setBackgroundColor(0xFF4CAF50); // Green
    btnRestart.setTextColor(0xFFFFFFFF); // White
    btnRestart.setPadding(32, 16, 32, 16);
    LinearLayout.LayoutParams btnParams2 = new LinearLayout.LayoutParams(
      0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    btnParams2.setMargins(8, 0, 0, 0);
    buttonRow.addView(btnRestart, btnParams2);

    layout.addView(buttonRow);

    // Crash report in scrollable text view
    ScrollView scrollView = new ScrollView(this);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);

    TextView reportText = new TextView(this);
    reportText.setText(crashReport);
    reportText.setTextSize(12);
    reportText.setTextColor(0xFF212121); // Dark gray
    reportText.setBackgroundColor(0xFFFFFFFF); // White background
    reportText.setPadding(16, 16, 16, 16);
    reportText.setTypeface(android.graphics.Typeface.MONOSPACE);

    scrollView.addView(reportText);
    layout.addView(scrollView, scrollParams);

    setContentView(layout);

    // Copy button click
    btnCopy.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
          ClipData clip = ClipData.newPlainText("Crash Report", crashReport);
          clipboard.setPrimaryClip(clip);
          Toast.makeText(CrashActivity.this, "✓ Copied to clipboard", Toast.LENGTH_SHORT).show();
        }
      });

    // Restart button click
    btnRestart.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          // Launch MainActivity
          Intent restartIntent = new Intent(CrashActivity.this, MainActivity.class);
          restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(restartIntent);
          finish();
          // Kill the crash activity process
          System.exit(0);
        }
      });
  }

  @Override
  public void onBackPressed() {
    // Don't allow going back - force restart or manual close
    finish();
  }
}

