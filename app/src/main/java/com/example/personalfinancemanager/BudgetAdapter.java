package com.example.personalfinancemanager;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    public interface OnBudgetLongClickListener {
        void onLongClick(Budget budget, int position);
    }

    private List<Budget> budgets = new ArrayList<>();
    private Map<String, Double> spentMap = new HashMap<>();
    private OnBudgetLongClickListener longClickListener;

    public void setBudgets(List<Budget> budgets) {
        this.budgets = budgets != null ? budgets : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSpentMap(Map<String, Double> map) {
        this.spentMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setOnBudgetLongClickListener(OnBudgetLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget, parent, false);
        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        Budget budget = budgets.get(position);
        double limit = budget.getMonthlyLimit();
        double spent = spentMap.getOrDefault(budget.getCategory(), 0.0);
        int percent = limit > 0 ? (int) ((spent / limit) * 100) : 0;

        holder.tvCategory.setText(budget.getCategory());
        holder.tvLimit.setText(String.format(Locale.getDefault(), "Limit: \u20B9%.0f", limit));
        holder.tvSpent.setText(String.format(Locale.getDefault(), "Spent: \u20B9%.0f", spent));
        holder.tvPercent.setText(String.format(Locale.getDefault(), "%d%%", Math.min(percent, 999)));

        holder.progressBudget.setProgress(Math.min(percent, 100));

        // Color-code the progress bar: green < 80%, amber 80-99%, red 100%+
        int color;
        if (percent >= 100) {
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_red);
        } else if (percent >= 80) {
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_amber);
        } else {
            color = ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_green);
        }
        holder.progressBudget.getProgressDrawable()
                .setColorFilter(color, PorterDuff.Mode.SRC_IN);

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(budget, position);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return budgets.size();
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        TextView tvCategory, tvLimit, tvSpent, tvPercent;
        ProgressBar progressBudget;

        BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvLimit = itemView.findViewById(R.id.tvLimit);
            tvSpent = itemView.findViewById(R.id.tvSpent);
            tvPercent = itemView.findViewById(R.id.tvPercent);
            progressBudget = itemView.findViewById(R.id.progressBudget);
        }
    }
}
