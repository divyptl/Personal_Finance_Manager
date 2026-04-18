package com.example.personalfinancemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SubscriptionAdapter extends RecyclerView.Adapter<SubscriptionAdapter.VH> {

    private List<Subscription> items = Collections.emptyList();
    private final SimpleDateFormat dateFmt =
            new SimpleDateFormat("dd MMM", Locale.getDefault());

    public void setItems(List<Subscription> items) {
        this.items = items != null ? items : Collections.<Subscription>emptyList();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subscription, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Subscription s = items.get(position);
        h.tvMerchant.setText(s.merchant);
        h.tvAmount.setText(formatCurrency(s.amount));
        h.tvCadence.setText(h.itemView.getContext().getString(
                R.string.subscription_cadence_count, s.cadence.label(), s.occurrences));
        h.tvCategory.setText(s.category);
        h.tvLastCharge.setText(h.itemView.getContext().getString(
                R.string.subscription_last_charge, dateFmt.format(new Date(s.lastChargeMillis))));
    }

    @Override
    public int getItemCount() { return items.size(); }

    private String formatCurrency(double amount) {
        return String.format(Locale.getDefault(), "\u20B9%.2f", amount);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvMerchant, tvAmount, tvCadence, tvCategory, tvLastCharge;
        VH(@NonNull View v) {
            super(v);
            tvMerchant   = v.findViewById(R.id.tvMerchant);
            tvAmount     = v.findViewById(R.id.tvAmount);
            tvCadence    = v.findViewById(R.id.tvCadence);
            tvCategory   = v.findViewById(R.id.tvCategory);
            tvLastCharge = v.findViewById(R.id.tvLastCharge);
        }
    }
}
