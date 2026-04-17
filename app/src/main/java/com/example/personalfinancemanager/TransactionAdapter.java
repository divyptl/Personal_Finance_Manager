package com.example.personalfinancemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionHolder> {

    /** Fired on long-press of a transaction row — host shows edit/delete menu. */
    public interface OnTransactionLongClickListener {
        void onLongClick(Transaction transaction);
    }

    // We keep two lists: the full source set last pushed from LiveData, and
    // the filtered list that backs the RecyclerView. Keeping them separate
    // means a new LiveData emission (e.g. a fresh SMS transaction landed)
    // doesn't clobber the user's active search — we just re-apply the filter.
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Transaction> transactions    = new ArrayList<>();
    private String searchQuery = "";
    private OnTransactionLongClickListener longClickListener;

    public void setOnLongClickListener(OnTransactionLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public TransactionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionHolder holder, int position) {
        Transaction current = transactions.get(position);

        holder.tvMessage.setText(current.getMessage());
        holder.tvCategory.setText(current.getCategory());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvDate.setText(sdf.format(new Date(current.getTimestamp())));

        String type = current.getType();
        boolean isExpense = type != null
                && (type.equalsIgnoreCase("Debit") || type.equalsIgnoreCase("expense"));

        if (isExpense) {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "-\u20B9%.2f", current.getAmount()));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_red));
        } else {
            holder.tvAmount.setText(String.format(Locale.getDefault(), "+\u20B9%.2f", current.getAmount()));
            holder.tvAmount.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.accent_green));
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(current);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    public void setTransactions(List<Transaction> transactions) {
        this.allTransactions = transactions != null ? transactions : new ArrayList<>();
        applyFilter();
    }

    /** @return the item at the given adapter position, or null if out of range. */
    public Transaction getTransactionAt(int position) {
        if (position < 0 || position >= transactions.size()) return null;
        return transactions.get(position);
    }

    /** Update the search query. Empty / null shows everything. */
    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        if (searchQuery.isEmpty()) {
            this.transactions = new ArrayList<>(allTransactions);
        } else {
            List<Transaction> filtered = new ArrayList<>();
            for (Transaction t : allTransactions) {
                if (matches(t, searchQuery)) filtered.add(t);
            }
            this.transactions = filtered;
        }
        notifyDataSetChanged();
    }

    private static boolean matches(Transaction t, String q) {
        if (t.getMessage() != null && t.getMessage().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (t.getCategory() != null && t.getCategory().toLowerCase(Locale.ROOT).contains(q)) return true;
        // Allow amount searches like "1500" or "1500.50".
        String amountStr = String.format(Locale.ROOT, "%.2f", t.getAmount());
        return amountStr.contains(q);
    }

    static class TransactionHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage, tvAmount, tvDate, tvCategory;

        TransactionHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvTransactionMessage);
            tvAmount = itemView.findViewById(R.id.tvTransactionAmount);
            tvDate = itemView.findViewById(R.id.tvTransactionDate);
            tvCategory = itemView.findViewById(R.id.tvCategory);
        }
    }
}
