package eu.faircode.netguard.features.shortcut;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.faircode.netguard.R;
import eu.faircode.netguard.Rule;
import eu.faircode.netguard.Util;

public class ShortcutSelectionAdapter extends RecyclerView.Adapter<ShortcutSelectionAdapter.ViewHolder> {

    private final List<Rule> rules;
    private final AppSelectedListener listener;

    interface AppSelectedListener {
        void onSelection(Rule rule);
    }

    ShortcutSelectionAdapter(List<Rule> rules, AppSelectedListener listener) {
        this.rules = rules;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_name, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Rule rule = rules.get(position);
        holder.ivAppIcon.setImageDrawable(Util.getAppIconDrawable(holder.view.getContext(), rule));
        holder.tvAppName.setText(rule.name);
        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSelection(rule);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View view;
        final ImageView ivAppIcon;
        final TextView tvAppName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            view = itemView;
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
        }
    }
}
