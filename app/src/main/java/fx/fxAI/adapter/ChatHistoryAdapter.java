package fx.fxAI.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

import fx.fxAI.R;
import fx.fxAI.model.ChatHistoryEntry;

public class ChatHistoryAdapter extends BaseAdapter {

  private Context context;
  private List<ChatHistoryEntry> historyList;
  private int currentIndex;
  private OnDeleteClickListener deleteListener;

  public interface OnDeleteClickListener {
    void onDeleteClick(int position);
  }

  public ChatHistoryAdapter(Context context, List<ChatHistoryEntry> historyList,
                            int currentIndex, OnDeleteClickListener listener) {
    this.context = context;
    this.historyList = historyList;
    this.currentIndex = currentIndex;
    this.deleteListener = listener;
  }
  
  // Setter for listener (called after adapter creation)
  public void setOnDeleteClickListener(OnDeleteClickListener listener) {
    this.deleteListener = listener;
  }

  public void setCurrentIndex(int index) {
    this.currentIndex = index;
    notifyDataSetChanged();
  }

  @Override
  public int getCount() {
    return historyList.size();
  }

  @Override
  public ChatHistoryEntry getItem(int position) {
    return historyList.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;

    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.item_chat_history, parent, false);
      holder = new ViewHolder();
      holder.titleText = convertView.findViewById(R.id.chatTitle);
      holder.templateText = convertView.findViewById(R.id.chatTemplate);
      holder.timestampText = convertView.findViewById(R.id.chatTimestamp);
      holder.currentIndicator = convertView.findViewById(R.id.currentIndicator);
      holder.deleteButton = convertView.findViewById(R.id.deleteButton);
      convertView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }

    ChatHistoryEntry entry = historyList.get(position);

    // Set title
    holder.titleText.setText(entry.getTitle());

    // Set template info
    if (entry.isTemplateBased()) {
      holder.templateText.setText("📋 " + entry.getTemplateName());
      holder.templateText.setVisibility(View.VISIBLE);
    } else {
      holder.templateText.setVisibility(View.GONE);
    }

    // Set timestamp
    holder.timestampText.setText(formatTimestamp(entry.getTimestamp()));

    // Show current indicator
    holder.currentIndicator.setVisibility(position == currentIndex ? View.VISIBLE : View.GONE);

    // Delete button click
    holder.deleteButton.setOnClickListener(v -> {
      if (deleteListener != null) {
        deleteListener.onDeleteClick(position);
      }
    });

    return convertView;
  }

  private String formatTimestamp(long timestamp) {
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
      "MMM dd, HH:mm", java.util.Locale.getDefault());
    return sdf.format(new java.util.Date(timestamp));
  }

  private static class ViewHolder {
    TextView titleText;
    TextView templateText;
    TextView timestampText;
    TextView currentIndicator;
    ImageButton deleteButton;
  }
}

