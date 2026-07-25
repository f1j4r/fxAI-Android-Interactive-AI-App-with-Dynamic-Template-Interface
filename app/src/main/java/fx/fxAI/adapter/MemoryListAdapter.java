package fx.fxAI.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import fx.fxAI.R;
import fx.fxAI.model.MemoryEntry;

public class MemoryListAdapter extends BaseAdapter {

  private Context context;
  private List<MemoryEntry> memoryList;
  private SimpleDateFormat dateFormat;

  public MemoryListAdapter(Context context, List<MemoryEntry> memoryList) {
    this.context = context;
    this.memoryList = memoryList;
    this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
  }

  @Override
  public int getCount() {
    return memoryList.size();
  }

  @Override
  public MemoryEntry getItem(int position) {
    return memoryList.get(position);
  }

  @Override
  public long getItemId(int position) {
    return memoryList.get(position).id;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    ViewHolder holder;

    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.item_memory, parent, false);
      holder = new ViewHolder();
      holder.txtTopic = convertView.findViewById(R.id.txtMemoryTopic);
      holder.txtScore = convertView.findViewById(R.id.txtMemoryScore);
      holder.txtDetails = convertView.findViewById(R.id.txtMemoryDetails);
      holder.txtTime = convertView.findViewById(R.id.txtMemoryTime);
      convertView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }

    MemoryEntry entry = memoryList.get(position);

    holder.txtTopic.setText(entry.topic != null ? entry.topic : "Unknown");
    holder.txtScore.setText("Score: " + entry.score);
    holder.txtDetails.setText(entry.details != null ? entry.details : "");
    holder.txtTime.setText(dateFormat.format(new Date(entry.timestamp)));

    return convertView;
  }

  public void refreshData(List<MemoryEntry> newList) {
    this.memoryList.clear();
    this.memoryList.addAll(newList);
    notifyDataSetChanged();
  }

  private static class ViewHolder {
    TextView txtTopic;
    TextView txtScore;
    TextView txtDetails;
    TextView txtTime;
  }
}

