package com.example.personalfinancemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PriceAlertAdapter extends RecyclerView.Adapter<PriceAlertAdapter.VH> {

    public interface Listener {
        void onToggle(PriceAlert alert, boolean enabled);
        void onDelete(PriceAlert alert);
        void onEdit(PriceAlert alert);
    }

    private List<PriceAlert> items = Collections.emptyList();
    private final Listener listener;
    private final SimpleDateFormat fmt =
            new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());

    public PriceAlertAdapter(Listener listener) { this.listener = listener; }

    public void setItems(List<PriceAlert> items) {
        this.items = items != null ? items : Collections.<PriceAlert>emptyList();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_price_alert, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PriceAlert a = items.get(position);
        h.tvTicker.setText(a.getTicker());

        String bounds;
        Double lo = a.getLowerBound();
        Double hi = a.getUpperBound();
        if (lo != null && hi != null) {
            bounds = h.itemView.getContext().getString(
                    R.string.format_bounds_both, lo, hi);
        } else if (hi != null) {
            bounds = h.itemView.getContext().getString(
                    R.string.format_bounds_upper, hi);
        } else if (lo != null) {
            bounds = h.itemView.getContext().getString(
                    R.string.format_bounds_lower, lo);
        } else {
            bounds = h.itemView.getContext().getString(R.string.format_bounds_none);
        }
        h.tvBounds.setText(bounds);

        if (a.getLastNotifiedAt() > 0) {
            h.tvLastNotified.setText(h.itemView.getContext().getString(
                    R.string.format_last_notified,
                    fmt.format(new Date(a.getLastNotifiedAt()))));
        } else {
            h.tvLastNotified.setText(R.string.label_never_notified);
        }

        // Clear listener before programmatic change to prevent feedback loops.
        h.switchEnabled.setOnCheckedChangeListener(null);
        h.switchEnabled.setChecked(a.isEnabled());
        h.switchEnabled.setOnCheckedChangeListener((btn, checked) -> {
            if (listener != null) listener.onToggle(a, checked);
        });

        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(a);
        });
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(a);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTicker, tvBounds, tvLastNotified, btnDelete;
        SwitchCompat switchEnabled;
        VH(@NonNull View v) {
            super(v);
            tvTicker       = v.findViewById(R.id.tvTicker);
            tvBounds       = v.findViewById(R.id.tvBounds);
            tvLastNotified = v.findViewById(R.id.tvLastNotified);
            btnDelete      = v.findViewById(R.id.btnDelete);
            switchEnabled  = v.findViewById(R.id.switchEnabled);
        }
    }
}
