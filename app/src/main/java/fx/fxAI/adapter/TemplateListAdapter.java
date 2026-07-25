package fx.fxAI.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import fx.fxAI.R;
import fx.fxAI.db.AppDatabase;
import fx.fxAI.db.TemplateDao;
import fx.fxAI.model.Template;

public class TemplateListAdapter extends BaseAdapter {

  private Context context;
  private List<Template> templates;
  private TemplateDao templateDao;
  private OnTemplateChangedListener changeListener;
  private OnItemClickListener itemClickListener;

  // ─── Interfaces ──────────────────────────────────────────────────────────

  public interface OnTemplateChangedListener {
    void onTemplateChanged();
  }

  public interface OnItemClickListener {
    void onItemClick(Template template);
  }

  // ─── Constructor ────────────────────────────────────────────────────────

  public TemplateListAdapter(Context context, List<Template> templates,
                             OnTemplateChangedListener changeListener,
                             OnItemClickListener itemClickListener) {
    this.context = context;
    this.templates = templates;
    this.changeListener = changeListener;
    this.itemClickListener = itemClickListener;
    this.templateDao = new TemplateDao(AppDatabase.getInstance(context));
  }

  // ─── BaseAdapter Methods ───────────────────────────────────────────────

  @Override
  public int getCount() {
    return templates.size();
  }

  @Override
  public Template getItem(int position) {
    return templates.get(position);
  }

  @Override
  public long getItemId(int position) {
    return templates.get(position).id;
  }

  @Override
  public View getView(final int position, View convertView, ViewGroup parent) {
    ViewHolder holder;

    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.item_template, parent, false);
      holder = new ViewHolder();
      holder.txtName = convertView.findViewById(R.id.txtTemplateName);
      holder.txtDesc = convertView.findViewById(R.id.txtTemplateDesc);
      holder.txtStatus = convertView.findViewById(R.id.txtTemplateStatus);
      holder.switchActive = convertView.findViewById(R.id.switchActive);
      holder.btnDelete = convertView.findViewById(R.id.btnDelete);
      convertView.setTag(holder);
    } else {
      holder = (ViewHolder) convertView.getTag();
    }

    final Template template = templates.get(position);

    // ─── Set data ──────────────────────────────────────────────────────
    holder.txtName.setText(template.name);
    holder.txtDesc.setText(template.description);

    if (template.isActive) {
      holder.txtStatus.setText("● Active");
      holder.txtStatus.setTextColor(0xFF4CAF50);
    } else {
      holder.txtStatus.setText("○ Inactive");
      holder.txtStatus.setTextColor(0xFF9E9E9E);
    }

    // ─── Toggle Switch ────────────────────────────────────────────────
    holder.switchActive.setOnCheckedChangeListener(null); // Clear previous
    holder.switchActive.setChecked(template.isActive);
    holder.switchActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
      template.isActive = isChecked;
      templateDao.update(template);
      if (changeListener != null) changeListener.onTemplateChanged();
      Toast.makeText(context, template.name + (isChecked ? " enabled" : " disabled"),
                     Toast.LENGTH_SHORT).show();
    });

    // ─── Delete Button ────────────────────────────────────────────────
    holder.btnDelete.setOnClickListener(v -> {
      new AlertDialog.Builder(context)
        .setTitle("Delete Template")
        .setMessage("Are you sure you want to delete \"" + template.name + "\"?")
        .setPositiveButton("Delete", (dialog, which) -> {
        templateDao.delete(template.id);
        templates.remove(position);
        notifyDataSetChanged();
        if (changeListener != null) changeListener.onTemplateChanged();
        Toast.makeText(context, template.name + " deleted", Toast.LENGTH_SHORT).show();
      })
      .setNegativeButton("Cancel", null)
        .show();
    });

    // ─── Item Click (the whole row, except switch & delete) ──────────
    // The root LinearLayout must have android:clickable="true" in XML
    convertView.setOnClickListener(v -> {
      if (itemClickListener != null) {
        itemClickListener.onItemClick(template);
      }
    });

    return convertView;
  }

  // ─── Helper ─────────────────────────────────────────────────────────────

  public void refreshData(List<Template> newTemplates) {
    this.templates.clear();
    this.templates.addAll(newTemplates);
    notifyDataSetChanged();
  }

  // ─── ViewHolder ─────────────────────────────────────────────────────────

  private static class ViewHolder {
    TextView txtName;
    TextView txtDesc;
    TextView txtStatus;
    Switch switchActive;
    ImageButton btnDelete;
  }
}
